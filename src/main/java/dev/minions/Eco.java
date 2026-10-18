package dev.minions;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

/** Vault economy through reflection so Vault is a soft dependency. */
public final class Eco {
    private Eco() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object provider() {
        try {
            Class<?> ec = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider rsp = Bukkit.getServicesManager().getRegistration((Class) ec);
            return rsp == null ? null : rsp.getProvider();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean available() { return provider() != null; }

    public static boolean deposit(UUID id, double amount) {
        Object econ = provider();
        if (econ == null) return false;
        try {
            econ.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class)
                    .invoke(econ, Bukkit.getOfflinePlayer(id), amount);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
