package dev.minions;

import org.bukkit.Material;

import java.util.Random;

/** One entry per minion kind. Add a constant to add a minion. */
public enum MinionType {
    // ---- Mining
    COBBLESTONE(Cat.MINING, "cobblestone", "Cobblestone", Material.COBBLESTONE, Material.COBBLESTONE, 1, 1, null, Material.WOODEN_PICKAXE, 0x7F7F7F, 14,
            new int[]{14, 14, 12, 12, 10, 10, 9, 9, 8, 8, 7, 6}, TextureData.COBBLESTONE),
    COAL(Cat.MINING, "coal", "Coal", Material.COAL_ORE, Material.COAL, 1, 1, null, Material.WOODEN_PICKAXE, 0x2B2B2B, 15, null, null),
    IRON(Cat.MINING, "iron", "Iron", Material.IRON_ORE, Material.IRON_INGOT, 1, 1, null, Material.WOODEN_PICKAXE, 0xD8D8D8, 17, null, null),
    GOLD(Cat.MINING, "gold", "Gold", Material.GOLD_ORE, Material.GOLD_INGOT, 1, 1, null, Material.WOODEN_PICKAXE, 0xFCDB05, 22, null, null),
    DIAMOND(Cat.MINING, "diamond", "Diamond", Material.DIAMOND_ORE, Material.DIAMOND, 1, 1, null, Material.WOODEN_PICKAXE, 0x4AEDD9, 29, null, null),
    LAPIS(Cat.MINING, "lapis", "Lapis", Material.LAPIS_ORE, Material.LAPIS_LAZULI, 4, 8, null, Material.WOODEN_PICKAXE, 0x345EC3, 29, null, null),
    REDSTONE(Cat.MINING, "redstone", "Redstone", Material.REDSTONE_ORE, Material.REDSTONE, 1, 1, null, Material.WOODEN_PICKAXE, 0xB02E26, 29, null, null),
    EMERALD(Cat.MINING, "emerald", "Emerald", Material.EMERALD_ORE, Material.EMERALD, 1, 1, null, Material.WOODEN_PICKAXE, 0x17DD62, 28, null, null),
    QUARTZ(Cat.MINING, "quartz", "Quartz", Material.NETHER_QUARTZ_ORE, Material.QUARTZ, 1, 1, null, Material.WOODEN_PICKAXE, 0xEDE6DF, 22, null, null),
    OBSIDIAN(Cat.MINING, "obsidian", "Obsidian", Material.OBSIDIAN, Material.OBSIDIAN, 1, 1, null, Material.WOODEN_PICKAXE, 0x1B1026, 45, null, null),
    GLOWSTONE(Cat.MINING, "glowstone", "Glowstone", Material.GLOWSTONE, Material.GLOWSTONE_DUST, 2, 4, null, Material.WOODEN_PICKAXE, 0xF9D26B, 25, null, null),
    GRAVEL(Cat.MINING, "gravel", "Gravel", Material.GRAVEL, Material.GRAVEL, 1, 1, null, Material.WOODEN_SHOVEL, 0x8A8585, 26, null, null),
    SAND(Cat.MINING, "sand", "Sand", Material.SAND, Material.SAND, 1, 1, null, Material.WOODEN_SHOVEL, 0xDBCF9C, 26, null, null),
    // ---- Farming (crops need farmland under the working layer)
    WHEAT(Cat.FARMING, "wheat", "Wheat", Material.WHEAT, Material.WHEAT, 1, 1, Material.FARMLAND, Material.WOODEN_HOE, 0xE6C84F, 15, null, null),
    CARROT(Cat.FARMING, "carrot", "Carrot", Material.CARROTS, Material.CARROT, 1, 1, Material.FARMLAND, Material.WOODEN_HOE, 0xF08A1C, 20, null, null),
    POTATO(Cat.FARMING, "potato", "Potato", Material.POTATOES, Material.POTATO, 1, 1, Material.FARMLAND, Material.WOODEN_HOE, 0xC9A55A, 20, null, null),
    PUMPKIN(Cat.FARMING, "pumpkin", "Pumpkin", Material.PUMPKIN, Material.PUMPKIN, 1, 1, null, Material.WOODEN_HOE, 0xE38F1A, 32, null, null),
    MELON(Cat.FARMING, "melon", "Melon", Material.MELON, Material.MELON_SLICE, 3, 7, null, Material.WOODEN_HOE, 0x8FD13A, 24, null, null),
    // ---- Foraging
    OAK(Cat.FORAGING, "oak", "Oak", Material.OAK_LOG, Material.OAK_LOG, 1, 1, null, Material.WOODEN_AXE, 0x9C7F4E, 48, null, null),
    BIRCH(Cat.FORAGING, "birch", "Birch", Material.BIRCH_LOG, Material.BIRCH_LOG, 1, 1, null, Material.WOODEN_AXE, 0xD7D3C0, 48, null, null),
    SPRUCE(Cat.FORAGING, "spruce", "Spruce", Material.SPRUCE_LOG, Material.SPRUCE_LOG, 1, 1, null, Material.WOODEN_AXE, 0x5B4026, 48, null, null),
    JUNGLE(Cat.FORAGING, "jungle", "Jungle", Material.JUNGLE_LOG, Material.JUNGLE_LOG, 1, 1, null, Material.WOODEN_AXE, 0x7A5A2E, 48, null, null),
    ACACIA(Cat.FORAGING, "acacia", "Acacia", Material.ACACIA_LOG, Material.ACACIA_LOG, 1, 1, null, Material.WOODEN_AXE, 0xAD5D32, 48, null, null),
    DARK_OAK(Cat.FORAGING, "dark_oak", "Dark Oak", Material.DARK_OAK_LOG, Material.DARK_OAK_LOG, 1, 1, null, Material.WOODEN_AXE, 0x3E2912, 48, null, null);

    public enum Cat {
        MINING("Mining"), FARMING("Farming"), FORAGING("Foraging");
        public final String label;
        Cat(String l) { label = l; }
    }

    public static final int MAX_TIER = 12;
    private static final double[] RATIO = {14, 14, 12, 12, 10, 10, 9, 9, 8, 8, 7, 6};
    private static final int[] STORAGE = {64, 192, 192, 384, 384, 576, 576, 768, 768, 960, 960, 960};
    /** raw items needed in EACH of the 8 ring slots to craft tier N (index N-1). 160 raw = 1 enchanted. */
    private static final int[] PER_SLOT = {10, 20, 40, 64, 160, 320, 640, 1280, 2560, 5120, 10240, 20480};

    public final Cat cat;
    public final String id, display;
    public final Material block, resource, floor, tool;
    public final int min, max, armorColor;
    private final double base;
    private final int[] exact;
    private final String[] urls;

    MinionType(Cat cat, String id, String display, Material block, Material resource, int min, int max, Material floor,
               Material tool, int armorColor, double base, int[] exact, String[] urls) {
        this.cat = cat; this.id = id; this.display = display; this.block = block; this.resource = resource;
        this.min = min; this.max = max; this.floor = floor; this.tool = tool; this.armorColor = armorColor;
        this.base = base; this.exact = exact; this.urls = urls;
    }

    private static int clamp(int t) { return Math.max(1, Math.min(MAX_TIER, t)); }

    /** seconds between actions (config.yml "types.<ID>.tba" can override all 12 values) */
    public double interval(int tier) {
        java.util.List<Double> o = MinionsPlugin.I == null ? null
                : MinionsPlugin.I.getConfig().getDoubleList("types." + name() + ".tba").isEmpty() ? null
                : MinionsPlugin.I.getConfig().getDoubleList("types." + name() + ".tba");
        if (o != null && o.size() >= clamp(tier)) return o.get(clamp(tier) - 1);
        if (exact != null) return exact[clamp(tier) - 1];
        return Math.round(base * RATIO[clamp(tier) - 1] / 14.0 * 10.0) / 10.0;
    }

    public int storage(int tier) { return STORAGE[clamp(tier) - 1]; }
    public int slots(int tier) { return (storage(tier) + 63) / 64; }
    public int perSlot(int tier) { return PER_SLOT[clamp(tier) - 1]; }
    /** total raw cost to craft/upgrade INTO this tier */
    public int cost(int tier) { return perSlot(tier) * 8; }
    public String url(int tier) { return urls == null ? null : urls[clamp(tier) - 1]; }
    public Material icon() { return block.isItem() ? block : resource; }
    public int amount(Random r) { return min >= max ? min : min + r.nextInt(max - min + 1); }
    public boolean isCrop() { return floor != null; }

    /** material of the 2nd compaction level (Enchanted Lapis Block...), null if this resource has none */
    public Material enchBlock() {
        return switch (this) {
            case COAL -> Material.COAL_BLOCK;
            case IRON -> Material.IRON_BLOCK;
            case GOLD -> Material.GOLD_BLOCK;
            case DIAMOND -> Material.DIAMOND_BLOCK;
            case LAPIS -> Material.LAPIS_BLOCK;
            case REDSTONE -> Material.REDSTONE_BLOCK;
            case EMERALD -> Material.EMERALD_BLOCK;
            case QUARTZ -> Material.QUARTZ_BLOCK;
            default -> null;
        };
    }

    public static MinionType fromId(String s) {
        for (MinionType t : values()) if (t.id.equalsIgnoreCase(s)) return t;
        return null;
    }

    public static MinionType fromResource(Material m) {
        for (MinionType t : values()) if (t.resource == m) return t;
        return null;
    }
}
