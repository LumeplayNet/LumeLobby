package de.felix.lumelobby;

import de.felix.lumelobby.api.LobbyJoinRouter;
import de.felix.lumelobby.api.AutoTeleportBypass;
import de.felix.lumelobby.api.HubCommandHook;
import de.felix.lumelobby.api.LumeLobbyApi;
import de.felix.lumelobby.config.LobbyConfig;
import de.felix.lumelobby.scheduler.PaperScheduler;
import de.felix.lumelobby.world.HubManager;
import de.felix.lumelobby.world.LobbyManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

@RequiredArgsConstructor
final class LumeLobbyApiImpl implements LumeLobbyApi {

    private final Plugin plugin;
    private final LobbyConfig config;
    private final PaperScheduler scheduler;
    private final HubManager hubManager;
    private final LobbyManager lobbyManager;
    private volatile LobbyJoinRouter lobbyJoinRouter;
    private volatile AutoTeleportBypass autoTeleportBypass;
    private volatile HubCommandHook hubCommandHook;

    private boolean lobbySameAsHub() {
        return config.lobby() != null && config.lobby().sameAsHub();
    }

    @Override
    public boolean hubEnabled() {
        return hubManager.enabled();
    }

    @Override
    public boolean lobbyEnabled() {
        if (lobbySameAsHub()) return hubEnabled();
        return lobbyManager.enabled();
    }

    @Override
    public World hubWorldOrNull() {
        return hubManager.hubWorldOrNull();
    }

    @Override
    public World lobbyWorldOrNull() {
        if (lobbySameAsHub()) return hubWorldOrNull();
        return lobbyManager.lobbyWorldOrNull();
    }

    @Override
    public Location hubSpawnOrDefault() {
        return hubManager.hubSpawnOrDefault();
    }

    @Override
    public Location lobbySpawnOrDefault() {
        if (lobbySameAsHub()) return hubSpawnOrDefault();
        return lobbyManager.lobbySpawnOrDefault();
    }

    @Override
    public void sendToHub(Player player) {
        hubManager.sendToHub(player);
    }

    @Override
    public void sendToLobby(Player player) {
        if (lobbySameAsHub()) {
            hubManager.sendToHub(player);
            return;
        }
        lobbyManager.sendToLobby(player);
    }

    @Override
    public void setHubSpawnFrom(Player player) {
        hubManager.setHubSpawnFrom(player);
    }

    @Override
    public void clearHubSpawn(Player player) {
        hubManager.clearHubSpawn(player);
    }

    @Override
    public void setLobbySpawnFrom(Player player) {
        if (lobbySameAsHub()) {
            hubManager.setHubSpawnFrom(player);
            return;
        }
        lobbyManager.setLobbySpawnFrom(player);
    }

    @Override
    public void clearLobbySpawn(Player player) {
        if (lobbySameAsHub()) {
            hubManager.clearHubSpawn(player);
            return;
        }
        lobbyManager.clearLobbySpawn(player);
    }

    @Override
    public boolean handleHubCommand(Player player) {
        HubCommandHook hook = hubCommandHook;
        if (hook == null) return false;
        try {
            return hook.onHubCommand(player);
        } catch (Exception e) {
            plugin.getLogger().warning("[Hub] hub command hook error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void setHubCommandHook(HubCommandHook hook) {
        this.hubCommandHook = hook;
    }

    @Override
    public boolean shouldTeleportToLobbyOnJoin(Player player) {
        if (!lobbyEnabled()) return false;
        if (lobbySameAsHub()) {
            if (!config.lobby().teleportOnJoin()) return false;
        } else {
            if (!lobbyManager.shouldTeleportOnJoin()) return false;
        }

        String modeRaw = config.lobby().teleportOnJoinMode();
        String mode = modeRaw == null ? "always" : modeRaw.trim().toLowerCase();

        return switch (mode) {
            case "never" -> false;
            case "during_start", "during_game_start", "starting" -> {
                LobbyJoinRouter router = lobbyJoinRouter;
                if (router == null) yield false;
                try {
                    yield router.shouldTeleportToLobby(player);
                } catch (Exception e) {
                    plugin.getLogger().warning("[Lobby] join router error: " + e.getMessage());
                    yield false;
                }
            }
            case "always" -> true;
            default -> true;
        };
    }

    @Override
    public void setLobbyJoinRouter(LobbyJoinRouter router) {
        this.lobbyJoinRouter = router;
    }

    @Override
    public boolean shouldBypassAutoTeleport(Player player) {
        AutoTeleportBypass bypass = autoTeleportBypass;
        if (bypass == null) return false;
        try {
            return bypass.bypassAutoTeleport(player);
        } catch (Exception e) {
            plugin.getLogger().warning("[Lobby] bypass hook error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void setAutoTeleportBypass(AutoTeleportBypass bypass) {
        this.autoTeleportBypass = bypass;
    }
}
