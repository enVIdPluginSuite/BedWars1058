package com.andrei1058.bedwars.support.version.v1_21_R1;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * String tags stored on items.
 * <p>
 * Older version support modules wrote these straight into the NBT compound of the item. Raw NBT
 * is not reachable without NMS on modern versions (items are made of data components), so tags are
 * kept in the item {@link PersistentDataContainer}, which the server persists, copies and sends to
 * the client exactly like it used to do with NBT.
 */
final class ItemTags {

    private final Plugin plugin;

    ItemTags(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Keys must be lower-case [a-z0-9._/-]. Tag keys used by this plugin (ex: "BedWars1058", "tierIdentifier")
     * are not, so they are normalized. The same normalization is applied for reads and writes.
     */
    NamespacedKey key(@NotNull String rawKey) {
        StringBuilder normalizedKey = new StringBuilder(rawKey.length());
        for (char character : rawKey.toLowerCase(Locale.ROOT).toCharArray()) {
            boolean isLetter = character >= 'a' && character <= 'z';
            boolean isDigit = character >= '0' && character <= '9';
            boolean isAllowedSymbol = character == '.' || character == '_' || character == '-' || character == '/';
            normalizedKey.append(isLetter || isDigit || isAllowedSymbol ? character : '_');
        }
        return new NamespacedKey(plugin, normalizedKey.toString());
    }

    /**
     * @return the tag container of the item, or null for missing or air items (they cannot carry tags).
     */
    private @Nullable PersistentDataContainer containerOf(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        ItemMeta meta = itemStack.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer();
    }

    boolean has(@Nullable ItemStack itemStack, @NotNull String rawKey) {
        PersistentDataContainer container = containerOf(itemStack);
        return container != null && container.has(key(rawKey), PersistentDataType.STRING);
    }

    /**
     * @return the stored value or null when there is none.
     */
    @Nullable String get(@Nullable ItemStack itemStack, @NotNull String rawKey) {
        PersistentDataContainer container = containerOf(itemStack);
        return container == null ? null : container.get(key(rawKey), PersistentDataType.STRING);
    }

    /**
     * Stores a value. The given item is not modified, a tagged copy is returned
     * (that is how the NBT based implementations behaved).
     */
    @NotNull ItemStack set(@NotNull ItemStack itemStack, @NotNull String rawKey, @NotNull String value) {
        ItemStack taggedCopy = itemStack.clone();
        ItemMeta meta = taggedCopy.getItemMeta();
        if (meta == null) {
            return taggedCopy;
        }
        meta.getPersistentDataContainer().set(key(rawKey), PersistentDataType.STRING, value);
        taggedCopy.setItemMeta(meta);
        return taggedCopy;
    }

    @Nullable Double getDouble(@Nullable ItemStack itemStack, @NotNull String rawKey) {
        PersistentDataContainer container = containerOf(itemStack);
        return container == null ? null : container.get(key(rawKey), PersistentDataType.DOUBLE);
    }
}
