package de.felix.lumelobby.ux;

import de.felix.lumelobby.api.LumeLobbyApi;
import de.felix.lumelobby.config.LobbyConfig;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class HubDoubleJumpListener implements Listener {

    private final Plugin plugin;
    private final LobbyConfig config;
    private final LumeLobbyApi api;

    private final Map<UUID, Long> cooldownUntilMs = new ConcurrentHashMap<>();

    private LobbyConfig.DoubleJumpConfig cfg() {
        if (config.hubUx() == null) return null;
        return config.hubUx().doubleJump();
    }

    private boolean enabled() {
        var c = cfg();
        return c != null && config.hubUx().enabled() && c.enabled();
    }

    private boolean isInHub(Player player) {
        if (player == null) return false;
        if (api != null && api.shouldBypassAutoTeleport(player)) return false;
        if (player.getWorld() == null) return false;
        String world = player.getWorld().getName();
        if (world == null) return false;

        String hub = config.hub() == null ? null : config.hub().worldName();
        if (hub != null && !hub.isBlank() && hub.equalsIgnoreCase(world)) return true;

        if (config.lobby() != null && config.lobby().sameAsHub()) {
            String lobby = config.lobby().worldName();
            return lobby != null && !lobby.isBlank() && lobby.equalsIgnoreCase(world);
        }
        return false;
    }

    private boolean canUse(Player player) {
        if (player == null) return false;
        if (!enabled()) return false;
        if (!isInHub(player)) return false;

        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return false;
        return true;
    }

    private void ensureFlight(Player player) {
        if (player == null) return;
        if (!canUse(player)) return;

        if (player.isOnGround() && !player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureFlight(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureFlight(event.getPlayer()), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldownUntilMs.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!canUse(player)) return;

        long now = System.currentTimeMillis();
        Long until = cooldownUntilMs.get(player.getUniqueId());
        if (until != null && until > now) {
            event.setCancelled(true);
            player.setFlying(false);
            return;
        }

        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);

        LobbyConfig.DoubleJumpConfig c = cfg();
        if (c == null) return;

        Vector dir = player.getLocation().getDirection();
        if (dir == null) dir = new Vector(0, 0, 0);
        dir.setY(0);
        if (dir.lengthSquared() > 1e-6) dir.normalize();
        dir.multiply(c.velocityForward());

        Vector v = dir.setY(c.velocityY());
        player.setVelocity(v);

        if (c.soundEnabled()) {
            try {
                String key = c.soundName();
                if (key != null && !key.isBlank()) {
                    var soundKey = org.bukkit.NamespacedKey.minecraft(key.trim().toLowerCase().replace('.', '_'));
                    var sound = Registry.SOUNDS.get(soundKey);
                    if (sound != null) player.playSound(player.getLocation(), sound, c.soundVolume(), c.soundPitch());
                }
            } catch (Exception ignored) {
            }
        }

        if (c.cooldownTicks() > 0) {
            cooldownUntilMs.put(player.getUniqueId(), now + (c.cooldownTicks() * 50L));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!enabled()) return;
        if (!isInHub(player)) return;
        if (!player.isOnGround()) return;
        if (player.getAllowFlight()) return;

        long now = System.currentTimeMillis();
        Long until = cooldownUntilMs.get(player.getUniqueId());
        if (until != null && until > now) return;
        player.setAllowFlight(true);
    }
}
