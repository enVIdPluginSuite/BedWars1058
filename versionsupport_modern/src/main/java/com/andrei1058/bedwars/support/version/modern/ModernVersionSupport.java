package com.andrei1058.bedwars.support.version.modern;

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
import com.andrei1058.bedwars.support.version.modern.despawnable.DespawnableAttributes;
import com.andrei1058.bedwars.support.version.modern.despawnable.DespawnableFactory;
import com.andrei1058.bedwars.support.version.modern.despawnable.DespawnableType;
import com.andrei1058.bedwars.support.version.modern.despawnable.HurtTracker;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
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
public class ModernVersionSupport extends VersionSupport {

    private static final String NPC_DAMAGE_TAG = "generic.attackDamage";

    private final ItemTags tags;
    private final HurtTracker hurtTracker;
    private final DespawnableFactory despawnableFactory;

    public ModernVersionSupport(Plugin plugin, String name) {
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
    public ItemStack addCustomData(ItemStack i, String data) {
        return tags.set(i, VersionSupport.PLUGIN_TAG_GENERIC_KEY, data);
    }

    @Override
    public ItemStack setTag(ItemStack itemStack, String key, String value) {
        return tags.set(itemStack, key, value);
    }

    @Override
    public boolean isCustomBedWarsItem(ItemStack i) {
        return tags.has(i, VersionSupport.PLUGIN_TAG_GENERIC_KEY);
    }

    @Override
    public String getCustomData(ItemStack i) {
        String data = tags.get(i, VersionSupport.PLUGIN_TAG_GENERIC_KEY);
        // an absent NBT string was read as an empty string
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
    public double getDamage(ItemStack i) {
        // Same as the NMS versions: a numeric tag named "generic.attackDamage" if present, 0 otherwise.
        Double value = tags.getDouble(i, NPC_DAMAGE_TAG);
        return value == null ? 0d : value;
    }

    @Override
    public ItemStack getItemInHand(@NotNull Player p) {
        return p.getInventory().getItemInMainHand();
    }

    @Override
    public void minusAmount(Player p, @NotNull ItemStack i, int amount) {
        if (i.getAmount() - amount <= 0) {
            if (p.getInventory().getItemInOffHand().equals(i)) {
                p.getInventory().setItemInOffHand(null);
            } else {
                p.getInventory().removeItem(i);
            }
            return;
        }
        i.setAmount(i.getAmount() - amount);
        p.updateInventory();
    }

    private static boolean nameEndsWith(@Nullable ItemStack itemStack, String suffix) {
        return itemStack != null && itemStack.getType().name().endsWith(suffix);
    }

    @Override
    public boolean isArmor(ItemStack itemStack) {
        if (itemStack == null) return false;
        String type = itemStack.getType().name();
        return type.endsWith("_HELMET") || type.endsWith("_CHESTPLATE") || type.endsWith("_LEGGINGS")
                || type.endsWith("_BOOTS") || itemStack.getType() == Material.ELYTRA;
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
        PotionMeta pm = (PotionMeta) itemStack.getItemMeta();
        return pm != null && pm.hasCustomEffects() && pm.hasCustomEffect(PotionEffectType.INVISIBILITY);
    }

    @Override
    public ItemStack colourItem(ItemStack itemStack, ITeam bedWarsTeam) {
        if (itemStack == null) return null;
        String type = itemStack.getType().toString();
        if (isBed(itemStack.getType())) {
            return new ItemStack(bedWarsTeam.getColor().bedMaterial(), itemStack.getAmount());
        } else if (type.contains("_STAINED_GLASS_PANE")) {
            return new ItemStack(bedWarsTeam.getColor().glassPaneMaterial(), itemStack.getAmount());
        } else if (type.contains("STAINED_GLASS") || type.equals("GLASS")) {
            return new ItemStack(bedWarsTeam.getColor().glassMaterial(), itemStack.getAmount());
        } else if (type.contains("_TERRACOTTA")) {
            return new ItemStack(bedWarsTeam.getColor().glazedTerracottaMaterial(), itemStack.getAmount());
        } else if (type.contains("_WOOL")) {
            return new ItemStack(bedWarsTeam.getColor().woolMaterial(), itemStack.getAmount());
        }
        return itemStack;
    }

    @Override
    public ItemStack createItemStack(String material, int amount, short data) {
        ItemStack i;
        try {
            i = new ItemStack(Material.valueOf(material), amount);
        } catch (Exception ex) {
            getPlugin().getLogger().log(Level.WARNING, material + " is not a valid " + getName() + " material!");
            i = new ItemStack(Material.BEDROCK);
        }
        return i;
    }

    @Override
    public ItemStack getPlayerHead(Player player, @Nullable ItemStack copyTagFrom) {
        ItemStack head = new ItemStack(materialPlayerHead());

        ItemMeta meta = head.getItemMeta();
        if (copyTagFrom != null && copyTagFrom.hasItemMeta()) {
            // the NMS versions copied the whole tag of the given item (name, lore, enchants, custom tags...)
            ItemMeta source = copyTagFrom.getItemMeta();
            if (source != null) {
                if (source.hasDisplayName()) meta.setDisplayName(source.getDisplayName());
                if (source.hasLore()) meta.setLore(source.getLore());
                source.getEnchants().forEach((enchantment, level) -> meta.addEnchant(enchantment, level, true));
                meta.addItemFlags(source.getItemFlags().toArray(new org.bukkit.inventory.ItemFlag[0]));
                if (source.isUnbreakable()) meta.setUnbreakable(true);
                if (source.hasCustomModelData()) meta.setCustomModelData(source.getCustomModelData());
                for (NamespacedKey key : source.getPersistentDataContainer().getKeys()) {
                    copyContainerValue(source, meta, key);
                }
            }
        }
        if (meta instanceof SkullMeta) {
            ((SkullMeta) meta).setOwnerProfile(player.getPlayerProfile());
        }
        head.setItemMeta(meta);
        return head;
    }

    private void copyContainerValue(ItemMeta from, ItemMeta to, NamespacedKey key) {
        var source = from.getPersistentDataContainer();
        var target = to.getPersistentDataContainer();
        if (source.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
            target.set(key, org.bukkit.persistence.PersistentDataType.STRING,
                    source.get(key, org.bukkit.persistence.PersistentDataType.STRING));
        } else if (source.has(key, org.bukkit.persistence.PersistentDataType.DOUBLE)) {
            target.set(key, org.bukkit.persistence.PersistentDataType.DOUBLE,
                    source.get(key, org.bukkit.persistence.PersistentDataType.DOUBLE));
        } else if (source.has(key, org.bukkit.persistence.PersistentDataType.INTEGER)) {
            target.set(key, org.bukkit.persistence.PersistentDataType.INTEGER,
                    source.get(key, org.bukkit.persistence.PersistentDataType.INTEGER));
        } else if (source.has(key, org.bukkit.persistence.PersistentDataType.LONG)) {
            target.set(key, org.bukkit.persistence.PersistentDataType.LONG,
                    source.get(key, org.bukkit.persistence.PersistentDataType.LONG));
        } else if (source.has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
            target.set(key, org.bukkit.persistence.PersistentDataType.BYTE,
                    source.get(key, org.bukkit.persistence.PersistentDataType.BYTE));
        }
    }

    @Override
    public String getInventoryName(@NotNull InventoryEvent e) {
        return e.getView().getTitle();
    }

    @Override
    public org.bukkit.inventory.Inventory getTopInventory(Player player) {
        if (player == null) return null;
        var view = player.getOpenInventory();
        return view == null ? null : view.getTopInventory();
    }

    @Override
    public org.bukkit.inventory.Inventory getTopInventory(InventoryEvent e) {
        return e.getView().getTopInventory();
    }

    @Override
    public org.bukkit.entity.HumanEntity getViewPlayer(InventoryEvent e) {
        return e.getView().getPlayer();
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
    public void sendTitle(@NotNull Player p, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        p.sendTitle(title == null ? " " : title, subtitle == null ? " " : subtitle, fadeIn, stay, fadeOut);
    }

    @Override
    public void playAction(@NotNull Player p, String text) {
        p.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.translateAlternateColorCodes('&', text))
        );
    }

    @Override
    public void hideEntity(@NotNull Entity e, Player p) {
        p.hideEntity(getPlugin(), e);
    }

    @Override
    public void voidKill(Player p) {
        DamageType type = Registry.DAMAGE_TYPE.get(NamespacedKey.minecraft("out_of_world"));
        if (type == null) {
            p.setHealth(0);
            return;
        }
        DamageSource source = DamageSource.builder(type).build();
        p.damage(1000, source);
    }

    @Override
    public void hideArmor(@NotNull Player victim, Player receiver) {
        Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
        items.put(EquipmentSlot.HEAD, new ItemStack(Material.AIR));
        items.put(EquipmentSlot.CHEST, new ItemStack(Material.AIR));
        items.put(EquipmentSlot.LEGS, new ItemStack(Material.AIR));
        items.put(EquipmentSlot.FEET, new ItemStack(Material.AIR));
        receiver.sendEquipmentChange(victim, items);
    }

    @Override
    public void showArmor(@NotNull Player victim, Player receiver) {
        PlayerInventory inventory = victim.getInventory();
        Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
        items.put(EquipmentSlot.HEAD, orAir(inventory.getHelmet()));
        items.put(EquipmentSlot.CHEST, orAir(inventory.getChestplate()));
        items.put(EquipmentSlot.LEGS, orAir(inventory.getLeggings()));
        items.put(EquipmentSlot.FEET, orAir(inventory.getBoots()));
        receiver.sendEquipmentChange(victim, items);
    }

    private static ItemStack orAir(@Nullable ItemStack itemStack) {
        return itemStack == null ? new ItemStack(Material.AIR) : itemStack;
    }

    @Override
    public void sendPlayerSpawnPackets(Player respawned, IArena arena) {
        if (respawned == null) return;
        if (arena == null) return;
        if (!arena.isPlayer(respawned)) return;

        // if method was used when the player was still in re-spawning screen
        if (arena.getRespawnSessions().containsKey(respawned)) return;

        Plugin plugin = getPlugin();

        for (Player p : arena.getPlayers()) {
            if (p == null) continue;
            if (p.equals(respawned)) continue;
            // if p is in re-spawning screen continue
            if (arena.getRespawnSessions().containsKey(p)) continue;

            if (p.getWorld().equals(respawned.getWorld())
                    && respawned.getLocation().distance(p.getLocation()) <= arena.getRenderDistance()) {

                // send respawned player to regular players
                resendEntity(p, respawned);

                // send nearby players to respawned player
                // if the player has invisibility hide armor
                if (p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    hideArmor(p, respawned);
                } else {
                    resendEntity(respawned, p);
                    showArmor(p, respawned);
                }
            }
        }

        for (Player spectator : arena.getSpectators()) {
            if (spectator == null) continue;
            if (spectator.equals(respawned)) continue;
            respawned.hidePlayer(plugin, spectator);
            if (spectator.getWorld().equals(respawned.getWorld())
                    && respawned.getLocation().distance(spectator.getLocation()) <= arena.getRenderDistance()) {

                // send respawned player to spectator
                resendEntity(spectator, respawned);
            }
        }
    }

    /**
     * Makes the viewer receive the entity again (spawn, rotation, velocity and equipment), which is what
     * the spawn packets used to do.
     */
    private void resendEntity(@NotNull Player viewer, @NotNull Player entity) {
        Plugin plugin = getPlugin();
        viewer.hideEntity(plugin, entity);
        viewer.showEntity(plugin, entity);
    }

    @Override
    public void setCollide(@NotNull Player p, IArena a, boolean value) {
        p.setCollidable(value);
        if (a == null) return;
        a.updateSpectatorCollideRule(p, value);
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

    @Override
    public void spawnShop(@NotNull Location loc, String name1, List<Player> players, IArena arena) {
        Location l = loc.clone();

        if (l.getWorld() == null) return;
        Villager vlg = (Villager) l.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        vlg.setAI(false);
        vlg.setRemoveWhenFarAway(false);
        vlg.setCollidable(false);
        vlg.setInvulnerable(true);
        vlg.setSilent(true);

        for (Player p : players) {
            String[] name = Language.getMsg(p, name1).split(",");
            if (name.length == 1) {
                ArmorStand a = createArmorStand(name[0], l.clone().add(0, 1.85, 0));
                new ShopHolo(Language.getPlayerLanguage(p).getIso(), a, null, l, arena);
            } else {
                ArmorStand a = createArmorStand(name[0], l.clone().add(0, 2.1, 0));
                ArmorStand b = createArmorStand(name[1], l.clone().add(0, 1.85, 0));
                new ShopHolo(Language.getPlayerLanguage(p).getIso(), a, b, l, arena);
            }
        }
        for (ShopHolo sh : ShopHolo.getShopHolo()) {
            if (sh.getA() == arena) {
                sh.update();
            }
        }
    }

    private static ArmorStand createArmorStand(String name, Location loc) {
        if (loc == null) return null;
        if (loc.getWorld() == null) return null;
        ArmorStand a = loc.getWorld().spawn(loc, ArmorStand.class);
        a.setGravity(false);
        a.setVisible(false);
        a.setCustomNameVisible(true);
        a.setCustomName(name);
        return a;
    }

    @Override
    public void spawnSilverfish(Location loc, ITeam bedWarsTeam, double speed, double health, int despawn, double damage) {
        var attr = new DespawnableAttributes(DespawnableType.SILVERFISH, speed, health, damage, despawn);
        var entity = despawnableFactory.spawn(attr, loc, bedWarsTeam);

        new Despawnable(
                entity,
                bedWarsTeam, despawn,
                Messages.SHOP_UTILITY_NPC_SILVERFISH_NAME,
                PlayerKillEvent.PlayerKillCause.SILVERFISH_FINAL_KILL,
                PlayerKillEvent.PlayerKillCause.SILVERFISH
        );
    }

    @Override
    public void spawnIronGolem(Location loc, ITeam bedWarsTeam, double speed, double health, int despawn) {
        var attr = new DespawnableAttributes(DespawnableType.IRON_GOLEM, speed, health, 4, despawn);
        var entity = despawnableFactory.spawn(attr, loc, bedWarsTeam);
        new Despawnable(
                entity,
                bedWarsTeam, despawn,
                Messages.SHOP_UTILITY_NPC_IRON_GOLEM_NAME,
                PlayerKillEvent.PlayerKillCause.IRON_GOLEM_FINAL_KILL,
                PlayerKillEvent.PlayerKillCause.IRON_GOLEM
        );
    }

    @Override
    public void spawnDragon(Location l, ITeam bwt) {
        if (l == null || l.getWorld() == null) {
            getPlugin().getLogger().log(Level.WARNING, "Could not spawn Dragon. Location is null");
            return;
        }
        EnderDragon ed = (EnderDragon) l.getWorld().spawnEntity(l, EntityType.ENDER_DRAGON);
        ed.setPhase(EnderDragon.Phase.CIRCLING);
    }

    @Override
    public void colorBed(ITeam bwt) {
        // Parity note: the NMS based modules tested a BlockState against the BlockData type Bed, which is never
        // true, so this has always been a no-op. Kept identical on purpose.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockState bed = bwt.getBed().clone().add(x, 0, z).getBlock().getState();
                Object state = bed;
                if (state instanceof Bed) {
                    bed.setType(bwt.getColor().bedMaterial());
                    bed.update();
                }
            }
        }
    }

    @Override
    public void registerTntWhitelist(float endStoneBlast, float glassBlast) {
        try {
            if (!BlastResistance.apply(endStoneBlast, glassBlast)) {
                getPlugin().getLogger().warning("Could not find the block blast resistance on this server version. "
                        + "Glass and end stone TNT protection is disabled.");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            getPlugin().getLogger().log(Level.WARNING, "Could not apply TNT protection to glass and end stone.", e);
        }
    }

    @Override
    public void setBlockTeamColor(@NotNull Block block, TeamColor teamColor) {
        if (block.getType().toString().contains("STAINED_GLASS") || block.getType().toString().equals("GLASS")) {
            block.setType(teamColor.glassMaterial());
        } else if (block.getType().toString().contains("_TERRACOTTA")) {
            block.setType(teamColor.glazedTerracottaMaterial());
        } else if (block.getType().toString().contains("_WOOL")) {
            block.setType(teamColor.woolMaterial());
        }
    }

    @Override
    public void setJoinSignBackground(@NotNull BlockState b, Material material) {
        if (b.getBlockData() instanceof WallSign) {
            b.getBlock().getRelative(((WallSign) b.getBlockData()).getFacing().getOppositeFace()).setType(material);
        }
    }

    @Override
    public void placeTowerBlocks(@NotNull Block b, @NotNull IArena a, @NotNull TeamColor color, int x, int y, int z) {
        b.getRelative(x, y, z).setType(color.woolMaterial());
        a.addPlacedBlock(b.getRelative(x, y, z));
    }

    @Override
    public void placeLadder(@NotNull Block b, int x, int y, int z, @NotNull IArena a, int ladderData) {
        Block block = b.getRelative(x, y, z);  //ladder block
        block.setType(Material.LADDER);
        Ladder ladder = (Ladder) block.getBlockData();
        a.addPlacedBlock(block);
        switch (ladderData) {
            case 2 -> {
                ladder.setFacing(BlockFace.NORTH);
                block.setBlockData(ladder);
            }
            case 3 -> {
                ladder.setFacing(BlockFace.SOUTH);
                block.setBlockData(ladder);
            }
            case 4 -> {
                ladder.setFacing(BlockFace.WEST);
                block.setBlockData(ladder);
            }
            case 5 -> {
                ladder.setFacing(BlockFace.EAST);
                block.setBlockData(ladder);
            }
        }
    }

    @Override
    public Fireball setFireballDirection(Fireball fireball, @NotNull Vector vector) {
        // Same magnitude the NMS code wrote into the acceleration fields.
        Vector acceleration = new Vector(vector.getX() * 0.1D, vector.getY() * 0.1D, vector.getZ() * 0.1D);
        fireball.setPower(acceleration);
        return fireball;
    }

    @Override
    public void playRedStoneDot(@NotNull Player player) {
        Location location = player.getLocation();
        Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1f);
        for (Player inWorld : player.getWorld().getPlayers()) {
            if (inWorld.equals(player)) continue;
            inWorld.spawnParticle(Particle.DUST, location.getX(), location.getY() + 2.6, location.getZ(),
                    1, 0, 0, 0, 0, dust);
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
        // level-name of server.properties, the same value the dedicated server settings hold
        File file = new File("server.properties");
        if (file.isFile()) {
            Properties properties = new Properties();
            try (InputStream in = new FileInputStream(file)) {
                properties.load(in);
                String level = properties.getProperty("level-name");
                if (level != null && !level.isBlank()) {
                    return level;
                }
            } catch (IOException ignored) {
                // use the default below
            }
        }
        List<org.bukkit.World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? "world" : worlds.get(0).getName();
    }

    @Override
    public int getVersion() {
        // impacts on sidebar
        // experimental score placeholders
        return 10;
    }
}
