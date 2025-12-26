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

@RequiredArgsConstructor
public final class LobbyManager {

    private final Plugin plugin;
    private final PaperScheduler scheduler;
    private final LobbyConfig config;
    private volatile LobbyConfig.Spawn spawnOverride;

    public boolean enabled() {
        return config.lobby().enabled();
    }

    public boolean shouldTeleportOnJoin() {
        return enabled() && config.lobby().teleportOnJoin();
    }

    public String teleportOnJoinMode() {
        return config.lobby().teleportOnJoinMode();
    }

    public void ensureLobbyWorld() {
        if (!enabled()) return;
        scheduler.callSync(() -> {
            ensureLobbyWorldSync();
            return null;
        });
    }

    public World lobbyWorldOrNull() {
        if (!enabled()) return null;
        return Bukkit.getWorld(config.lobby().worldName());
    }

    public Location lobbySpawnOrDefault() {
        World lobby = lobbyWorldOrNull();
        if (lobby != null) {
            Location configured = configuredSpawnOrNull(lobby);
            if (configured != null) return configured;
            return lobby.getSpawnLocation();
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public void sendToLobby(Player player) {
        if (player == null) return;
        scheduler.callSync(() -> {
            if (enabled()) ensureLobbyWorldSync();
            Location spawn = lobbySpawnOrDefault();
            if (spawn == null || spawn.getWorld() == null) {
                plugin.getLogger().warning("[Lobby] Teleport failed: no lobby spawn available");
                return null;
            }

            player.teleportAsync(spawn).exceptionally(ex -> {
                plugin.getLogger().warning("[Lobby] Teleport failed: " + ex.getMessage());
                return null;
            });
            return null;
        });
    }

    public void setLobbySpawnFrom(Player player) {
        if (player == null) return;
        Location loc = player.getLocation();
        String lobbyName = config.lobby().worldName();
        if (loc.getWorld() == null || lobbyName == null || lobbyName.isBlank() || !lobbyName.equals(loc.getWorld().getName())) {
            player.sendMessage("§cDu musst in der Lobby-Welt stehen (" + lobbyName + "), um den Lobby-Spawn zu setzen.");
            return;
        }

        spawnOverride = new LobbyConfig.Spawn(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());

        if (plugin instanceof JavaPlugin jp) {
            var cfg = jp.getConfig();
            cfg.options().copyDefaults(true);
            cfg.set("lobby.spawn", List.of(loc.getX(), loc.getY(), loc.getZ(), (double) loc.getYaw(), (double) loc.getPitch()));
            jp.saveConfig();
        }

        applyConfiguredSpawn(loc.getWorld());
        player.sendMessage("§aLobby-Spawn gesetzt und in config.yml gespeichert.");
    }

    public void clearLobbySpawn(Player player) {
        spawnOverride = null;
        if (plugin instanceof JavaPlugin jp) {
            var cfg = jp.getConfig();
            cfg.options().copyDefaults(true);
            cfg.set("lobby.spawn", null);
            cfg.set("lobby.spawn.x", null);
            cfg.set("lobby.spawn.y", null);
            cfg.set("lobby.spawn.z", null);
            cfg.set("lobby.spawn.yaw", null);
            cfg.set("lobby.spawn.pitch", null);
            jp.saveConfig();
        }
        if (player != null) player.sendMessage("§aLobby-Spawn aus config.yml entfernt (fallback: World-Spawn).");
    }

    private void ensureLobbyWorldSync() {
        String name = config.lobby().worldName();
        if (name == null || name.isBlank()) return;

        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            applyLobbyRules(existing);
            return;
        }

        if (!config.lobby().createLobbyWorld()) {
            plugin.getLogger().warning("[Lobby] Lobby world '" + name + "' missing and createLobbyWorld=false");
            return;
        }

        boolean imported = tryImportLobbyTemplateWorld(name);

        var creator = new WorldCreator(name);
        creator.generateStructures(false);
        if (!imported) creator.generator(new VoidChunkGenerator());
        World world = creator.createWorld();
        if (world == null) throw new IllegalStateException("Lobby world creation failed: " + name);

        if (!imported) {
            int y = Math.max(30, config.lobby().lobbyY());
            world.setSpawnLocation(0, y + 2, 0);
            generatePlatform(world, y);
        }

        applyLobbyRules(world);
        applyConfiguredSpawn(world);
        plugin.getLogger().info("[Lobby] Created lobby world: " + name + (imported ? " (template)" : ""));
    }

    private void applyLobbyRules(World world) {
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

        if (config.lobby().protectionEnabled() && config.lobby().clearMobsOnLoad()) {
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
        int size = 6;
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                Material floor = (Math.abs(x) == size || Math.abs(z) == size) ? Material.BLACK_CONCRETE : Material.SMOOTH_STONE;
                world.getBlockAt(x, y, z).setType(floor, false);
                world.getBlockAt(x, y + 1, z).setType(Material.AIR, false);
                world.getBlockAt(x, y + 2, z).setType(Material.AIR, false);
            }
        }
        world.getBlockAt(0, y + 1, 0).setType(Material.BEACON, false);
        world.getBlockAt(0, y, 0).setType(Material.IRON_BLOCK, false);
    }

    private boolean tryImportLobbyTemplateWorld(String lobbyWorldName) {
        try {
            Path templateRoot = ensureTemplateFolder();
            Path templateWorld = detectTemplateWorldFolder(templateRoot);
            if (templateWorld == null) return false;

            Path worldContainer = Bukkit.getWorldContainer().toPath();
            Path target = worldContainer.resolve(lobbyWorldName);
            if (Files.exists(target)) {
                plugin.getLogger().info("[Lobby] Template found but lobby world folder already exists: " + target);
                return false;
            }

            copyDirectory(templateWorld, target);
            plugin.getLogger().info("[Lobby] Imported lobby template '" + templateWorld.getFileName() + "' → " + lobbyWorldName);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Lobby] Failed to import lobby template: " + e.getMessage());
            return false;
        }
    }

    private Path ensureTemplateFolder() throws IOException {
        Path root = plugin.getDataFolder().toPath().resolve("templates").resolve("lobby");
        Files.createDirectories(root);

        Path howTo = root.resolve("HOW_TO_USE.txt");
        if (!Files.exists(howTo)) {
            String text = """
                LumeLobby Lobby Template
                =======================

                1) Put a world folder into this directory, e.g.
                   plugins/LumeLobby/templates/lobby/MyLobbyWorld/level.dat

                2) Set the lobby world name in config.yml:
                   lobby.worldName: "lume_lobby"

                3) Restart the server.

                Notes:
                - The template is only imported if the lobby world folder does NOT exist yet.
                - If you already have a lobby world and want to re-import, stop the server and delete the world folder first.
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
            plugin.getLogger().warning("[Lobby] Multiple lobby templates found. Using: " + candidates.get(0).getFileName());
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
        LobbyConfig.Spawn spawn = spawnOverride != null ? spawnOverride : config.lobby().spawn();
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
