package dev.minions;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class MinionListener implements Listener {
    private final MinionsPlugin plugin;

    public MinionListener(MinionsPlugin plugin) { this.plugin = plugin; }

    private boolean isMinionEntity(Entity e) {
        return plugin.manager.byEntity.containsKey(e.getUniqueId()) || plugin.manager.holos.contains(e.getUniqueId());
    }

    private boolean allowed(Player p, Minion m) {
        return m.owner.equals(p.getUniqueId()) || p.hasPermission("minions.admin")
                || plugin.getConfig().getBoolean("allow-others-to-open", false);
    }

    /** Right-click a block with a minion item => place the minion; with a storage chest => attach it. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack it = e.getItem();
        if (Items.isAir(it)) return;
        if (Items.typeOf(it) != null) {
            e.setCancelled(true);
            if (e.getAction() == Action.RIGHT_CLICK_BLOCK)
                plugin.manager.tryPlace(e.getPlayer(), e.getClickedBlock(), e.getBlockFace(), it);
        } else if (Items.storageSlotsOf(it) > 0) {
            e.setCancelled(true);
            if (e.getAction() == Action.RIGHT_CLICK_BLOCK)
                plugin.manager.placeChest(e.getPlayer(), e.getClickedBlock(), e.getBlockFace(), it);
        } else if (Items.upgradeOf(it) != null || Items.fuelOf(it) != null && it.hasItemMeta() || Items.isEC(it) || Items.ec2Base(it) != null) {
            // custom items that share a vanilla material must not be used as that vanilla item
            if (it.getType().isBlock() || it.getType().isEdible() || it.getType().name().endsWith("_BUCKET")) e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Minion owner = plugin.manager.chestAt(e.getClickedBlock());
            if (owner != null && !allowed(e.getPlayer(), owner)) {
                e.setCancelled(true);
                e.getPlayer().sendMessage("§cThis storage belongs to §e" + owner.ownerName + "§c.");
            }
        }
    }

    /** Right-click a minion => open its menu (sneaking or not). A minion is only ever picked up from its menu. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClickStand(PlayerInteractAtEntityEvent e) {
        Entity en = e.getRightClicked();
        if (!isMinionEntity(en)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Minion m = plugin.manager.byEntity.get(en.getUniqueId());
        if (m == null) return;
        Player p = e.getPlayer();
        if (!allowed(p, m)) {
            p.sendMessage("§cThis minion belongs to §e" + m.ownerName + "§c.");
            return;
        }
        plugin.gui.openMinion(p, m);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (isMinionEntity(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (isMinionEntity(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onManipulate(PlayerArmorStandManipulateEvent e) {
        if (isMinionEntity(e.getRightClicked())) e.setCancelled(true);
    }

    // ---------------------------------------------------- storage chest rules
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Minion m = plugin.manager.chestAt(b);
        if (m != null) {
            if (!allowed(e.getPlayer(), m)) { e.setCancelled(true); return; }
            e.setDropItems(false);
            e.setCancelled(true);
            plugin.manager.releaseChest(e.getPlayer(), m, true);
            plugin.manager.save();
            return;
        }
        // never let a block that is part of a minion's live area be broken into nothing by accident: nothing to do
        Minion at = plugin.manager.minionAt(b);
        if (at != null) e.setCancelled(true);
    }

    /** safety net: Enchanted Blocks / enchanted logs are menu items, never placeable blocks */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        ItemStack it = e.getItemInHand();
        if (Items.ec2Base(it) != null || Items.isEC(it) || Items.typeOf(it) != null || Items.upgradeOf(it) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.manager.chestAt(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestClick(InventoryClickEvent e) {
        if (Items.isLocked(e.getCurrentItem()) || Items.isLocked(e.getCursor())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestDrag(InventoryDragEvent e) {
        for (ItemStack it : e.getInventory().getContents()) {
            if (Items.isLocked(it)) {
                int size = e.getInventory().getSize();
                for (int s : e.getRawSlots()) {
                    if (s < size && Items.isLocked(e.getInventory().getItem(s))) { e.setCancelled(true); return; }
                }
                return;
            }
        }
    }

    /** Hoppers do not work on minion chests. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        if (isMinionChest(e.getSource()) || isMinionChest(e.getDestination())) e.setCancelled(true);
    }

    private boolean isMinionChest(Inventory inv) {
        if (inv == null || inv.getLocation() == null) return false;
        return plugin.manager.chestAt(inv.getLocation().getBlock()) != null;
    }
}
