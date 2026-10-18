package dev.minions;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.*;

/**
 * Crafting-table support. Bukkit recipes can't demand "10 items in one slot", so we register three
 * placeholder shapes and validate the real amounts ourselves in Prepare/CraftItemEvent.
 *
 *  Ring  (8 around centre):  Tier I  = 10 resource each + wooden tool in the centre
 *                            Tier up = per-slot cost each + the previous-tier minion in the centre
 *                            Storage = Enchanted Oak Log ring around the previous storage chest
 *  Empty-centre ring:        Small Storage = 8 Oak Log each
 *  Plus (5 slots):           Enchanted item = 32 of the same resource in each of the 5 slots
 */
public final class Recipes implements Listener {
    private final MinionsPlugin plugin;
    private final NamespacedKey RING, STORE, PLUS;

    private record Res(ItemStack out, int[] take, MinionType type, int tier) {}

    public Recipes(MinionsPlugin plugin) {
        this.plugin = plugin;
        RING = new NamespacedKey(plugin, "minion_ring");
        STORE = new NamespacedKey(plugin, "minion_storage_ring");
        PLUS = new NamespacedKey(plugin, "minion_enchant_plus");
    }

    public void register() {
        Set<Material> res = new LinkedHashSet<>(), centers = new LinkedHashSet<>();
        for (MinionType t : MinionType.values()) {
            res.add(t.resource);
            centers.add(t.tool);
            centers.add(t.icon());
        }
        centers.add(Material.PLAYER_HEAD);
        centers.add(Material.LEATHER_HELMET);      // minions without a skin texture wear a dyed helmet
        centers.add(Material.CHEST);
        res.add(Material.OAK_LOG);

        ShapedRecipe ring = new ShapedRecipe(RING, new ItemStack(Material.BARRIER));
        ring.shape("AAA", "ABA", "AAA");
        ring.setIngredient('A', new RecipeChoice.MaterialChoice(new ArrayList<>(res)));
        ring.setIngredient('B', new RecipeChoice.MaterialChoice(new ArrayList<>(centers)));
        Bukkit.addRecipe(ring);

        ShapedRecipe store = new ShapedRecipe(STORE, new ItemStack(Material.BARRIER));
        store.shape("AAA", "A A", "AAA");
        store.setIngredient('A', Material.OAK_LOG);
        Bukkit.addRecipe(store);

        ShapedRecipe plus = new ShapedRecipe(PLUS, new ItemStack(Material.BARRIER));
        plus.shape(" A ", "AAA", " A ");
        plus.setIngredient('A', new RecipeChoice.MaterialChoice(new ArrayList<>(res)));
        Bukkit.addRecipe(plus);
    }

    public void unregister() {
        Bukkit.removeRecipe(RING);
        Bukkit.removeRecipe(STORE);
        Bukkit.removeRecipe(PLUS);
    }

    private boolean ours(Recipe r) {
        if (!(r instanceof Keyed k)) return false;
        NamespacedKey key = k.getKey();
        return key.equals(RING) || key.equals(STORE) || key.equals(PLUS);
    }

    // ------------------------------------------------------------- evaluation
    private static final int[] RING_IDX = {0, 1, 2, 3, 5, 6, 7, 8};

    private Res eval(ItemStack[] m) {
        if (m == null || m.length < 9) return null;
        boolean ringFull = true;
        for (int i : RING_IDX) if (Items.isAir(m[i])) ringFull = false;
        if (ringFull) return evalRing(m);
        boolean plus = !Items.isAir(m[1]) && !Items.isAir(m[3]) && !Items.isAir(m[4]) && !Items.isAir(m[5]) && !Items.isAir(m[7])
                && Items.isAir(m[0]) && Items.isAir(m[2]) && Items.isAir(m[6]) && Items.isAir(m[8]);
        return plus ? evalPlus(m) : null;
    }

    private Res evalPlus(ItemStack[] m) {
        Material res = m[4].getType();
        int[] idx = {1, 3, 4, 5, 7};
        Material eb = Items.ecBase(m[4]);
        if (eb != null) {                                           // 5 x 32 Enchanted X  ->  Enchanted X Block
            MinionType bt = MinionType.fromResource(eb);
            if (bt == null || bt.enchBlock() == null) return null;
            for (int i : idx) if (Items.ecBase(m[i]) != eb || m[i].getAmount() < 32) return null;
            int[] take = new int[9];
            for (int i : idx) take[i] = 32;
            return new Res(Items.enchantedBlock(bt, 1), take, null, 0);
        }
        for (int i : idx) {
            if (!Items.isPlain(m[i], res) || m[i].getAmount() < 32) return null;
        }
        if (MinionType.fromResource(res) == null && res != Material.OAK_LOG) return null;
        int[] take = new int[9];
        for (int i : idx) take[i] = 32;
        return new Res(Items.enchanted(res, 1), take, null, 0);
    }

    private Res evalRing(ItemStack[] m) {
        Material base = Items.ecBase(m[0]);
        boolean ec = base != null;
        Material res = ec ? base : m[0].getType();
        int min = Integer.MAX_VALUE;
        for (int i : RING_IDX) {
            boolean ok = ec ? Items.ecBase(m[i]) == res : Items.isPlain(m[i], res);
            if (!ok) return null;
            min = Math.min(min, m[i].getAmount());
        }
        ItemStack c = m[4];
        int[] take = new int[9];

        // ---- storage chests
        if (Items.isAir(c)) {
            if (!ec && res == Material.OAK_LOG && min >= 8) {
                for (int i : RING_IDX) take[i] = 8;
                return new Res(Items.storageItem(0), take, null, 0);
            }
            return null;
        }
        int st = Items.storageSlotsOf(c);
        if (st > 0) {
            int idx = -1;
            for (int i = 0; i < Items.CHEST_SLOTS.length; i++) if (Items.CHEST_SLOTS[i] == st) idx = i;
            if (!ec || res != Material.OAK_LOG || idx < 0 || idx >= 2) return null;     // craftable: Medium, Large
            int need = idx + 1;
            if (min < need) return null;
            for (int i : RING_IDX) take[i] = need;
            take[4] = 1;
            return new Res(Items.storageItem(idx + 1), take, null, 0);
        }

        // ---- tier up with a minion in the centre
        MinionType t = Items.typeOf(c);
        if (t != null) {
            int cur = Items.tierOf(c), next = cur + 1;
            if (next > 11 || t.resource != res) return null;       // tier XII is done in the minion menu
            int per = t.perSlot(next);
            boolean needEc = per >= 160;
            int amt = needEc ? per / 160 : per;
            if (needEc != ec || min < amt) return null;
            for (int i : RING_IDX) take[i] = amt;
            take[4] = 1;
            return new Res(Items.minionItem(t, next, Items.genOf(c)), take, t, next);
        }

        // ---- tier I: resource ring around the wooden tool
        if (ec) return null;
        for (MinionType mt : MinionType.values()) {
            if (mt.resource != res || !Items.isPlain(c, mt.tool)) continue;
            int amt = mt.perSlot(1);
            if (min < amt) return null;
            for (int i : RING_IDX) take[i] = amt;
            take[4] = 1;
            return new Res(Items.minionItem(mt, 1, 0), take, mt, 1);
        }
        return null;
    }

    // ----------------------------------------------------------------- events
    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        if (!ours(e.getRecipe())) return;
        Res r = eval(e.getInventory().getMatrix());
        e.getInventory().setResult(r == null ? null : r.out());
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!ours(e.getRecipe())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        CraftingInventory inv = e.getInventory();
        Res r = eval(inv.getMatrix());
        if (r == null) { inv.setResult(null); return; }

        ItemStack[] mat = inv.getMatrix();
        for (int i = 0; i < 9 && i < mat.length; i++) {
            if (r.take()[i] > 0 && mat[i] != null) {
                int a = mat[i].getAmount() - r.take()[i];
                if (a <= 0) mat[i] = null; else mat[i].setAmount(a);
            }
        }
        inv.setMatrix(mat);

        ItemStack out = r.out().clone();
        if (e.isShiftClick() || !Items.isAir(p.getItemOnCursor())) Items.give(p, out);
        else p.setItemOnCursor(out);
        if (r.type() != null) plugin.manager.markUnique(p.getUniqueId(), r.type(), r.tier());
        p.playSound(p.getLocation(), "entity.item.pickup", 1f, 1f);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Res n = eval(inv.getMatrix());
            inv.setResult(n == null ? null : n.out());
            p.updateInventory();
        });
    }
}
