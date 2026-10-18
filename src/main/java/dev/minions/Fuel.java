package dev.minions;

import org.bukkit.Material;

/** Minion fuels. Boost and duration can be overridden in config.yml under fuels.<ID>. */
public enum Fuel {
    COAL("Coal", Material.COAL, false, 0.05, 30, "§aCommon", false),
    COAL_BLOCK("Block of Coal", Material.COAL_BLOCK, false, 0.05, 300, "§aCommon", false),
    ENCHANTED_BREAD("Enchanted Bread", Material.BREAD, true, 0.05, 720, "§aUncommon", false),
    ENCHANTED_COAL("Enchanted Coal", Material.COAL, true, 0.10, 1440, "§aUncommon", false),
    ENCHANTED_CHARCOAL("Enchanted Charcoal", Material.CHARCOAL, true, 0.20, 2160, "§9Rare", false),
    SOLAR_PANEL("Solar Panel", Material.DAYLIGHT_DETECTOR, true, 0.25, -1, "§9Rare", true),
    TASTY_CHEESE("Tasty Cheese", Material.GOLDEN_CARROT, true, 0.25, 60, "§9Rare", false),
    FOUL_FLESH("Foul Flesh", Material.ROTTEN_FLESH, true, 0.90, 300, "§5Epic", false),
    HAMSTER_WHEEL("Hamster Wheel", Material.RABBIT_FOOT, true, 0.50, 1440, "§5Epic", false),
    CATALYST("Catalyst", Material.NETHER_WART, true, 2.00, 180, "§5Epic", false),
    HYPER_CATALYST("Hyper Catalyst", Material.NETHER_STAR, true, 3.00, 360, "§6Legendary", false),
    ENCHANTED_LAVA_BUCKET("Enchanted Lava Bucket", Material.LAVA_BUCKET, true, 0.25, -1, "§aUncommon", false),
    MAGMA_BUCKET("Magma Bucket", Material.MAGMA_BLOCK, true, 0.30, -1, "§9Rare", false),
    PLASMA_BUCKET("Plasma Bucket", Material.BLAZE_POWDER, true, 0.35, -1, "§5Epic", false);

    public final String display, rarity;
    public final Material material;
    public final boolean custom;      // custom = glowing item with PDC tag, plain = vanilla item without meta
    public final boolean daytime;     // only works while it is day in the minion's world
    public final double defBoost;
    public final long defMinutes;

    Fuel(String display, Material material, boolean custom, double boost, long minutes, String rarity, boolean daytime) {
        this.display = display; this.material = material; this.custom = custom;
        this.defBoost = boost; this.defMinutes = minutes; this.rarity = rarity; this.daytime = daytime;
    }

    public double boost() {
        return MinionsPlugin.I.getConfig().getDouble("fuels." + name() + ".boost", defBoost);
    }

    /** duration in ms, -1 = infinite */
    public long millis() {
        long m = MinionsPlugin.I.getConfig().getLong("fuels." + name() + ".minutes", defMinutes);
        return m < 0 ? -1 : m * 60_000L;
    }

    public static Fuel byId(String s) {
        if (s == null) return null;
        try { return valueOf(s); } catch (IllegalArgumentException e) { return null; }
    }
}
