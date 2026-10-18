package dev.minions;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MinionsPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    public static MinionsPlugin I;
    public MinionManager manager;
    public Gui gui;
    public Recipes recipes;
    /** Y offset of the working layer relative to the minion (-1 = the layer it stands on) */
    public int layerOffset = -1;
    /** optional skins.yml (Hypixel head textures per minion + tier, made by tools/import_hypixel_skins.py) */
    public YamlConfiguration skins;

    public void loadSkins() {
        File f = new File(getDataFolder(), "skins.yml");
        skins = f.exists() ? YamlConfiguration.loadConfiguration(f) : null;
        if (skins != null) getLogger().info("Loaded head textures from skins.yml");
    }

    @Override
    public void onEnable() {
        I = this;
        saveDefaultConfig();
        Items.init(this);
        layerOffset = getConfig().getInt("work-layer-offset", -1);
        loadSkins();
        manager = new MinionManager(this);
        manager.load();
        gui = new Gui(this);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new MinionListener(this), this);
        recipes = new Recipes(this);
        recipes.register();
        getServer().getPluginManager().registerEvents(recipes, this);
        getCommand("minions").setExecutor(this);
        getCommand("minions").setTabCompleter(this);

        Bukkit.getScheduler().runTaskTimer(this, manager::tick, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, gui::refreshOpen, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, manager::idleAll, 20L, 2L);
        long save = Math.max(30, getConfig().getLong("save-interval-seconds", 300)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, manager::save, save, save);
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.save();
            manager.despawnAll();
        }
        if (recipes != null) recipes.unregister();
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
            if (!p.hasPermission("minions.use")) { p.sendMessage("§cNo permission."); return true; }
            gui.openMain(p);
            return true;
        }
        if (!s.hasPermission("minions.admin")) { s.sendMessage("§cNo permission."); return true; }
        switch (a[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                layerOffset = getConfig().getInt("work-layer-offset", -1);
                loadSkins();
                manager.refreshAppearance();
                s.sendMessage("§aMinions config + skins reloaded.");
            }
            case "give" -> {
                if (a.length < 3) {
                    s.sendMessage("§c/minions give <player> <minion|fuel|upgrade|storage|enchanted_x|enchantedblock_x> [tier|amount]");
                    s.sendMessage("§7Minions: " + String.join(", ", java.util.Arrays.stream(MinionType.values()).map(x -> x.id).toList()));
                    return true;
                }
                Player t = Bukkit.getPlayer(a[1]);
                if (t == null) { s.sendMessage("§cPlayer not found."); return true; }
                String w = a[2].toLowerCase();
                int n = 1;
                if (a.length > 3) try { n = Integer.parseInt(a[3]); } catch (NumberFormatException ignored) { }
                ItemStack it = null;
                MinionType mt = MinionType.fromId(w);
                if (mt != null) it = Items.minionItem(mt, Math.max(1, Math.min(MinionType.MAX_TIER, n)), 0);
                else if (w.startsWith("enchantedblock_") && MinionType.fromId(w.substring(15)) != null
                        && MinionType.fromId(w.substring(15)).enchBlock() != null)
                    it = Items.enchantedBlock(MinionType.fromId(w.substring(15)), n);
                else if (w.startsWith("enchanted_") && MinionType.fromId(w.substring(10)) != null)
                    it = Items.enchanted(MinionType.fromId(w.substring(10)).resource, n);
                else if (Upgrade.byId(w) != null) it = Items.upgrade(Upgrade.byId(w));
                else if (Fuel.byId(w.toUpperCase()) != null) it = Items.fuelItem(Fuel.byId(w.toUpperCase()), n);
                else if (w.startsWith("storage_")) {
                    for (int i = 0; i < Items.CHEST_NAMES.length; i++)
                        if (w.equals("storage_" + Items.CHEST_NAMES[i].toLowerCase())) it = Items.storageItem(i);
                }
                if (it == null) { s.sendMessage("§cUnknown item. Use TAB to see everything."); return true; }
                Items.give(t, it);
                s.sendMessage("§aGave " + t.getName() + " " + w + ".");
            }
            case "texture" -> {
                if (a.length < 3) {
                    s.sendMessage("§c/minions texture <minion> <url|hash|base64|hand> [tier]");
                    s.sendMessage("§7Hold any custom head (minecraft-heads.com) and use §ehand§7 to copy its skin.");
                    return true;
                }
                MinionType mt = MinionType.fromId(a[1]);
                if (mt == null) { s.sendMessage("§cUnknown minion."); return true; }
                String val = a[2];
                if (val.equalsIgnoreCase("hand")) {
                    if (!(s instanceof Player pl)) { s.sendMessage("Players only."); return true; }
                    ItemStack h = pl.getInventory().getItemInMainHand();
                    String found = null;
                    if (h.getType() == org.bukkit.Material.PLAYER_HEAD && h.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm
                            && sm.getOwnerProfile() != null && sm.getOwnerProfile().getTextures().getSkin() != null)
                        found = sm.getOwnerProfile().getTextures().getSkin().toString();
                    if (found == null) { s.sendMessage("§cHold a custom player head in your hand first."); return true; }
                    val = found;
                }
                String key = "textures." + mt.name() + "." + (a.length > 3 ? a[3] : "default");
                getConfig().set(key, val);
                if (Items.textureUrl(mt, a.length > 3 ? Math.max(1, Math.min(12, parseInt(a[3]))) : 1) == null) {
                    getConfig().set(key, "");
                    s.sendMessage("§cThat doesn't look like a valid texture (URL, 64-char hash or Base64).");
                    return true;
                }
                saveConfig();
                manager.refreshAppearance();
                s.sendMessage("§aHead texture saved for §e" + mt.display + "§a" + (a.length > 3 ? " tier " + a[3] : "") + ".");
            }
            case "list" -> {
                int count = 0;
                for (Minion m : manager.minions.values()) {
                    s.sendMessage("§7- §e" + m.ownerName + " §7" + m.type.display + " " + Items.ROMAN[m.tier]
                            + " @ " + m.world + " " + m.x + "," + m.y + "," + m.z);
                    count++;
                }
                s.sendMessage("§a" + count + " minions.");
            }
            default -> s.sendMessage("§c/minions [give|list|reload|texture]");
        }
        return true;
    }

    private static int parseInt(String v) {
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return 1; }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("minions.admin")) return out;
        if (a.length == 1) { out.add("give"); out.add("list"); out.add("reload"); out.add("texture"); }
        else if (a.length == 2 && a[0].equalsIgnoreCase("texture")) { for (MinionType t : MinionType.values()) out.add(t.id); }
        else if (a.length == 3 && a[0].equalsIgnoreCase("texture")) out.add("hand");
        else if (a.length == 2 && a[0].equalsIgnoreCase("give")) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            for (MinionType t : MinionType.values()) {
                out.add(t.id);
                out.add("enchanted_" + t.id);
                if (t.enchBlock() != null) out.add("enchantedblock_" + t.id);
            }
            for (Upgrade u : Upgrade.values()) out.add(u.id);
            for (Fuel f : Fuel.values()) out.add(f.name().toLowerCase());
            for (String n : Items.CHEST_NAMES) out.add("storage_" + n.toLowerCase());
        }
        out.removeIf(x -> !x.toLowerCase().startsWith(a[a.length - 1].toLowerCase()));
        return out;
    }
}
