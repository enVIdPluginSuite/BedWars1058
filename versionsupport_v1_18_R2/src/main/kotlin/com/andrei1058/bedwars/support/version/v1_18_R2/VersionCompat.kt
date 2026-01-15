package com.andrei1058.bedwars.support.version.v1_18_R2

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.command.CommandMap
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Locale

/**
 * Helpers for the parts of the Bukkit API that changed shape between 1.18 and 26.x.
 *
 * Version support modules are compiled against one API version but run on every newer one, so anything that
 * turned from an enum into an interface or was renamed (sounds, attributes, particles) is resolved here by name,
 * at runtime, instead of being referenced directly.
 */
object VersionCompat {

    /** Sound is an enum up to 1.21.1 and an interface (with a static valueOf) afterwards. */
    private val soundValueOf: Method? = try {
        Sound::class.java.getMethod("valueOf", String::class.java)
    } catch (noSuchMethod: NoSuchMethodException) {
        null
    }

    private var cachedCommandMap: CommandMap? = null

    /**
     * Same contract as the old `Sound.valueOf`: throws if the sound does not exist.
     */
    @JvmStatic
    fun sound(name: String?): Sound {
        requireNotNull(name) { "Sound name is null" }

        val byConstantName = soundByConstantName(name)
        if (byConstantName != null) {
            return byConstantName
        }

        // not a constant name, try the registry (ex: "entity.player.levelup")
        val registryKey = NamespacedKey.fromString(name.lowercase(Locale.ROOT))
        val sound = if (registryKey == null) null else registryLookup("SOUNDS", registryKey) as Sound?
        return sound ?: throw IllegalArgumentException("No sound named $name")
    }

    private fun soundByConstantName(name: String): Sound? {
        val valueOf = soundValueOf ?: return null
        return try {
            valueOf.invoke(null, name) as Sound?
        } catch (unknownConstantName: InvocationTargetException) {
            null
        } catch (reflectionFailure: ReflectiveOperationException) {
            throw IllegalArgumentException("Cannot resolve sound $name", reflectionFailure)
        }
    }

    /**
     * Attributes were renamed in 1.21.3 (generic.max_health became max_health) and the constants changed with
     * them. Registry keys are the stable thing on new servers, enum constants (`GENERIC_MAX_HEALTH`) on old ones.
     *
     * @param name registry style name without namespace, ex: "max_health"
     */
    @JvmStatic
    fun attribute(name: String): Attribute? {
        val fromRegistry = registryLookup("ATTRIBUTE", NamespacedKey.minecraft(name))
            ?: registryLookup("ATTRIBUTE", NamespacedKey.minecraft("generic.$name"))
        if (fromRegistry != null) {
            return fromRegistry as Attribute
        }

        val constantName = "GENERIC_" + name.uppercase(Locale.ROOT)
        return Attribute::class.java.enumConstants?.firstOrNull { (it as Enum<*>).name == constantName }
    }

    /**
     * Particle constants were renamed in 1.20.5, the first name that exists is returned.
     */
    @JvmStatic
    fun particle(vararg possibleNames: String): Particle {
        for (name in possibleNames) {
            try {
                return Particle.valueOf(name)
            } catch (unknownName: IllegalArgumentException) {
                // try the next name
            }
        }
        throw IllegalArgumentException("None of the particles exists: " + possibleNames.joinToString())
    }

    /**
     * Reads `Registry.<registryField>.get(key)` without linking to Registry, which does not have every
     * registry on every version.
     *
     * @return null when the registry or the key does not exist on this server.
     */
    private fun registryLookup(registryField: String, key: NamespacedKey): Any? {
        return try {
            val registryClass = Class.forName("org.bukkit.Registry")
            val registry = registryClass.getField(registryField).get(null) ?: return null
            registryClass.getMethod("get", NamespacedKey::class.java).invoke(registry, key)
        } catch (notAvailable: ReflectiveOperationException) {
            null
        }
    }

    @JvmStatic
    fun commandMap(): CommandMap? {
        if (cachedCommandMap == null) {
            // CraftServer#getCommandMap is not part of the Bukkit interface
            cachedCommandMap = try {
                val getCommandMap = Bukkit.getServer().javaClass.getMethod("getCommandMap")
                getCommandMap.invoke(Bukkit.getServer()) as CommandMap
            } catch (reflectionFailure: ReflectiveOperationException) {
                return null
            }
        }
        return cachedCommandMap
    }

    /**
     * The Paper mob goal API is used to replace the vanilla AI of team golems and silverfish.
     */
    @JvmStatic
    fun isPaper(): Boolean {
        return try {
            Class.forName("com.destroystokyo.paper.entity.ai.MobGoals")
            true
        } catch (notPaper: ClassNotFoundException) {
            false
        }
    }
}
