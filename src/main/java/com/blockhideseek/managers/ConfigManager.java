package com.blockhideseek.managers;

import com.blockhideseek.BlockHideSeek;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigManager {

    private final BlockHideSeek plugin;

    private int gameTime;
    private int seekerCountdown;
    private int stillTimeTicks;
    private int trackerCooldown;
    private int trackerInaccuracy;
    private List<Material> allowedBlocks;
    private Location seekerSpawn;
    private Location gameSpawn;

    public ConfigManager(BlockHideSeek plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();

        gameTime = plugin.getConfig().getInt("game.game-time", 300);
        seekerCountdown = plugin.getConfig().getInt("game.seeker-countdown", 60);
        stillTimeTicks = plugin.getConfig().getInt("game.still-time-ticks", 30);
        trackerCooldown = plugin.getConfig().getInt("tracker.cooldown", 45);
        trackerInaccuracy = plugin.getConfig().getInt("tracker.inaccuracy-radius", 5);

        // Load allowed blocks
        allowedBlocks = new ArrayList<>();
        List<String> blockNames = plugin.getConfig().getStringList("allowed-blocks");
        for (String name : blockNames) {
            try {
                Material mat = Material.valueOf(name.toUpperCase());
                if (mat.isBlock()) {
                    allowedBlocks.add(mat);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid block material in config: " + name);
            }
        }

        if (allowedBlocks.isEmpty()) {
            allowedBlocks.add(Material.STONE);
            allowedBlocks.add(Material.OAK_PLANKS);
            allowedBlocks.add(Material.GRASS_BLOCK);
            plugin.getLogger().warning("No valid blocks in config, using defaults.");
        }

        // Load seeker spawn
        String worldName = plugin.getConfig().getString("seeker-spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) world = Bukkit.getWorlds().get(0);
        double x = plugin.getConfig().getDouble("seeker-spawn.x", 0);
        double y = plugin.getConfig().getDouble("seeker-spawn.y", 64);
        double z = plugin.getConfig().getDouble("seeker-spawn.z", 0);
        float yaw = (float) plugin.getConfig().getDouble("seeker-spawn.yaw", 0);
        float pitch = (float) plugin.getConfig().getDouble("seeker-spawn.pitch", 0);
        seekerSpawn = new Location(world, x, y, z, yaw, pitch);

        // Load game spawn (where hiders start and seekers tp to after countdown)
        String gsWorldName = plugin.getConfig().getString("game-spawn.world", "world");
        World gsWorld = Bukkit.getWorld(gsWorldName);
        if (gsWorld == null) gsWorld = Bukkit.getWorlds().get(0);
        double gsx = plugin.getConfig().getDouble("game-spawn.x", 0);
        double gsy = plugin.getConfig().getDouble("game-spawn.y", 64);
        double gsz = plugin.getConfig().getDouble("game-spawn.z", 0);
        float gsyaw = (float) plugin.getConfig().getDouble("game-spawn.yaw", 0);
        float gspitch = (float) plugin.getConfig().getDouble("game-spawn.pitch", 0);
        gameSpawn = new Location(gsWorld, gsx, gsy, gsz, gsyaw, gspitch);
    }

    public void save() {
        plugin.getConfig().set("game.game-time", gameTime);
        plugin.getConfig().set("game.seeker-countdown", seekerCountdown);
        plugin.getConfig().set("game.still-time-ticks", stillTimeTicks);
        plugin.getConfig().set("tracker.cooldown", trackerCooldown);
        plugin.getConfig().set("tracker.inaccuracy-radius", trackerInaccuracy);

        List<String> blockNames = allowedBlocks.stream()
                .map(Material::name)
                .collect(Collectors.toList());
        plugin.getConfig().set("allowed-blocks", blockNames);

        plugin.getConfig().set("seeker-spawn.world", seekerSpawn.getWorld().getName());
        plugin.getConfig().set("seeker-spawn.x", seekerSpawn.getX());
        plugin.getConfig().set("seeker-spawn.y", seekerSpawn.getY());
        plugin.getConfig().set("seeker-spawn.z", seekerSpawn.getZ());
        plugin.getConfig().set("seeker-spawn.yaw", seekerSpawn.getYaw());
        plugin.getConfig().set("seeker-spawn.pitch", seekerSpawn.getPitch());

        plugin.getConfig().set("game-spawn.world", gameSpawn.getWorld().getName());
        plugin.getConfig().set("game-spawn.x", gameSpawn.getX());
        plugin.getConfig().set("game-spawn.y", gameSpawn.getY());
        plugin.getConfig().set("game-spawn.z", gameSpawn.getZ());
        plugin.getConfig().set("game-spawn.yaw", gameSpawn.getYaw());
        plugin.getConfig().set("game-spawn.pitch", gameSpawn.getPitch());

        plugin.saveConfig();
    }

    // Getters and setters

    public int getGameTime() { return gameTime; }
    public void setGameTime(int gameTime) { this.gameTime = gameTime; }

    public int getSeekerCountdown() { return seekerCountdown; }
    public void setSeekerCountdown(int seekerCountdown) { this.seekerCountdown = seekerCountdown; }

    public int getStillTimeTicks() { return stillTimeTicks; }

    public int getTrackerCooldown() { return trackerCooldown; }
    public void setTrackerCooldown(int trackerCooldown) { this.trackerCooldown = trackerCooldown; }

    public int getTrackerInaccuracy() { return trackerInaccuracy; }

    public List<Material> getAllowedBlocks() { return allowedBlocks; }

    public void addBlock(Material material) {
        if (!allowedBlocks.contains(material)) {
            allowedBlocks.add(material);
            save();
        }
    }

    public boolean removeBlock(Material material) {
        boolean removed = allowedBlocks.remove(material);
        if (removed) save();
        return removed;
    }

    public Location getSeekerSpawn() { return seekerSpawn; }

    public void setSeekerSpawn(Location location) {
        this.seekerSpawn = location.clone();
        save();
    }

    public Location getGameSpawn() { return gameSpawn; }

    public void setGameSpawn(Location location) {
        this.gameSpawn = location.clone();
        save();
    }
}
