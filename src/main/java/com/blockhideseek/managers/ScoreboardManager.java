package com.blockhideseek.managers;

import com.blockhideseek.BlockHideSeek;
import com.blockhideseek.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import org.bukkit.scoreboard.Team;

import java.util.UUID;

public class ScoreboardManager {

    private final BlockHideSeek plugin;
    private Scoreboard scoreboard;
    private Objective objective;
    private Team hiderTeam;
    private Team seekerTeam;
    private Team spectatorTeam;

    public ScoreboardManager(BlockHideSeek plugin) {
        this.plugin = plugin;
    }

    public void setupScoreboard(GameManager gameManager) {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = scoreboard.registerNewObjective(
                "hideseek",
                Criteria.DUMMY,
                Component.text("Block Hide & Seek", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Create tab list teams
        hiderTeam = scoreboard.registerNewTeam("hiders");
        hiderTeam.color(NamedTextColor.GREEN);
        hiderTeam.prefix(Component.text("[Hider] ", NamedTextColor.GREEN));

        seekerTeam = scoreboard.registerNewTeam("seekers");
        seekerTeam.color(NamedTextColor.RED);
        seekerTeam.prefix(Component.text("[Seeker] ", NamedTextColor.RED));

        spectatorTeam = scoreboard.registerNewTeam("spectators");
        spectatorTeam.color(NamedTextColor.GRAY);
        spectatorTeam.prefix(Component.text("[Spectator] ", NamedTextColor.GRAY));

        // Assign players to teams based on their roles
        for (Player player : gameManager.getPlayersInGame()) {
            assignTeam(player, gameManager.getRole(player));
        }

        updateScoreboard(gameManager);

        // Apply to all players in game
        for (Player player : gameManager.getPlayersInGame()) {
            player.setScoreboard(scoreboard);
        }
    }

    /**
     * Assign a player to the correct tab list team based on their role.
     */
    public void assignTeam(Player player, com.blockhideseek.PlayerRole role) {
        if (scoreboard == null) return;
        if (role == null) return;

        // Remove from all teams first
        if (hiderTeam != null) hiderTeam.removeEntry(player.getName());
        if (seekerTeam != null) seekerTeam.removeEntry(player.getName());
        if (spectatorTeam != null) spectatorTeam.removeEntry(player.getName());

        switch (role) {
            case HIDER -> { if (hiderTeam != null) hiderTeam.addEntry(player.getName()); }
            case SEEKER -> { if (seekerTeam != null) seekerTeam.addEntry(player.getName()); }
            case SPECTATOR -> { if (spectatorTeam != null) spectatorTeam.addEntry(player.getName()); }
        }
    }

    /**
     * Remove a player from all teams.
     */
    public void removeFromTeams(Player player) {
        if (scoreboard == null) return;
        if (hiderTeam != null) hiderTeam.removeEntry(player.getName());
        if (seekerTeam != null) seekerTeam.removeEntry(player.getName());
        if (spectatorTeam != null) spectatorTeam.removeEntry(player.getName());
    }

    public void updateScoreboard(GameManager gameManager) {
        if (scoreboard == null || objective == null) return;

        // Clear old entries
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        int timeRemaining = gameManager.getTimeRemaining();
        int minutes = timeRemaining / 60;
        int seconds = timeRemaining % 60;
        String timeStr = String.format("%02d:%02d", minutes, seconds);

        GameState state = gameManager.getState();
        String stateStr = switch (state) {
            case HIDING -> "§eHiding Phase";
            case PLAYING -> "§cHunting Phase";
            default -> "§7Waiting";
        };

        // Build scoreboard lines (higher score = higher position)
        int line = 9;
        objective.getScore("§e§l").setScore(line--);
        objective.getScore(stateStr).setScore(line--);
        objective.getScore("§f").setScore(line--);

        // Show seeker countdown during hiding phase
        if (state == GameState.HIDING) {
            int countdown = gameManager.getSeekerCountdownRemaining();
            objective.getScore("§cSeekers out in: §e" + countdown + "s").setScore(line--);
            objective.getScore("§8").setScore(line--);
        }

        objective.getScore("§fTime Left: §a" + timeStr).setScore(line--);
        objective.getScore("§r").setScore(line--);
        objective.getScore("§aHiders: §f" + gameManager.getHiderCount()).setScore(line--);
        objective.getScore("§cSeekers: §f" + gameManager.getSeekerCount()).setScore(line--);
        if (gameManager.getSpectatorCount() > 0) {
            objective.getScore("§7Spectators: §f" + gameManager.getSpectatorCount()).setScore(line--);
        }
        objective.getScore("§r§r").setScore(line--);
        objective.getScore("§6blockhideseek").setScore(line);
    }

    public void removeScoreboard() {
        if (scoreboard == null) return;

        // Reset all players to the main scoreboard
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getScoreboard() == scoreboard) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }

        scoreboard = null;
        objective = null;
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }
}
