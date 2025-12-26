package de.felix.lumelobby.api;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface HubCommandHook {

    /**
     * @return true if the hub command was handled (e.g. player left a gamemode and was teleported).
     */
    boolean onHubCommand(Player player);
}

