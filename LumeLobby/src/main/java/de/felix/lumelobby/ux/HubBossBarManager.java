package de.felix.lumelobby.ux;

import de.felix.lumelobby.config.LobbyConfig;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
final class HubBossBarManager {

    private final Plugin plugin;
    private final LobbyConfig config;
    private final ConcurrentHashMap<UUID, Boolean> active = new ConcurrentHashMap<>();
    private volatile BossBar bossBar;

    void tick(Iterable<? extends Player> onlinePlayers, HubScoreboardManager.HubWorldPredicate isInHubWorld) {
        if (!enabled()) {
            stop();
            return;
        }

        ensureCreated();
        BossBar bar = bossBar;
        if (bar == null) return;

        bar.setTitle(colorize(config.hubUx().bossbar().title()));
        bar.setProgress(config.hubUx().bossbar().progress());

        for (Player player : onlinePlayers) {
            if (player == null) continue;
            boolean inHub = isInHubWorld.isInHub(player);
            if (inHub) {
                if (active.putIfAbsent(player.getUniqueId(), Boolean.TRUE) == null) {
                    bar.addPlayer(player);
                }
            } else {
                if (active.remove(player.getUniqueId()) != null) {
                    bar.removePlayer(player);
                }
            }
        }
    }

    void stop() {
        BossBar bar = bossBar;
        bossBar = null;
        active.clear();
        if (bar != null) {
            try {
                bar.removeAll();
            } catch (Exception ignored) {
            }
        }
    }

    void onQuit(Player player) {
        if (player == null) return;
        BossBar bar = bossBar;
        if (bar == null) return;
        if (active.remove(player.getUniqueId()) != null) {
            try {
                bar.removePlayer(player);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean enabled() {
        return config.hubUx() != null && config.hubUx().enabled() && config.hubUx().bossbar() != null && config.hubUx().bossbar().enabled();
    }

    private void ensureCreated() {
        if (bossBar != null) return;
        try {
            LobbyConfig.BossBarConfig cfg = config.hubUx().bossbar();
            BarColor color = parseColor(cfg.color());
            BarStyle style = parseStyle(cfg.style());
            bossBar = Bukkit.createBossBar(colorize(cfg.title()), color, style);
            bossBar.setVisible(true);
            bossBar.setProgress(cfg.progress());
        } catch (Exception e) {
            plugin.getLogger().warning("[HubUx] BossBar init failed: " + e.getMessage());
            bossBar = null;
        }
    }

    private static BarColor parseColor(String raw) {
        if (raw == null) return BarColor.BLUE;
        try {
            return BarColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return BarColor.BLUE;
        }
    }

    private static BarStyle parseStyle(String raw) {
        if (raw == null) return BarStyle.SOLID;
        try {
            return BarStyle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return BarStyle.SOLID;
        }
    }

    private static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
