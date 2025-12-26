package de.felix.lumelobby.ux;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

@RequiredArgsConstructor
public final class HubUxListener implements Listener {

    private final Plugin plugin;
    private final Supplier<HubUxManager> hubUx;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            HubUxManager ux = hubUx.get();
            if (ux == null) return;
            ux.refresh(event.getPlayer());
            ux.loadoutManager().onOtherPlayerJoin(event.getPlayer());
        }, 5L);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            HubUxManager ux = hubUx.get();
            if (ux == null) return;
            ux.refresh(event.getPlayer());
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        HubUxManager ux = hubUx.get();
        if (ux == null) return;
        ux.onQuit(event.getPlayer());
    }
}
