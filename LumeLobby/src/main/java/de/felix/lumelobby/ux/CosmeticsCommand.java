package de.felix.lumelobby.ux;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class CosmeticsCommand implements BasicCommand {

    private final Supplier<HubUxManager> hubUx;

    public CosmeticsCommand(Supplier<HubUxManager> hubUx) {
        this.hubUx = Objects.requireNonNull(hubUx, "hubUx");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl benutzen.");
            return;
        }
        HubUxManager ux = hubUx.get();
        if (ux == null) return;
        ux.cosmeticsManager().openMenu(player);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
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
