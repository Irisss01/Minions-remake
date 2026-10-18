package dev.minions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.function.IntConsumer;

import java.util.*;

/** A placed minion: data + all of its behaviour. */
public final class Minion {
    public enum Status { OK, FULL, NO_SPACE }

    public static final int MAX_SLOTS = 15;

    public final UUID id;
    public UUID owner;
    public String ownerName = "?";
    public String world;
    public int x, y, z;
    public float yaw;
    public MinionType type;
    public int tier = 1;
    public long generated;
    public final ItemStack[] storage = new ItemStack[MAX_SLOTS];
    public final ItemStack[] upgrades = new ItemStack[2];
    public ItemStack shipper;
    public ItemStack skin;              // cosmetic Minion Skin (a custom player head) shown instead of the tier head
    public Fuel fuel;
    public long fuelExpires;            // 0 = none, -1 = never expires
    public long nextActionAt;
    public boolean placing = true;      // next action is a placement (filling the area / refilling the hole it just mined)
    public int virtFilled, virtN;       // bookkeeping for offline / unloaded work
    public int cx, cy, cz, chestSlots;  // physical Minion Storage chest (chestSlots 0 = none)

    // runtime only
    public ArmorStand stand, holo;
    public boolean active, busy;
    public boolean watched;             // a player is close enough to see the animations
    public Block pending;               // the spot we just mined: the next action puts a block back exactly there
    public Status status = Status.OK;
    private final Random rnd = new Random();
    private BukkitTask anim;
    // current pose in degrees (right arm xyz, left arm xz, head xy, body x)
    private double pRx = IDLE_RX, pRy = 0, pRz = IDLE_RZ, pLx = IDLE_LX, pLz = IDLE_LZ, pHx = 0, pHy = 0, pBx = 0;
    private static final double IDLE_RX = -35, IDLE_RZ = 6, IDLE_LX = -20, IDLE_LZ = -8;

    public Minion(UUID id) { this.id = id; }

    // ------------------------------------------------------------------ stats
    public int slots() { return Math.min(MAX_SLOTS, type.slots(tier)); }
    public int maxStorage() { return type.storage(tier); }
    public int radius() { return hasUpgrade(Upgrade.EXPANDER) ? 3 : 2; }
    public int layerY() { return y + MinionsPlugin.I.layerOffset; }

    public boolean hasUpgrade(Upgrade u) {
        for (ItemStack s : upgrades) if (Items.upgradeOf(s) == u) return true;
        return false;
    }

    public Upgrade shippingUpgrade() { return Items.upgradeOf(shipper); }

    public boolean fuelActive(long at) {
        return fuel != null && (fuelExpires == -1 || at < fuelExpires);
    }

    private double dayFactor(long at) {
        World w = Bukkit.getWorld(world);
        if (w == null || System.currentTimeMillis() - at > 60_000L) return 0.5;     // offline: about half the time is day
        long t = w.getTime();
        return (t < 12300 || t > 23850) ? 1.0 : 0.0;
    }

    /** total speed bonus: fuel + speed upgrades (0.10 = +10%) */
    public double boost(long at) {
        double b = 0;
        if (fuelActive(at)) b += fuel.boost() * (fuel.daytime ? dayFactor(at) : 1.0);
        for (ItemStack s : upgrades) {
            Upgrade u = Items.upgradeOf(s);
            if (u != null) b += u.speed;
        }
        return b;
    }

    public long intervalMs(long at) {
        return (long) (type.interval(tier) * 1000.0 / (1.0 + boost(at)));
    }

    public boolean hasCompactor() { return hasUpgrade(Upgrade.COMPACTOR) || hasUpgrade(Upgrade.SUPER_COMPACTOR); }

    /** what one harvest produces (after Auto Smelter / Flint Shovel) */
    public Material productMaterial() {
        if (type.resource == Material.GRAVEL && hasUpgrade(Upgrade.FLINT_SHOVEL)) return Material.FLINT;
        if (hasUpgrade(Upgrade.AUTO_SMELTER)) {
            Material s = Upgrade.SMELT.get(type.resource);
            if (s != null) return s;
        }
        return type.resource;
    }

    /** the tool in its hand gets better every two tiers */
    public Material tool() {
        String[] pre = {"WOODEN", "STONE", "IRON", "GOLDEN", "DIAMOND", "NETHERITE"};
        String n = type.tool.name();
        Material m = Material.getMaterial(pre[Math.min(5, (tier - 1) / 2)] + "_" + n.substring(n.indexOf('_') + 1));
        return m != null ? m : type.tool;
    }

    /** items currently stored (own storage + linked chest) and the total capacity */
    public int storedItems() {
        int c = 0;
        for (int i = 0; i < slots(); i++) if (storage[i] != null) c += storage[i].getAmount();
        Inventory ci = chestInv();
        if (ci != null)
            for (int i = 0; i < Math.min(chestSlots, ci.getSize()); i++) {
                ItemStack s = ci.getItem(i);
                if (!Items.isAir(s) && !Items.isLocked(s)) c += s.getAmount();
            }
        return c;
    }

    public int capacity() { return (slots() + chestSlots) * 64; }

    public double itemsPerHour(long at) {
        double secs = Math.max(0.1, intervalMs(at) / 1000.0);
        double perAction = 3600.0 / (secs * 2.0);                // one block = place + break = 2 actions
        double v = perAction * (type.min + type.max) / 2.0;
        if (hasUpgrade(Upgrade.DIAMOND_SPREADING)) v += perAction * 0.10;
        return v;
    }

    // ---------------------------------------------------------------- storage
    public Inventory chestInv() {
        if (chestSlots <= 0) return null;
        World w = Bukkit.getWorld(world);
        if (w == null || !w.isChunkLoaded(cx >> 4, cz >> 4)) return null;
        Block b = w.getBlockAt(cx, cy, cz);
        if (b.getState() instanceof Chest c) return c.getBlockInventory();
        chestSlots = 0;                                      // chest got removed
        return null;
    }

    public boolean hasRoom(ItemStack s) {
        for (int i = 0; i < slots(); i++) {
            ItemStack c = storage[i];
            if (c == null) return true;
            if (c.isSimilar(s) && c.getAmount() < c.getMaxStackSize()) return true;
        }
        Inventory ci = chestInv();
        if (ci != null) {
            for (int i = 0; i < Math.min(chestSlots, ci.getSize()); i++) {
                ItemStack c = ci.getItem(i);
                if (Items.isAir(c) || (c.isSimilar(s) && c.getAmount() < c.getMaxStackSize())) return true;
            }
        }
        return false;
    }

    /** returns the amount that did NOT fit */
    public int add(ItemStack in) {
        int left = in.getAmount();
        for (int i = 0; i < slots() && left > 0; i++) {
            ItemStack c = storage[i];
            if (c != null && c.isSimilar(in)) {
                int mv = Math.min(c.getMaxStackSize() - c.getAmount(), left);
                if (mv > 0) { c.setAmount(c.getAmount() + mv); left -= mv; }
            }
        }
        for (int i = 0; i < slots() && left > 0; i++) {
            if (storage[i] == null) {
                int mv = Math.min(in.getMaxStackSize(), left);
                ItemStack n = in.clone();
                n.setAmount(mv);
                storage[i] = n;
                left -= mv;
            }
        }
        Inventory ci = left > 0 ? chestInv() : null;
        final int beforeChest = left;
        if (ci != null) {
            int n = Math.min(chestSlots, ci.getSize());
            for (int i = 0; i < n && left > 0; i++) {
                ItemStack c = ci.getItem(i);
                if (!Items.isAir(c) && c.isSimilar(in)) {
                    int mv = Math.min(c.getMaxStackSize() - c.getAmount(), left);
                    if (mv > 0) { c.setAmount(c.getAmount() + mv); ci.setItem(i, c); left -= mv; }
                }
            }
            for (int i = 0; i < n && left > 0; i++) {
                if (Items.isAir(ci.getItem(i))) {
                    int mv = Math.min(in.getMaxStackSize(), left);
                    ItemStack nn = in.clone();
                    nn.setAmount(mv);
                    ci.setItem(i, nn);
                    left -= mv;
                }
            }
            if (left < beforeChest) chestFx();
        }
        return left;
    }

    /** little enchant-glyph trail from the minion into its chest */
    private void chestFx() {
        if (stand == null || !stand.isValid() || chestSlots <= 0) return;
        World w = stand.getWorld();
        Location a = new Location(w, x + 0.5, y + 0.7, z + 0.5), b = new Location(w, cx + 0.5, cy + 0.7, cz + 0.5);
        Vector d = b.toVector().subtract(a.toVector());
        for (int i = 0; i <= 5; i++) w.spawnParticle(Particle.ENCHANT, a.clone().add(d.clone().multiply(i / 5.0)), 1, 0.02, 0.02, 0.02, 0.0);
    }

    private boolean isRaw(ItemStack s) {
        return s != null && s.getType() == productMaterial() && !s.hasItemMeta();
    }

    private int rawCount() {
        int c = 0;
        for (int i = 0; i < slots(); i++) if (isRaw(storage[i])) c += storage[i].getAmount();
        return c;
    }

    private void removeRaw(int n) {
        for (int i = 0; i < slots() && n > 0; i++) {
            ItemStack s = storage[i];
            if (isRaw(s)) {
                int t = Math.min(n, s.getAmount());
                n -= t;
                if (t == s.getAmount()) storage[i] = null; else s.setAmount(s.getAmount() - t);
            }
        }
    }

    private boolean isEc(ItemStack s, Material res) { return Items.ecBase(s) == res; }

    private int ecCount(Material res) {
        int c = 0;
        for (int i = 0; i < slots(); i++) if (isEc(storage[i], res)) c += storage[i].getAmount();
        return c;
    }

    private void removeEc(Material res, int n) {
        for (int i = 0; i < slots() && n > 0; i++) {
            ItemStack s = storage[i];
            if (isEc(s, res)) {
                int t = Math.min(n, s.getAmount());
                n -= t;
                if (t == s.getAmount()) storage[i] = null; else s.setAmount(s.getAmount() - t);
            }
        }
    }

    /** puts n enchanted items back (a stack holds at most 64) - used to undo a compaction that found no room */
    private void giveBackEc(Material res, int n) {
        while (n > 0) {
            int k = Math.min(64, n);
            add(Items.enchanted(res, k));
            n -= k;
        }
    }

    /**
     * Compactor: 160 Lapis -> 1 Enchanted Lapis Lazuli.
     * Super Compactor 3000: also 160 Enchanted Lapis Lazuli -> 1 Enchanted Lapis Block (Lapis, Coal, Iron, Gold,
     * Diamond, Redstone, Emerald, Quartz). The results are the same items you craft yourself, so they can be used to
     * upgrade minions.
     */
    private void compact() {
        if (!hasCompactor()) return;
        Material pm = productMaterial();
        while (rawCount() >= 160) {
            removeRaw(160);
            if (add(Items.enchanted(pm, 1)) > 0) {          // no room for the compacted item: undo
                add(new ItemStack(pm, 160));
                break;
            }
        }
        if (hasUpgrade(Upgrade.SUPER_COMPACTOR) && type.enchBlock() != null && pm == type.resource) {
            while (ecCount(pm) >= 160) {
                removeEc(pm, 160);
                if (add(Items.enchantedBlock(type, 1)) > 0) {
                    giveBackEc(pm, 160);
                    break;
                }
            }
        }
    }

    // --------------------------------------------------------- automated shipping
    private double price(ItemStack s) {
        var cfg = MinionsPlugin.I.getConfig();
        Material eb = Items.ecBase(s), eb2 = Items.ec2Base(s);
        Material m = eb2 != null ? eb2 : eb != null ? eb : s.getType();
        double p = cfg.getDouble("prices." + m.name(), cfg.getDouble("prices.default", 1.0));
        return eb2 != null ? p * 160 * 160 : eb != null ? p * 160 : p;
    }

    /** sells everything (storage + chest). Returns false if shipping isn't possible. */
    public boolean sellAll() {
        Upgrade u = shippingUpgrade();
        if (u == null || !Eco.available()) return false;
        double total = 0;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (storage[i] != null) { total += price(storage[i]) * storage[i].getAmount(); storage[i] = null; }
        }
        Inventory ci = chestInv();
        if (ci != null) {
            for (int i = 0; i < Math.min(chestSlots, ci.getSize()); i++) {
                ItemStack c = ci.getItem(i);
                if (!Items.isAir(c)) { total += price(c) * c.getAmount(); ci.setItem(i, null); }
            }
        }
        total *= u.rate;
        if (total <= 0) return false;
        Eco.deposit(owner, total);
        Player p = Bukkit.getPlayer(owner);
        if (p != null) p.sendMessage("§6Your " + type.display + " Minion sold items for §e" + String.format("%.1f", total) + " coins§6!");
        return true;
    }

    private void produce() {
        ItemStack s = new ItemStack(productMaterial(), type.amount(rnd));
        if (add(s.clone()) >= 0) generated += s.getAmount();
        if (hasUpgrade(Upgrade.DIAMOND_SPREADING) && rnd.nextDouble() < 0.10) add(new ItemStack(Material.DIAMOND));
        compact();
    }

    private boolean roomForHarvest() {
        ItemStack probe = new ItemStack(productMaterial());
        if (hasRoom(probe)) return true;
        if (shippingUpgrade() != null && sellAll()) return hasRoom(probe);
        return false;
    }

    // ------------------------------------------------------------ area helpers
    private boolean isMineable(Block b) {
        if (b.getType() != type.block) return false;
        return !(b.getBlockData() instanceof Ageable a) || a.getAge() >= a.getMaximumAge();
    }

    private boolean isPlaceable(Block b) {
        return b.getType().isAir() && (type.floor == null || b.getRelative(0, -1, 0).getType() == type.floor);
    }

    private void scan(World w, List<Block> mine, List<Block> air) {
        int r = radius(), ly = layerY();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0) continue;
                Block b = w.getBlockAt(x + dx, ly, z + dz);
                if (isMineable(b)) mine.add(b); else if (isPlaceable(b)) air.add(b);
            }
        }
    }

    private BlockData freshBlock() {
        BlockData d = type.block.createBlockData();
        if (d instanceof Ageable a) a.setAge(a.getMaximumAge());
        return d;
    }

    /** makes the real blocks match the virtual state after offline work */
    public void syncWorld(World w) {
        List<Block> mine = new ArrayList<>(), air = new ArrayList<>();
        scan(w, mine, air);
        List<Block> all = new ArrayList<>(mine);
        all.addAll(air);
        if (all.isEmpty()) return;
        Collections.shuffle(all, rnd);
        int k = Math.min(Math.max(0, virtFilled), all.size());
        for (int i = 0; i < all.size(); i++) {
            Block b = all.get(i);
            if (i < k) { if (!isMineable(b)) b.setBlockData(freshBlock(), false); }
            else if (isMineable(b)) b.setType(Material.AIR, false);
        }
        placing = k < all.size();
    }

    // -------------------------------------------------------------- animation
    private void later(long ticks, Runnable r) {
        if (ticks <= 0) r.run(); else Bukkit.getScheduler().runTaskLater(MinionsPlugin.I, r, ticks);
    }

    private static double ease(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static float lerpAngle(float a, float b, float t) {
        float d = (b - a) % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return a + d * t;
    }

    private float yawTo(Block t) {
        return (float) Math.toDegrees(Math.atan2(-(t.getX() - x), t.getZ() - z));
    }

    private boolean standOk() { return stand != null && stand.isValid(); }

    public void stopAnim() {
        if (anim != null) { anim.cancel(); anim = null; }
    }

    /** sets the whole pose in one go (degrees) */
    private void pose(double rx, double ry, double rz, double lx, double lz, double hx, double hy, double bx) {
        pRx = rx; pRy = ry; pRz = rz; pLx = lx; pLz = lz; pHx = hx; pHy = hy; pBx = bx;
        if (!standOk()) return;
        stand.setRightArmPose(new EulerAngle(Math.toRadians(rx), Math.toRadians(ry), Math.toRadians(rz)));
        stand.setLeftArmPose(new EulerAngle(Math.toRadians(lx), 0, Math.toRadians(lz)));
        stand.setHeadPose(new EulerAngle(Math.toRadians(hx), Math.toRadians(hy), 0));
        stand.setBodyPose(new EulerAngle(Math.toRadians(bx), 0, 0));
    }

    public void initPose() { pose(IDLE_RX, 0, IDLE_RZ, IDLE_LX, IDLE_LZ, 0, 0, 0); }

    /** runs one frame per tick; when the stand disappears the action is abandoned */
    private void animate(int total, IntConsumer frame, Runnable end) {
        stopAnim();
        final int[] k = {0};
        anim = Bukkit.getScheduler().runTaskTimer(MinionsPlugin.I, () -> {
            if (!standOk()) { stopAnim(); busy = false; return; }
            if (k[0] >= total) { stopAnim(); end.run(); return; }
            frame.accept(k[0]++);
        }, 0L, 1L);
    }

    /** eases every joint back to the idle pose, then calls done */
    private void relax(Runnable done) {
        final double a0 = pRx, b0 = pRy, c0 = pRz, d0 = pLx, e0 = pLz, f0 = pHx, g0 = pHy, h0 = pBx;
        animate(6, k -> {
            double t = ease((k + 1) / 6.0);
            pose(lerp(a0, IDLE_RX, t), lerp(b0, 0, t), lerp(c0, IDLE_RZ, t), lerp(d0, IDLE_LX, t),
                    lerp(e0, IDLE_LZ, t), lerp(f0, 0, t), lerp(g0, 0, t), lerp(h0, 0, t));
        }, () -> { if (done != null) done.run(); });
    }

    /** gentle breathing / looking around while waiting; a sad head-shake when it can't work */
    public void idle(int t) {
        if (!standOk() || busy || anim != null) return;
        double s = Math.sin(t / 12.0), nod = Math.sin(t / 31.0);
        if (status != Status.OK) pose(IDLE_RX, 0, IDLE_RZ, IDLE_LX, IDLE_LZ, 20, 16 * Math.sin(t / 4.0), 4);
        else pose(IDLE_RX + 2.5 * s, 0, IDLE_RZ, IDLE_LX - 2.5 * s, IDLE_LZ, 3 * s, 12 * nod, 1.2 * s);
    }

    private void holdBlock(boolean block) {
        if (!standOk() || stand.getEquipment() == null) return;
        stand.getEquipment().setItemInMainHand(new ItemStack(block ? type.icon() : tool()));
    }

    private static Location center(Block b) { return b.getLocation().add(0.5, 0.5, 0.5); }

    private void blockFx(Block b, BlockData d, boolean place) {
        World w = b.getWorld();
        var sg = d.getSoundGroup();
        w.playSound(center(b), place ? sg.getPlaceSound() : sg.getBreakSound(), 0.9f, place ? 1f : 0.9f);
        w.spawnParticle(Particle.BLOCK, center(b), place ? 14 : 24, 0.28, 0.28, 0.28, 0.0, d);
    }

    private void crack(Block b, float progress) {
        for (Player p : b.getWorld().getNearbyPlayers(center(b), 32)) p.sendBlockDamage(b.getLocation(), progress);
    }

    /** one pickaxe hit: sound, chips, crack stage, arm swing */
    private void hit(Block t, BlockData d, double progress) {
        World w = t.getWorld();
        w.playSound(center(t), d.getSoundGroup().getHitSound(), 0.8f, 0.85f + rnd.nextFloat() * 0.3f);
        w.spawnParticle(Particle.BLOCK, center(t), 5, 0.25, 0.25, 0.25, 0.0, d);
        crack(t, (float) Math.min(0.95, progress));
        if (standOk()) stand.swingMainHand();
    }

    /** the harvested item flies from the block into the minion */
    private void popFx(Block t) {
        World w = t.getWorld();
        Location a = center(t), b = new Location(w, x + 0.5, y + 0.7, z + 0.5);
        Vector d = b.toVector().subtract(a.toVector());
        ItemStack icon = new ItemStack(productMaterial());
        for (int i = 0; i <= 6; i++) {
            final double f = i / 6.0;
            later(i, () -> w.spawnParticle(Particle.ITEM, a.clone().add(d.clone().multiply(f)), 2, 0.04, 0.04, 0.04, 0.0, icon));
        }
        later(7, () -> w.playSound(b, "entity.item.pickup", 0.35f, 1.4f));
    }

    // -------------------------------------------------------------- behaviour
    /**
     * One real, visible action (chunk is loaded). Exactly like Hypixel:
     *   1. while there is an empty spot in the working area  -> place a block there (fills the whole area first)
     *   2. once the area is full                              -> break ONE block, the resource goes into storage
     *   3. next action                                        -> place a block back in the spot it just mined
     *   4. then break another block ... and so on, forever (loop 2 -> 3 -> 2 -> 3)
     */
    public void act(World w, boolean animate) {
        if (busy) return;
        status = Status.OK;
        List<Block> mine = new ArrayList<>(), air = new ArrayList<>();
        scan(w, mine, air);
        virtN = mine.size() + air.size();
        virtFilled = mine.size();
        if (virtN == 0) { placing = true; pending = null; status = Status.NO_SPACE; return; }

        if (!air.isEmpty()) {                                       // fill phase / refill the hole we just made
            Block t = (pending != null && air.contains(pending)) ? pending : air.get(rnd.nextInt(air.size()));
            pending = null;
            placing = air.size() > 1;                               // more holes left => keep placing, else break next
            virtFilled++;
            doPlace(w, t, animate);
            return;
        }
        if (!roomForHarvest()) { status = Status.FULL; return; }
        Block t = mine.get(rnd.nextInt(mine.size()));
        pending = t;                                                // this exact spot gets refilled next
        placing = true;
        virtFilled--;
        doMine(w, t, animate);
    }

    private void doPlace(World w, Block t, boolean animate) {
        Runnable fin = () -> {
            if (!isPlaceable(t)) return;
            BlockData d = freshBlock();
            t.setBlockData(d, true);
            blockFx(t, d, true);
            if (type.isCrop()) w.spawnParticle(Particle.HAPPY_VILLAGER, center(t), 6, 0.3, 0.3, 0.3, 0.0);
        };
        final int TURN = 4, REACH = 11;
        int budget = (int) (intervalMs(System.currentTimeMillis()) / 50) - 8;      // ticks we have before the next action
        if (!animate || !standOk() || budget < TURN + REACH) { fin.run(); return; }
        busy = true;
        holdBlock(true);
        final float from = stand.getLocation().getYaw(), to = yawTo(t);
        final double sRx = pRx, sLx = pLx;
        animate(TURN + REACH, k -> {
            if (k < TURN) {                                           // turn smoothly, raise both hands
                double f = ease((k + 1) / (double) TURN);
                stand.setRotation(lerpAngle(from, to, (float) f), 0f);
                pose(lerp(sRx, -50, f), 0, IDLE_RZ, lerp(sLx, -45, f), -8, 10 * f, 0, 3 * f);
                return;
            }
            if (k == TURN) yaw = to;
            int s = k - TURN;
            double push = s <= 6 ? ease(s / 6.0) : 1.0 - ease((s - 6) / 5.0);   // both hands push forward, then back
            pose(-50 - 50 * push, -4, IDLE_RZ, -45 - 40 * push, -6, 10 + 8 * push, 0, 3 + 7 * push);
            if (s == 6) fin.run();
        }, () -> {
            holdBlock(false);
            relax(() -> busy = false);
        });
    }

    private void doMine(World w, Block t, boolean animate) {
        Runnable fin = () -> {
            crack(t, 0f);
            if (!isMineable(t)) return;
            BlockData d = t.getBlockData();
            t.setType(Material.AIR, false);
            if (animate) {
                blockFx(t, d, false);
                popFx(t);
            }
            produce();
        };
        final int TURN = 4, PER = 6;
        int budget = (int) (intervalMs(System.currentTimeMillis()) / 50) - 8;      // fast fuel => fewer strikes
        if (!animate || !standOk() || budget < TURN + PER) { fin.run(); return; }
        final int STRIKES = Math.max(1, Math.min(4, (budget - TURN) / PER));
        busy = true;
        final BlockData d0 = t.getBlockData();
        final float from = stand.getLocation().getYaw(), to = yawTo(t);
        final double sRx = pRx, sLx = pLx;
        animate(TURN + STRIKES * PER, k -> {
            if (k < TURN) {                                           // turn, lift the tool
                double f = ease((k + 1) / (double) TURN);
                stand.setRotation(lerpAngle(from, to, (float) f), 0f);
                pose(lerp(sRx, -150, f), -8, IDLE_RZ, lerp(sLx, -25, f), -6, 12 * f, 0, 2 * f);
                return;
            }
            if (k == TURN) yaw = to;
            int s = k - TURN;
            double c = Math.cos(2 * Math.PI * (s % PER) / PER);       // 1 = tool up, -1 = tool down
            pose(-125 - 45 * c, -8, IDLE_RZ, -25 + 12 * c, -6, 12 + 8 * Math.max(0, -c), 0, 4 - 4 * c);
            if (s % PER == PER / 2) hit(t, d0, (s + 1) / (double) (STRIKES * PER));
        }, () -> {
            fin.run();
            relax(() -> busy = false);
        });
    }

    /** Credits actions that happened while the chunk was unloaded / the server was off. */
    public int catchUp(long now) {
        int done = 0;
        long cap = MinionsPlugin.I.getConfig().getLong("offline.max-hours", 0) * 3_600_000L;
        if (cap > 0 && now - nextActionAt > cap) nextActionAt = now - cap;
        int guard = 0;
        while (nextActionAt <= now && guard++ < 3_000_000) {
            if (!virtualAction(nextActionAt)) {                       // storage full: idle
                status = Status.FULL;
                nextActionAt = now + intervalMs(now);
                return done;
            }
            done++;
            status = Status.OK;
            nextActionAt += Math.max(250, intervalMs(nextActionAt));
        }
        return done;
    }

    /** same loop as act(), but on numbers only (chunk unloaded / server was off) */
    private boolean virtualAction(long at) {
        if (fuel != null && fuelExpires != -1 && at >= fuelExpires) { fuel = null; fuelExpires = 0; }
        int n = virtN > 0 ? virtN : (2 * radius() + 1) * (2 * radius() + 1) - 1;
        if (virtFilled > n) virtFilled = n;
        if (virtFilled < n) {                                       // place (fill, or refill the mined spot)
            virtFilled++;
            placing = virtFilled < n;
            return true;
        }
        if (!roomForHarvest()) return false;                        // area full => break one
        virtFilled--;
        produce();
        placing = true;
        return true;
    }
}
