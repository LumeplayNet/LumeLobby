package de.felix.lumelobby.ux;

import de.felix.lumelobby.api.LumeLobbyApi;
import de.felix.lumelobby.config.LobbyConfig;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class HubCosmeticsManager {

    private static final String PERM_PREFIX = "lumelobby.cosmetics.";

    private final Plugin plugin;
    private final LobbyConfig config;
    private final LumeLobbyApi api;

    private final Map<UUID, EnumSet<Cosmetic>> enabled = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastTrailLoc = new ConcurrentHashMap<>();
    private final Map<UUID, Double> auraPhase = new ConcurrentHashMap<>();
    private final Map<UUID, Long> gadgetCooldownUntilMs = new ConcurrentHashMap<>();

    private final File storeFile;
    private final Object saveLock = new Object();
    private volatile boolean saveQueued;

    HubCosmeticsManager(Plugin plugin, LobbyConfig config, LumeLobbyApi api) {
        this.plugin = plugin;
        this.config = config;
        this.api = api;
        this.storeFile = new File(plugin.getDataFolder(), "cosmetics.yml");
        load();
    }

    void tick(Iterable<? extends Player> onlinePlayers, HubScoreboardManager.HubWorldPredicate isInHubWorld) {
        for (Player player : onlinePlayers) {
            if (player == null) continue;
            boolean inHub = isInHubWorld.isInHub(player);
            if (!inHub) {
                cleanup(player);
                continue;
            }
            if (api != null && api.shouldBypassAutoTeleport(player)) {
                cleanup(player);
                continue;
            }

            ensureMenuItem(player);
            ensureGadgetItem(player);
            tickWings(player);
            tickHalo(player);
            tickAura(player);
            tickTrail(player);
        }
    }

    void onQuit(Player player) {
        if (player == null) return;
        cleanup(player);
    }

    void shutdown() {
        saveNow();
    }

    boolean isCosmeticsMenuItem(ItemStack item) {
        return hasByte(item, menuKey());
    }

    boolean isGadgetItem(ItemStack item) {
        return hasByte(item, gadgetKey());
    }

    boolean handleInteract(Player player, ItemStack usedItem) {
        if (player == null) return false;
        if (usedItem == null) return false;
        if (api != null && api.shouldBypassAutoTeleport(player)) return false;

        if (isCosmeticsMenuItem(usedItem)) {
            openMenu(player);
            return true;
        }
        if (isGadgetItem(usedItem)) {
            useGadget(player);
            return true;
        }
        return false;
    }

    boolean handleMenuClick(Player player, Inventory top, ItemStack clicked) {
        if (player == null) return false;
        if (!isCosmeticsMenu(top)) return false;
        if (clicked == null) return true;

        String id = getString(clicked, actionKey());
        if (id == null) return true;
        Cosmetic cosmetic = Cosmetic.from(id);
        if (cosmetic == null) return true;

        toggle(player, cosmetic);
        openMenu(player);
        return true;
    }

    boolean isCosmeticsMenu(Inventory inventory) {
        if (inventory == null) return false;
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof CosmeticsMenuHolder;
    }

    void openMenu(Player player) {
        if (player == null) return;
        Inventory inv = Bukkit.createInventory(new CosmeticsMenuHolder(), InventoryType.CHEST, colorize("&bCosmetics"));

        for (Cosmetic cosmetic : Cosmetic.values()) {
            int slot = cosmetic.slot();
            inv.setItem(slot, buildMenuItem(player, cosmetic));
        }

        player.openInventory(inv);
    }

    private ItemStack buildMenuItem(Player player, Cosmetic cosmetic) {
        boolean on = isEnabled(player, cosmetic);
        boolean unlocked = hasUnlock(player, cosmetic);

        Material material = on ? cosmetic.materialOn() : cosmetic.materialOff();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(actionKey(), PersistentDataType.STRING, cosmetic.id());

            String state = unlocked ? (on ? "&aEnabled" : "&7Disabled") : "&cLocked";
            meta.setDisplayName(colorize(cosmetic.displayName() + " &8- " + state));
            meta.setLore(List.of(
                colorize("&7Right click toggle"),
                colorize("&7Permission: &f" + PERM_PREFIX + cosmetic.id())
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void toggle(Player player, Cosmetic cosmetic) {
        if (player == null || cosmetic == null) return;
        if (!hasUnlock(player, cosmetic)) {
            player.sendMessage("§cDu hast dieses Cosmetic nicht freigeschaltet.");
            return;
        }

        EnumSet<Cosmetic> set = enabled.computeIfAbsent(player.getUniqueId(), k -> EnumSet.noneOf(Cosmetic.class));
        boolean nowOn;
        if (set.contains(cosmetic)) {
            set.remove(cosmetic);
            nowOn = false;
        } else {
            set.add(cosmetic);
            nowOn = true;
        }

        if (!nowOn) {
            if (cosmetic == Cosmetic.GADGET) removeGadgetItem(player);
        } else {
            if (cosmetic == Cosmetic.GADGET) ensureGadgetItem(player);
        }

        saveSoon();
    }

    private boolean hasUnlock(Player player, Cosmetic cosmetic) {
        if (player == null || cosmetic == null) return false;
        return player.hasPermission(PERM_PREFIX + cosmetic.id());
    }

    private boolean isEnabled(Player player, Cosmetic cosmetic) {
        if (player == null || cosmetic == null) return false;
        Set<Cosmetic> set = enabled.get(player.getUniqueId());
        return set != null && set.contains(cosmetic);
    }

    private void ensureMenuItem(Player player) {
        var cfg = config.hubUx().cosmetics();
        var itemCfg = cfg == null ? null : cfg.menuItem();
        if (itemCfg == null || !itemCfg.enabled()) return;

        int slot = clampSlot(itemCfg.slot());
        ItemStack existing = player.getInventory().getItem(slot);
        if (isCosmeticsMenuItem(existing)) return;

        Material material = parseMaterial(itemCfg.material(), Material.ENDER_CHEST);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(menuKey(), PersistentDataType.BYTE, (byte) 1);
            meta.setDisplayName(colorize(itemCfg.name()));
            List<String> lore = itemCfg.lore();
            if (lore == null || lore.isEmpty()) lore = List.of("&7Right click to open");
            meta.setLore(lore.stream().map(HubCosmeticsManager::colorize).toList());
            item.setItemMeta(meta);
        }
        player.getInventory().setItem(slot, item);
    }

    private void ensureGadgetItem(Player player) {
        if (!isEnabled(player, Cosmetic.GADGET)) return;
        var cfg = config.hubUx().cosmetics();
        var gadgetCfg = cfg == null ? null : cfg.gadgetItem();
        int slot = gadgetCfg == null ? 2 : clampSlot(gadgetCfg.slot());
        ItemStack existing = player.getInventory().getItem(slot);
        if (isGadgetItem(existing)) return;

        Material material = parseMaterial(gadgetCfg == null ? null : gadgetCfg.material(), Material.BLAZE_ROD);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(gadgetKey(), PersistentDataType.BYTE, (byte) 1);
            meta.setDisplayName(colorize(gadgetCfg == null ? "&e&lGadget" : gadgetCfg.name()));
            List<String> lore = gadgetCfg == null ? null : gadgetCfg.lore();
            if (lore == null || lore.isEmpty()) lore = List.of("&7Right click", "&7Small boost + particles");
            meta.setLore(lore.stream().map(HubCosmeticsManager::colorize).toList());
            item.setItemMeta(meta);
        }
        player.getInventory().setItem(slot, item);
    }

    private void removeGadgetItem(Player player) {
        var cfg = config.hubUx().cosmetics();
        var gadgetCfg = cfg == null ? null : cfg.gadgetItem();
        int slot = gadgetCfg == null ? 2 : clampSlot(gadgetCfg.slot());
        ItemStack existing = player.getInventory().getItem(slot);
        if (isGadgetItem(existing)) player.getInventory().setItem(slot, null);
    }

    private void useGadget(Player player) {
        if (!isEnabled(player, Cosmetic.GADGET)) return;
        long now = System.currentTimeMillis();

        var cfg = config.hubUx().cosmetics();
        var gadgetCfg = cfg == null ? null : cfg.gadgetItem();
        long cd = gadgetCfg == null ? 2000L : Math.max(0, gadgetCfg.cooldownMs());

        Long until = gadgetCooldownUntilMs.get(player.getUniqueId());
        if (until != null && until > now) return;
        gadgetCooldownUntilMs.put(player.getUniqueId(), now + cd);

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        Vector dir = loc.getDirection();
        if (dir == null) dir = new Vector(0, 0, 0);
        dir.setY(0);
        if (dir.lengthSquared() > 1e-6) dir.normalize();
        double f = gadgetCfg == null ? 0.7 : gadgetCfg.velocityForward();
        double y = gadgetCfg == null ? 0.35 : gadgetCfg.velocityY();
        dir.multiply(f).setY(y);
        player.setVelocity(dir);

        Particle particle = parseParticle(gadgetCfg == null ? null : gadgetCfg.particle(), Particle.FIREWORK);
        int count = gadgetCfg == null ? 35 : Math.max(0, gadgetCfg.particleCount());
        world.spawnParticle(particle, loc.add(0, 1.0, 0), count, 0.35, 0.35, 0.35, 0.02);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
    }

    private void tickTrail(Player player) {
        if (!isEnabled(player, Cosmetic.TRAIL)) return;
        Location now = player.getLocation();
        Location last = lastTrailLoc.put(player.getUniqueId(), now.clone());
        if (last == null) return;
        if (last.getWorld() == null || now.getWorld() == null) return;
        if (!last.getWorld().equals(now.getWorld())) return;
        if (last.distanceSquared(now) < 0.15 * 0.15) return;

        World w = now.getWorld();
        Particle particle = parseParticle(config.hubUx().cosmetics().particles().trail(), Particle.CLOUD);
        w.spawnParticle(particle, now.clone().add(0, 0.1, 0), 4, 0.05, 0.01, 0.05, 0.0);
    }

    private void tickHalo(Player player) {
        if (!isEnabled(player, Cosmetic.HALO)) return;
        Location base = player.getLocation().add(0, 2.15, 0);
        World w = base.getWorld();
        if (w == null) return;
        var haloCfg = config.hubUx().cosmetics().halo();
        double r = haloCfg == null ? 0.45 : haloCfg.radius();
        int pts = haloCfg == null ? 12 : Math.max(3, haloCfg.points());
        long periodMs = haloCfg == null ? 2000L : Math.max(500L, haloCfg.periodMs());

        long t = Instant.now().toEpochMilli();
        double rot = (t % periodMs) / (double) periodMs * Math.PI * 2;
        Particle particle = parseParticle(config.hubUx().cosmetics().particles().halo(), Particle.FIREWORK);
        for (int i = 0; i < pts; i++) {
            double a = rot + (Math.PI * 2 * i / (double) pts);
            double x = Math.cos(a) * r;
            double z = Math.sin(a) * r;
            w.spawnParticle(particle, base.clone().add(x, 0, z), 1, 0, 0, 0, 0);
        }
    }

    private void tickAura(Player player) {
        if (!isEnabled(player, Cosmetic.AURA)) return;
        Location base = player.getLocation().add(0, 0.9, 0);
        World w = base.getWorld();
        if (w == null) return;
        double p = auraPhase.compute(player.getUniqueId(), (k, v) -> v == null ? 0.0 : v + 0.25);
        double r = 0.65;
        Particle particle = parseParticle(config.hubUx().cosmetics().particles().aura(), Particle.ENCHANT);
        for (int i = 0; i < 3; i++) {
            double a = p + (i * (Math.PI * 2 / 3));
            double x = Math.cos(a) * r;
            double z = Math.sin(a) * r;
            w.spawnParticle(particle, base.clone().add(x, 0.15 * i, z), 1, 0, 0, 0, 0);
        }
    }

    private void tickWings(Player player) {
        if (!isEnabled(player, Cosmetic.WINGS)) return;
        Location loc = player.getLocation();
        World w = loc.getWorld();
        if (w == null) return;

        float yaw = loc.getYaw();
        double rad = Math.toRadians(yaw);
        Vector forward = new Vector(-Math.sin(rad), 0, Math.cos(rad));
        if (forward.lengthSquared() > 1e-6) forward.normalize();
        Vector up = new Vector(0, 1, 0);
        Vector right = new Vector(forward.getZ(), 0, -forward.getX());
        right.normalize();

        Location base = loc.clone().add(0, 1.25, 0).subtract(forward.clone().multiply(0.25));

        long t = Instant.now().toEpochMilli();
        var wingsCfg = config.hubUx().cosmetics().wings();
        long flapPeriod = wingsCfg == null ? 1500L : Math.max(200L, wingsCfg.flapPeriodMs());
        double flapStrength = wingsCfg == null ? 0.08 : wingsCfg.flapStrength();
        double flap = Math.sin((t % flapPeriod) / (double) flapPeriod * Math.PI * 2) * flapStrength;

        spawnWing(w, base, right, up, forward, +1, flap);
        spawnWing(w, base, right, up, forward, -1, flap);
    }

    private void spawnWing(World w, Location base, Vector right, Vector up, Vector forward, int side, double flap) {
        int points = Math.max(1, config.hubUx().cosmetics().wings().points());
        Particle particle = parseParticle(config.hubUx().cosmetics().particles().wings(), Particle.END_ROD);
        for (int i = 0; i < points; i++) {
            double y = i * 0.12;
            double x = 0.18 + i * 0.10;
            double z = (i * 0.03) + flap;
            Vector off = right.clone().multiply(side * x).add(up.clone().multiply(y)).add(forward.clone().multiply(-z));
            Location p = base.clone().add(off);
            w.spawnParticle(particle, p, 1, 0, 0, 0, 0);
        }
    }

    private void cleanup(Player player) {
        UUID id = player.getUniqueId();
        lastTrailLoc.remove(id);
        auraPhase.remove(id);
        gadgetCooldownUntilMs.remove(id);
    }

    private static int clampSlot(int slot) {
        if (slot < 0) return 0;
        if (slot > 35) return 35;
        return slot;
    }

    private static Material parseMaterial(String raw, Material fallback) {
        if (raw == null) return fallback;
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Particle parseParticle(String raw, Particle fallback) {
        if (raw == null) return fallback;
        try {
            return Particle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void load() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            if (!storeFile.exists()) return;
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(storeFile);
            for (String key : yml.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                } catch (Exception ignored) {
                    continue;
                }
                EnumSet<Cosmetic> set = EnumSet.noneOf(Cosmetic.class);
                for (Cosmetic cosmetic : Cosmetic.values()) {
                    if (yml.getBoolean(key + "." + cosmetic.id(), false)) set.add(cosmetic);
                }
                enabled.put(uuid, set);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Cosmetics] Failed to load cosmetics.yml: " + e.getMessage());
        }
    }

    private void saveSoon() {
        synchronized (saveLock) {
            if (saveQueued) return;
            saveQueued = true;
        }
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::saveNow, 10L);
    }

    private void saveNow() {
        synchronized (saveLock) {
            saveQueued = false;
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

            YamlConfiguration yml = new YamlConfiguration();
            for (Map.Entry<UUID, EnumSet<Cosmetic>> e : enabled.entrySet()) {
                String root = e.getKey().toString();
                for (Cosmetic cosmetic : Cosmetic.values()) {
                    yml.set(root + "." + cosmetic.id(), e.getValue().contains(cosmetic));
                }
            }
            yml.save(storeFile);
        } catch (Exception e) {
            plugin.getLogger().warning("[Cosmetics] Failed to save cosmetics.yml: " + e.getMessage());
        }
    }

    private NamespacedKey menuKey() {
        return new NamespacedKey(plugin, "hub_cosmetics_menu");
    }

    private NamespacedKey gadgetKey() {
        return new NamespacedKey(plugin, "hub_gadget");
    }

    private NamespacedKey actionKey() {
        return new NamespacedKey(plugin, "cosmetics_action");
    }

    private static boolean hasByte(ItemStack item, NamespacedKey key) {
        if (item == null || key == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte val = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    private static String getString(ItemStack item, NamespacedKey key) {
        if (item == null || key == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class CosmeticsMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 27);
        }
    }

    private enum Cosmetic {
        WINGS("wings", 10, Material.FEATHER, Material.GRAY_DYE, "&d&lWings"),
        TRAIL("trail", 11, Material.STRING, Material.GRAY_DYE, "&b&lTrail"),
        HALO("halo", 12, Material.GOLD_NUGGET, Material.GRAY_DYE, "&e&lHalo"),
        AURA("aura", 13, Material.BLAZE_POWDER, Material.GRAY_DYE, "&5&lAura"),
        GADGET("gadget", 14, Material.BLAZE_ROD, Material.GRAY_DYE, "&6&lGadget");

        private final String id;
        private final int slot;
        private final Material onMat;
        private final Material offMat;
        private final String display;

        Cosmetic(String id, int slot, Material onMat, Material offMat, String display) {
            this.id = id;
            this.slot = slot;
            this.onMat = onMat;
            this.offMat = offMat;
            this.display = display;
        }

        String id() {
            return id;
        }

        int slot() {
            return slot;
        }

        Material materialOn() {
            return onMat;
        }

        Material materialOff() {
            return offMat;
        }

        String displayName() {
            return display;
        }

        static Cosmetic from(String id) {
            if (id == null) return null;
            String k = id.trim().toLowerCase(Locale.ROOT);
            for (Cosmetic c : values()) {
                if (c.id.equals(k)) return c;
            }
            return null;
        }
    }
}
