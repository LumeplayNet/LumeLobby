package de.felix.lumelobby.commands;

import de.felix.lumelobby.api.LumeLobbyApi;
import de.felix.lumecommands.ui.CommandUi;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

public final class HubCommand implements BasicCommand {

    private static final String PERM_ADMIN = "lumelobby.admin";

    private final LumeLobbyApi api;
    private final CommandUi ui = new CommandUi("Lobby");

    public HubCommand(LumeLobbyApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl benutzen.");
            return;
        }

        if (!api.hubEnabled()) {
            player.sendMessage("§cHub ist deaktiviert (config: hub.enabled=false).");
            return;
        }

        String action = (args == null || args.length == 0) ? "" : String.valueOf(args[0]).toLowerCase();
        if ("setspawn".equals(action) || "set".equals(action)) {
            if (!player.hasPermission(PERM_ADMIN)) {
                ui.noPermission(player, PERM_ADMIN);
                return;
            }
            api.setHubSpawnFrom(player);
            return;
        }
        if ("clearspawn".equals(action) || "clear".equals(action)) {
            if (!player.hasPermission(PERM_ADMIN)) {
                ui.noPermission(player, PERM_ADMIN);
                return;
            }
            api.clearHubSpawn(player);
            return;
        }

        if (api.handleHubCommand(player)) {
            ui.success(player, "Zum Hub.");
            return;
        }

        api.sendToHub(player);
        ui.success(player, "Zum Hub teleportiert.");
    }

    @Override
    public List<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        if (args == null || args.length == 0) return List.of("setspawn", "clearspawn");
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            return List.of("setspawn", "clearspawn").stream().filter(s -> s.startsWith(p)).toList();
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
