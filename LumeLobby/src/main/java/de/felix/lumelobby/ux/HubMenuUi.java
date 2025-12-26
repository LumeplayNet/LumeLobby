package de.felix.lumelobby.ux;

import de.felix.lumelobby.config.LobbyConfig;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

final class HubMenuUi {

    private static final String ACTION_KEY = "hub_menu_action";
    private static final String ACTION_SKYWARS = "skywars";

    static boolean isHubMenu(Inventory inventory) {
        if (inventory == null) return false;
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof HubMenuHolder;
    }

    static String actionFrom(ItemStack item, NamespacedKey key) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    static void open(Player player, Plugin plugin, LobbyConfig config) {
        if (player == null) return;
        var menu = config.hubUx().menu();
        if (menu == null || !menu.enabled()) return;

        String title = colorize(menu.title());
        Inventory inv = Bukkit.createInventory(new HubMenuHolder(), 27, title);

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        NamespacedKey actionKey = new NamespacedKey(plugin, ACTION_KEY);
        LobbyConfig.MenuItemConfig skywars = menu.skywars();
        if (skywars != null && skywars.enabled()) {
            int slot = clampSlot(skywars.slot(), inv.getSize());
            inv.setItem(slot, buildActionItem(
                Material.COMPASS,
                colorize(skywars.name()),
                colorizeLore(skywars.lore()),
                actionKey,
                ACTION_SKYWARS
            ));
        }

        player.openInventory(inv);
    }

    private static ItemStack buildActionItem(Material material, String name, List<String> lore, NamespacedKey actionKey, String actionId) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, actionId);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static List<String> colorizeLore(List<String> lore) {
        if (lore == null) return List.of();
        return lore.stream().map(HubMenuUi::colorize).toList();
    }

    private static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static int clampSlot(int slot, int size) {
        if (size <= 0) return 0;
        if (slot < 0) return 0;
        if (slot >= size) return size - 1;
        return slot;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class HubMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 9);
        }
    }
}

