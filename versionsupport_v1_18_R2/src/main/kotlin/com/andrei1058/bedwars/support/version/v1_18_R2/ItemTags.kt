package com.andrei1058.bedwars.support.version.v1_18_R2

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.Locale

/**
 * String tags stored on items.
 *
 * Older version support modules wrote these straight into the NBT compound of the item. Raw NBT
 * is not reachable without NMS on modern versions (items are made of data components), so tags are
 * kept in the item [PersistentDataContainer], which the server persists, copies and sends to
 * the client exactly like it used to do with NBT.
 */
internal class ItemTags(private val plugin: Plugin) {

    /**
     * Keys must be lower-case [a-z0-9._/-]. Tag keys used by this plugin (ex: "BedWars1058", "tierIdentifier")
     * are not, so they are normalized. The same normalization is applied for reads and writes.
     */
    fun key(rawKey: String): NamespacedKey {
        val normalizedKey = StringBuilder(rawKey.length)
        for (character in rawKey.lowercase(Locale.ROOT)) {
            val isLetter = character in 'a'..'z'
            val isDigit = character in '0'..'9'
            val isAllowedSymbol = character == '.' || character == '_' || character == '-' || character == '/'
            normalizedKey.append(if (isLetter || isDigit || isAllowedSymbol) character else '_')
        }
        return NamespacedKey(plugin, normalizedKey.toString())
    }

    /**
     * @return the tag container of the item, or null for missing or air items (they cannot carry tags).
     */
    private fun containerOf(itemStack: ItemStack?): PersistentDataContainer? {
        if (itemStack == null || itemStack.type.isAir) {
            return null
        }
        return itemStack.itemMeta?.persistentDataContainer
    }

    fun has(itemStack: ItemStack?, rawKey: String): Boolean {
        val container = containerOf(itemStack) ?: return false
        return container.has(key(rawKey), PersistentDataType.STRING)
    }

    /**
     * @return the stored value or null when there is none.
     */
    fun get(itemStack: ItemStack?, rawKey: String): String? {
        return containerOf(itemStack)?.get(key(rawKey), PersistentDataType.STRING)
    }

    /**
     * Stores a value. The given item is not modified, a tagged copy is returned
     * (that is how the NBT based implementations behaved).
     */
    fun set(itemStack: ItemStack, rawKey: String, value: String): ItemStack {
        val taggedCopy = itemStack.clone()
        val meta = taggedCopy.itemMeta ?: return taggedCopy
        meta.persistentDataContainer.set(key(rawKey), PersistentDataType.STRING, value)
        taggedCopy.itemMeta = meta
        return taggedCopy
    }

    fun getDouble(itemStack: ItemStack?, rawKey: String): Double? {
        return containerOf(itemStack)?.get(key(rawKey), PersistentDataType.DOUBLE)
    }
}
