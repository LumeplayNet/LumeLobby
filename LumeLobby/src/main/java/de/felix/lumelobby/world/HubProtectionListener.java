package de.felix.lumelobby.world;

import de.felix.lumelobby.config.LobbyConfig;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class HubProtectionListener implements Listener {

    private final Plugin plugin;
    private final LobbyConfig config;
    private final HubManager hubManager;
    private final Set<String> recentlyRevoked = ConcurrentHashMap.newKeySet();
    private static final String PERM_ADMIN = "lumelobby.admin";

    private boolean enabled() {
        return config.hub().enabled() && config.hub().protectionEnabled();
    }

    private boolean isHubWorld(World world) {
        if (world == null) return false;
        String name = config.hub().worldName();
        return name != null && !name.isBlank() && name.equals(world.getName());
    }

    private boolean isInHub(Player player) {
        return player != null && isHubWorld(player.getWorld());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!enabled()) return;
        if (!isHubWorld(event.getBlock().getWorld())) return;
        Player player = event.getPlayer();
        if (player != null && player.hasPermission(PERM_ADMIN)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!enabled()) return;
        if (!isHubWorld(event.getBlock().getWorld())) return;
        Player player = event.getPlayer();
        if (player != null && player.hasPermission(PERM_ADMIN)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!enabled()) return;
        if (!isHubWorld(event.getBlockClicked().getWorld())) return;
        Player player = event.getPlayer();
        if (player != null && player.hasPermission(PERM_ADMIN)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!enabled()) return;
        if (!isHubWorld(event.getBlockClicked().getWorld())) return;
        Player player = event.getPlayer();
        if (player != null && player.hasPermission(PERM_ADMIN)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!enabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInHub(player)) return;
        event.setCancelled(true);
        player.setFireTicks(0);
        player.setFallDistance(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!enabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInHub(player)) return;
        event.setCancelled(true);
        if (player.getFoodLevel() < 20) player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!enabled()) return;
        if (!isHubWorld(event.getLocation().getWorld())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onVoidRescue(PlayerMoveEvent event) {
        if (!enabled()) return;
        Player player = event.getPlayer();
        if (!isInHub(player)) return;
        if (event.getTo() == null) return;
        if (event.getTo().getY() > 5) return;

        Location spawn = hubManager.hubSpawnOrDefault();
        if (spawn == null || spawn.getWorld() == null) return;

        player.setFallDistance(0);
        player.setFireTicks(0);
        player.teleportAsync(spawn);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!enabled()) return;
        if (!config.hub().disableAdvancements()) return;
        Player player = event.getPlayer();
        if (!isInHub(player)) return;

        Advancement advancement = event.getAdvancement();
        if (advancement == null || advancement.getKey() == null) return;
        String key = player.getUniqueId() + ":" + advancement.getKey();
        if (!recentlyRevoked.add(key)) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                AdvancementProgress progress = player.getAdvancementProgress(advancement);
                for (String criterion : progress.getAwardedCriteria()) {
                    progress.revokeCriteria(criterion);
                }
            } catch (Exception ignored) {
            } finally {
                Bukkit.getScheduler().runTaskLater(plugin, () -> recentlyRevoked.remove(key), 1L);
            }
        });
    }
}
