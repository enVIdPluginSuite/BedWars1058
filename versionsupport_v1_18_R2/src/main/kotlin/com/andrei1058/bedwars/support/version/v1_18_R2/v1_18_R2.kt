package com.andrei1058.bedwars.support.version.v1_18_R2

import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.arena.shop.ShopHolo
import com.andrei1058.bedwars.api.arena.team.ITeam
import com.andrei1058.bedwars.api.arena.team.TeamColor
import com.andrei1058.bedwars.api.entity.Despawnable
import com.andrei1058.bedwars.api.events.player.PlayerKillEvent
import com.andrei1058.bedwars.api.language.Language
import com.andrei1058.bedwars.api.language.Messages
import com.andrei1058.bedwars.api.server.VersionSupport
import com.andrei1058.bedwars.support.version.common.VersionCommon
import com.andrei1058.bedwars.support.version.v1_18_R2.despawnable.DespawnableAttributes
import com.andrei1058.bedwars.support.version.v1_18_R2.despawnable.DespawnableFactory
import com.andrei1058.bedwars.support.version.v1_18_R2.despawnable.DespawnableType
import com.andrei1058.bedwars.support.version.v1_18_R2.despawnable.HurtTracker
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.block.data.type.Bed
import org.bukkit.block.data.type.Ladder
import org.bukkit.block.data.type.WallSign
import org.bukkit.command.Command
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EnderDragon
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Fireball
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.entity.Villager
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.io.File
import java.io.IOException
import java.util.Properties
import java.util.logging.Level

/**
 * Version support for Minecraft 1.18.2 and newer.
 *
 * This implementation deliberately has no NMS/CraftBukkit dependency. Everything the older, per-revision
 * modules did through server internals is done through the Bukkit/Paper API instead, or - when the API
 * has no equivalent - through reflection that is resolved at runtime and does not rely on obfuscated names.
 *
 * It is compiled against the 1.18.2 API, the oldest one it supports, and every newer revision module extends
 * it (v1_19_R2 extends v1_18_R2, ..., v1_21_R1 extends v1_20_R4). A newer module only overrides what the API
 * does differently on its version, see for example `v1_21_R1`.
 *
 * Code in this class must stay binary compatible with the newest servers: API types that changed shape later
 * (Sound, Attribute, InventoryView, ...) are only touched through [VersionCompat] or overridden by the module of
 * the version that changed them.
 */
@Suppress("ClassName", "unused")
open class v1_18_R2(plugin: Plugin, name: String) : VersionSupport(plugin, name) {

    private val tags = ItemTags(plugin)
    private val hurtTracker = HurtTracker(this)
    private val despawnableFactory = DespawnableFactory(this, hurtTracker)

    init {
        loadDefaultEffects()
    }

    override fun registerVersionListeners() {
        VersionCommon(this)
        Bukkit.getPluginManager().registerEvents(hurtTracker, plugin)
    }

    override fun registerCommand(name: String, cmd: Command) {
        val commandMap = VersionCompat.commandMap()
        if (commandMap == null) {
            plugin.logger.severe("Could not access the server command map. /$name was not registered.")
            return
        }
        commandMap.register(name, cmd)
    }

    override fun isBukkitCommandRegistered(name: String): Boolean {
        val commandMap = VersionCompat.commandMap()
        return commandMap != null && commandMap.getCommand(name) != null
    }

    // ---------------------------------------------------------------------------------------------
    // items
    // ---------------------------------------------------------------------------------------------

    override fun getTag(itemStack: ItemStack?, key: String): String? = tags.get(itemStack, key)

    override fun addCustomData(itemStack: ItemStack, data: String): ItemStack =
        tags.set(itemStack, PLUGIN_TAG_GENERIC_KEY, data)

    override fun setTag(itemStack: ItemStack, key: String, value: String): ItemStack =
        tags.set(itemStack, key, value)

    override fun isCustomBedWarsItem(itemStack: ItemStack?): Boolean = tags.has(itemStack, PLUGIN_TAG_GENERIC_KEY)

    override fun getCustomData(itemStack: ItemStack?): String {
        // the NBT based versions returned an empty string for an absent tag
        return tags.get(itemStack, PLUGIN_TAG_GENERIC_KEY) ?: ""
    }

    override fun getShopUpgradeIdentifier(itemStack: ItemStack?): String {
        return tags.get(itemStack, PLUGIN_TAG_TIER_KEY) ?: "null"
    }

    override fun setShopUpgradeIdentifier(itemStack: ItemStack, identifier: String): ItemStack =
        tags.set(itemStack, PLUGIN_TAG_TIER_KEY, identifier)

    override fun getDamage(itemStack: ItemStack?): Double {
        // Same as the NMS versions: a numeric tag named "generic.attackDamage" if present, 0 otherwise.
        return tags.getDouble(itemStack, ATTACK_DAMAGE_TAG) ?: 0.0
    }

    override fun getItemInHand(player: Player): ItemStack = player.inventory.itemInMainHand

    override fun minusAmount(player: Player, itemStack: ItemStack, amount: Int) {
        val remainingAmount = itemStack.amount - amount

        if (remainingAmount <= 0) {
            // the whole stack is used up: remove it from wherever it is held
            val heldInOffHand = player.inventory.itemInOffHand == itemStack
            if (heldInOffHand) {
                player.inventory.setItemInOffHand(null)
            } else {
                player.inventory.removeItem(itemStack)
            }
            return
        }

        itemStack.amount = remainingAmount
        player.updateInventory()
    }

    private fun nameEndsWith(itemStack: ItemStack?, suffix: String): Boolean {
        return itemStack != null && itemStack.type.name.endsWith(suffix)
    }

    override fun isArmor(itemStack: ItemStack?): Boolean {
        val isArmorPiece = nameEndsWith(itemStack, "_HELMET") || nameEndsWith(itemStack, "_CHESTPLATE")
                || nameEndsWith(itemStack, "_LEGGINGS") || nameEndsWith(itemStack, "_BOOTS")
        val isElytra = itemStack != null && itemStack.type == Material.ELYTRA
        return isArmorPiece || isElytra
    }

    override fun isTool(itemStack: ItemStack?): Boolean {
        // pickaxes, axes, shovels and hoes
        return nameEndsWith(itemStack, "_PICKAXE") || nameEndsWith(itemStack, "_AXE")
                || nameEndsWith(itemStack, "_SHOVEL") || nameEndsWith(itemStack, "_HOE")
    }

    override fun isSword(itemStack: ItemStack?): Boolean = nameEndsWith(itemStack, "_SWORD")

    override fun isAxe(itemStack: ItemStack?): Boolean = nameEndsWith(itemStack, "_AXE")

    override fun isBow(itemStack: ItemStack?): Boolean = itemStack != null && itemStack.type == Material.BOW

    override fun isProjectile(itemStack: ItemStack?): Boolean {
        // The NMS versions asked the item for the entity it is displayed as, which is never a projectile for
        // a plain inventory item. Kept as is: callers also filter by material through Misc#isProjectile.
        return false
    }

    override fun isInvisibilityPotion(itemStack: ItemStack): Boolean {
        if (itemStack.type != Material.POTION) return false
        val potionMeta = itemStack.itemMeta as? PotionMeta ?: return false
        return potionMeta.hasCustomEffects() && potionMeta.hasCustomEffect(PotionEffectType.INVISIBILITY)
    }

    override fun colourItem(itemStack: ItemStack?, bedWarsTeam: ITeam): ItemStack? {
        if (itemStack == null) return null

        val teamColor = bedWarsTeam.color
        val material = itemStack.type
        val materialName = material.toString()
        val amount = itemStack.amount

        // the pane check must come first: panes are named like stained glass too
        return when {
            isBed(material) -> ItemStack(teamColor.bedMaterial(), amount)
            materialName.contains("_STAINED_GLASS_PANE") -> ItemStack(teamColor.glassPaneMaterial(), amount)
            materialName.contains("STAINED_GLASS") || materialName == "GLASS" ->
                ItemStack(teamColor.glassMaterial(), amount)
            materialName.contains("_TERRACOTTA") -> ItemStack(teamColor.glazedTerracottaMaterial(), amount)
            materialName.contains("_WOOL") -> ItemStack(teamColor.woolMaterial(), amount)
            else -> itemStack
        }
    }

    override fun createItemStack(material: String, amount: Int, data: Short): ItemStack {
        return try {
            ItemStack(Material.valueOf(material), amount)
        } catch (invalidMaterialName: Exception) {
            plugin.logger.log(Level.WARNING, "$material is not a valid ${getName()} material!")
            ItemStack(Material.BEDROCK)
        }
    }

    override fun getPlayerHead(player: Player, copyTagFrom: ItemStack?): ItemStack {
        val head = ItemStack(materialPlayerHead())
        val headMeta = head.itemMeta

        if (copyTagFrom != null && copyTagFrom.hasItemMeta()) {
            copyDisplayData(copyTagFrom.itemMeta, headMeta)
        }
        if (headMeta is SkullMeta) {
            headMeta.owningPlayer = player
        }

        head.itemMeta = headMeta
        return head
    }

    /**
     * The NMS versions copied the whole tag of the source item: name, lore, enchants, flags and custom tags.
     */
    private fun copyDisplayData(source: ItemMeta?, target: ItemMeta?) {
        if (source == null || target == null) return

        if (source.hasDisplayName()) target.setDisplayName(source.displayName)
        if (source.hasLore()) target.lore = source.lore
        for ((enchantment, level) in source.enchants) {
            target.addEnchant(enchantment, level, true)
        }
        target.addItemFlags(*source.itemFlags.toTypedArray())
        if (source.isUnbreakable) target.isUnbreakable = true
        if (source.hasCustomModelData()) target.setCustomModelData(source.customModelData)

        for (tagKey in source.persistentDataContainer.keys) {
            copyTag(source.persistentDataContainer, target.persistentDataContainer, tagKey)
        }
    }

    /**
     * Item tags do not say which type they hold, so the types used by this plugin are tried in turn.
     */
    private fun copyTag(from: PersistentDataContainer, to: PersistentDataContainer, tagKey: NamespacedKey) {
        for (type in TAG_TYPES) {
            if (copyTagIfOfType(from, to, tagKey, type)) {
                return
            }
        }
    }

    private fun <T : Any> copyTagIfOfType(
        from: PersistentDataContainer, to: PersistentDataContainer,
        tagKey: NamespacedKey, type: PersistentDataType<*, T>
    ): Boolean {
        if (!from.has(tagKey, type)) {
            return false
        }
        val value = from.get(tagKey, type) ?: return false
        to.set(tagKey, type, value)
        return true
    }

    override fun getInventoryName(event: InventoryEvent): String = event.view.title

    // InventoryView became an interface in 1.21, code compiled against the old class breaks on it.
    // The 1.21 module overrides these three methods with versions compiled against the new API.
    override fun getTopInventory(player: Player?): Inventory? = player?.openInventory?.topInventory

    override fun getTopInventory(event: InventoryEvent): Inventory = event.view.topInventory

    override fun getViewPlayer(event: InventoryEvent): HumanEntity = event.view.player

    override fun getSound(name: String?): Sound = VersionCompat.sound(name)

    override fun setUnbreakable(itemMeta: ItemMeta) {
        itemMeta.isUnbreakable = true
    }

    // ---------------------------------------------------------------------------------------------
    // players
    // ---------------------------------------------------------------------------------------------

    override fun sendTitle(player: Player, title: String?, subtitle: String?, fadeIn: Int, stay: Int, fadeOut: Int) {
        // a null text would keep the previous title, a space clears it
        player.sendTitle(title ?: " ", subtitle ?: " ", fadeIn, stay, fadeOut)
    }

    override fun playAction(player: Player, text: String) {
        player.spigot().sendMessage(
            ChatMessageType.ACTION_BAR,
            TextComponent(ChatColor.translateAlternateColorCodes('&', text))
        )
    }

    override fun hideEntity(entity: Entity, viewer: Player) {
        viewer.hideEntity(plugin, entity)
    }

    override fun voidKill(player: Player) {
        // The death handling reads the last damage cause to tell a void death from a fight.
        player.lastDamageCause = EntityDamageEvent(player, EntityDamageEvent.DamageCause.VOID, VOID_DAMAGE)
        player.health = 0.0
    }

    override fun hideArmor(victim: Player, receiver: Player) {
        // only what the receiver is shown changes, the victim keeps wearing the armor
        val noItem = ItemStack(Material.AIR)
        for (slot in ARMOR_SLOTS) {
            receiver.sendEquipmentChange(victim, slot, noItem)
        }
    }

    override fun showArmor(victim: Player, receiver: Player) {
        val victimInventory = victim.inventory
        receiver.sendEquipmentChange(victim, EquipmentSlot.HEAD, orAir(victimInventory.helmet))
        receiver.sendEquipmentChange(victim, EquipmentSlot.CHEST, orAir(victimInventory.chestplate))
        receiver.sendEquipmentChange(victim, EquipmentSlot.LEGS, orAir(victimInventory.leggings))
        receiver.sendEquipmentChange(victim, EquipmentSlot.FEET, orAir(victimInventory.boots))
    }

    private fun orAir(itemStack: ItemStack?): ItemStack = itemStack ?: ItemStack(Material.AIR)

    /**
     * Makes everyone around a freshly respawned player see them (and the other way around).
     *
     * The NMS versions sent the spawn packets by hand. The API equivalent is hiding and showing the entity
     * again, which makes the server send the full spawn data (position, equipment, velocity) to that viewer.
     */
    override fun sendPlayerSpawnPackets(respawned: Player?, arena: IArena?) {
        if (respawned == null) return
        if (arena == null) return
        if (!arena.isPlayer(respawned)) return

        // if the method was used while the player was still on the re-spawning screen
        if (arena.respawnSessions.containsKey(respawned)) return

        for (otherPlayer in arena.players) {
            if (otherPlayer == null) continue
            if (otherPlayer == respawned) continue
            // players on the re-spawning screen are not in the world yet
            if (arena.respawnSessions.containsKey(otherPlayer)) continue
            if (!isWithinRenderDistance(respawned, otherPlayer, arena)) continue

            // the other player sees the respawned one
            resendEntity(otherPlayer, respawned)

            // the respawned player sees the other one, without armor if it is invisible
            if (otherPlayer.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                hideArmor(otherPlayer, respawned)
            } else {
                resendEntity(respawned, otherPlayer)
                showArmor(otherPlayer, respawned)
            }
        }

        for (spectator in arena.spectators) {
            if (spectator == null) continue
            if (spectator == respawned) continue

            // spectators are never visible to players
            respawned.hidePlayer(plugin, spectator)
            if (isWithinRenderDistance(respawned, spectator, arena)) {
                resendEntity(spectator, respawned)
            }
        }
    }

    private fun isWithinRenderDistance(first: Player, second: Player, arena: IArena): Boolean {
        return first.world == second.world
                && first.location.distance(second.location) <= arena.renderDistance
    }

    /**
     * Makes the viewer receive the entity again (spawn, rotation, velocity and equipment).
     */
    private fun resendEntity(viewer: Player, entity: Player) {
        viewer.hideEntity(plugin, entity)
        viewer.showEntity(plugin, entity)
    }

    override fun setCollide(player: Player, arena: IArena?, value: Boolean) {
        player.isCollidable = value
        if (arena == null) return
        arena.updateSpectatorCollideRule(player, value)
    }

    override fun setSource(tnt: TNTPrimed, owner: Player) {
        tnt.source = owner
    }

    override fun spigotShowPlayer(victim: Player, receiver: Player) {
        receiver.showPlayer(plugin, victim)
    }

    override fun spigotHidePlayer(victim: Player, receiver: Player) {
        receiver.hidePlayer(plugin, victim)
    }

    // ---------------------------------------------------------------------------------------------
    // world
    // ---------------------------------------------------------------------------------------------

    override fun registerEntities() {
        // nothing to register: no custom entity classes are needed without NMS
    }

    override fun spawnShop(location: Location, nameMessage: String, players: List<Player>, arena: IArena) {
        val world = location.world ?: return

        spawnShopVillager(world, location)

        // every player reads the name in their own language, so each gets their own hologram
        for (viewer in players) {
            val nameLines = Language.getMsg(viewer, nameMessage).split(",")
            val languageIso = Language.getPlayerLanguage(viewer).iso

            val hasSubtitle = nameLines.size > 1
            if (hasSubtitle) {
                val title = createHologramLine(nameLines[0], location.clone().add(0.0, TITLE_LINE_HEIGHT, 0.0))
                val subtitle = createHologramLine(nameLines[1], location.clone().add(0.0, SUBTITLE_LINE_HEIGHT, 0.0))
                ShopHolo(languageIso, title, subtitle, location, arena)
            } else {
                val line = createHologramLine(nameLines[0], location.clone().add(0.0, SINGLE_LINE_HEIGHT, 0.0))
                ShopHolo(languageIso, line, null, location, arena)
            }
        }

        for (hologram in ShopHolo.getShopHolo()) {
            if (hologram.a === arena) {
                hologram.update()
            }
        }
    }

    /**
     * A villager that cannot move, be hurt or make noise: the click target of the shop.
     */
    private fun spawnShopVillager(world: World, location: Location) {
        val villager = world.spawnEntity(location, EntityType.VILLAGER) as Villager
        villager.setAI(false)
        villager.removeWhenFarAway = false
        villager.isCollidable = false
        villager.isInvulnerable = true
        villager.isSilent = true
    }

    private fun createHologramLine(text: String, location: Location): ArmorStand? {
        val world = location.world ?: return null

        val hologramLine = world.spawn(location, ArmorStand::class.java)
        hologramLine.setGravity(false)
        hologramLine.isVisible = false
        hologramLine.isCustomNameVisible = true
        hologramLine.customName = text
        return hologramLine
    }

    override fun spawnSilverfish(
        location: Location, bedWarsTeam: ITeam, speed: Double, health: Double, despawn: Int, damage: Double
    ) {
        spawnDespawnable(
            DespawnableType.SILVERFISH, location, bedWarsTeam, speed, health, damage, despawn,
            Messages.SHOP_UTILITY_NPC_SILVERFISH_NAME,
            PlayerKillEvent.PlayerKillCause.SILVERFISH_FINAL_KILL,
            PlayerKillEvent.PlayerKillCause.SILVERFISH
        )
    }

    override fun spawnIronGolem(location: Location, bedWarsTeam: ITeam, speed: Double, health: Double, despawn: Int) {
        val golemAttackDamage = 4.0
        spawnDespawnable(
            DespawnableType.IRON_GOLEM, location, bedWarsTeam, speed, health, golemAttackDamage, despawn,
            Messages.SHOP_UTILITY_NPC_IRON_GOLEM_NAME,
            PlayerKillEvent.PlayerKillCause.IRON_GOLEM_FINAL_KILL,
            PlayerKillEvent.PlayerKillCause.IRON_GOLEM
        )
    }

    /**
     * Spawns a team mob and registers it as a [Despawnable], which removes it after `despawnSeconds`
     * and keeps its name up to date.
     */
    private fun spawnDespawnable(
        type: DespawnableType, location: Location, team: ITeam, speed: Double, health: Double,
        damage: Double, despawnSeconds: Int, nameMessage: String,
        finalKillCause: PlayerKillEvent.PlayerKillCause, regularKillCause: PlayerKillEvent.PlayerKillCause
    ) {
        val attributes = DespawnableAttributes(type, speed, health, damage, despawnSeconds)
        val mob: LivingEntity = despawnableFactory.spawn(attributes, location, team)

        Despawnable(mob, team, despawnSeconds, nameMessage, finalKillCause, regularKillCause)
    }

    override fun spawnDragon(location: Location?, team: ITeam) {
        val world = location?.world
        if (location == null || world == null) {
            plugin.logger.log(Level.WARNING, "Could not spawn Dragon. Location is null")
            return
        }
        val dragon = world.spawnEntity(location, EntityType.ENDER_DRAGON) as EnderDragon
        dragon.phase = EnderDragon.Phase.CIRCLING
    }

    override fun colorBed(team: ITeam) {
        // Parity note: the NMS based modules tested a BlockState against the BlockData type Bed, which is never
        // true, so this has always been a no-op. Kept identical on purpose.
        for (offsetX in -1..1) {
            for (offsetZ in -1..1) {
                val nearbyBlock: BlockState = team.bed.clone().add(offsetX.toDouble(), 0.0, offsetZ.toDouble())
                    .block.state
                val blockState: Any = nearbyBlock
                if (blockState is Bed) {
                    nearbyBlock.type = team.color.bedMaterial()
                    nearbyBlock.update()
                }
            }
        }
    }

    override fun registerTntWhitelist(endStoneBlastResistance: Float, glassBlastResistance: Float) {
        try {
            if (!BlastResistance.apply(endStoneBlastResistance, glassBlastResistance)) {
                plugin.logger.warning(
                    "Could not find the block blast resistance on this server version. " +
                            "Glass and end stone TNT protection is disabled."
                )
            }
        } catch (failure: ReflectiveOperationException) {
            plugin.logger.log(Level.WARNING, "Could not apply TNT protection to glass and end stone.", failure)
        } catch (failure: RuntimeException) {
            plugin.logger.log(Level.WARNING, "Could not apply TNT protection to glass and end stone.", failure)
        }
    }

    override fun setBlockTeamColor(block: Block, teamColor: TeamColor) {
        val materialName = block.type.toString()

        when {
            materialName.contains("STAINED_GLASS") || materialName == "GLASS" -> block.type = teamColor.glassMaterial()
            materialName.contains("_TERRACOTTA") -> block.type = teamColor.glazedTerracottaMaterial()
            materialName.contains("_WOOL") -> block.type = teamColor.woolMaterial()
        }
    }

    override fun setJoinSignBackground(signState: BlockState, material: Material) {
        val wallSign = signState.blockData as? WallSign ?: return
        // the block the sign is attached to is behind it
        val behindTheSign = wallSign.facing.oppositeFace
        signState.block.getRelative(behindTheSign).type = material
    }

    override fun placeTowerBlocks(origin: Block, arena: IArena, color: TeamColor, x: Int, y: Int, z: Int) {
        val towerBlock = origin.getRelative(x, y, z)
        towerBlock.type = color.woolMaterial()
        // placed blocks are the ones players may break and that get reset after the game
        arena.addPlacedBlock(towerBlock)
    }

    override fun placeLadder(origin: Block, x: Int, y: Int, z: Int, arena: IArena, ladderData: Int) {
        val ladderBlock = origin.getRelative(x, y, z)
        ladderBlock.type = Material.LADDER
        arena.addPlacedBlock(ladderBlock)

        val facing = ladderFacing(ladderData) ?: return
        val ladder = ladderBlock.blockData as Ladder
        ladder.facing = facing
        ladderBlock.blockData = ladder
    }

    /**
     * Translates the legacy ladder data value (the old block "data" byte) to the direction it faces.
     *
     * @return null for values that are not a ladder direction, the ladder is left as placed.
     */
    private fun ladderFacing(legacyLadderData: Int): BlockFace? = when (legacyLadderData) {
        2 -> BlockFace.NORTH
        3 -> BlockFace.SOUTH
        4 -> BlockFace.WEST
        5 -> BlockFace.EAST
        else -> null
    }

    override fun setFireballDirection(fireball: Fireball, vector: Vector): Fireball {
        // the fireball accelerates along the (normalized) direction by 0.1 per tick, like the NMS code did
        fireball.direction = vector
        return fireball
    }

    override fun playRedStoneDot(player: Player) {
        // a red dot floating above the player's head, shown to everyone but the player
        val aboveHead = player.location.add(0.0, 2.6, 0.0)
        val redDust = Particle.DustOptions(Color.RED, 1f)
        for (viewer in player.world.players) {
            if (viewer == player) continue
            viewer.spawnParticle(redstoneDustParticle(), aboveHead, 1, 0.0, 0.0, 0.0, 0.0, redDust)
        }
    }

    /** The colored dust particle was called REDSTONE until 1.20.4. */
    private fun redstoneDustParticle(): Particle = VersionCompat.particle("DUST", "REDSTONE")

    override fun clearArrowsFromPlayerBody(player: Player) {
        // minecraft clears them on death on newer version
    }

    override fun playVillagerEffect(player: Player, location: Location) {
        player.spawnParticle(VersionCompat.particle("HAPPY_VILLAGER", "VILLAGER_HAPPY"), location, 1)
    }

    // ---------------------------------------------------------------------------------------------
    // materials
    // ---------------------------------------------------------------------------------------------

    override fun materialFireball(): Material = Material.FIRE_CHARGE

    override fun materialPlayerHead(): Material = Material.PLAYER_HEAD

    override fun materialSnowball(): Material = Material.SNOWBALL

    override fun materialGoldenHelmet(): Material = Material.GOLDEN_HELMET

    override fun materialGoldenChestPlate(): Material = Material.GOLDEN_CHESTPLATE

    override fun materialGoldenLeggings(): Material = Material.GOLDEN_LEGGINGS

    override fun materialNetheriteHelmet(): Material = Material.NETHERITE_HELMET

    override fun materialNetheriteChestPlate(): Material = Material.NETHERITE_CHESTPLATE

    override fun materialNetheriteLeggings(): Material = Material.NETHERITE_LEGGINGS

    override fun materialElytra(): Material = Material.ELYTRA

    override fun materialCake(): Material = Material.CAKE

    override fun materialCraftingTable(): Material = Material.CRAFTING_TABLE

    override fun materialEnchantingTable(): Material = Material.ENCHANTING_TABLE

    override fun woolMaterial(): Material = Material.WHITE_WOOL

    // ---------------------------------------------------------------------------------------------
    // misc
    // ---------------------------------------------------------------------------------------------

    override fun getMainLevel(): String {
        val levelName = readLevelNameFromServerProperties()
        if (levelName != null) {
            return levelName
        }
        // not readable: the first loaded world is the main one
        return Bukkit.getWorlds().firstOrNull()?.name ?: "world"
    }

    /**
     * @return the `level-name` of server.properties, or null when it is missing or empty.
     */
    private fun readLevelNameFromServerProperties(): String? {
        val propertiesFile = File("server.properties")
        if (!propertiesFile.isFile) {
            return null
        }

        val properties = Properties()
        try {
            propertiesFile.inputStream().use { input -> properties.load(input) }
        } catch (unreadable: IOException) {
            return null
        }

        val levelName = properties.getProperty("level-name")
        return if (levelName.isNullOrBlank()) null else levelName
    }

    override fun getVersion(): Int {
        // impacts on sidebar
        return 8
    }

    private companion object {
        /** Damage amount of the fake void damage that kills the player. */
        const val VOID_DAMAGE = 1000.0

        /** Numeric item tag read by [getDamage], the name the NBT based versions used. */
        const val ATTACK_DAMAGE_TAG = "generic.attackDamage"

        // Height of the hologram lines above the shop NPC: a single line, or two lines (title above subtitle).
        const val SINGLE_LINE_HEIGHT = 1.85
        const val TITLE_LINE_HEIGHT = 2.1
        const val SUBTITLE_LINE_HEIGHT = 1.85

        val ARMOR_SLOTS = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

        val TAG_TYPES: List<PersistentDataType<*, *>> = listOf(
            PersistentDataType.STRING, PersistentDataType.DOUBLE, PersistentDataType.INTEGER,
            PersistentDataType.LONG, PersistentDataType.BYTE
        )
    }
}
