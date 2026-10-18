package dev.minions;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** All the menu / interaction sounds in one place (namespaced sound keys work on every 1.21 build). */
public final class Sfx {
    private Sfx() {}

    private static void play(Player p, String key, float vol, float pitch) {
        if (p != null && p.isOnline()) p.playSound(p.getLocation(), key, vol, pitch);
    }

    private static void later(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(MinionsPlugin.I, r, ticks);
    }

    /** menu navigation */
    public static void click(Player p) { play(p, "ui.button.click", 0.6f, 1.2f); }

    /** minion menu opened */
    public static void open(Player p) {
        play(p, "block.ender_chest.open", 0.35f, 1.5f);
        play(p, "block.amethyst_block.chime", 0.4f, 1.8f);
    }

    /** the happy "collect" sound: item pickup + a rising run of xp pops + a chime */
    public static void collect(Player p, int stacks) {
        play(p, "entity.item.pickup", 0.9f, 1.1f);
        int n = Math.min(5, 2 + stacks / 3);
        for (int i = 0; i < n; i++) {
            final float pitch = 0.9f + 0.17f * i;
            later(i * 2L, () -> play(p, "entity.experience_orb.pickup", 0.5f, pitch));
        }
        later(n * 2L, () -> play(p, "block.amethyst_block.chime", 0.7f, 1.7f));
    }

    /** taking a single stack */
    public static void pop(Player p) {
        play(p, "entity.item.pickup", 0.8f, 1.0f + (float) Math.random() * 0.3f);
    }

    public static void error(Player p) { play(p, "entity.villager.no", 0.8f, 1.0f); }

    public static void fuel(Player p) {
        play(p, "item.firecharge.use", 0.7f, 1.0f);
        play(p, "block.fire.ambient", 0.6f, 1.3f);
    }

    public static void plug(Player p) {
        play(p, "block.beacon.activate", 0.5f, 1.8f);
        play(p, "block.piston.extend", 0.4f, 1.6f);
    }

    public static void unplug(Player p) { play(p, "block.beacon.deactivate", 0.5f, 1.8f); }

    public static void upgrade(Player p) {
        play(p, "block.anvil.use", 0.5f, 1.4f);
        later(3L, () -> play(p, "entity.player.levelup", 0.8f, 1.1f));
    }

    public static void warn(Player p) { play(p, "block.note_block.bass", 0.8f, 0.7f); }

    public static void pickup(Player p) {
        play(p, "entity.enderman.teleport", 0.5f, 1.6f);
        later(2L, () -> play(p, "entity.item.pickup", 0.9f, 0.8f));
    }

    public static void place(Player p) {
        play(p, "block.beacon.power_select", 0.6f, 1.5f);
        play(p, "block.stone.place", 1f, 1f);
    }

    public static void chest(Player p) {
        play(p, "block.chest.close", 0.7f, 1.2f);
        play(p, "entity.item.pickup", 0.6f, 1.2f);
    }
}
