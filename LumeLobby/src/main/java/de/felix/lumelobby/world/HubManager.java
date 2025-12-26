package de.felix.lumelobby.world;

import de.felix.lumelobby.config.LobbyConfig;
import de.felix.lumelobby.scheduler.PaperScheduler;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public final class HubManager {

    private final Plugin plugin;
    private final PaperScheduler scheduler;
    private final LobbyConfig config;
    private volatile LobbyConfig.Spawn spawnOverride;

    public boolean enabled() {
        return config.hub().enabled();
    }

    public boolean shouldTeleportOnJoin() {
        return enabled() && config.hub().teleportOnJoin();
    }

    public void ensureHubWorld() {
        if (!enabled()) return;
        scheduler.callSync(() -> {
            ensureHubWorldSync();
            return null;
        });
    }

    public World hubWorldOrNull() {
        if (!enabled()) return null;
        return Bukkit.getWorld(config.hub().worldName());
    }

    public Location hubSpawnOrDefault() {
        World hub = hubWorldOrNull();
        if (hub != null) {
            Location configured = configuredSpawnOrNull(hub);
            if (configured != null) return configured;
            return hub.getSpawnLocation();
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public void sendToHub(Player player) {
        if (player == null) return;
        scheduler.callSync(() -> {
            if (enabled()) ensureHubWorldSync();
            Location spawn = hubSpawnOrDefault();
            if (spawn == null || spawn.getWorld() == null) {
                plugin.getLogger().warning("[Hub] Teleport failed: no hub spawn available");
                return null;
            }

            player.teleportAsync(spawn).exceptionally(ex -> {
                plugin.getLogger().warning("[Hub] Teleport failed: " + ex.getMessage());
                return null;
            });
            return null;
        });
    }

    public void setHubSpawnFrom(Player player) {
        if (player == null) return;
        Location loc = player.getLocation();
        String hubName = config.hub().worldName();
        if (loc.getWorld() == null || hubName == null || hubName.isBlank() || !hubName.equals(loc.getWorld().getName())) {
            player.sendMessage("§cDu musst in der Hub-Welt stehen (" + hubName + "), um den Hub-Spawn zu setzen.");
            return;
        }

        spawnOverride = new LobbyConfig.Spawn(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());

        if (plugin instanceof JavaPlugin jp) {
            var cfg = jp.getConfig();
            cfg.options().copyDefaults(true);
            cfg.set("hub.spawn", List.of(loc.getX(), loc.getY(), loc.getZ(), (double) loc.getYaw(), (double) loc.getPitch()));
            jp.saveConfig();
        }

        applyConfiguredSpawn(loc.getWorld());
        player.sendMessage("§aHub-Spawn gesetzt und in config.yml gespeichert.");
    }

    public void clearHubSpawn(Player player) {
        spawnOverride = null;
        if (plugin instanceof JavaPlugin jp) {
            var cfg = jp.getConfig();
            cfg.options().copyDefaults(true);
            cfg.set("hub.spawn", null);
            cfg.set("hub.spawn.x", null);
            cfg.set("hub.spawn.y", null);
            cfg.set("hub.spawn.z", null);
            cfg.set("hub.spawn.yaw", null);
            cfg.set("hub.spawn.pitch", null);
            jp.saveConfig();
        }
        if (player != null) player.sendMessage("§aHub-Spawn aus config.yml entfernt (fallback: World-Spawn).");
    }

    private void ensureHubWorldSync() {
        String name = config.hub().worldName();
        if (name == null || name.isBlank()) return;

        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            applyHubRules(existing);
            return;
        }

        if (!config.hub().createHubWorld()) {
            plugin.getLogger().warning("[Hub] Hub world '" + name + "' missing and createHubWorld=false");
            return;
        }

        boolean imported = tryImportHubTemplateWorld(name);

        var creator = new WorldCreator(name);
        creator.generateStructures(false);
        if (!imported) creator.generator(new VoidChunkGenerator());
        World world = creator.createWorld();
        if (world == null) throw new IllegalStateException("Hub world creation failed: " + name);

        if (!imported) {
            int y = Math.max(30, config.hub().hubY());
            world.setSpawnLocation(0, y + 2, 0);
            generatePlatform(world, y);
        }

        applyHubRules(world);
        applyConfiguredSpawn(world);
        plugin.getLogger().info("[Hub] Created hub world: " + name + (imported ? " (template)" : ""));
    }

    private void applyHubRules(World world) {
        if (world == null) return;
        world.setAutoSave(false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setTime(6000);
        world.setStorm(false);

        if (config.hub().protectionEnabled() && config.hub().clearMobsOnLoad()) {
            purgeMobs(world);
        }
    }

    private void purgeMobs(World world) {
        try {
            for (var e : world.getEntities()) {
                if (!(e instanceof LivingEntity living)) continue;
                if (living instanceof Player) continue;
                if (living instanceof ArmorStand) continue;
                living.remove();
            }
        } catch (Exception ignored) {
        }
    }

    private static void generatePlatform(World world, int y) {
        int size = 8;
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                Material floor = (Math.abs(x) == size || Math.abs(z) == size) ? Material.BLACK_CONCRETE : Material.SMOOTH_QUARTZ;
                world.getBlockAt(x, y, z).setType(floor, false);
                world.getBlockAt(x, y + 1, z).setType(Material.AIR, false);
                world.getBlockAt(x, y + 2, z).setType(Material.AIR, false);
            }
        }
        world.getBlockAt(0, y + 1, 0).setType(Material.BEACON, false);
        world.getBlockAt(0, y, 0).setType(Material.IRON_BLOCK, false);
    }

    private boolean tryImportHubTemplateWorld(String hubWorldName) {
        try {
            Path templateRoot = ensureTemplateFolder();
            Path templateWorld = detectTemplateWorldFolder(templateRoot);
            if (templateWorld == null) return false;

            Path worldContainer = Bukkit.getWorldContainer().toPath();
            Path target = worldContainer.resolve(hubWorldName);
            if (Files.exists(target)) {
                plugin.getLogger().info("[Hub] Template found but hub world folder already exists: " + target);
                return false;
            }

            copyDirectory(templateWorld, target);
            plugin.getLogger().info("[Hub] Imported hub template '" + templateWorld.getFileName() + "' → " + hubWorldName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Hub] Failed to import hub template: " + e.getMessage());
            return false;
        }
    }

    private Path ensureTemplateFolder() throws IOException {
        Path root = plugin.getDataFolder().toPath().resolve("templates").resolve("hub");
        Files.createDirectories(root);

        Path howTo = root.resolve("HOW_TO_USE.txt");
        if (!Files.exists(howTo)) {
            String text = """
                LumeLobby Hub Template
                =====================

                1) Put a world folder into this directory, e.g.
                   plugins/LumeLobby/templates/hub/MyHubWorld/level.dat

                2) Set the hub world name in config.yml:
                   hub.worldName: "lume_hub"

                3) Restart the server.

                Notes:
                - The template is only imported if the hub world folder does NOT exist yet.
                - If you already have a hub world and want to re-import, stop the server and delete the world folder first.
                - Keep exactly ONE world folder in here (otherwise the first one is used).
                """;
            Files.writeString(howTo, text, StandardCharsets.UTF_8);
        }
        return root;
    }

    private Path detectTemplateWorldFolder(Path root) throws IOException {
        if (root == null) return null;
        if (!Files.isDirectory(root)) return null;

        List<Path> candidates = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                if (Files.exists(dir.resolve("level.dat"))) candidates.add(dir);
            });
        }

        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
        if (candidates.size() > 1) {
            plugin.getLogger().warning("[Hub] Multiple hub templates found. Using: " + candidates.get(0).getFileName());
        }
        return candidates.get(0);
    }

    private static void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = sourceDir.relativize(dir);
                Path target = targetDir.resolve(rel);
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = sourceDir.relativize(file);
                Path target = targetDir.resolve(rel);
                Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Location configuredSpawnOrNull(World world) {
        if (world == null) return null;
        LobbyConfig.Spawn spawn = spawnOverride != null ? spawnOverride : config.hub().spawn();
        if (spawn == null) return null;

        double x = spawn.x() != null ? spawn.x() : world.getSpawnLocation().getX();
        double y = spawn.y() != null ? spawn.y() : world.getSpawnLocation().getY();
        double z = spawn.z() != null ? spawn.z() : world.getSpawnLocation().getZ();
        float yaw = spawn.yaw() != null ? spawn.yaw() : world.getSpawnLocation().getYaw();
        float pitch = spawn.pitch() != null ? spawn.pitch() : world.getSpawnLocation().getPitch();
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void applyConfiguredSpawn(World world) {
        Location configured = configuredSpawnOrNull(world);
        if (configured == null) return;
        world.setSpawnLocation(configured.getBlockX(), configured.getBlockY(), configured.getBlockZ());
    }
}
