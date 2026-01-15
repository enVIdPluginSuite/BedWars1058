package com.andrei1058.bedwars.support.version.v1_18_R2

import org.bukkit.Bukkit
import org.bukkit.Material
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.math.abs

/**
 * Changes the blast resistance of end stone and glass so they can be used as the (in)famous
 * "TNT protection" blocks, exactly what the NMS based modules did.
 *
 * The blast resistance is a plain field of the internal block class, there is no API to change it.
 * To stay independent from the server revision nothing is referenced at compile time:
 *  - the block instance is resolved with CraftMagicNumbers, whose package is read from the running server
 *    (relocated with a revision on Spigot, plain on Paper 1.20.5+ and 26.x);
 *  - the field is looked up by its Mojang name and, if the server is obfuscated, by signature: the only float
 *    field whose value equals [Material.getBlastResistance] for a set of well known blocks.
 */
internal object BlastResistance {

    private const val MOJANG_FIELD_NAME = "explosionResistance"
    private const val COMPARISON_TOLERANCE = 0.0001f

    /**
     * Blocks whose resistance is known through the API, used to recognise the right field on obfuscated servers.
     */
    private val calibrationBlocks = listOf(
        Material.STONE, Material.OBSIDIAN, Material.END_STONE, Material.GLASS, Material.BEDROCK, Material.DIRT
    )

    /**
     * Found once and kept: after the first change, the calibration blocks no longer report the values the
     * API knows, so the field could not be recognised a second time.
     */
    private var cachedResistanceField: Field? = null

    /**
     * @return false when the resistance field could not be found on this server version.
     */
    @Throws(ReflectiveOperationException::class)
    fun apply(endStoneResistance: Float, glassResistance: Float): Boolean {
        val getInternalBlock = findGetInternalBlock()

        val resistanceField = cachedResistanceField ?: findResistanceField(getInternalBlock) ?: return false
        resistanceField.isAccessible = true
        cachedResistanceField = resistanceField

        resistanceField.setFloat(getInternalBlock.invoke(null, Material.END_STONE), endStoneResistance)
        for (glassType in listGlassMaterials()) {
            resistanceField.setFloat(getInternalBlock.invoke(null, glassType), glassResistance)
        }
        return true
    }

    /**
     * Plain glass, tinted glass and every stained glass color (panes are not part of the protection).
     */
    private fun listGlassMaterials(): List<Material> {
        val glassTypes = mutableListOf(Material.GLASS, Material.TINTED_GLASS)
        for (material in Material.values()) {
            val isStainedGlass = !material.isLegacy && material.name.endsWith("_STAINED_GLASS")
            if (isStainedGlass) {
                glassTypes.add(material)
            }
        }
        return glassTypes
    }

    /**
     * Finds `CraftMagicNumbers.getBlock(Material)`, which turns an API material into the internal block.
     */
    @Throws(ClassNotFoundException::class, NoSuchMethodException::class)
    private fun findGetInternalBlock(): Method {
        val craftBukkitPackage = Bukkit.getServer().javaClass.packageName
        val magicNumbers = Class.forName("$craftBukkitPackage.util.CraftMagicNumbers")
        try {
            return magicNumbers.getMethod("getBlock", Material::class.java)
        } catch (exactSignatureMissing: NoSuchMethodException) {
            // Some revisions declare a wider parameter type, accept any static single argument "getBlock".
            val widerMatch = magicNumbers.methods.firstOrNull { method ->
                method.name == "getBlock"
                        && method.parameterCount == 1
                        && method.parameterTypes[0].isAssignableFrom(Material::class.java)
                        && Modifier.isStatic(method.modifiers)
            }
            return widerMatch ?: throw exactSignatureMissing
        }
    }

    /**
     * Looks the field up by its Mojang name first and, on obfuscated servers, by its values.
     */
    @Throws(ReflectiveOperationException::class)
    private fun findResistanceField(getInternalBlock: Method): Field? {
        val stoneBlockClass = getInternalBlock.invoke(null, Material.STONE).javaClass
        return findFieldByName(stoneBlockClass) ?: findFieldByCalibration(stoneBlockClass, getInternalBlock)
    }

    private fun findFieldByName(blockClass: Class<*>): Field? {
        // the field lives in a parent class (Block -> BlockBehaviour), so walk up
        for (type in classHierarchy(blockClass)) {
            val field = try {
                type.getDeclaredField(MOJANG_FIELD_NAME)
            } catch (notInThisClass: NoSuchFieldException) {
                continue
            }
            if (isInstanceFloat(field)) {
                return field
            }
        }
        return null
    }

    /**
     * Picks the only float field that holds, for every calibration block, the same value as the API reports.
     */
    @Throws(ReflectiveOperationException::class)
    private fun findFieldByCalibration(blockClass: Class<*>, getInternalBlock: Method): Field? {
        for (type in classHierarchy(blockClass)) {
            for (candidate in type.declaredFields) {
                if (!isInstanceFloat(candidate)) {
                    continue
                }
                candidate.isAccessible = true
                if (matchesApiResistance(candidate, getInternalBlock)) {
                    return candidate
                }
            }
        }
        return null
    }

    @Throws(ReflectiveOperationException::class)
    private fun matchesApiResistance(candidate: Field, getInternalBlock: Method): Boolean {
        for (block in calibrationBlocks) {
            val fieldValue = candidate.getFloat(getInternalBlock.invoke(null, block))
            if (abs(fieldValue - block.blastResistance) > COMPARISON_TOLERANCE) {
                return false
            }
        }
        return true
    }

    /** The class followed by its parents, up to (excluding) Object. */
    private fun classHierarchy(startClass: Class<*>): Sequence<Class<*>> {
        return generateSequence(startClass) { it.superclass }.takeWhile { it != Any::class.java }
    }

    private fun isInstanceFloat(field: Field): Boolean {
        return field.type == Float::class.javaPrimitiveType && !Modifier.isStatic(field.modifiers)
    }
}
