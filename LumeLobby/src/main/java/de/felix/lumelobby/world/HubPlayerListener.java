package de.felix.lumelobby.world;

import de.felix.lumelobby.api.LumeLobbyApi;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@RequiredArgsConstructor
public final class HubPlayerListener implements Listener {

    private final HubManager hubManager;
    private final LumeLobbyApi api;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!hubManager.shouldTeleportOnJoin()) return;
        if (api.shouldBypassAutoTeleport(event.getPlayer())) return;
        hubManager.sendToHub(event.getPlayer());
    }
}
