package dev.minions;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.util.EulerAngle;

import java.io.File;
import java.util.*;

public final class MinionManager {
    private final MinionsPlugin plugin;
    public final Map<UUID, Minion> minions = new LinkedHashMap<>();
    public final Map<UUID, Minion> byEntity = new HashMap<>();
    public final Set<UUID> holos = new HashSet<>();
    private final Map<UUID, Set<String>> unique = new HashMap<>();
    private final File file, uniqueFile;

    public MinionManager(MinionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "minions.yml");
        this.uniqueFile = new File(plugin.getDataFolder(), "unlocked.yml");
    }

    // ------------------------------------------------------------ persistence
    public void load() {
        if (uniqueFile.exists()) {
            YamlConfiguration u = YamlConfiguration.loadConfiguration(uniqueFile);
            ConfigurationSection s = u.getConfigurationSection("players");
            if (s != null) for (String k : s.getKeys(false))
                unique.put(UUID.fromString(k), new HashSet<>(s.getStringList(k)));
        }
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("minions");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                String p = "minions." + key;
                Minion m = new Minion(UUID.fromString(key));
                m.owner = UUID.fromString(Objects.requireNonNull(y.getString(p + ".owner")));
                m.ownerName = y.getString(p + ".ownerName", "?");
                m.world = y.getString(p + ".world");
                m.x = y.getInt(p + ".x"); m.y = y.getInt(p + ".y"); m.z = y.getInt(p + ".z");
                m.yaw = (float) y.getDouble(p + ".yaw");
                m.type = MinionType.fromId(y.getString(p + ".type"));
                if (m.type == null) continue;
                m.tier = Math.max(1, Math.min(MinionType.MAX_TIER, y.getInt(p + ".tier", 1)));
                m.generated = y.getLong(p + ".generated");
                m.nextActionAt = y.getLong(p + ".nextActionAt", System.currentTimeMillis());
                m.placing = y.getBoolean(p + ".placing", true);
                m.virtFilled = y.getInt(p + ".virtFilled");
                m.virtN = y.getInt(p + ".virtN");
                m.fuel = Fuel.byId(y.getString(p + ".fuel"));
                m.fuelExpires = y.getLong(p + ".fuelExpires");
                for (int i = 0; i < Minion.MAX_SLOTS; i++) m.storage[i] = y.getItemStack(p + ".storage." + i);
                for (int i = 0; i < 2; i++) m.upgrades[i] = y.getItemStack(p + ".upgrades." + i);
                m.shipper = y.getItemStack(p + ".shipper");
                m.skin = y.getItemStack(p + ".skin");
                m.cx = y.getInt(p + ".cx"); m.cy = y.getInt(p + ".cy"); m.cz = y.getInt(p + ".cz");
                m.chestSlots = y.getInt(p + ".chestSlots");
                minions.put(m.id, m);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not load minion " + key + ": " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + minions.size() + " minions.");
    }

    private boolean saveQueued;

    /** debounced save: many menu clicks in a row only write the file once, 5 seconds later */
    public void saveSoon() {
        if (saveQueued) return;
        saveQueued = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> { saveQueued = false; save(); }, 100L);
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Minion m : minions.values()) {
            String p = "minions." + m.id;
            y.set(p + ".owner", m.owner.toString());
            y.set(p + ".ownerName", m.ownerName);
            y.set(p + ".world", m.world);
            y.set(p + ".x", m.x); y.set(p + ".y", m.y); y.set(p + ".z", m.z);
            y.set(p + ".yaw", m.yaw);
            y.set(p + ".type", m.type.id);
            y.set(p + ".tier", m.tier);
            y.set(p + ".generated", m.generated);
            y.set(p + ".nextActionAt", m.nextActionAt);
            y.set(p + ".placing", m.placing);
            y.set(p + ".virtFilled", m.virtFilled);
            y.set(p + ".virtN", m.virtN);
            y.set(p + ".fuel", m.fuel == null ? null : m.fuel.name());
            y.set(p + ".fuelExpires", m.fuelExpires);
            for (int i = 0; i < Minion.MAX_SLOTS; i++) y.set(p + ".storage." + i, m.storage[i]);
            for (int i = 0; i < 2; i++) y.set(p + ".upgrades." + i, m.upgrades[i]);
            y.set(p + ".shipper", m.shipper);
            y.set(p + ".skin", m.skin);
            y.set(p + ".cx", m.cx); y.set(p + ".cy", m.cy); y.set(p + ".cz", m.cz);
            y.set(p + ".chestSlots", m.chestSlots);
        }
        YamlConfiguration u = new YamlConfiguration();
        for (var e : unique.entrySet()) u.set("players." + e.getKey(), new ArrayList<>(e.getValue()));
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            u.save(uniqueFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save minions: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------- ticker
    public void tick() {
        long now = System.currentTimeMillis();
        for (Minion m : new ArrayList<>(minions.values())) {
            World w = Bukkit.getWorld(m.world);
            if (w == null) continue;
            boolean loaded = w.isChunkLoaded(m.x >> 4, m.z >> 4);
            if (!loaded) {                         // minions keep working while their chunk is unloaded
                if (m.active) { despawn(m); m.active = false; }
                if (now >= m.nextActionAt) m.catchUp(now);
                continue;
            }
            if (!m.active) {                       // chunk just loaded / server just started
                int done = m.catchUp(now);
                if (done > 0) m.syncWorld(w);
                spawn(m);
                m.active = true;
            } else if (m.stand == null || !m.stand.isValid()) {
                spawn(m);
            }
            m.watched = !w.getNearbyPlayers(new Location(w, m.x + 0.5, m.y, m.z + 0.5), 24).isEmpty();
            if (m.fuel != null && m.fuelExpires != -1 && now >= m.fuelExpires) { m.fuel = null; m.fuelExpires = 0; }
            if (m.status == Minion.Status.FULL && m.hasRoom(new ItemStack(m.productMaterial()))) m.status = Minion.Status.OK;
            if (now >= m.nextActionAt) {
                long iv = m.intervalMs(now);
                if (now - m.nextActionAt > iv * 3) {
                    if (m.catchUp(now) > 0) m.syncWorld(w);
                } else {
                    m.act(w, m.watched && plugin.getConfig().getBoolean("animations", true));
                    m.nextActionAt = now + m.intervalMs(now);
                }
            }
            updateHolo(m);
        }
    }

    // ---------------------------------------------------------------- entities
    private int idleT = 0;

    /** called every 2 ticks: breathing / looking around for minions somebody can see */
    public void idleAll() {
        idleT += 2;
        for (Minion m : minions.values())
            if (m.active && m.watched) m.idle(idleT + (m.id.hashCode() & 63));
    }

    public void refreshAppearance() {
        for (Minion m : minions.values()) applyAppearance(m);
    }

    private static int shade(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    private TrimMaterial trimFor(int tier) {
        if (tier <= 2) return null;
        if (tier <= 4) return TrimMaterial.COPPER;
        if (tier <= 6) return TrimMaterial.IRON;
        if (tier <= 8) return TrimMaterial.GOLD;
        if (tier <= 10) return TrimMaterial.DIAMOND;
        return TrimMaterial.EMERALD;
    }

    /** leather armor in the minion's colour; higher tiers get a shiny trim (like Hypixel's tier look) */
    private ItemStack armor(Material mat, int rgb, int tier) {
        ItemStack it = new ItemStack(mat);
        try {
            LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta();
            lm.setColor(Color.fromRGB(rgb));
            TrimMaterial tm = trimFor(tier);
            if (tm != null && lm instanceof org.bukkit.inventory.meta.ArmorMeta am) am.setTrim(new ArmorTrim(tm, TrimPattern.SENTRY));
            if (tier >= 11) lm.setEnchantmentGlintOverride(true);
            it.setItemMeta(lm);
        } catch (Throwable ignored) { }
        return it;
    }

    public void applyAppearance(Minion m) {
        if (m.stand == null || !m.stand.isValid()) return;
        EntityEquipment eq = m.stand.getEquipment();
        if (eq == null) return;
        int c = m.type.armorColor;
        eq.setHelmet(m.skin != null ? m.skin.clone() : Items.icon(m.type, m.tier));
        eq.setChestplate(armor(Material.LEATHER_CHESTPLATE, c, m.tier));
        eq.setLeggings(armor(Material.LEATHER_LEGGINGS, shade(c, 0.78), m.tier));
        eq.setBoots(armor(Material.LEATHER_BOOTS, shade(c, 0.6), m.tier));
        eq.setItemInMainHand(new ItemStack(m.tool()));
    }

    public void spawn(Minion m) {
        World w = Bukkit.getWorld(m.world);
        if (w == null) return;
        despawn(m);
        Location l = new Location(w, m.x + 0.5, m.y, m.z + 0.5, m.yaw, 0f);
        ArmorStand a = w.spawn(l, ArmorStand.class);
        a.setBasePlate(false);
        a.setArms(true);
        a.setGravity(false);
        a.setSilent(true);
        a.setSmall(plugin.getConfig().getBoolean("small-minions", true));
        a.setInvulnerable(true);
        a.setPersistent(false);                   // we respawn them ourselves, never saved by the server
        a.setCanPickupItems(false);
        a.setDisabledSlots(org.bukkit.inventory.EquipmentSlot.HAND, org.bukkit.inventory.EquipmentSlot.OFF_HAND,
                org.bukkit.inventory.EquipmentSlot.HEAD, org.bukkit.inventory.EquipmentSlot.CHEST,
                org.bukkit.inventory.EquipmentSlot.LEGS, org.bukkit.inventory.EquipmentSlot.FEET);     // nobody can take its armor / tool
        m.stand = a;
        byEntity.put(a.getUniqueId(), m);
        m.initPose();
        applyAppearance(m);
    }

    public void despawn(Minion m) {
        m.stopAnim();
        m.busy = false;
        m.pending = null;
        if (m.stand != null) { byEntity.remove(m.stand.getUniqueId()); m.stand.remove(); m.stand = null; }
        removeHolo(m);
    }

    public void despawnAll() { for (Minion m : minions.values()) despawn(m); }

    private void removeHolo(Minion m) {
        if (m.holo != null) { holos.remove(m.holo.getUniqueId()); m.holo.remove(); m.holo = null; }
    }

    private void updateHolo(Minion m) {
        if (m.status == Minion.Status.OK || m.stand == null) { removeHolo(m); return; }
        String text = m.status == Minion.Status.FULL ? "§cMy storage is full! :(" : "§cI need space to work! :(";
        if (m.holo == null || !m.holo.isValid()) {
            World w = Bukkit.getWorld(m.world);
            if (w == null) return;
            boolean small = plugin.getConfig().getBoolean("small-minions", true);
            ArmorStand h = w.spawn(new Location(w, m.x + 0.5, m.y + (small ? 0.95 : 1.9), m.z + 0.5), ArmorStand.class);
            h.setVisible(false);
            h.setGravity(false);
            h.setMarker(true);
            h.setSmall(true);
            h.setInvulnerable(true);
            h.setPersistent(false);
            h.setCustomNameVisible(true);
            m.holo = h;
            holos.add(h.getUniqueId());
        }
        if (!text.equals(m.holo.getCustomName())) m.holo.setCustomName(text);
    }

    // ------------------------------------------------------------- slot limits
    public Minion minionAt(Block b) {
        for (Minion m : minions.values())
            if (m.x == b.getX() && m.y == b.getY() && m.z == b.getZ() && m.world.equals(b.getWorld().getName())) return m;
        return null;
    }

    public int owned(UUID owner) {
        int c = 0;
        for (Minion m : minions.values()) if (m.owner.equals(owner)) c++;
        return c;
    }

    public int uniqueCount(UUID id) { return unique.getOrDefault(id, Set.of()).size(); }

    public void markUnique(UUID id, MinionType t, int tier) {
        if (unique.computeIfAbsent(id, k -> new HashSet<>()).add(t.id + ":" + tier)) save();
    }

    public int slotLimit(Player p) {
        var cfg = plugin.getConfig();
        int per = Math.max(1, cfg.getInt("uniques-per-slot", 5));
        int limit = cfg.getInt("base-slots", 5) + uniqueCount(p.getUniqueId()) / per;
        limit = Math.min(limit, cfg.getInt("max-slots", 31));
        for (int n = 1; n <= 64; n++) if (p.hasPermission("minions.slots." + n)) limit = Math.max(limit, n);
        return limit;
    }

    // --------------------------------------------------------------- placement
    public void tryPlace(Player p, Block clicked, BlockFace face, ItemStack hand) {
        MinionType type = Items.typeOf(hand);
        if (type == null) return;
        int tier = Items.tierOf(hand);
        long gen = Items.genOf(hand);
        Block t = clicked.getRelative(face);

        if (!t.isPassable() || t.isLiquid() || !t.getRelative(BlockFace.UP).isPassable()) {
            p.sendMessage("§cYou can't place a minion here!");
            return;
        }
        if (!t.getRelative(BlockFace.DOWN).getType().isSolid()) {
            p.sendMessage("§cMinions need solid ground to stand on!");
            return;
        }
        if (minionAt(t) != null) { p.sendMessage("§cThere's already a minion here!"); return; }

        int limit = slotLimit(p);
        if (!p.hasPermission("minions.bypass") && owned(p.getUniqueId()) >= limit) {
            p.sendMessage("§cYou have reached your minion limit! §7(" + limit + " minion slots)");
            p.sendMessage("§7Craft more §eunique §7minions/tiers to unlock more slots.");
            return;
        }
        // the 5x5 working area is the layer the minion stands ON (one block below it)
        int ly = t.getY() + plugin.layerOffset, free = 0, filled = 0, total = 0;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                Block b = t.getWorld().getBlockAt(t.getX() + dx, ly, t.getZ() + dz);
                boolean place = b.getType().isAir() && (type.floor == null || b.getRelative(0, -1, 0).getType() == type.floor);
                if (place) { free++; total++; }
                else if (b.getType() == type.block) { filled++; total++; }
            }
        if (free + filled == 0) {
            p.sendMessage("§cThis minion needs open space! Dig out its 5x5 working area (the layer it stands on).");
            if (type.isCrop()) p.sendMessage("§7Crops need §eFarmland§7 under the working layer.");
            return;
        }

        Minion m = new Minion(UUID.randomUUID());
        m.owner = p.getUniqueId();
        m.ownerName = p.getName();
        m.world = t.getWorld().getName();
        m.x = t.getX(); m.y = t.getY(); m.z = t.getZ();
        m.yaw = p.getLocation().getYaw() + 180f;
        m.type = type; m.tier = tier; m.generated = gen;
        m.virtFilled = filled; m.virtN = total;
        m.placing = free > 0;
        long now = System.currentTimeMillis();
        m.nextActionAt = now + m.intervalMs(now);
        minions.put(m.id, m);
        spawn(m);
        m.active = true;
        markUnique(p.getUniqueId(), type, tier);

        ItemStack h = p.getInventory().getItemInMainHand();
        if (h.getAmount() <= 1) p.getInventory().setItemInMainHand(null); else h.setAmount(h.getAmount() - 1);
        Sfx.place(p);
        p.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, t.getLocation().add(0.5, 0.8, 0.5), 12, 0.3, 0.4, 0.3, 0.0);
        p.sendMessage("§aYou placed a §9" + type.display + " Minion " + Items.ROMAN[tier] + "§a! §7("
                + owned(p.getUniqueId()) + "/" + limit + ")");
        save();
    }

    // --------------------------------------------------------- storage chests
    public Minion chestAt(Block b) {
        for (Minion m : minions.values())
            if (m.chestSlots > 0 && m.cx == b.getX() && m.cy == b.getY() && m.cz == b.getZ()
                    && m.world.equals(b.getWorld().getName())) return m;
        return null;
    }

    /** right-click an existing minion chest with a bigger storage item => upgrade it in place, keeping the items */
    private boolean upgradeChest(Player p, Minion m, int slots) {
        if (!m.owner.equals(p.getUniqueId()) && !p.hasPermission("minions.admin")) {
            p.sendMessage("§cThis storage belongs to §e" + m.ownerName + "§c.");
            Sfx.error(p);
            return true;
        }
        if (slots <= m.chestSlots) {
            p.sendMessage("§cThat storage isn't bigger than the one this minion already has!");
            Sfx.error(p);
            return true;
        }
        Inventory inv = m.chestInv();
        if (inv == null) return true;
        int old = m.chestSlots;
        for (int i = old; i < Math.min(slots, inv.getSize()); i++) if (Items.isLocked(inv.getItem(i))) inv.setItem(i, null);
        for (int i = slots; i < inv.getSize(); i++) inv.setItem(i, Items.lockedPane());
        Items.give(p, Items.storageItem(Items.chestIndex(old)));
        m.chestSlots = slots;
        ItemStack h = p.getInventory().getItemInMainHand();
        if (h.getAmount() <= 1) p.getInventory().setItemInMainHand(null); else h.setAmount(h.getAmount() - 1);
        Sfx.upgrade(p);
        Block b = p.getWorld().getBlockAt(m.cx, m.cy, m.cz);
        p.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 1.0, 0.5), 15, 0.4, 0.4, 0.4, 0.0);
        p.sendMessage("§aStorage upgraded to §e" + slots + " §aslots! §7(your old chest was returned)");
        save();
        return true;
    }

    public boolean placeChest(Player p, Block clicked, BlockFace face, ItemStack hand) {
        int slots = Items.storageSlotsOf(hand);
        Minion existing = chestAt(clicked);
        if (existing != null) return upgradeChest(p, existing, slots);
        Block t = clicked.getRelative(face);
        if (!t.getType().isAir()) { p.sendMessage("§cThere's no room for that chest!"); return false; }
        Minion target = null;
        for (Minion m : minions.values()) {
            if (!m.world.equals(t.getWorld().getName()) || m.y != t.getY() || m.chestSlots > 0) continue;
            if (Math.abs(m.x - t.getX()) + Math.abs(m.z - t.getZ()) != 1) continue;
            if (!m.owner.equals(p.getUniqueId()) && !p.hasPermission("minions.admin")) continue;
            target = m;
            break;
        }
        if (target == null) {
            p.sendMessage("§cPlace this chest directly beside one of your minions (same height, not above/below).");
            return false;
        }
        t.setType(Material.CHEST);
        var data = (org.bukkit.block.data.type.Chest) t.getBlockData();
        data.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
        t.setBlockData(data, false);
        Inventory inv = ((org.bukkit.block.Chest) t.getState()).getBlockInventory();
        for (int i = slots; i < inv.getSize(); i++) inv.setItem(i, Items.lockedPane());
        target.cx = t.getX(); target.cy = t.getY(); target.cz = t.getZ();
        target.chestSlots = slots;
        ItemStack h = p.getInventory().getItemInMainHand();
        if (h.getAmount() <= 1) p.getInventory().setItemInMainHand(null); else h.setAmount(h.getAmount() - 1);
        Sfx.chest(p);
        p.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, t.getLocation().add(0.5, 1.0, 0.5), 12, 0.3, 0.3, 0.3, 0.0);
        p.sendMessage("§aYour minion now has §e" + slots + " §aextra storage slots!");
        save();
        return true;
    }

    /** removes the physical chest, returning contents + the storage item */
    public void releaseChest(Player p, Minion m, boolean breakBlock) {
        if (m.chestSlots <= 0) return;
        Inventory ci = m.chestInv();
        int slots = m.chestSlots;
        if (ci != null) {
            for (int i = 0; i < Math.min(slots, ci.getSize()); i++) {
                ItemStack c = ci.getItem(i);
                if (!Items.isAir(c)) Items.give(p, c);
            }
            ci.clear();
        }
        World w = Bukkit.getWorld(m.world);
        if (w != null && breakBlock) w.getBlockAt(m.cx, m.cy, m.cz).setType(Material.AIR, false);
        Items.give(p, Items.storageItem(Items.chestIndex(slots)));
        m.chestSlots = 0;
    }

    public void pickup(Player p, Minion m) {
        for (int i = 0; i < Minion.MAX_SLOTS; i++) if (m.storage[i] != null) Items.give(p, m.storage[i]);
        if (m.fuel != null && m.fuelExpires == -1) Items.give(p, Items.fuelItem(m.fuel, 1));
        for (ItemStack u : m.upgrades) if (u != null) Items.give(p, u);
        if (m.shipper != null) Items.give(p, m.shipper);
        if (m.skin != null) Items.give(p, m.skin);
        releaseChest(p, m, true);
        Items.give(p, Items.minionItem(m.type, m.tier, m.generated));
        despawn(m);
        minions.remove(m.id);
        p.closeInventory();
        p.sendMessage("§aYou picked up your minion!");
        save();
    }

    // ---------------------------------------------------------------- upgrading
    /** {raw, enchanted, enchanted blocks} the player owns for this resource */
    private int[] count(Player p, Material res) {
        int raw = 0, ec = 0, ec2 = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (Items.isAir(s)) continue;
            if (Items.ec2Base(s) == res) ec2 += s.getAmount();
            else if (Items.ecBase(s) == res) ec += s.getAmount();
            else if (Items.isPlain(s, res)) raw += s.getAmount();
        }
        return new int[]{raw, ec, ec2};
    }

    private static long rawValue(int[] c) { return c[0] + c[1] * 160L + c[2] * 25_600L; }

    /** raw items the player owns for this minion (enchanted count 160 each, enchanted blocks 25600 each) */
    public long have(Player p, MinionType t) { return rawValue(count(p, t.resource)); }

    public boolean canPay(Player p, MinionType t, int cost) { return have(p, t) >= cost; }

    /** kind: 0 = raw, 1 = enchanted, 2 = enchanted block */
    private void take(Player p, Material res, int kind, int n) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getStorageContents();
        for (int i = 0; i < c.length && n > 0; i++) {
            ItemStack s = c[i];
            if (Items.isAir(s)) continue;
            boolean match = kind == 2 ? Items.ec2Base(s) == res : kind == 1 ? Items.ecBase(s) == res : Items.isPlain(s, res);
            if (!match) continue;
            int t = Math.min(n, s.getAmount());
            n -= t;
            if (t == s.getAmount()) inv.setItem(i, null); else s.setAmount(s.getAmount() - t);
        }
    }

    public boolean upgrade(Player p, Minion m) {
        if (m.tier >= MinionType.MAX_TIER) return false;
        int cost = m.type.cost(m.tier + 1);
        if (!canPay(p, m.type, cost)) {
            p.sendMessage("§cYou don't have the required materials!");
            return false;
        }
        // pay with raw first, then enchanted, then enchanted blocks; the overpay comes back as enchanted + raw
        int[] c = count(p, m.type.resource);
        int useRaw = Math.min(c[0], cost);
        long rest = cost - useRaw;
        int useEc = (int) Math.min(c[1], (rest + 159) / 160);
        rest -= useEc * 160L;
        int useEc2 = 0;
        if (rest > 0) { useEc2 = (int) ((rest + 25_599) / 25_600); rest -= useEc2 * 25_600L; }
        take(p, m.type.resource, 0, useRaw);
        take(p, m.type.resource, 1, useEc);
        take(p, m.type.resource, 2, useEc2);
        long change = Math.max(0, -rest);
        long backEc = change / 160, backRaw = change % 160;
        while (backEc > 0) {
            int amt = (int) Math.min(64, backEc);
            Items.give(p, Items.enchanted(m.type.resource, amt));
            backEc -= amt;
        }
        while (backRaw > 0) {
            int amt = (int) Math.min(64, backRaw);
            Items.give(p, new ItemStack(m.type.resource, amt));
            backRaw -= amt;
        }
        m.tier++;
        markUnique(p.getUniqueId(), m.type, m.tier);
        applyAppearance(m);
        Sfx.upgrade(p);
        if (m.stand != null && m.stand.isValid())
            m.stand.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, m.stand.getLocation().add(0, 0.8, 0), 25, 0.3, 0.4, 0.3, 0.2);
        p.sendMessage("§aYour minion is now §9" + m.type.display + " Minion " + Items.ROMAN[m.tier] + "§a!");
        save();
        return true;
    }
}
