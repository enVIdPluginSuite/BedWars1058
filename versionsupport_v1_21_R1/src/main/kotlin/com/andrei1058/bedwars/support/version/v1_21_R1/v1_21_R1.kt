package com.andrei1058.bedwars.support.version.v1_21_R1

import com.andrei1058.bedwars.support.version.v1_20_R4.v1_20_R4
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.damage.DamageSource
import org.bukkit.entity.Fireball
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector

/**
 * Version support for Minecraft 1.21.x and 26.x.
 *
 * Everything shared with the older versions is inherited (see `v1_18_R2`, the API-only base). This class only
 * holds what the 1.21 API does differently, compiled against the 1.21 API:
 *  - [InventoryView][org.bukkit.inventory.InventoryView] became an interface;
 *  - damage can be dealt with a typed [DamageSource];
 *  - player profiles and fireball power have dedicated API.
 *
 * The class is `open` because every newer revision module is an (empty) subclass of the previous one.
 */
@Suppress("ClassName", "unused")
open class v1_21_R1(plugin: Plugin, name: String) : v1_20_R4(plugin, name) {

    // InventoryView became an interface in 1.21, code compiled against the old class breaks on it.
    override fun getInventoryName(event: InventoryEvent): String = event.view.title

    override fun getTopInventory(player: Player?): Inventory? = player?.openInventory?.topInventory

    override fun getTopInventory(event: InventoryEvent): Inventory = event.view.topInventory

    override fun getViewPlayer(event: InventoryEvent): HumanEntity = event.view.player

    override fun voidKill(player: Player) {
        val voidDamage = Registry.DAMAGE_TYPE.get(NamespacedKey.minecraft("out_of_world"))
        if (voidDamage == null) {
            player.health = 0.0
            return
        }
        // damage with the "void" source so death messages and kill credits match the old behaviour
        val voidSource = DamageSource.builder(voidDamage).build()
        player.damage(VOID_DAMAGE, voidSource)
    }

    override fun getPlayerHead(player: Player, copyTagFrom: ItemStack?): ItemStack {
        val head = super.getPlayerHead(player, copyTagFrom)
        // the full profile keeps the skin textures, the owning player alone only has name and id
        val skullMeta = head.itemMeta as? SkullMeta ?: return head
        skullMeta.ownerProfile = player.playerProfile
        head.itemMeta = skullMeta
        return head
    }

    override fun setFireballDirection(fireball: Fireball, vector: Vector): Fireball {
        // Same magnitude the NMS code wrote into the fireball acceleration fields.
        val accelerationScale = 0.1
        fireball.setPower(Vector(vector.x * accelerationScale, vector.y * accelerationScale, vector.z * accelerationScale))
        return fireball
    }

    override fun getVersion(): Int {
        // impacts on sidebar
        // experimental score placeholders
        return 10
    }

    private companion object {
        const val VOID_DAMAGE = 1000.0
    }
}
