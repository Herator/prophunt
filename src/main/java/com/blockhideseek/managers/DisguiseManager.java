package com.blockhideseek.managers;

import com.blockhideseek.BlockHideSeek;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class DisguiseManager {

    private final BlockHideSeek plugin;

    // Player UUID -> their block display entity
    private final Map<UUID, BlockDisplay> disguiseEntities = new HashMap<>();
    // Player UUID -> what material they're disguised as
    private final Map<UUID, Material> disguiseMaterials = new HashMap<>();
    // Player UUID -> the location of the "solid" block placed when still
    private final Map<UUID, Location> solidBlocks = new HashMap<>();
    // Player UUID -> ticks they've been still
    private final Map<UUID, Integer> stillTicks = new HashMap<>();
    // Player UUID -> last known location
    private final Map<UUID, Location> lastLocations = new HashMap<>();

    private BukkitTask trackingTask;

    public DisguiseManager(BlockHideSeek plugin) {
        this.plugin = plugin;
    }

    /**
     * Disguise a player as a block.
     */
    public void disguisePlayer(Player player, Material blockMaterial) {
        // Remove any existing disguise
        removeDisguise(player);

        UUID uuid = player.getUniqueId();
        disguiseMaterials.put(uuid, blockMaterial);

        // Make the player invisible
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));

        // Spawn a block display entity at the player's location
        spawnBlockDisplay(player, blockMaterial);

        // Start tracking if not already
        startTracking();

        lastLocations.put(uuid, player.getLocation().clone());
        stillTicks.put(uuid, 0);

        player.sendMessage(net.kyori.adventure.text.Component.text(
                "You are now disguised as " + formatName(blockMaterial) + "!",
                net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    private void spawnBlockDisplay(Player player, Material blockMaterial) {
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation().clone();

        // Remove old display if exists
        BlockDisplay old = disguiseEntities.get(uuid);
        if (old != null && !old.isDead()) {
            old.remove();
        }

        // Spawn block display at player feet, offset so the block centers on the player
        Location spawnLoc = loc.clone().add(-0.5, 0, -0.5);
        spawnLoc.setYaw(0);
        spawnLoc.setPitch(0);

        BlockDisplay display = spawnLoc.getWorld().spawn(spawnLoc, BlockDisplay.class, entity -> {
            BlockData data = blockMaterial.createBlockData();
            entity.setBlock(data);

            // No translation — we handle the offset in teleport positions instead
            Transformation transformation = new Transformation(
                    new Vector3f(0f, 0f, 0f),          // no translation
                    new AxisAngle4f(0, 0, 0, 1),       // no rotation
                    new Vector3f(1.0f, 1.0f, 1.0f),    // scale
                    new AxisAngle4f(0, 0, 0, 1)        // no rotation
            );
            entity.setTransformation(transformation);
            entity.setShadowRadius(0);
            entity.setShadowStrength(0);
        });

        disguiseEntities.put(uuid, display);
    }

    /**
     * Start the repeating task that moves block displays with players
     * and handles the "stand still = solid block" mechanic.
     */
    private void startTracking() {
        if (trackingTask != null) return;

        trackingTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (disguiseEntities.isEmpty()) {
                    cancel();
                    trackingTask = null;
                    return;
                }

                for (Map.Entry<UUID, BlockDisplay> entry : new HashMap<>(disguiseEntities).entrySet()) {
                    UUID uuid = entry.getKey();
                    BlockDisplay display = entry.getValue();
                    Player player = Bukkit.getPlayer(uuid);

                    if (player == null || !player.isOnline() || display.isDead()) {
                        continue;
                    }

                    // If locked (snapped to grid), check if player wants to break out
                    if (solidBlocks.containsKey(uuid)) {
                        Location currentLoc = player.getLocation();
                        Location lastLoc = lastLocations.get(uuid);

                        boolean moved = lastLoc == null ||
                                Math.abs(currentLoc.getX() - lastLoc.getX()) > 0.05 ||
                                Math.abs(currentLoc.getY() - lastLoc.getY()) > 0.05 ||
                                Math.abs(currentLoc.getZ() - lastLoc.getZ()) > 0.05;

                        // Player moved or sneaked - break out of solid mode
                        if (moved || player.isSneaking()) {
                            removeSolidBlock(uuid);
                            lastLocations.put(uuid, currentLoc.clone());

                            // Snap display back to following the player (offset to center)
                            Location displayLoc = currentLoc.clone().add(-0.5, 0, -0.5);
                            displayLoc.setYaw(0);
                            displayLoc.setPitch(0);
                            display.teleport(displayLoc);
                        }
                        continue;
                    }

                    Location currentLoc = player.getLocation();
                    Location lastLoc = lastLocations.get(uuid);

                    boolean moved = lastLoc == null ||
                            Math.abs(currentLoc.getX() - lastLoc.getX()) > 0.05 ||
                            Math.abs(currentLoc.getY() - lastLoc.getY()) > 0.05 ||
                            Math.abs(currentLoc.getZ() - lastLoc.getZ()) > 0.05;

                    if (moved) {
                        // Player moved - reset still counter
                        stillTicks.put(uuid, 0);
                        lastLocations.put(uuid, currentLoc.clone());

                        // Move the block display to follow the player (offset to center)
                        Location displayLoc = currentLoc.clone().add(-0.5, 0, -0.5);
                        displayLoc.setYaw(0);
                        displayLoc.setPitch(0);
                        display.teleport(displayLoc);
                    } else {
                        // Player is standing still
                        int ticks = stillTicks.getOrDefault(uuid, 0) + 1;
                        stillTicks.put(uuid, ticks);

                        int requiredTicks = plugin.getConfigManager().getStillTimeTicks();
                        if (ticks >= requiredTicks && !solidBlocks.containsKey(uuid)) {
                            // Snap the display entity to the grid (looks like a placed block)
                            snapToGrid(player, uuid, display);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Snap the block display entity to the grid so it looks like a placed block.
     * No real block is placed — it's just the entity positioned on the block grid.
     */
    private void snapToGrid(Player player, UUID uuid, BlockDisplay display) {
        // Get the block position the player is standing in
        Location blockLoc = player.getLocation().getBlock().getLocation();

        // Snap the display entity to the grid position
        Location gridLoc = blockLoc.clone();
        gridLoc.setYaw(0);
        gridLoc.setPitch(0);
        display.teleport(gridLoc);

        // Mark as solid (snapped to grid)
        solidBlocks.put(uuid, blockLoc.clone());

        player.sendMessage(net.kyori.adventure.text.Component.text(
                "You blended in! Move or Sneak to break out.",
                net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    /**
     * Remove the solid block placed for a player.
     */
    private void removeSolidBlock(UUID uuid) {
        solidBlocks.remove(uuid);

        // Reset still counter so they don't instantly re-solidify
        stillTicks.put(uuid, 0);
    }

    /**
     * Break a hider out of solid block mode back to following mode.
     * The hider keeps their disguise but the real block is removed and they can move again.
     */
    public void breakSolidBlock(Player player) {
        UUID uuid = player.getUniqueId();
        removeSolidBlock(uuid);
        lastLocations.put(uuid, player.getLocation().clone());

        player.sendMessage(net.kyori.adventure.text.Component.text(
                "Your block was broken! RUN!",
                net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    /**
     * Remove a player's disguise completely.
     */
    public void removeDisguise(Player player) {
        UUID uuid = player.getUniqueId();

        // Remove block display
        BlockDisplay display = disguiseEntities.remove(uuid);
        if (display != null && !display.isDead()) {
            display.remove();
        }

        // Remove solid block
        removeSolidBlock(uuid);

        // Remove materials
        disguiseMaterials.remove(uuid);
        stillTicks.remove(uuid);
        lastLocations.remove(uuid);

        // Remove invisibility
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    /**
     * Check if a player is disguised.
     */
    public boolean isDisguised(Player player) {
        return disguiseMaterials.containsKey(player.getUniqueId());
    }

    /**
     * Check if a player is currently in solid (grid-snapped) mode.
     */
    public boolean isSolid(Player player) {
        return solidBlocks.containsKey(player.getUniqueId());
    }

    /**
     * Get the material a player is disguised as.
     */
    public Material getDisguiseMaterial(Player player) {
        return disguiseMaterials.get(player.getUniqueId());
    }

    /**
     * Check if a location has a solid disguise block.
     */
    public Player getHiderAtSolidBlock(Location blockLocation) {
        for (Map.Entry<UUID, Location> entry : solidBlocks.entrySet()) {
            Location solidLoc = entry.getValue();
            if (solidLoc.getBlockX() == blockLocation.getBlockX() &&
                solidLoc.getBlockY() == blockLocation.getBlockY() &&
                solidLoc.getBlockZ() == blockLocation.getBlockZ() &&
                solidLoc.getWorld().equals(blockLocation.getWorld())) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    return player;
                }
            }
        }
        return null;
    }

    /**
     * Clean up all disguises.
     */
    public void cleanupAll() {
        for (Map.Entry<UUID, BlockDisplay> entry : disguiseEntities.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isDead()) {
                entry.getValue().remove();
            }
        }

        // Remove invisibility from all disguised players
        for (UUID uuid : disguiseMaterials.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
        }

        disguiseEntities.clear();
        disguiseMaterials.clear();
        solidBlocks.clear();
        stillTicks.clear();
        lastLocations.clear();

        if (trackingTask != null) {
            trackingTask.cancel();
            trackingTask = null;
        }
    }

    private String formatName(Material material) {
        String name = material.name().replace('_', ' ').toLowerCase();
        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (capitalize) {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                result.append(c);
            }
            if (c == ' ') capitalize = true;
        }
        return result.toString();
    }
}
