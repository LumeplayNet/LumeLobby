package de.felix.lumelobby;

import de.felix.lumelobby.api.LumeLobbyApi;
import de.felix.lumelobby.config.LobbyConfig;
import de.felix.lumelobby.scheduler.PaperScheduler;
import de.felix.lumelobby.ux.HubUxListener;
import de.felix.lumelobby.ux.HubUxManager;
import de.felix.lumelobby.ux.HubUxInteractionListener;
import de.felix.lumelobby.ux.HubDoubleJumpListener;
import de.felix.lumelobby.world.HubManager;
import de.felix.lumelobby.world.HubPlayerListener;
import de.felix.lumelobby.world.HubProtectionListener;
import de.felix.lumelobby.world.LobbyManager;
import de.felix.lumelobby.world.LobbyPlayerListener;
import de.felix.lumelobby.world.LobbyProtectionListener;
import de.felix.lumelobby.motd.MotdListener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

public final class LumeLobbyPlugin extends JavaPlugin {

    private volatile LobbyConfig configModel;
    private volatile PaperScheduler scheduler;
    private volatile HubManager hubManager;
    private volatile LobbyManager lobbyManager;
    private volatile HubUxManager hubUx;
    private volatile LumeLobbyApi api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        registerPermissions();

        configModel = LobbyConfig.from(getConfig());
        scheduler = new PaperScheduler(this);

        hubManager = new HubManager(this, scheduler, configModel);
        hubManager.ensureHubWorld();

        lobbyManager = new LobbyManager(this, scheduler, configModel);
        if (!configModel.lobby().sameAsHub()) {
            lobbyManager.ensureLobbyWorld();
        }

        var api = new LumeLobbyApiImpl(this, configModel, scheduler, hubManager, lobbyManager);
        getServer().getServicesManager().register(LumeLobbyApi.class, api, this, ServicePriority.Normal);
        this.api = api;

        getServer().getPluginManager().registerEvents(new MotdListener(configModel), this);
        getServer().getPluginManager().registerEvents(new HubPlayerListener(hubManager, api), this);
        getServer().getPluginManager().registerEvents(new LobbyPlayerListener(hubManager, api), this);
        getServer().getPluginManager().registerEvents(new HubProtectionListener(this, configModel, hubManager), this);
        if (!configModel.lobby().sameAsHub()) {
            getServer().getPluginManager().registerEvents(new LobbyProtectionListener(this, configModel, lobbyManager), this);
        }

        hubUx = HubUxManager.create(this, configModel, hubManager, api);
        getServer().getPluginManager().registerEvents(new HubUxListener(this, () -> this.hubUx), this);
        getServer().getPluginManager().registerEvents(new HubUxInteractionListener(this, () -> this.hubUx), this);
        getServer().getPluginManager().registerEvents(new HubDoubleJumpListener(this, configModel, api), this);
        hubUx.start();

        registerCommands(api);
    }

    @Override
    public void onDisable() {
        HubUxManager ux = hubUx;
        hubUx = null;
        if (ux != null) ux.stop();
        api = null;
        getServer().getServicesManager().unregisterAll(this);
    }

    private void registerCommands(LumeLobbyApi api) {
        registerCommand("hub", "Teleport to hub", java.util.List.of("spawn"), new de.felix.lumelobby.commands.HubCommand(api));
        registerCommand("lobby", "Teleport to lobby", java.util.List.of(), new de.felix.lumelobby.commands.LobbyCommand(api));
        registerCommand("lumelobby", "LumeLobby admin command", java.util.List.of("ll"), new de.felix.lumelobby.commands.LumeLobbyCommand(this, api));
        registerCommand("cosmetics", "Open cosmetics menu", java.util.List.of("cos"), new de.felix.lumelobby.ux.CosmeticsCommand(() -> this.hubUx));
    }

    public void reloadHubUx() {
        reloadConfig();
        configModel = LobbyConfig.from(getConfig());

        HubUxManager current = hubUx;
        if (current != null) current.stop();

        LumeLobbyApi currentApi = api;
        if (currentApi == null) {
            getLogger().warning("[HubUx] reload skipped: API not available");
            return;
        }
        hubUx = HubUxManager.create(this, configModel, hubManager, currentApi);
        hubUx.start();
        getLogger().info("[HubUx] Reloaded from config.yml");
    }

    private void registerPermissions() {
        var pm = getServer().getPluginManager();

        if (pm.getPermission("lumelobby.use") == null) {
            pm.addPermission(new Permission("lumelobby.use", "Use lobby commands", PermissionDefault.TRUE));
        }
        if (pm.getPermission("lumelobby.admin") == null) {
            pm.addPermission(new Permission("lumelobby.admin", "Admin lobby commands", PermissionDefault.OP));
        }
    }
}
