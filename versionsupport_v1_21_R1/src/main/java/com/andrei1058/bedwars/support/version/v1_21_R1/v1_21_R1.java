package com.andrei1058.bedwars.support.version.v1_21_R1;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.shop.ShopHolo;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.arena.team.TeamColor;
import com.andrei1058.bedwars.api.entity.Despawnable;
import com.andrei1058.bedwars.api.events.player.PlayerKillEvent;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.api.server.VersionSupport;
import com.andrei1058.bedwars.support.version.common.VersionCommon;
import com.andrei1058.bedwars.support.version.v1_21_R1.despawnable.DespawnableAttributes;
import com.andrei1058.bedwars.support.version.v1_21_R1.despawnable.DespawnableFactory;
import com.andrei1058.bedwars.support.version.v1_21_R1.despawnable.DespawnableType;
import com.andrei1058.bedwars.support.version.v1_21_R1.despawnable.HurtTracker;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

/**
 * Version support for Minecraft 1.21.x and 26.x.
 * <p>
 * This implementation deliberately has no NMS/CraftBukkit dependency. Everything the older, per-revision
 * modules did through server internals is done through the Bukkit/Paper API instead, or - when the API
 * has no equivalent - through reflection that is resolved at runtime and does not rely on obfuscated names.
 * That is what lets one class serve every 1.21.x revision and the 26.x line.
 */
@SuppressWarnings("unused")
public class v1_21_R1 extends VersionSupport {

    /** Numeric item tag read by {@link #getDamage}, the name the NBT based versions used. */
    private static final String ATTACK_DAMAGE_TAG = "generic.attackDamage";

    private static final List<PersistentDataType<?, ?>> TAG_TYPES = Arrays.asList(
            PersistentDataType.STRING, PersistentDataType.DOUBLE, PersistentDataType.INTEGER,
            PersistentDataType.LONG, PersistentDataType.BYTE);

    private final ItemTags tags;
    private final HurtTracker hurtTracker;
    private final DespawnableFactory despawnableFactory;

    public v1_21_R1(Plugin plugin, String name) {
        super(plugin, name);
        loadDefaultEffects();
        this.tags = new ItemTags(plugin);
        this.hurtTracker = new HurtTracker(this);
        this.despawnableFactory = new DespawnableFactory(this, hurtTracker);
    }

    @Override
    public void registerVersionListeners() {
        new VersionCommon(this);
        Bukkit.getPluginManager().registerEvents(hurtTracker, getPlugin());
    }

    @Override
    public void registerCommand(String name, Command cmd) {
        CommandMap map = ModernCompat.commandMap();
        if (map == null) {
            getPlugin().getLogger().severe("Could not access the server command map. /" + name + " was not registered.");
            return;
        }
        map.register(name, cmd);
    }

    @Override
    public boolean isBukkitCommandRegistered(String name) {
        CommandMap map = ModernCompat.commandMap();
        return map != null && map.getCommand(name) != null;
    }

    // ---------------------------------------------------------------------------------------------
    // items
    // ---------------------------------------------------------------------------------------------

    @Override
    public String getTag(ItemStack itemStack, String key) {
        return tags.get(itemStack, key);
    }

    @Override
    public ItemStack addCustomData(ItemStack itemStack, String data) {
        return tags.set(itemStack, VersionSupport.PLUGIN_TAG_GENERIC_KEY, data);
    }

    @Override
    public ItemStack setTag(ItemStack itemStack, String key, String value) {
        return tags.set(itemStack, key, value);
    }

    @Override
    public boolean isCustomBedWarsItem(ItemStack itemStack) {
        return tags.has(itemStack, VersionSupport.PLUGIN_TAG_GENERIC_KEY);
    }

    @Override
    public String getCustomData(ItemStack itemStack) {
        String data = tags.get(itemStack, VersionSupport.PLUGIN_TAG_GENERIC_KEY);
        // the NBT based versions returned an empty string for an absent tag
        return data == null ? "" : data;
    }

    @Override
    public String getShopUpgradeIdentifier(ItemStack itemStack) {
        String identifier = tags.get(itemStack, VersionSupport.PLUGIN_TAG_TIER_KEY);
        return identifier == null ? "null" : identifier;
    }

    @Override
    public ItemStack setShopUpgradeIdentifier(ItemStack itemStack, String identifier) {
        return tags.set(itemStack, VersionSupport.PLUGIN_TAG_TIER_KEY, identifier);
    }

    @Override
    public double getDamage(ItemStack itemStack) {
        // Same as the NMS versions: a numeric tag named "generic.attackDamage" if present, 0 otherwise.
        Double value = tags.getDouble(itemStack, ATTACK_DAMAGE_TAG);
        return value == null ? 0d : value;
    }

    @Override
    public ItemStack getItemInHand(@NotNull Player player) {
        return player.getInventory().getItemInMainHand();
    }

    @Override
    public void minusAmount(Player player, @NotNull ItemStack itemStack, int amount) {
        int remainingAmount = itemStack.getAmount() - amount;

        if (remainingAmount <= 0) {
            // the whole stack is used up: remove it from wherever it is held
            boolean heldInOffHand = player.getInventory().getItemInOffHand().equals(itemStack);
            if (heldInOffHand) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().removeItem(itemStack);
            }
            return;
        }

        itemStack.setAmount(remainingAmount);
        player.updateInventory();
    }

    private static boolean nameEndsWith(@Nullable ItemStack itemStack, String suffix) {
        return itemStack != null && itemStack.getType().name().endsWith(suffix);
    }

    @Override
    public boolean isArmor(ItemStack itemStack) {
        boolean isArmorPiece = nameEndsWith(itemStack, "_HELMET") || nameEndsWith(itemStack, "_CHESTPLATE")
                || nameEndsWith(itemStack, "_LEGGINGS") || nameEndsWith(itemStack, "_BOOTS");
        boolean isElytra = itemStack != null && itemStack.getType() == Material.ELYTRA;
        return isArmorPiece || isElytra;
    }

    @Override
    public boolean isTool(ItemStack itemStack) {
        // pickaxes, axes, shovels and hoes
        return nameEndsWith(itemStack, "_PICKAXE") || nameEndsWith(itemStack, "_AXE")
                || nameEndsWith(itemStack, "_SHOVEL") || nameEndsWith(itemStack, "_HOE");
    }

    @Override
    public boolean isSword(ItemStack itemStack) {
        return nameEndsWith(itemStack, "_SWORD");
    }

    @Override
    public boolean isAxe(ItemStack itemStack) {
        return nameEndsWith(itemStack, "_AXE");
    }

    @Override
    public boolean isBow(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() == Material.BOW;
    }

    @Override
    public boolean isProjectile(ItemStack itemStack) {
        // The NMS versions asked the item for the entity it is displayed as, which is never a projectile for
        // a plain inventory item. Kept as is: callers also filter by material through Misc#isProjectile.
        return false;
    }

    @Override
    public boolean isInvisibilityPotion(@NotNull ItemStack itemStack) {
        if (!itemStack.getType().equals(Material.POTION)) return false;
        PotionMeta potionMeta = (PotionMeta) itemStack.getItemMeta();
        return potionMeta != null && potionMeta.hasCustomEffects()
                && potionMeta.hasCustomEffect(PotionEffectType.INVISIBILITY);
    }

    @Override
    public ItemStack colourItem(ItemStack itemStack, ITeam bedWarsTeam) {
        if (itemStack == null) return null;

        TeamColor teamColor = bedWarsTeam.getColor();
        Material material = itemStack.getType();
        String materialName = material.toString();
        int amount = itemStack.getAmount();

        // the pane check must come first: panes are named like stained glass too
        if (isBed(material)) {
            return new ItemStack(teamColor.bedMaterial(), amount);
        } else if (materialName.contains("_STAINED_GLASS_PANE")) {
            return new ItemStack(teamColor.glassPaneMaterial(), amount);
        } else if (materialName.contains("STAINED_GLASS") || materialName.equals("GLASS")) {
            return new ItemStack(teamColor.glassMaterial(), amount);
        } else if (materialName.contains("_TERRACOTTA")) {
            return new ItemStack(teamColor.glazedTerracottaMaterial(), amount);
        } else if (materialName.contains("_WOOL")) {
            return new ItemStack(teamColor.woolMaterial(), amount);
        }
        return itemStack;
    }

    @Override
    public ItemStack createItemStack(String material, int amount, short data) {
        try {
            return new ItemStack(Material.valueOf(material), amount);
        } catch (Exception invalidMaterialName) {
            getPlugin().getLogger().log(Level.WARNING, material + " is not a valid " + getName() + " material!");
            return new ItemStack(Material.BEDROCK);
        }
    }

    @Override
    public ItemStack getPlayerHead(Player player, @Nullable ItemStack copyTagFrom) {
        ItemStack head = new ItemStack(materialPlayerHead());
        ItemMeta headMeta = head.getItemMeta();

        if (copyTagFrom != null && copyTagFrom.hasItemMeta()) {
            copyDisplayData(copyTagFrom.getItemMeta(), headMeta);
        }
        if (headMeta instanceof SkullMeta) {
            ((SkullMeta) headMeta).setOwnerProfile(player.getPlayerProfile());
        }

        head.setItemMeta(headMeta);
        return head;
    }

    /**
     * The NMS versions copied the whole tag of the source item: name, lore, enchants, flags and custom tags.
     */
    private void copyDisplayData(@Nullable ItemMeta source, ItemMeta target) {
        if (source == null) return;

        if (source.hasDisplayName()) target.setDisplayName(source.getDisplayName());
        if (source.hasLore()) target.setLore(source.getLore());
        source.getEnchants().forEach((enchantment, level) -> target.addEnchant(enchantment, level, true));
        target.addItemFlags(source.getItemFlags().toArray(new ItemFlag[0]));
        if (source.isUnbreakable()) target.setUnbreakable(true);
        if (source.hasCustomModelData()) target.setCustomModelData(source.getCustomModelData());

        for (NamespacedKey tagKey : source.getPersistentDataContainer().getKeys()) {
            copyTag(source.getPersistentDataContainer(), target.getPersistentDataContainer(), tagKey);
        }
    }

    /**
     * Item tags do not say which type they hold, so the types used by this plugin are tried in turn.
     */
    private void copyTag(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey tagKey) {
        for (PersistentDataType<?, ?> type : TAG_TYPES) {
            if (copyTagIfOfType(from, to, tagKey, type)) {
                return;
            }
        }
    }

    private static <T> boolean copyTagIfOfType(PersistentDataContainer from, PersistentDataContainer to,
                                               NamespacedKey tagKey, PersistentDataType<?, T> type) {
        if (!from.has(tagKey, type)) {
            return false;
        }
        to.set(tagKey, type, from.get(tagKey, type));
        return true;
    }

    @Override
    public String getInventoryName(@NotNull InventoryEvent event) {
        return event.getView().getTitle();
    }

    // InventoryView became an interface in 1.21, code compiled against the old class breaks on it.
    // These methods are the only place the core touches it, compiled here against the new API.
    @Override
    public Inventory getTopInventory(Player player) {
        if (player == null) return null;
        InventoryView openView = player.getOpenInventory();
        return openView == null ? null : openView.getTopInventory();
    }

    @Override
    public Inventory getTopInventory(InventoryEvent event) {
        return event.getView().getTopInventory();
    }

    @Override
    public HumanEntity getViewPlayer(InventoryEvent event) {
        return event.getView().getPlayer();
    }

    @Override
    public Sound getSound(String name) {
        return ModernCompat.sound(name);
    }

    @Override
    public void setUnbreakable(@NotNull ItemMeta itemMeta) {
        itemMeta.setUnbreakable(true);
    }

    // ---------------------------------------------------------------------------------------------
    // players
    // ---------------------------------------------------------------------------------------------

    @Override
    public void sendTitle(@NotNull Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        // a null text would keep the previous title, a space clears it
        player.sendTitle(title == null ? " " : title, subtitle == null ? " " : subtitle, fadeIn, stay, fadeOut);
    }

    @Override
    public void playAction(@NotNull Player player, String text) {
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.translateAlternateColorCodes('&', text))
        );
    }

    @Override
    public void hideEntity(@NotNull Entity entity, Player viewer) {
        viewer.hideEntity(getPlugin(), entity);
    }

    @Override
    public void voidKill(Player player) {
        DamageType voidDamage = Registry.DAMAGE_TYPE.get(NamespacedKey.minecraft("out_of_world"));
        if (voidDamage == null) {
            player.setHealth(0);
            return;
        }
        // damage with the "void" source so death messages and kill credits match the old behaviour
        DamageSource voidSource = DamageSource.builder(voidDamage).build();
        player.damage(1000, voidSource);
    }

    @Override
    public void hideArmor(@NotNull Player victim, Player receiver) {
        // only what the receiver is shown changes, the victim keeps wearing the armor
        Map<EquipmentSlot, ItemStack> emptyArmor = new EnumMap<>(EquipmentSlot.class);
        emptyArmor.put(EquipmentSlot.HEAD, new ItemStack(Material.AIR));
        emptyArmor.put(EquipmentSlot.CHEST, new ItemStack(Material.AIR));
        emptyArmor.put(EquipmentSlot.LEGS, new ItemStack(Material.AIR));
        emptyArmor.put(EquipmentSlot.FEET, new ItemStack(Material.AIR));
        receiver.sendEquipmentChange(victim, emptyArmor);
    }

    @Override
    public void showArmor(@NotNull Player victim, Player receiver) {
        PlayerInventory victimInventory = victim.getInventory();
        Map<EquipmentSlot, ItemStack> wornArmor = new EnumMap<>(EquipmentSlot.class);
        wornArmor.put(EquipmentSlot.HEAD, orAir(victimInventory.getHelmet()));
        wornArmor.put(EquipmentSlot.CHEST, orAir(victimInventory.getChestplate()));
        wornArmor.put(EquipmentSlot.LEGS, orAir(victimInventory.getLeggings()));
        wornArmor.put(EquipmentSlot.FEET, orAir(victimInventory.getBoots()));
        receiver.sendEquipmentChange(victim, wornArmor);
    }

    private static ItemStack orAir(@Nullable ItemStack itemStack) {
        return itemStack == null ? new ItemStack(Material.AIR) : itemStack;
    }

    /**
     * Makes everyone around a freshly respawned player see them (and the other way around).
     * <p>
     * The NMS versions sent the spawn packets by hand. The API equivalent is hiding and showing the entity
     * again, which makes the server send the full spawn data (position, equipment, velocity) to that viewer.
     */
    @Override
    public void sendPlayerSpawnPackets(Player respawned, IArena arena) {
        if (respawned == null) return;
        if (arena == null) return;
        if (!arena.isPlayer(respawned)) return;

        // if the method was used while the player was still on the re-spawning screen
        if (arena.getRespawnSessions().containsKey(respawned)) return;

        for (Player otherPlayer : arena.getPlayers()) {
            if (otherPlayer == null) continue;
            if (otherPlayer.equals(respawned)) continue;
            // players on the re-spawning screen are not in the world yet
            if (arena.getRespawnSessions().containsKey(otherPlayer)) continue;
            if (!isWithinRenderDistance(respawned, otherPlayer, arena)) continue;

            // the other player sees the respawned one
            resendEntity(otherPlayer, respawned);

            // the respawned player sees the other one, without armor if it is invisible
            if (otherPlayer.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                hideArmor(otherPlayer, respawned);
            } else {
                resendEntity(respawned, otherPlayer);
                showArmor(otherPlayer, respawned);
            }
        }

        for (Player spectator : arena.getSpectators()) {
            if (spectator == null) continue;
            if (spectator.equals(respawned)) continue;

            // spectators are never visible to players
            respawned.hidePlayer(getPlugin(), spectator);
            if (isWithinRenderDistance(respawned, spectator, arena)) {
                resendEntity(spectator, respawned);
            }
        }
    }

    private boolean isWithinRenderDistance(@NotNull Player first, @NotNull Player second, @NotNull IArena arena) {
        return first.getWorld().equals(second.getWorld())
                && first.getLocation().distance(second.getLocation()) <= arena.getRenderDistance();
    }

    /**
     * Makes the viewer receive the entity again (spawn, rotation, velocity and equipment).
     */
    private void resendEntity(@NotNull Player viewer, @NotNull Player entity) {
        Plugin plugin = getPlugin();
        viewer.hideEntity(plugin, entity);
        viewer.showEntity(plugin, entity);
    }

    @Override
    public void setCollide(@NotNull Player player, IArena arena, boolean value) {
        player.setCollidable(value);
        if (arena == null) return;
        arena.updateSpectatorCollideRule(player, value);
    }

    @Override
    public void setSource(TNTPrimed tnt, Player owner) {
        tnt.setSource(owner);
    }

    @Override
    public void spigotShowPlayer(Player victim, @NotNull Player receiver) {
        receiver.showPlayer(getPlugin(), victim);
    }

    @Override
    public void spigotHidePlayer(Player victim, @NotNull Player receiver) {
        receiver.hidePlayer(getPlugin(), victim);
    }

    // ---------------------------------------------------------------------------------------------
    // world
    // ---------------------------------------------------------------------------------------------

    @Override
    public void registerEntities() {
    }

    // Height of the hologram lines above the shop NPC: a single line, or two lines (title above subtitle).
    private static final double SINGLE_LINE_HEIGHT = 1.85d;
    private static final double TITLE_LINE_HEIGHT = 2.1d;
    private static final double SUBTITLE_LINE_HEIGHT = 1.85d;

    @Override
    public void spawnShop(@NotNull Location location, String nameMessage, List<Player> players, IArena arena) {
        World world = location.getWorld();
        if (world == null) return;

        spawnShopVillager(world, location);

        // every player reads the name in their own language, so each gets their own hologram
        for (Player viewer : players) {
            String[] nameLines = Language.getMsg(viewer, nameMessage).split(",");
            String languageIso = Language.getPlayerLanguage(viewer).getIso();

            boolean hasSubtitle = nameLines.length > 1;
            if (hasSubtitle) {
                ArmorStand title = createHologramLine(nameLines[0], location.clone().add(0, TITLE_LINE_HEIGHT, 0));
                ArmorStand subtitle = createHologramLine(nameLines[1], location.clone().add(0, SUBTITLE_LINE_HEIGHT, 0));
                new ShopHolo(languageIso, title, subtitle, location, arena);
            } else {
                ArmorStand line = createHologramLine(nameLines[0], location.clone().add(0, SINGLE_LINE_HEIGHT, 0));
                new ShopHolo(languageIso, line, null, location, arena);
            }
        }

        for (ShopHolo hologram : ShopHolo.getShopHolo()) {
            if (hologram.getA() == arena) {
                hologram.update();
            }
        }
    }

    /**
     * A villager that cannot move, be hurt or make noise: the click target of the shop.
     */
    private void spawnShopVillager(@NotNull World world, @NotNull Location location) {
        Villager villager = (Villager) world.spawnEntity(location, EntityType.VILLAGER);
        villager.setAI(false);
        villager.setRemoveWhenFarAway(false);
        villager.setCollidable(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
    }

    private static ArmorStand createHologramLine(String text, Location location) {
        if (location == null) return null;
        if (location.getWorld() == null) return null;

        ArmorStand hologramLine = location.getWorld().spawn(location, ArmorStand.class);
        hologramLine.setGravity(false);
        hologramLine.setVisible(false);
        hologramLine.setCustomNameVisible(true);
        hologramLine.setCustomName(text);
        return hologramLine;
    }

    @Override
    public void spawnSilverfish(Location location, ITeam bedWarsTeam, double speed, double health, int despawn, double damage) {
        spawnDespawnable(
                DespawnableType.SILVERFISH, location, bedWarsTeam, speed, health, damage, despawn,
                Messages.SHOP_UTILITY_NPC_SILVERFISH_NAME,
                PlayerKillEvent.PlayerKillCause.SILVERFISH_FINAL_KILL,
                PlayerKillEvent.PlayerKillCause.SILVERFISH
        );
    }

    @Override
    public void spawnIronGolem(Location location, ITeam bedWarsTeam, double speed, double health, int despawn) {
        double golemAttackDamage = 4;
        spawnDespawnable(
                DespawnableType.IRON_GOLEM, location, bedWarsTeam, speed, health, golemAttackDamage, despawn,
                Messages.SHOP_UTILITY_NPC_IRON_GOLEM_NAME,
                PlayerKillEvent.PlayerKillCause.IRON_GOLEM_FINAL_KILL,
                PlayerKillEvent.PlayerKillCause.IRON_GOLEM
        );
    }

    /**
     * Spawns a team mob and registers it as a {@link Despawnable}, which removes it after {@code despawnSeconds}
     * and keeps its name up to date.
     */
    private void spawnDespawnable(DespawnableType type, Location location, ITeam team, double speed, double health,
                                  double damage, int despawnSeconds, String nameMessage,
                                  PlayerKillEvent.PlayerKillCause finalKillCause,
                                  PlayerKillEvent.PlayerKillCause regularKillCause) {
        DespawnableAttributes attributes = new DespawnableAttributes(type, speed, health, damage, despawnSeconds);
        LivingEntity mob = despawnableFactory.spawn(attributes, location, team);

        new Despawnable(mob, team, despawnSeconds, nameMessage, finalKillCause, regularKillCause);
    }

    @Override
    public void spawnDragon(Location location, ITeam team) {
        if (location == null || location.getWorld() == null) {
            getPlugin().getLogger().log(Level.WARNING, "Could not spawn Dragon. Location is null");
            return;
        }
        EnderDragon dragon = (EnderDragon) location.getWorld().spawnEntity(location, EntityType.ENDER_DRAGON);
        dragon.setPhase(EnderDragon.Phase.CIRCLING);
    }

    @Override
    public void colorBed(ITeam team) {
        // Parity note: the NMS based modules tested a BlockState against the BlockData type Bed, which is never
        // true, so this has always been a no-op. Kept identical on purpose.
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                BlockState nearbyBlock = team.getBed().clone().add(offsetX, 0, offsetZ).getBlock().getState();
                Object blockState = nearbyBlock;
                if (blockState instanceof Bed) {
                    nearbyBlock.setType(team.getColor().bedMaterial());
                    nearbyBlock.update();
                }
            }
        }
    }

    @Override
    public void registerTntWhitelist(float endStoneBlastResistance, float glassBlastResistance) {
        try {
            if (!BlastResistance.apply(endStoneBlastResistance, glassBlastResistance)) {
                getPlugin().getLogger().warning("Could not find the block blast resistance on this server version. "
                        + "Glass and end stone TNT protection is disabled.");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            getPlugin().getLogger().log(Level.WARNING, "Could not apply TNT protection to glass and end stone.", e);
        }
    }

    @Override
    public void setBlockTeamColor(@NotNull Block block, TeamColor teamColor) {
        String materialName = block.getType().toString();

        if (materialName.contains("STAINED_GLASS") || materialName.equals("GLASS")) {
            block.setType(teamColor.glassMaterial());
        } else if (materialName.contains("_TERRACOTTA")) {
            block.setType(teamColor.glazedTerracottaMaterial());
        } else if (materialName.contains("_WOOL")) {
            block.setType(teamColor.woolMaterial());
        }
    }

    @Override
    public void setJoinSignBackground(@NotNull BlockState signState, Material material) {
        if (signState.getBlockData() instanceof WallSign wallSign) {
            // the block the sign is attached to is behind it
            BlockFace behindTheSign = wallSign.getFacing().getOppositeFace();
            signState.getBlock().getRelative(behindTheSign).setType(material);
        }
    }

    @Override
    public void placeTowerBlocks(@NotNull Block origin, @NotNull IArena arena, @NotNull TeamColor color, int x, int y, int z) {
        Block towerBlock = origin.getRelative(x, y, z);
        towerBlock.setType(color.woolMaterial());
        // placed blocks are the ones players may break and that get reset after the game
        arena.addPlacedBlock(towerBlock);
    }

    @Override
    public void placeLadder(@NotNull Block origin, int x, int y, int z, @NotNull IArena arena, int ladderData) {
        Block ladderBlock = origin.getRelative(x, y, z);
        ladderBlock.setType(Material.LADDER);
        arena.addPlacedBlock(ladderBlock);

        BlockFace facing = ladderFacing(ladderData);
        if (facing != null) {
            Ladder ladder = (Ladder) ladderBlock.getBlockData();
            ladder.setFacing(facing);
            ladderBlock.setBlockData(ladder);
        }
    }

    /**
     * Translates the legacy ladder data value (the old block "data" byte) to the direction it faces.
     *
     * @return null for values that are not a ladder direction, the ladder is left as placed.
     */
    private static @Nullable BlockFace ladderFacing(int legacyLadderData) {
        switch (legacyLadderData) {
            case 2:
                return BlockFace.NORTH;
            case 3:
                return BlockFace.SOUTH;
            case 4:
                return BlockFace.WEST;
            case 5:
                return BlockFace.EAST;
            default:
                return null;
        }
    }

    @Override
    public Fireball setFireballDirection(Fireball fireball, @NotNull Vector vector) {
        // Same magnitude the NMS code wrote into the fireball acceleration fields.
        double accelerationScale = 0.1D;
        Vector acceleration = new Vector(vector.getX() * accelerationScale, vector.getY() * accelerationScale,
                vector.getZ() * accelerationScale);
        fireball.setPower(acceleration);
        return fireball;
    }

    @Override
    public void playRedStoneDot(@NotNull Player player) {
        // a red dot floating above the player's head, shown to everyone but the player
        Location aboveHead = player.getLocation().add(0, 2.6, 0);
        Particle.DustOptions redDust = new Particle.DustOptions(Color.RED, 1f);
        for (Player viewer : player.getWorld().getPlayers()) {
            if (viewer.equals(player)) continue;
            viewer.spawnParticle(Particle.DUST, aboveHead, 1, 0, 0, 0, 0, redDust);
        }
    }

    @Override
    public void clearArrowsFromPlayerBody(Player player) {
        // minecraft clears them on death on newer version
    }

    @Override
    public void playVillagerEffect(@NotNull Player player, Location location) {
        player.spawnParticle(Particle.HAPPY_VILLAGER, location, 1);
    }

    // ---------------------------------------------------------------------------------------------
    // materials
    // ---------------------------------------------------------------------------------------------

    @Override
    public Material materialFireball() {
        return Material.FIRE_CHARGE;
    }

    @Override
    public Material materialPlayerHead() {
        return Material.PLAYER_HEAD;
    }

    @Override
    public Material materialSnowball() {
        return Material.SNOWBALL;
    }

    @Override
    public Material materialGoldenHelmet() {
        return Material.GOLDEN_HELMET;
    }

    @Override
    public Material materialGoldenChestPlate() {
        return Material.GOLDEN_CHESTPLATE;
    }

    @Override
    public Material materialGoldenLeggings() {
        return Material.GOLDEN_LEGGINGS;
    }

    @Override
    public Material materialNetheriteHelmet() {
        return Material.NETHERITE_HELMET;
    }

    @Override
    public Material materialNetheriteChestPlate() {
        return Material.NETHERITE_CHESTPLATE;
    }

    @Override
    public Material materialNetheriteLeggings() {
        return Material.NETHERITE_LEGGINGS;
    }

    @Override
    public Material materialElytra() {
        return Material.ELYTRA;
    }

    @Override
    public Material materialCake() {
        return Material.CAKE;
    }

    @Override
    public Material materialCraftingTable() {
        return Material.CRAFTING_TABLE;
    }

    @Override
    public Material materialEnchantingTable() {
        return Material.ENCHANTING_TABLE;
    }

    @Override
    public Material woolMaterial() {
        return Material.WHITE_WOOL;
    }

    // ---------------------------------------------------------------------------------------------
    // misc
    // ---------------------------------------------------------------------------------------------

    @Override
    public String getMainLevel() {
        String levelName = readLevelNameFromServerProperties();
        if (levelName != null) {
            return levelName;
        }
        // not readable: the first loaded world is the main one
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? "world" : worlds.get(0).getName();
    }

    /**
     * @return the {@code level-name} of server.properties, or null when it is missing or empty.
     */
    private @Nullable String readLevelNameFromServerProperties() {
        File propertiesFile = new File("server.properties");
        if (!propertiesFile.isFile()) {
            return null;
        }

        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(propertiesFile)) {
            properties.load(input);
        } catch (IOException unreadable) {
            return null;
        }

        String levelName = properties.getProperty("level-name");
        return levelName == null || levelName.isBlank() ? null : levelName;
    }

    @Override
    public int getVersion() {
        // impacts on sidebar
        // experimental score placeholders
        return 10;
    }
}
