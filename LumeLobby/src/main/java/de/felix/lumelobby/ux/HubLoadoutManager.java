package de.felix.lumelobby.ux;

import de.felix.lumelobby.config.LobbyConfig;
import de.felix.lumelobby.world.HubManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

@RequiredArgsConstructor
final class HubLoadoutManager {

    private final Plugin plugin;
    private final LobbyConfig config;
    private final HubManager hubManager;

    private NamespacedKey compassKey() {
        return new NamespacedKey(plugin, "hub_compass");
    }

    private NamespacedKey playerToggleKey() {
        return new NamespacedKey(plugin, "hub_player_toggle");
    }

    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Boolean> playersHidden = new java.util.concurrent.ConcurrentHashMap<>();

    boolean enabled() {
        var ux = config.hubUx();
        return hubManager.enabled() && ux != null && ux.enabled() && ux.loadout() != null && ux.loadout().enabled();
    }

    void refresh(Player player, boolean inHub) {
        if (player == null) return;
        if (!enabled()) return;
        if (!inHub) {
            onExitHub(player);
            return;
        }

        LobbyConfig.LoadoutConfig loadout = config.hubUx().loadout();
        if (loadout.clearInventory()) player.getInventory().clear();

        LobbyConfig.CompassConfig compass = loadout.compass();
        if (compass != null && compass.enabled()) {
            ensureCompass(player, compass);
        }

        LobbyConfig.PlayerVisibilityToggleConfig toggle = loadout.playerToggle();
        if (toggle != null && toggle.enabled()) {
            ensurePlayerToggleItem(player, toggle, isPlayersHidden(player));
        }
    }

    void ensureItemsTick(Player player, boolean inHub) {
        if (player == null) return;
        if (!enabled() || !inHub) return;

        LobbyConfig.CompassConfig compass = config.hubUx().loadout() == null ? null : config.hubUx().loadout().compass();
        if (compass != null && compass.enabled()) {
            ensureCompass(player, compass);
        }

        LobbyConfig.PlayerVisibilityToggleConfig toggle = config.hubUx().loadout() == null ? null : config.hubUx().loadout().playerToggle();
        if (toggle != null && toggle.enabled()) {
            ensurePlayerToggleItem(player, toggle, isPlayersHidden(player));
        }
    }

    boolean handleInteract(Player player, EquipmentSlot hand, org.bukkit.event.block.Action action, ItemStack usedItem) {
        if (player == null) return false;
        if (!enabled()) return false;
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return false;
        if (!(action == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) return false;

        if (!isInHub(player)) return false;

        ItemStack item = usedItem;
        if (item == null) {
            item = (hand == EquipmentSlot.OFF_HAND) ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        }
        if (isPlayerToggleItem(item)) {
            togglePlayers(player);
            return true;
        }
        if (isHubCompass(item)) {
            openCompassAction(player);
            return true;
        }
        return false;
    }

    boolean isHubCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte val = meta.getPersistentDataContainer().get(compassKey(), PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    boolean isPlayerToggleItem(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte val = meta.getPersistentDataContainer().get(playerToggleKey(), PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    boolean isInHub(Player player) {
        if (player == null) return false;
        if (player.getWorld() == null) return false;
        String worldName = player.getWorld().getName();
        if (worldName == null) return false;
        String hubName = config.hub().worldName();
        return hubName != null && !hubName.isBlank() && hubName.equalsIgnoreCase(worldName);
    }

    void onExitHub(Player player) {
        if (player == null) return;
        if (isPlayersHidden(player)) {
            showAllPlayers(player);
        }
        playersHidden.remove(player.getUniqueId());
    }

    void onOtherPlayerJoin(Player joined) {
        if (joined == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == null) continue;
            if (viewer.equals(joined)) continue;
            if (!isInHub(viewer)) continue;
            if (!isPlayersHidden(viewer)) continue;
            try {
                viewer.hidePlayer(plugin, joined);
            } catch (Exception ignored) {
            }
        }
    }

    private void ensureCompass(Player player, LobbyConfig.CompassConfig compass) {
        int slot = Math.max(0, compass.slot());
        if (slot > 35) slot = 0;

        ItemStack existing = player.getInventory().getItem(slot);
        if (isHubCompass(existing)) return;

        for (ItemStack it : player.getInventory().getContents()) {
            if (isHubCompass(it)) return;
        }

        player.getInventory().setItem(slot, createCompass(compass));
    }

    private void ensurePlayerToggleItem(Player player, LobbyConfig.PlayerVisibilityToggleConfig toggle, boolean hidden) {
        int slot = Math.max(0, toggle.slot());
        if (slot > 35) slot = 8;

        ItemStack existing = player.getInventory().getItem(slot);
        if (existing != null && isPlayerToggleItem(existing)) {
            updateToggleItemMeta(existing, toggle, hidden);
            player.getInventory().setItem(slot, existing);
            return;
        }

        for (ItemStack it : player.getInventory().getContents()) {
            if (isPlayerToggleItem(it)) return;
        }

        player.getInventory().setItem(slot, createToggleItem(toggle, hidden));
    }

    private ItemStack createCompass(LobbyConfig.CompassConfig compass) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(compassKey(), PersistentDataType.BYTE, (byte) 1);
            meta.setDisplayName(colorize(compass.name()));
            List<String> lore = compass.lore();
            if (lore == null || lore.isEmpty()) {
                lore = List.of("&7Right click to open the menu.");
            }
            meta.setLore(lore.stream().map(HubLoadoutManager::colorize).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createToggleItem(LobbyConfig.PlayerVisibilityToggleConfig toggle, boolean hidden) {
        Material mat = parseMaterial(hidden ? toggle.hideMaterial() : toggle.showMaterial(), hidden ? Material.GRAY_DYE : Material.LIME_DYE);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(playerToggleKey(), PersistentDataType.BYTE, (byte) 1);
            updateToggleMeta(meta, toggle, hidden);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void updateToggleItemMeta(ItemStack item, LobbyConfig.PlayerVisibilityToggleConfig toggle, boolean hidden) {
        if (item == null) return;
        Material desired = parseMaterial(hidden ? toggle.hideMaterial() : toggle.showMaterial(), hidden ? Material.GRAY_DYE : Material.LIME_DYE);
        if (item.getType() != desired) item.setType(desired);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(playerToggleKey(), PersistentDataType.BYTE, (byte) 1);
        updateToggleMeta(meta, toggle, hidden);
        item.setItemMeta(meta);
    }

    private void updateToggleMeta(ItemMeta meta, LobbyConfig.PlayerVisibilityToggleConfig toggle, boolean hidden) {
        String name = hidden ? toggle.hideName() : toggle.showName();
        List<String> lore = hidden ? toggle.hideLore() : toggle.showLore();
        meta.setDisplayName(colorize(name));
        if (lore == null || lore.isEmpty()) {
            lore = hidden
                ? List.of("&7Right click to show players.")
                : List.of("&7Right click to hide players.");
        }
        meta.setLore(lore.stream().map(HubLoadoutManager::colorize).toList());
    }

    private void togglePlayers(Player player) {
        if (player == null) return;
        boolean hidden = !isPlayersHidden(player);
        playersHidden.put(player.getUniqueId(), hidden);

        if (hidden) hideAllPlayers(player);
        else showAllPlayers(player);

        LobbyConfig.PlayerVisibilityToggleConfig toggle = config.hubUx().loadout() == null ? null : config.hubUx().loadout().playerToggle();
        if (toggle != null && toggle.enabled()) {
            ensurePlayerToggleItem(player, toggle, hidden);
        }

        player.sendMessage(hidden ? "§7Spieler ausgeblendet." : "§aSpieler angezeigt.");
    }

    private boolean isPlayersHidden(Player player) {
        if (player == null) return false;
        return Boolean.TRUE.equals(playersHidden.get(player.getUniqueId()));
    }

    private void hideAllPlayers(Player viewer) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == null) continue;
            if (other.equals(viewer)) continue;
            try {
                viewer.hidePlayer(plugin, other);
            } catch (Exception ignored) {
            }
        }
    }

    private void showAllPlayers(Player viewer) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == null) continue;
            if (other.equals(viewer)) continue;
            try {
                viewer.showPlayer(plugin, other);
            } catch (Exception ignored) {
            }
        }
    }

    private static Material parseMaterial(String raw, Material fallback) {
        if (raw == null) return fallback;
        try {
            return Material.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void openCompassAction(Player player) {
        var menu = config.hubUx().menu();
        if (menu != null && menu.enabled()) {
            HubMenuUi.open(player, plugin, config);
            return;
        }

        LobbyConfig.MenuItemConfig skywars = menu == null ? null : menu.skywars();
        String cmd = skywars == null ? "sw join" : skywars.command();
        runCommand(player, cmd);
    }

    void runSkywarsQuickplay(Player player) {
        LobbyConfig.MenuItemConfig skywars = config.hubUx().menu() == null ? null : config.hubUx().menu().skywars();
        String cmd = skywars == null ? "sw join" : skywars.command();
        runCommand(player, cmd);
    }

    private void runCommand(Player player, String command) {
        if (player == null) return;
        String normalized = command == null ? "" : command.trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isBlank()) return;
        final String cmd = normalized;

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                player.performCommand(cmd);
            } catch (Exception e) {
                player.sendMessage("§cKonnte Command nicht ausführen: /" + cmd);
            }
        });
    }

    private static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
