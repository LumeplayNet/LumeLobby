package de.felix.lumelobby.api;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface AutoTeleportBypass {
    boolean bypassAutoTeleport(Player player);
}

