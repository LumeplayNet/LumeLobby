package de.felix.lumelobby.motd;

import de.felix.lumelobby.config.LobbyConfig;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.List;

@RequiredArgsConstructor
public final class MotdListener implements Listener {

    private final LobbyConfig config;

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (config.motd() == null || !config.motd().enabled()) return;

        String text = renderMotd();
        if (text == null || text.isBlank()) return;

        trySetAdventureMotd(event, text);
        event.setMotd(text);
    }

    private String renderMotd() {
        List<String> lines = config.motd().lines();
        if (lines == null || lines.isEmpty()) {
            lines = List.of(
                "&bLumeplay.net &7| &fSkyWars",
                "&7Online: &f%online%&7/&f%max% &8- &bplay.lumeplay.net"
            );
        }

        String joined = String.join("\n", lines);
        joined = joined.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        joined = joined.replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));
        return colorize(joined);
    }

    private static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static void trySetAdventureMotd(ServerListPingEvent event, String legacy) {
        try {
            Component c = LegacyComponentSerializer.legacySection().deserialize(legacy);
            event.getClass().getMethod("motd", Component.class).invoke(event, c);
        } catch (Throwable ignored) {
        }
    }
}
