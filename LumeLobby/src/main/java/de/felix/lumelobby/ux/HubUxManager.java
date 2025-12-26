package de.felix.lumelobby.ux;

import de.felix.lumelobby.api.LumeLobbyApi;
import de.felix.lumelobby.config.LobbyConfig;
import de.felix.lumelobby.world.HubManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

@RequiredArgsConstructor
public final class HubUxManager {

    private final Plugin plugin;
    private final LobbyConfig config;
    private final HubManager hubManager;
    private final LumeLobbyApi api;

    private final HubScoreboardManager scoreboardManager;
    private final HubBossBarManager bossBarManager;
    private final HubLoadoutManager loadoutManager;
    private final HubCosmeticsManager cosmeticsManager;
    private volatile BukkitTask task;
    private volatile boolean stoppedDueToError;
    private volatile long tickCounter;

    public static HubUxManager create(Plugin plugin, LobbyConfig config, HubManager hubManager, LumeLobbyApi api) {
        return new HubUxManager(
            plugin,
            config,
            hubManager,
            api,
            new HubScoreboardManager(plugin, config),
            new HubBossBarManager(plugin, config),
            new HubLoadoutManager(plugin, config, hubManager),
            new HubCosmeticsManager(plugin, config, api)
        );
    }

    public void start() {
        if (!enabled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);

        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void stop() {
        BukkitTask t = task;
        task = null;
        if (t != null) t.cancel();
        scoreboardManager.clearAll();
        bossBarManager.stop();
        cosmeticsManager.shutdown();
    }

    public void refresh(Player player) {
        boolean inHub = isInHub(player);
        scoreboardManager.refresh(player, inHub);
        loadoutManager.refresh(player, inHub);
    }

    public void onQuit(Player player) {
        scoreboardManager.refresh(player, false);
        bossBarManager.onQuit(player);
        loadoutManager.onExitHub(player);
        cosmeticsManager.onQuit(player);
    }

    HubLoadoutManager loadoutManager() {
        return loadoutManager;
    }

    HubCosmeticsManager cosmeticsManager() {
        return cosmeticsManager;
    }

    private void tick() {
        if (stoppedDueToError) return;
        tickCounter++;
        var online = Bukkit.getOnlinePlayers();
        try {
            int scoreboardTicks = Math.max(1, config.hubUx().scoreboard().updateTicks());
            var cosmeticsCfg = config.hubUx().cosmetics();
            boolean cosmeticsEnabled = cosmeticsCfg == null || cosmeticsCfg.enabled();
            int cosmeticsTicks = cosmeticsCfg == null ? 2 : Math.max(1, cosmeticsCfg.updateTicks());

            if (tickCounter % scoreboardTicks == 0) {
                scoreboardManager.tick(online, this::isInHub);
                bossBarManager.tick(online, this::isInHub);
            }

            for (Player player : online) {
                boolean inHub = isInHub(player);
                loadoutManager.ensureItemsTick(player, inHub);
            }
            if (cosmeticsEnabled && tickCounter % cosmeticsTicks == 0) {
                cosmeticsManager.tick(online, this::isInHub);
            }
        } catch (NoClassDefFoundError e) {
            stoppedDueToError = true;
            plugin.getLogger().severe("[HubUx] Missing class at runtime: " + e.getMessage());
            plugin.getLogger().severe("[HubUx] This usually happens if the plugin jar was overwritten while the server is running. Stop the server and start it again.");
            stop();
        } catch (Throwable t) {
            stoppedDueToError = true;
            plugin.getLogger().severe("[HubUx] Fatal error, disabling HubUx: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            stop();
        }
    }

    private boolean enabled() {
        return hubManager.enabled() && config.hubUx() != null && config.hubUx().enabled();
    }

    private boolean isInHub(Player player) {
        if (player == null) return false;
        if (!hubManager.enabled()) return false;
        if (api != null && api.shouldBypassAutoTeleport(player)) return false;
        if (player.getWorld() == null) return false;

        String worldName = player.getWorld().getName();
        if (worldName == null) return false;

        var worlds = config.hubUx() == null ? null : config.hubUx().worlds();
        if (worlds != null && !worlds.isEmpty()) {
            for (String w : worlds) {
                if (w == null || w.isBlank()) continue;
                if (w.equalsIgnoreCase(worldName)) return true;
            }
            return false;
        }

        String hubWorld = config.hub().worldName();
        if (hubWorld != null && !hubWorld.isBlank() && hubWorld.equalsIgnoreCase(worldName)) return true;

        if (config.lobby() != null && config.lobby().sameAsHub()) {
            String lobbyWorld = config.lobby().worldName();
            return lobbyWorld != null && !lobbyWorld.isBlank() && lobbyWorld.equalsIgnoreCase(worldName);
        }
        return false;
    }
}
