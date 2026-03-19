package com.blockhideseek.commands;

import com.blockhideseek.BlockHideSeek;
import com.blockhideseek.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class HideSeekCommand implements CommandExecutor {

    private final BlockHideSeek plugin;

    public HideSeekCommand(BlockHideSeek plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender);
            case "setseekerspawn" -> handleSetSeekerSpawn(sender);
            case "addblock" -> handleAddBlock(sender, args);
            case "removeblock" -> handleRemoveBlock(sender, args);
            case "blocks" -> handleListBlocks(sender);
            case "settime" -> handleSetTime(sender, args);
            case "setcountdown" -> handleSetCountdown(sender, args);
            case "setcooldown" -> handleSetCooldown(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    /**
     * /hs start <seeker1> [seeker2...] — Everyone else online becomes a hider.
     */
    private void handleStart(CommandSender sender, String[] args) {
        if (plugin.getGameManager().getState() != GameState.WAITING) {
            sender.sendMessage(Component.text("A game is already running! Use /hs stop first.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /hs start <seeker1> [seeker2...]", NamedTextColor.RED));
            sender.sendMessage(Component.text("All other online players will be hiders.", NamedTextColor.GRAY));
            return;
        }

        // Parse seekers
        List<Player> seekers = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Player seeker = Bukkit.getPlayer(args[i]);
            if (seeker == null || !seeker.isOnline()) {
                sender.sendMessage(Component.text("Player not found: " + args[i], NamedTextColor.RED));
                return;
            }
            seekers.add(seeker);
        }

        // Everyone else is a hider
        List<Player> hiders = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!seekers.contains(online)) {
                hiders.add(online);
            }
        }

        if (hiders.isEmpty()) {
            sender.sendMessage(Component.text("There are no players to be hiders!", NamedTextColor.RED));
            return;
        }

        if (seekers.isEmpty()) {
            sender.sendMessage(Component.text("You need at least one seeker!", NamedTextColor.RED));
            return;
        }

        boolean started = plugin.getGameManager().startGame(seekers, hiders);
        if (!started) {
            sender.sendMessage(Component.text("Failed to start game.", NamedTextColor.RED));
        }
    }

    private void handleStop(CommandSender sender) {
        if (plugin.getGameManager().getState() == GameState.WAITING) {
            sender.sendMessage(Component.text("No game is currently running.", NamedTextColor.RED));
            return;
        }
        plugin.getGameManager().forceStop();
        sender.sendMessage(Component.text("Game stopped!", NamedTextColor.GREEN));
    }

    private void handleSetSeekerSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        plugin.getConfigManager().setSeekerSpawn(player.getLocation());
        sender.sendMessage(Component.text("Seeker spawn set to your current location!", NamedTextColor.GREEN));
    }

    private void handleAddBlock(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /hs addblock <material>", NamedTextColor.RED));
            return;
        }
        try {
            Material mat = Material.valueOf(args[1].toUpperCase());
            if (!mat.isBlock()) {
                sender.sendMessage(Component.text(args[1] + " is not a block!", NamedTextColor.RED));
                return;
            }
            plugin.getConfigManager().addBlock(mat);
            sender.sendMessage(Component.text("Added " + mat.name() + " to the allowed blocks list!",
                    NamedTextColor.GREEN));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown material: " + args[1], NamedTextColor.RED));
        }
    }

    private void handleRemoveBlock(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /hs removeblock <material>", NamedTextColor.RED));
            return;
        }
        try {
            Material mat = Material.valueOf(args[1].toUpperCase());
            if (plugin.getConfigManager().removeBlock(mat)) {
                sender.sendMessage(Component.text("Removed " + mat.name() + " from the allowed blocks list!",
                        NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text(mat.name() + " is not in the allowed blocks list.",
                        NamedTextColor.YELLOW));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown material: " + args[1], NamedTextColor.RED));
        }
    }

    private void handleListBlocks(CommandSender sender) {
        List<Material> blocks = plugin.getConfigManager().getAllowedBlocks();
        sender.sendMessage(Component.text("Allowed Blocks (" + blocks.size() + "):",
                NamedTextColor.GOLD, TextDecoration.BOLD));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(blocks.get(i).name());
        }
        sender.sendMessage(Component.text(sb.toString(), NamedTextColor.YELLOW));
    }

    private void handleSetTime(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /hs settime <seconds>", NamedTextColor.RED));
            return;
        }
        try {
            int time = Integer.parseInt(args[1]);
            if (time < 30) {
                sender.sendMessage(Component.text("Game time must be at least 30 seconds.", NamedTextColor.RED));
                return;
            }
            plugin.getConfigManager().setGameTime(time);
            plugin.getConfigManager().save();
            sender.sendMessage(Component.text("Game time set to " + time + " seconds!", NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number: " + args[1], NamedTextColor.RED));
        }
    }

    private void handleSetCountdown(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /hs setcountdown <seconds>", NamedTextColor.RED));
            return;
        }
        try {
            int time = Integer.parseInt(args[1]);
            if (time < 5) {
                sender.sendMessage(Component.text("Countdown must be at least 5 seconds.", NamedTextColor.RED));
                return;
            }
            plugin.getConfigManager().setSeekerCountdown(time);
            plugin.getConfigManager().save();
            sender.sendMessage(Component.text("Seeker countdown set to " + time + " seconds!", NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number: " + args[1], NamedTextColor.RED));
        }
    }

    private void handleSetCooldown(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /hs setcooldown <seconds>", NamedTextColor.RED));
            return;
        }
        try {
            int time = Integer.parseInt(args[1]);
            if (time < 1) {
                sender.sendMessage(Component.text("Cooldown must be at least 1 second.", NamedTextColor.RED));
                return;
            }
            plugin.getConfigManager().setTrackerCooldown(time);
            plugin.getConfigManager().save();
            sender.sendMessage(Component.text("Tracker cooldown set to " + time + " seconds!", NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number: " + args[1], NamedTextColor.RED));
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reload();
        sender.sendMessage(Component.text("Configuration reloaded!", NamedTextColor.GREEN));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Block Hide and Seek ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/hs start <seeker1> [seeker2...] ", NamedTextColor.YELLOW)
                .append(Component.text("- Start a game", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/hs stop ", NamedTextColor.YELLOW)
                .append(Component.text("- Stop the current game", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/hs setseekerspawn ", NamedTextColor.YELLOW)
                .append(Component.text("- Set seeker spawn point", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/hs addblock <material> ", NamedTextColor.YELLOW)
                .append(Component.text("- Add a block to disguise list", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/hs removeblock <material> ", NamedTextColor.YELLOW)
                .append(Component.text("- Remove a block from disguise list", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/hs blocks ", NamedTextColor.YELLOW)
                .append(Component.text("- List all allowed blocks", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/hs settime <seconds> ", NamedTextColor.YELLOW)
                .append(Component.text("- Set game duration", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/hs setcountdown <seconds> ", NamedTextColor.YELLOW)
                .append(Component.text("- Set seeker release countdown", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/hs setcooldown <seconds> ", NamedTextColor.YELLOW)
                .append(Component.text("- Set tracker cooldown", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/hs reload ", NamedTextColor.YELLOW)
                .append(Component.text("- Reload config", NamedTextColor.GRAY)));
    }
}
