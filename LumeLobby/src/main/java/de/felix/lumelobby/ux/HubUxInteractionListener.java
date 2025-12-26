package de.felix.lumelobby.ux;

import lombok.RequiredArgsConstructor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

@RequiredArgsConstructor
public final class HubUxInteractionListener implements Listener {

    private final Plugin plugin;
    private final Supplier<HubUxManager> hubUx;

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        HubUxManager hubUx = this.hubUx.get();
        if (hubUx == null) return;
        Player player = event.getPlayer();
        boolean handled = hubUx.loadoutManager().handleInteract(player, event.getHand(), event.getAction(), event.getItem());
        if (!handled) handled = hubUx.cosmeticsManager().handleInteract(player, event.getItem());
        if (handled) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        HubUxManager hubUx = this.hubUx.get();
        if (hubUx == null) return;
        Player player = event.getPlayer();
        if (!hubUx.loadoutManager().isInHub(player)) return;
        if (hubUx.loadoutManager().isHubCompass(event.getMainHandItem())
            || hubUx.loadoutManager().isHubCompass(event.getOffHandItem())
            || hubUx.loadoutManager().isPlayerToggleItem(event.getMainHandItem())
            || hubUx.loadoutManager().isPlayerToggleItem(event.getOffHandItem())
            || hubUx.cosmeticsManager().isCosmeticsMenuItem(event.getMainHandItem())
            || hubUx.cosmeticsManager().isCosmeticsMenuItem(event.getOffHandItem())
            || hubUx.cosmeticsManager().isGadgetItem(event.getMainHandItem())
            || hubUx.cosmeticsManager().isGadgetItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        HubUxManager hubUx = this.hubUx.get();
        if (hubUx == null) return;
        Player player = event.getPlayer();
        if (!hubUx.loadoutManager().isInHub(player)) return;
        ItemStack item = event.getItemDrop() == null ? null : event.getItemDrop().getItemStack();
        if (!hubUx.loadoutManager().isHubCompass(item)
            && !hubUx.loadoutManager().isPlayerToggleItem(item)
            && !hubUx.cosmeticsManager().isCosmeticsMenuItem(item)
            && !hubUx.cosmeticsManager().isGadgetItem(item)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent event) {
        HubUxManager hubUx = this.hubUx.get();
        if (hubUx == null) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (HubMenuUi.isHubMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
            NamespacedKey actionKey = new NamespacedKey(plugin, "hub_menu_action");
            String action = HubMenuUi.actionFrom(event.getCurrentItem(), actionKey);
            if (action == null) return;
            switch (action) {
                case "skywars" -> {
                    player.closeInventory();
                    hubUx.loadoutManager().runSkywarsQuickplay(player);
                }
                default -> player.closeInventory();
            }
            return;
        }

        if (hubUx.cosmeticsManager().isCosmeticsMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
            hubUx.cosmeticsManager().handleMenuClick(player, event.getView().getTopInventory(), event.getCurrentItem());
            return;
        }

        if (!hubUx.loadoutManager().isInHub(player)) return;
        ItemStack it = event.getCurrentItem();
        if (!hubUx.loadoutManager().isHubCompass(it)
            && !hubUx.loadoutManager().isPlayerToggleItem(it)
            && !hubUx.cosmeticsManager().isCosmeticsMenuItem(it)
            && !hubUx.cosmeticsManager().isGadgetItem(it)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvDrag(InventoryDragEvent event) {
        HubUxManager hubUx = this.hubUx.get();
        if (hubUx == null) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (HubMenuUi.isHubMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
            return;
        }

        if (!hubUx.loadoutManager().isInHub(player)) return;
        for (ItemStack item : event.getNewItems().values()) {
            if (hubUx.loadoutManager().isHubCompass(item)
                || hubUx.loadoutManager().isPlayerToggleItem(item)
                || hubUx.cosmeticsManager().isCosmeticsMenuItem(item)
                || hubUx.cosmeticsManager().isGadgetItem(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
