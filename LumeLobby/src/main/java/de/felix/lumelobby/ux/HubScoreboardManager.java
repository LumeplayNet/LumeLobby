package de.felix.lumelobby.ux;

import de.felix.lumelobby.config.LobbyConfig;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
final class HubScoreboardManager {

    private static final String OBJECTIVE_NAME = "lumehub";
    private static final int MAX_LINES = 15;
    private static final int MAX_PART = 64;

    private final Plugin plugin;
    private final LobbyConfig config;
    private final Map<UUID, PlayerBoard> boards = new ConcurrentHashMap<>();
    private final List<ChatColor> lineColors = Arrays.stream(ChatColor.values()).filter(ChatColor::isColor).toList();

    void tick(Iterable<? extends Player> onlinePlayers, HubWorldPredicate isInHubWorld) {
        if (!enabled()) {
            clearAll();
            return;
        }

        for (Player player : onlinePlayers) {
            if (player == null) continue;
            boolean inHub = isInHubWorld.isInHub(player);
            if (!inHub) {
                removeIfOwned(player);
                continue;
            }
            ensureAndUpdate(player);
        }
    }

    void refresh(Player player, boolean inHub) {
        if (player == null) return;
        if (!enabled() || !inHub) {
            removeIfOwned(player);
            return;
        }
        ensureAndUpdate(player);
    }

    void clearAll() {
        for (UUID id : new ArrayList<>(boards.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) removeIfOwned(player);
            else boards.remove(id);
        }
    }

    private boolean enabled() {
        return config.hubUx().enabled() && config.hubUx().scoreboard().enabled();
    }

    private void ensureAndUpdate(Player player) {
        PlayerBoard existing = boards.get(player.getUniqueId());
        if (existing != null) {
            if (player.getScoreboard() != existing.scoreboard()) {
                boards.remove(player.getUniqueId());
                return;
            }
            updateLines(player, existing);
            return;
        }

        Scoreboard current = player.getScoreboard();

        Scoreboard board = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getNewScoreboard();
        if (board == null) return;

        Objective objective;
        try {
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, colorize(config.hubUx().scoreboard().title()));
        } catch (IllegalArgumentException already) {
            objective = board.getObjective(OBJECTIVE_NAME);
        }
        if (objective == null) return;
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> entries = new ArrayList<>();
        Map<Integer, Team> teams = new LinkedHashMap<>();
        for (int i = 0; i < MAX_LINES; i++) {
            String entry = entryForLine(i);
            entries.add(entry);
            Team team = board.getTeam(teamName(i));
            if (team == null) team = board.registerNewTeam(teamName(i));
            team.addEntry(entry);
            teams.put(i, team);
        }

        PlayerBoard created = new PlayerBoard(board, objective, current, entries, teams);
        boards.put(player.getUniqueId(), created);
        player.setScoreboard(board);
        updateLines(player, created);
    }

    private void updateLines(Player player, PlayerBoard pb) {
        List<String> lines = renderLines(player);
        for (int i = 0; i < MAX_LINES; i++) {
            String entry = pb.entries().get(i);
            Team team = pb.teams().get(i);
            if (team == null) continue;

            if (i < lines.size()) {
                String line = lines.get(i);
                var parts = splitForTeam(line);
                team.setPrefix(parts.prefix());
                team.setSuffix(parts.suffix());
                pb.objective().getScore(entry).setScore(MAX_LINES - i);
            } else {
                team.setPrefix("");
                team.setSuffix("");
                pb.scoreboard().resetScores(entry);
            }
        }
    }

    private List<String> renderLines(Player player) {
        List<String> raw = config.hubUx().scoreboard().lines();
        if (raw == null || raw.isEmpty()) {
            raw = List.of(
                "&b&lLumeplay",
                "&7%date% &8• &7%time%",
                "&7",
                "&7Name: &f%player%",
                "&7Online: &f%online%&7/&f%max%",
                "&7Ping: &f%ping%ms",
                "&7TPS: &f%tps% &8(&f%mspt%mspt&8)",
                "&7",
                "&b/sw join &8- &7Quick Play",
                "&7Doublejump: &fSpace x2",
                "&7",
                "&bplay.lumeplay.net"
            );
        }

        List<String> out = new ArrayList<>();
        for (String line : raw) {
            if (line == null) line = "";
            String replaced = applyPlaceholders(line, player);
            out.add(colorize(replaced));
            if (out.size() >= MAX_LINES) break;
        }
        return out;
    }

    private String applyPlaceholders(String line, Player player) {
        String out = line;
        out = out.replace("%player%", player.getName());
        out = out.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        out = out.replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));
        out = out.replace("%ping%", String.valueOf(player.getPing()));
        out = out.replace("%world%", player.getWorld() == null ? "" : player.getWorld().getName());
        out = out.replace("%time%", java.time.LocalTime.now().withNano(0).toString());
        out = out.replace("%date%", java.time.LocalDate.now().toString());
        out = out.replace("%tps%", formatTps());
        out = out.replace("%mspt%", formatMspt());
        return out;
    }

    private static String formatTps() {
        try {
            double[] tps = Bukkit.getTPS();
            if (tps == null || tps.length == 0) return "20.0";
            return String.format(java.util.Locale.ROOT, "%.1f", Math.min(20.0, tps[0]));
        } catch (Throwable ignored) {
            return "20.0";
        }
    }

    private static String formatMspt() {
        try {
            double[] tps = Bukkit.getTPS();
            if (tps == null || tps.length == 0) return "50.0";
            double v = tps[0] <= 0 ? 50.0 : (1000.0 / tps[0]);
            return String.format(java.util.Locale.ROOT, "%.1f", v);
        } catch (Throwable ignored) {
            return "50.0";
        }
    }

    private void removeIfOwned(Player player) {
        PlayerBoard pb = boards.remove(player.getUniqueId());
        if (pb == null) return;
        if (player.getScoreboard() != pb.scoreboard()) return;
        player.setScoreboard(pb.previousScoreboard());
    }

    private String entryForLine(int i) {
        if (i >= 0 && i < lineColors.size()) return lineColors.get(i).toString();
        return ChatColor.COLOR_CHAR + Integer.toHexString(i % 16);
    }

    private static String teamName(int i) {
        return "lh" + i;
    }

    private static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static TeamParts splitForTeam(String input) {
        String text = input == null ? "" : input;
        if (text.length() <= MAX_PART) return new TeamParts(text, "");

        String prefix = text.substring(0, MAX_PART);
        if (prefix.endsWith(String.valueOf(ChatColor.COLOR_CHAR))) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }

        String remaining = text.substring(prefix.length());
        String colors = ChatColor.getLastColors(prefix);
        String suffixRaw = colors + remaining;
        String suffix = suffixRaw.length() <= MAX_PART ? suffixRaw : suffixRaw.substring(0, MAX_PART);
        if (suffix.endsWith(String.valueOf(ChatColor.COLOR_CHAR))) {
            suffix = suffix.substring(0, suffix.length() - 1);
        }
        return new TeamParts(prefix, suffix);
    }

    private record PlayerBoard(
        Scoreboard scoreboard,
        Objective objective,
        Scoreboard previousScoreboard,
        List<String> entries,
        Map<Integer, Team> teams
    ) {}

    @FunctionalInterface
    interface HubWorldPredicate {
        boolean isInHub(Player player);
    }

    private record TeamParts(String prefix, String suffix) {}
}
