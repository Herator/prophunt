package com.blockhideseek.managers;

import com.blockhideseek.BlockHideSeek;
import com.blockhideseek.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.UUID;

public class ScoreboardManager {

    private final BlockHideSeek plugin;
    private Scoreboard scoreboard;
    private Objective objective;

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

        updateScoreboard(gameManager);

        // Apply to all players in game
        for (Player player : gameManager.getPlayersInGame()) {
            player.setScoreboard(scoreboard);
        }
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
