package de.felix.lumelobby.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public interface LumeLobbyApi {

    boolean hubEnabled();

    boolean lobbyEnabled();

    World hubWorldOrNull();

    World lobbyWorldOrNull();

    Location hubSpawnOrDefault();

    Location lobbySpawnOrDefault();

    void sendToHub(Player player);

    void sendToLobby(Player player);

    void setHubSpawnFrom(Player player);

    void clearHubSpawn(Player player);

    void setLobbySpawnFrom(Player player);

    void clearLobbySpawn(Player player);

    /**
     * Hook that runs when a player executes /hub (LumeLobby).
     * Use this for gamemodes: leave the match/queue and then send the player to hub.
     */
    boolean handleHubCommand(Player player);

    void setHubCommandHook(HubCommandHook hook);

    boolean shouldTeleportToLobbyOnJoin(Player player);

    void setLobbyJoinRouter(LobbyJoinRouter router);

    boolean shouldBypassAutoTeleport(Player player);

    void setAutoTeleportBypass(AutoTeleportBypass bypass);
}
