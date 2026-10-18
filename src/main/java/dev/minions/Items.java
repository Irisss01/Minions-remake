package dev.minions;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Items {
    public static NamespacedKey K_TYPE, K_TIER, K_GEN, K_UID, K_UPG, K_EC, K_FUEL, K_STORAGE, K_LOCK, K_EC2;
    public static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"};
    public static final int[] CHEST_SLOTS = {3, 9, 15, 21, 27};
    public static final String[] CHEST_NAMES = {"Small", "Medium", "Large", "X-Large", "XX-Large"};

    private Items() {}

    public static void init(Plugin p) {
        K_TYPE = new NamespacedKey(p, "minion_type");
        K_TIER = new NamespacedKey(p, "minion_tier");
        K_GEN = new NamespacedKey(p, "minion_generated");
        K_UID = new NamespacedKey(p, "minion_uid");
        K_UPG = new NamespacedKey(p, "minion_upgrade");
        K_EC = new NamespacedKey(p, "enchanted_base");
        K_FUEL = new NamespacedKey(p, "minion_fuel");
        K_STORAGE = new NamespacedKey(p, "minion_storage");
        K_LOCK = new NamespacedKey(p, "locked_slot");
        K_EC2 = new NamespacedKey(p, "enchanted_block_base");
    }

    public static boolean isAir(ItemStack s) { return s == null || s.getType().isAir(); }

    private static PersistentDataContainer pdc(ItemStack it) {
        return it.getItemMeta().getPersistentDataContainer();
    }

    public static ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        if (lore.length > 0) im.setLore(new ArrayList<>(Arrays.asList(lore)));
        im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(im);
        return it;
    }

    public static ItemStack addLore(ItemStack it, String... extra) {
        ItemMeta im = it.getItemMeta();
        List<String> l = im.hasLore() ? new ArrayList<>(im.getLore()) : new ArrayList<>();
        l.addAll(Arrays.asList(extra));
        im.setLore(l);
        it.setItemMeta(im);
        return it;
    }

    public static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.US, "%.1f", d);
    }

    // ------------------------------------------------------------- heads
    private static final Pattern URL_IN_JSON = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private static String resolveTexture(String v) {
        if (v == null || v.isBlank()) return null;
        v = v.trim();
        if (v.startsWith("http")) return v;
        if (v.matches("[0-9a-fA-F]{64}")) return "http://textures.minecraft.net/texture/" + v;
        try {
            String json = new String(Base64.getDecoder().decode(v), StandardCharsets.UTF_8);
            Matcher m = URL_IN_JSON.matcher(json);
            if (m.find()) return m.group(1);
        } catch (IllegalArgumentException ignored) { }
        return null;
    }

    /** texture url for a minion head: config.yml first, then built-in data, else null (block icon). */
    public static String textureUrl(MinionType t, int tier) {
        var cfg = MinionsPlugin.I.getConfig();
        String base = "textures." + t.name();
        String v = cfg.getString(base + "." + tier);
        if (v == null || v.isBlank()) v = cfg.getString(base + ".default");
        if (v == null || v.isBlank()) v = cfg.getString(base);
        String u = resolveTexture(v);
        if (u != null) return u;
        var sk = MinionsPlugin.I.skins;                       // skins.yml (made by tools/import_hypixel_skins.py)
        if (sk != null) {
            String sv = sk.getString(t.name() + "." + tier);
            if (sv == null || sv.isBlank()) sv = sk.getString(t.name() + ".default");
            u = resolveTexture(sv);
            if (u != null) return u;
        }
        return t.url(tier);
    }

    /** a custom player head that carries a skin texture (usable in the Minion Skin slot) */
    public static boolean isSkinHead(ItemStack it) {
        if (isAir(it) || it.getType() != Material.PLAYER_HEAD || !(it.getItemMeta() instanceof SkullMeta sm)) return false;
        PlayerProfile pr = sm.getOwnerProfile();
        return pr != null && pr.getTextures().getSkin() != null;
    }

    /** no texture known yet: a helmet dyed in the minion's colour (never an ore / block on its head) */
    public static ItemStack fallbackHelmet(MinionType t) {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        try {
            LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta();
            lm.setColor(Color.fromRGB(t.armorColor));
            lm.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            it.setItemMeta(lm);
        } catch (Throwable ignored) { }
        return it;
    }

    public static ItemStack head(String url) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        if (url == null) return it;
        try {
            SkullMeta m = (SkullMeta) it.getItemMeta();
            PlayerProfile pr = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(url.getBytes()), "minion");
            PlayerTextures t = pr.getTextures();
            t.setSkin(new URL(url));
            pr.setTextures(t);
            m.setOwnerProfile(pr);
            it.setItemMeta(m);
        } catch (Exception ignored) { }
        return it;
    }

    /** The visible "head" of a minion: custom skull if a texture is known, otherwise a dyed helmet. */
    public static ItemStack icon(MinionType t, int tier) {
        String url = textureUrl(t, tier);
        return url != null ? head(url) : fallbackHelmet(t);
    }

    // ----------------------------------------------------------- minion item
    public static String costText(MinionType t, int tier) {
        int per = t.perSlot(tier);
        if (per >= 160) return "§a" + (per / 160) + " Enchanted " + pretty(t.resource) + " §7(x8)";
        return "§a" + per + " " + pretty(t.resource) + " §7(x8)";
    }

    public static ItemStack minionItem(MinionType t, int tier, long generated) {
        ItemStack it = icon(t, tier);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§9" + t.display + " Minion " + ROMAN[tier]);
        String what = t.cat == MinionType.Cat.MINING ? "generating and mining" : t.cat == MinionType.Cat.FARMING
                ? "planting and harvesting" : "growing and chopping";
        m.setLore(new ArrayList<>(Arrays.asList(
                "§7Place this minion and it will start",
                "§7" + what + " " + pretty(t.resource) + "!",
                "§7Requires an open area to place",
                "§7blocks. Minions also work when",
                "§7you are offline!",
                "",
                "§7Time Between Actions: §a" + num(t.interval(tier)) + "s",
                "§7Max Storage: §e" + t.storage(tier),
                "§7Resources Generated: §b" + generated,
                "",
                "§9§lRARE")));
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(K_TYPE, PersistentDataType.STRING, t.id);
        pdc.set(K_TIER, PersistentDataType.INTEGER, tier);
        pdc.set(K_GEN, PersistentDataType.LONG, generated);
        pdc.set(K_UID, PersistentDataType.STRING, UUID.randomUUID().toString());
        it.setItemMeta(m);
        return it;
    }

    public static MinionType typeOf(ItemStack it) {
        if (isAir(it) || !it.hasItemMeta()) return null;
        String id = pdc(it).get(K_TYPE, PersistentDataType.STRING);
        return id == null ? null : MinionType.fromId(id);
    }

    public static int tierOf(ItemStack it) {
        Integer t = pdc(it).get(K_TIER, PersistentDataType.INTEGER);
        return t == null ? 1 : Math.max(1, Math.min(MinionType.MAX_TIER, t));
    }

    public static long genOf(ItemStack it) {
        Long g = pdc(it).get(K_GEN, PersistentDataType.LONG);
        return g == null ? 0 : g;
    }

    // ------------------------------------------------------- enchanted items
    private static void glint(ItemMeta im) {
        Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (e != null) im.addEnchant(e, 1, true);
        im.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    }

    public static ItemStack enchanted(Material base, int amount) {
        ItemStack it = new ItemStack(base, Math.max(1, Math.min(64, amount)));
        ItemMeta im = it.getItemMeta();
        im.setDisplayName("§aEnchanted " + pretty(base));
        im.setLore(new ArrayList<>(Arrays.asList("§7Crafted from 160 " + pretty(base) + ".", "",
                "§7Craft: 5 slots in a + shape,", "§7each holding 32 " + pretty(base) + ".", "", "§a§lUNCOMMON")));
        glint(im);
        im.getPersistentDataContainer().set(K_EC, PersistentDataType.STRING, base.name());
        it.setItemMeta(im);
        return it;
    }

    /** Enchanted Lapis Block / Enchanted Iron Block ...: 160 enchanted items compacted once more */
    public static ItemStack enchantedBlock(MinionType t, int amount) {
        Material blk = t.enchBlock();
        ItemStack it = new ItemStack(blk, Math.max(1, Math.min(64, amount)));
        ItemMeta im = it.getItemMeta();
        im.setDisplayName("§aEnchanted " + pretty(blk));
        im.setLore(new ArrayList<>(Arrays.asList("§7Crafted from 160 Enchanted " + pretty(t.resource) + ".", "",
                "§7Craft: 5 slots in a + shape,", "§7each holding 32 Enchanted " + pretty(t.resource) + ".", "",
                "§7Made automatically by a", "§9Super Compactor 3000§7.", "", "§9§lRARE")));
        glint(im);
        im.getPersistentDataContainer().set(K_EC2, PersistentDataType.STRING, t.resource.name());
        it.setItemMeta(im);
        return it;
    }

    /** raw resource an Enchanted Block was made from, or null if it is not one */
    public static Material ec2Base(ItemStack it) {
        if (isAir(it) || !it.hasItemMeta()) return null;
        String s = pdc(it).get(K_EC2, PersistentDataType.STRING);
        return s == null ? null : Material.getMaterial(s);
    }

    /** base material of an enchanted item, or null if it is not one */
    public static Material ecBase(ItemStack it) {
        if (isAir(it) || !it.hasItemMeta()) return null;
        String s = pdc(it).get(K_EC, PersistentDataType.STRING);
        return s == null ? null : Material.getMaterial(s);
    }

    public static boolean isEC(ItemStack it) { return ecBase(it) != null; }

    /** a completely plain (no meta) stack of this material */
    public static boolean isPlain(ItemStack it, Material m) {
        return !isAir(it) && it.getType() == m && !it.hasItemMeta();
    }

    // --------------------------------------------------------------- upgrades
    public static ItemStack upgrade(Upgrade u) {
        List<String> lore = new ArrayList<>(Arrays.asList(u.lore));
        if (u.speed > 0) lore.add("§7Speed: §a+" + (int) Math.round(u.speed * 100) + "%");
        lore.add("");
        lore.add(u.shipping ? "§7Place in a minion's shipping slot." : "§7Place in a minion's upgrade slot.");
        lore.add("");
        lore.add(rarityLine(u.rarity));
        ItemStack it = named(u.icon, u.name, lore.toArray(new String[0]));
        ItemMeta im = it.getItemMeta();
        if (!u.rarity.startsWith("§a")) glint(im);
        im.getPersistentDataContainer().set(K_UPG, PersistentDataType.STRING, u.id);
        it.setItemMeta(im);
        return it;
    }

    /** "§9Rare" -> "§9§lRARE" */
    public static String rarityLine(String rarity) {
        if (rarity == null || rarity.length() < 3) return "";
        return rarity.substring(0, 2) + "§l" + rarity.substring(2).toUpperCase();
    }

    public static int chestIndex(int slots) {
        int idx = 0;
        for (int i = 0; i < CHEST_SLOTS.length; i++) if (CHEST_SLOTS[i] == slots) idx = i;
        return idx;
    }

    public static Upgrade upgradeOf(ItemStack it) {
        if (isAir(it) || !it.hasItemMeta()) return null;
        return Upgrade.byId(pdc(it).get(K_UPG, PersistentDataType.STRING));
    }

    // ------------------------------------------------------------------ fuels
    public static ItemStack fuelItem(Fuel f, int amount) {
        if (!f.custom) return new ItemStack(f.material, amount);
        long ms = f.millis();
        List<String> lore = new ArrayList<>();
        lore.add("§6Minion Fuel");
        lore.add("§7Place in a minion's fuel slot.");
        lore.add("");
        lore.add("§7Speed: §a+" + (int) Math.round(f.boost() * 100) + "%" + (f.daytime ? " §8(daytime only)" : ""));
        lore.add("§7Lasts: §b" + (ms < 0 ? "Forever" : fmtTime(ms)));
        lore.add("");
        lore.add(rarityLine(f.rarity));
        ItemStack it = named(f.material, f.rarity.substring(0, 2) + f.display, lore.toArray(new String[0]));
        ItemMeta im = it.getItemMeta();
        glint(im);
        im.getPersistentDataContainer().set(K_FUEL, PersistentDataType.STRING, f.name());
        it.setItemMeta(im);
        it.setAmount(Math.max(1, amount));
        return it;
    }

    public static Fuel fuelOf(ItemStack it) {
        if (isAir(it)) return null;
        if (it.hasItemMeta()) {
            String s = pdc(it).get(K_FUEL, PersistentDataType.STRING);
            return Fuel.byId(s);
        }
        for (Fuel f : Fuel.values()) if (!f.custom && f.material == it.getType()) return f;
        return null;
    }

    // ---------------------------------------------------------------- storage
    public static ItemStack storageItem(int tierIndex) {
        int slots = CHEST_SLOTS[tierIndex];
        ItemStack it = named(Material.CHEST, "§a" + CHEST_NAMES[tierIndex] + " Storage",
                "§7Place this chest next to a minion", "§7(not above or below) to give it", "§e" + slots + " §7extra storage slots.",
                "", "§7Hoppers do not work on it.", "", "§a§lUNCOMMON");
        ItemMeta im = it.getItemMeta();
        im.getPersistentDataContainer().set(K_STORAGE, PersistentDataType.INTEGER, slots);
        it.setItemMeta(im);
        return it;
    }

    public static int storageSlotsOf(ItemStack it) {
        if (isAir(it) || !it.hasItemMeta()) return 0;
        Integer i = pdc(it).get(K_STORAGE, PersistentDataType.INTEGER);
        return i == null ? 0 : i;
    }

    public static ItemStack lockedPane() {
        ItemStack it = named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§cLocked!", "§7Upgrade your minion's chest", "§7to unlock this slot.");
        ItemMeta im = it.getItemMeta();
        im.getPersistentDataContainer().set(K_LOCK, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(im);
        return it;
    }

    public static boolean isLocked(ItemStack it) {
        return !isAir(it) && it.hasItemMeta() && pdc(it).has(K_LOCK, PersistentDataType.BYTE);
    }

    // ----------------------------------------------------------------- misc
    public static void give(Player p, ItemStack it) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (ItemStack l : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
    }

    /** puts the item in the player's HAND (selected slot if empty, else first free hotbar slot), else inventory */
    public static void giveHeld(Player p, ItemStack it) {
        var inv = p.getInventory();
        if (isAir(inv.getItemInMainHand())) { inv.setItemInMainHand(it); return; }
        for (int i = 0; i < 9; i++) {
            if (isAir(inv.getItem(i))) { inv.setItem(i, it); inv.setHeldItemSlot(i); return; }
        }
        give(p, it);
    }

    public static String fmtTime(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    public static String pretty(Material m) {
        String[] parts = m.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return sb.toString().trim();
    }
}
