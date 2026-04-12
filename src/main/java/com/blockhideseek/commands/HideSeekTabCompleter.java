package com.blockhideseek.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HideSeekTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "start", "stop", "spectate", "setseekerspawn", "setgamespawn", "addblock", "removeblock",
            "blocks", "settime", "setcountdown", "setcooldown", "reload"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            completions = SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        } else if (args.length >= 2) {
            String sub = args[0].toLowerCase();
            String partial = args[args.length - 1].toUpperCase();

            switch (sub) {
                case "start", "spectate" -> {
                    // Suggest player names
                    completions = Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toUpperCase().startsWith(partial))
                            .collect(Collectors.toList());
                }
                case "addblock" -> {
                    completions = new ArrayList<>();
                    if ("ALL".startsWith(partial)) completions.add("all");
                    Arrays.stream(Material.values())
                            .filter(Material::isBlock)
                            .map(Material::name)
                            .filter(n -> n.startsWith(partial))
                            .limit(30)
                            .forEach(completions::add);
                }
                case "removeblock" -> {
                    // Suggest block materials
                    completions = Arrays.stream(Material.values())
                            .filter(Material::isBlock)
                            .map(Material::name)
                            .filter(n -> n.startsWith(partial))
                            .limit(30) // Limit to prevent lag
                            .collect(Collectors.toList());
                }
                case "settime" -> completions = Arrays.asList("60", "120", "180", "300", "600");
                case "setcountdown" -> completions = Arrays.asList("30", "45", "60", "90", "120");
                case "setcooldown" -> completions = Arrays.asList("15", "30", "45", "60", "90");
            }
        }

        return completions;
    }
}
