package de.felix.lumelobby.config;

import lombok.NonNull;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

public record LobbyConfig(
    HubConfig hub,
    LobbyWorldConfig lobby,
    HubUxConfig hubUx,
    MotdConfig motd
) {
    public static LobbyConfig from(@NonNull FileConfiguration cfg) {
        var hub = new HubConfig(
            cfg.getBoolean("hub.enabled", true),
            cfg.getBoolean("hub.createHubWorld", true),
            cfg.getString("hub.worldName", "lume_hub"),
            cfg.getBoolean("hub.teleportOnJoin", true),
            readSpawn(cfg, "hub.spawn"),
            cfg.getInt("hub.hubY", 120),
            cfg.getBoolean("hub.protection.enabled", true),
            cfg.getBoolean("hub.protection.disableAdvancements", true),
            cfg.getBoolean("hub.protection.clearMobsOnLoad", true)
        );

        var lobby = new LobbyWorldConfig(
            cfg.getBoolean("lobby.enabled", true),
            cfg.getBoolean("lobby.sameAsHub", true),
            cfg.getBoolean("lobby.createLobbyWorld", true),
            cfg.getString("lobby.worldName", "lume_lobby"),
            cfg.getBoolean("lobby.teleportOnJoin", false),
            cfg.getString("lobby.teleportOnJoinMode", "never"),
            readSpawn(cfg, "lobby.spawn"),
            cfg.getInt("lobby.lobbyY", 120),
            cfg.getBoolean("lobby.protection.enabled", true),
            cfg.getBoolean("lobby.protection.disableAdvancements", true),
            cfg.getBoolean("lobby.protection.clearMobsOnLoad", true)
        );

        var hubUx = new HubUxConfig(
            cfg.getBoolean("hubUx.enabled", true),
            cfg.getStringList("hubUx.worlds"),
            new ScoreboardConfig(
                cfg.getBoolean("hubUx.scoreboard.enabled", true),
                cfg.getString("hubUx.scoreboard.title", "&bLumeplay"),
                cfg.getStringList("hubUx.scoreboard.lines"),
                Math.max(1, cfg.getInt("hubUx.scoreboard.updateTicks", 20))
            ),
            new BossBarConfig(
                cfg.getBoolean("hubUx.bossbar.enabled", true),
                cfg.getString("hubUx.bossbar.title", "&bLumeplay"),
                cfg.getString("hubUx.bossbar.color", "BLUE"),
                cfg.getString("hubUx.bossbar.style", "SOLID"),
                clamp01(cfg.getDouble("hubUx.bossbar.progress", 1.0))
            ),
            new DoubleJumpConfig(
                cfg.getBoolean("hubUx.doubleJump.enabled", true),
                cfg.getDouble("hubUx.doubleJump.velocityY", 0.9),
                cfg.getDouble("hubUx.doubleJump.velocityForward", 0.8),
                Math.max(0, cfg.getInt("hubUx.doubleJump.cooldownTicks", 20)),
                cfg.getBoolean("hubUx.doubleJump.sound.enabled", true),
                cfg.getString("hubUx.doubleJump.sound.name", "ENTITY_BAT_TAKEOFF"),
                (float) cfg.getDouble("hubUx.doubleJump.sound.volume", 1.0),
                (float) cfg.getDouble("hubUx.doubleJump.sound.pitch", 1.2)
            ),
            new CosmeticsConfig(
                cfg.getBoolean("hubUx.cosmetics.enabled", true),
                Math.max(1, cfg.getInt("hubUx.cosmetics.updateTicks", 2)),
                new CosmeticsMenuItemConfig(
                    cfg.getBoolean("hubUx.cosmetics.menuItem.enabled", true),
                    cfg.getInt("hubUx.cosmetics.menuItem.slot", 4),
                    cfg.getString("hubUx.cosmetics.menuItem.material", "ENDER_CHEST"),
                    cfg.getString("hubUx.cosmetics.menuItem.name", "&b&lCosmetics"),
                    cfg.getStringList("hubUx.cosmetics.menuItem.lore")
                ),
                new CosmeticsGadgetConfig(
                    cfg.getInt("hubUx.cosmetics.gadgetItem.slot", 2),
                    cfg.getString("hubUx.cosmetics.gadgetItem.material", "BLAZE_ROD"),
                    cfg.getString("hubUx.cosmetics.gadgetItem.name", "&e&lGadget"),
                    cfg.getStringList("hubUx.cosmetics.gadgetItem.lore"),
                    Math.max(0, cfg.getLong("hubUx.cosmetics.gadget.cooldownMs", 2000L)),
                    cfg.getDouble("hubUx.cosmetics.gadget.velocityY", 0.35),
                    cfg.getDouble("hubUx.cosmetics.gadget.velocityForward", 0.7),
                    cfg.getString("hubUx.cosmetics.gadget.particle", "FIREWORK"),
                    Math.max(0, cfg.getInt("hubUx.cosmetics.gadget.particleCount", 35))
                ),
                new CosmeticsParticlesConfig(
                    cfg.getString("hubUx.cosmetics.particles.wings", "END_ROD"),
                    cfg.getString("hubUx.cosmetics.particles.trail", "CLOUD"),
                    cfg.getString("hubUx.cosmetics.particles.halo", "FIREWORK"),
                    cfg.getString("hubUx.cosmetics.particles.aura", "ENCHANT")
                ),
                new WingsConfig(
                    Math.max(1, cfg.getInt("hubUx.cosmetics.wings.points", 9)),
                    clamp01(cfg.getDouble("hubUx.cosmetics.wings.flapStrength", 0.08)),
                    Math.max(200L, cfg.getLong("hubUx.cosmetics.wings.flapPeriodMs", 1500L))
                ),
                new HaloConfig(
                    clamp01(cfg.getDouble("hubUx.cosmetics.halo.radius", 0.45)),
                    Math.max(3, cfg.getInt("hubUx.cosmetics.halo.points", 12)),
                    Math.max(500L, cfg.getLong("hubUx.cosmetics.halo.periodMs", 2000L))
                )
            ),
            new LoadoutConfig(
                cfg.getBoolean("hubUx.loadout.enabled", true),
                cfg.getBoolean("hubUx.loadout.clearInventory", false),
                new CompassConfig(
                    cfg.getBoolean("hubUx.loadout.compass.enabled", true),
                    cfg.getInt("hubUx.loadout.compass.slot", 0),
                    cfg.getString("hubUx.loadout.compass.name", "&b&lPlay"),
                    cfg.getStringList("hubUx.loadout.compass.lore")
                ),
                new PlayerVisibilityToggleConfig(
                    cfg.getBoolean("hubUx.loadout.playerToggle.enabled", true),
                    cfg.getInt("hubUx.loadout.playerToggle.slot", 8),
                    cfg.getString("hubUx.loadout.playerToggle.show.material", "LIME_DYE"),
                    cfg.getString("hubUx.loadout.playerToggle.show.name", "&a&lPlayers: Shown"),
                    cfg.getStringList("hubUx.loadout.playerToggle.show.lore"),
                    cfg.getString("hubUx.loadout.playerToggle.hide.material", "GRAY_DYE"),
                    cfg.getString("hubUx.loadout.playerToggle.hide.name", "&7&lPlayers: Hidden"),
                    cfg.getStringList("hubUx.loadout.playerToggle.hide.lore")
                )
            ),
            new MenuConfig(
                cfg.getBoolean("hubUx.menu.enabled", true),
                cfg.getString("hubUx.menu.title", "&bLumeplay"),
                new MenuItemConfig(
                    cfg.getBoolean("hubUx.menu.skywars.enabled", true),
                    cfg.getInt("hubUx.menu.skywars.slot", 11),
                    cfg.getString("hubUx.menu.skywars.name", "&b&lSkyWars"),
                    cfg.getStringList("hubUx.menu.skywars.lore"),
                    cfg.getString("hubUx.menu.skywars.command", "sw join")
                )
            )
        );

        var motd = new MotdConfig(
            cfg.getBoolean("motd.enabled", true),
            cfg.getStringList("motd.lines")
        );

        return new LobbyConfig(hub, lobby, hubUx, motd);
    }

    private static double clamp01(double value) {
        if (value < 0) return 0;
        if (value > 1) return 1;
        return value;
    }

    private static Spawn readSpawn(@NonNull FileConfiguration cfg, String path) {
        if (cfg.isList(path)) {
            List<?> raw = cfg.getList(path);
            if (raw != null && raw.size() >= 3) {
                Double x = toDoubleOrNull(raw, 0);
                Double y = toDoubleOrNull(raw, 1);
                Double z = toDoubleOrNull(raw, 2);
                Float yaw = raw.size() >= 4 ? toFloatOrNull(raw, 3) : null;
                Float pitch = raw.size() >= 5 ? toFloatOrNull(raw, 4) : null;
                return new Spawn(x, y, z, yaw, pitch);
            }
            return null;
        }

        boolean has = cfg.contains(path + ".x")
            || cfg.contains(path + ".y")
            || cfg.contains(path + ".z")
            || cfg.contains(path + ".yaw")
            || cfg.contains(path + ".pitch");
        if (!has) return null;

        Double x = cfg.contains(path + ".x") ? cfg.getDouble(path + ".x") : null;
        Double y = cfg.contains(path + ".y") ? cfg.getDouble(path + ".y") : null;
        Double z = cfg.contains(path + ".z") ? cfg.getDouble(path + ".z") : null;
        Float yaw = cfg.contains(path + ".yaw") ? (float) cfg.getDouble(path + ".yaw") : null;
        Float pitch = cfg.contains(path + ".pitch") ? (float) cfg.getDouble(path + ".pitch") : null;
        return new Spawn(x, y, z, yaw, pitch);
    }

    private static Double toDoubleOrNull(List<?> list, int idx) {
        if (list == null || idx < 0 || idx >= list.size()) return null;
        Object v = list.get(idx);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Float toFloatOrNull(List<?> list, int idx) {
        Double d = toDoubleOrNull(list, idx);
        return d == null ? null : d.floatValue();
    }

    public record HubConfig(
        boolean enabled,
        boolean createHubWorld,
        String worldName,
        boolean teleportOnJoin,
        Spawn spawn,
        int hubY,
        boolean protectionEnabled,
        boolean disableAdvancements,
        boolean clearMobsOnLoad
    ) {}

    public record LobbyWorldConfig(
        boolean enabled,
        boolean sameAsHub,
        boolean createLobbyWorld,
        String worldName,
        boolean teleportOnJoin,
        String teleportOnJoinMode,
        Spawn spawn,
        int lobbyY,
        boolean protectionEnabled,
        boolean disableAdvancements,
        boolean clearMobsOnLoad
    ) {}

    public record HubUxConfig(
        boolean enabled,
        List<String> worlds,
        ScoreboardConfig scoreboard,
        BossBarConfig bossbar,
        DoubleJumpConfig doubleJump,
        CosmeticsConfig cosmetics,
        LoadoutConfig loadout,
        MenuConfig menu
    ) {}

    public record ScoreboardConfig(
        boolean enabled,
        String title,
        List<String> lines,
        int updateTicks
    ) {}

    public record BossBarConfig(
        boolean enabled,
        String title,
        String color,
        String style,
        double progress
    ) {}

    public record DoubleJumpConfig(
        boolean enabled,
        double velocityY,
        double velocityForward,
        int cooldownTicks,
        boolean soundEnabled,
        String soundName,
        float soundVolume,
        float soundPitch
    ) {}

    public record CosmeticsConfig(
        boolean enabled,
        int updateTicks,
        CosmeticsMenuItemConfig menuItem,
        CosmeticsGadgetConfig gadgetItem,
        CosmeticsParticlesConfig particles,
        WingsConfig wings,
        HaloConfig halo
    ) {
        public String normalizeParticle(String raw, String fallback) {
            String s = raw == null ? "" : raw.trim();
            if (s.isBlank()) s = fallback;
            return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
        }
    }

    public record CosmeticsMenuItemConfig(
        boolean enabled,
        int slot,
        String material,
        String name,
        List<String> lore
    ) {}

    public record CosmeticsGadgetConfig(
        int slot,
        String material,
        String name,
        List<String> lore,
        long cooldownMs,
        double velocityY,
        double velocityForward,
        String particle,
        int particleCount
    ) {}

    public record CosmeticsParticlesConfig(
        String wings,
        String trail,
        String halo,
        String aura
    ) {}

    public record WingsConfig(
        int points,
        double flapStrength,
        long flapPeriodMs
    ) {}

    public record HaloConfig(
        double radius,
        int points,
        long periodMs
    ) {}

    public record LoadoutConfig(
        boolean enabled,
        boolean clearInventory,
        CompassConfig compass,
        PlayerVisibilityToggleConfig playerToggle
    ) {}

    public record CompassConfig(
        boolean enabled,
        int slot,
        String name,
        List<String> lore
    ) {}

    public record PlayerVisibilityToggleConfig(
        boolean enabled,
        int slot,
        String showMaterial,
        String showName,
        List<String> showLore,
        String hideMaterial,
        String hideName,
        List<String> hideLore
    ) {}

    public record MenuConfig(
        boolean enabled,
        String title,
        MenuItemConfig skywars
    ) {}

    public record MenuItemConfig(
        boolean enabled,
        int slot,
        String name,
        List<String> lore,
        String command
    ) {}

    public record MotdConfig(
        boolean enabled,
        List<String> lines
    ) {}

    public record Spawn(
        Double x,
        Double y,
        Double z,
        Float yaw,
        Float pitch
    ) {}
}
