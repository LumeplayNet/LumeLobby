package de.felix.lumelobby.api;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface LobbyJoinRouter {
    boolean shouldTeleportToLobby(Player player);
}

