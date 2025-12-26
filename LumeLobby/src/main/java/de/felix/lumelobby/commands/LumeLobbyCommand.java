package de.felix.lumelobby.commands;

import de.felix.lumelobby.LumeLobbyPlugin;
import de.felix.lumelobby.api.LumeLobbyApi;
import de.felix.lumecommands.ui.CommandUi;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

public final class LumeLobbyCommand implements BasicCommand {

    private static final String PERM_ADMIN = "lumelobby.admin";

    private final LumeLobbyPlugin plugin;
    private final LumeLobbyApi api;
    private final CommandUi ui = new CommandUi("LumeLobby");

    public LumeLobbyCommand(LumeLobbyPlugin plugin, LumeLobbyApi api) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl benutzen.");
            return;
        }

        String action = (args == null || args.length == 0) ? "status" : String.valueOf(args[0]).toLowerCase();

        switch (action) {
            case "reload" -> {
                if (!player.hasPermission(PERM_ADMIN)) {
                    ui.noPermission(player, PERM_ADMIN);
                    return;
                }
                plugin.reloadHubUx();
                ui.success(player, "Hub UX reloaded.");
                return;
            }
            case "setspawn", "set" -> {
                if (!player.hasPermission(PERM_ADMIN)) {
                    ui.noPermission(player, PERM_ADMIN);
                    return;
                }
                setSpawnHere(player);
                return;
            }
            case "clearspawn", "clear" -> {
                if (!player.hasPermission(PERM_ADMIN)) {
                    ui.noPermission(player, PERM_ADMIN);
                    return;
                }
                clearSpawn(player);
                return;
            }
            case "status", "info", "debug" -> {
                sendStatus(player);
                return;
            }
            default -> {
                ui.error(player, "Unbekannte Aktion: " + action);
                ui.info(player, "Nutze: /lumelobby status | reload | setspawn | clearspawn");
            }
        }
    }

    private void setSpawnHere(Player player) {
        World world = player.getWorld();
        if (world == null) return;

        World hub = api.hubWorldOrNull();
        World lobby = api.lobbyWorldOrNull();

        if (hub != null && hub.equals(world)) {
            api.setHubSpawnFrom(player);
            ui.success(player, "Hub-Spawn gesetzt.");
            return;
        }
        if (lobby != null && lobby.equals(world)) {
            api.setLobbySpawnFrom(player);
            ui.success(player, "Lobby-Spawn gesetzt.");
            return;
        }

        String hubName = hub == null ? String.valueOf(api.hubSpawnOrDefault() == null ? "?" : api.hubSpawnOrDefault().getWorld().getName()) : hub.getName();
        String lobbyName = lobby == null ? String.valueOf(api.lobbySpawnOrDefault() == null ? "?" : api.lobbySpawnOrDefault().getWorld().getName()) : lobby.getName();
        ui.error(player, "Du musst in Hub/Lobby stehen, um den Spawn zu setzen.");
        ui.info(player, "Hub: " + hubName + " | Lobby: " + lobbyName + " | Du: " + world.getName());
    }

    private void clearSpawn(Player player) {
        World world = player.getWorld();
        if (world == null) return;

        World hub = api.hubWorldOrNull();
        World lobby = api.lobbyWorldOrNull();

        if (hub != null && hub.equals(world)) {
            api.clearHubSpawn(player);
            ui.success(player, "Hub-Spawn entfernt.");
            return;
        }
        if (lobby != null && lobby.equals(world)) {
            api.clearLobbySpawn(player);
            ui.success(player, "Lobby-Spawn entfernt.");
            return;
        }

        ui.error(player, "Du musst in Hub/Lobby stehen, um den Spawn zu entfernen.");
    }

    private void sendStatus(Player player) {
        World world = player.getWorld();
        String worldName = world == null ? "?" : world.getName();

        World hub = api.hubWorldOrNull();
        World lobby = api.lobbyWorldOrNull();

        ui.info(player, "World: " + worldName);
        ui.info(player, "Hub enabled: " + api.hubEnabled() + " | hubWorld: " + (hub == null ? "null" : hub.getName()));
        ui.info(player, "Lobby enabled: " + api.lobbyEnabled() + " | lobbyWorld: " + (lobby == null ? "null" : lobby.getName()));
    }

    @Override
    public List<String> suggest(CommandSourceStack stack, String[] args) {
        if (args == null || args.length == 0) return List.of("status", "reload", "setspawn", "clearspawn");
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            return List.of("status", "reload", "setspawn", "clearspawn").stream().filter(s -> s.startsWith(p)).toList();
        }
        return List.of();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return true;
    }

    @Override
    public String permission() {
        return null;
    }
}
