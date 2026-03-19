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

        // Spawn block display at player feet
        BlockDisplay display = loc.getWorld().spawn(loc.clone().add(-0.5, 0, -0.5), BlockDisplay.class, entity -> {
            BlockData data = blockMaterial.createBlockData();
            entity.setBlock(data);

            // Scale to 1x1x1 block size (default display size)
            Transformation transformation = new Transformation(
                    new Vector3f(0, 0, 0),          // translation
                    new AxisAngle4f(0, 0, 1, 0),     // left rotation
                    new Vector3f(1.0f, 1.0f, 1.0f),  // scale
                    new AxisAngle4f(0, 0, 1, 0)      // right rotation
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

                    Location currentLoc = player.getLocation();
                    Location lastLoc = lastLocations.get(uuid);

                    boolean moved = lastLoc == null ||
                            Math.abs(currentLoc.getX() - lastLoc.getX()) > 0.05 ||
                            Math.abs(currentLoc.getY() - lastLoc.getY()) > 0.05 ||
                            Math.abs(currentLoc.getZ() - lastLoc.getZ()) > 0.05;

                    if (moved) {
                        // Player moved - remove solid block if exists, reset still counter
                        removeSolidBlock(uuid);
                        stillTicks.put(uuid, 0);
                        lastLocations.put(uuid, currentLoc.clone());

                        // Move the block display to follow the player
                        display.teleport(currentLoc.clone().add(-0.5, 0, -0.5));

                        // Make sure display is visible
                        if (display.getViewRange() < 1.0f) {
                            display.setViewRange(1.0f);
                        }
                    } else {
                        // Player is standing still
                        int ticks = stillTicks.getOrDefault(uuid, 0) + 1;
                        stillTicks.put(uuid, ticks);

                        int requiredTicks = plugin.getConfigManager().getStillTimeTicks();
                        if (ticks >= requiredTicks && !solidBlocks.containsKey(uuid)) {
                            // Place a solid block
                            placeSolidBlock(player, uuid);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Place a real block at the player's location to make them look like part of the world.
     */
    private void placeSolidBlock(Player player, UUID uuid) {
        Material material = disguiseMaterials.get(uuid);
        if (material == null) return;

        Location blockLoc = player.getLocation().getBlock().getLocation();

        // Only place if the space is air
        if (blockLoc.getBlock().getType() == Material.AIR ||
            blockLoc.getBlock().getType() == Material.CAVE_AIR) {

            blockLoc.getBlock().setType(material);
            solidBlocks.put(uuid, blockLoc.clone());

            // Hide the block display (it's now a real block)
            BlockDisplay display = disguiseEntities.get(uuid);
            if (display != null && !display.isDead()) {
                display.setViewRange(0);
            }

            // Teleport player slightly into the block so they don't appear separate
            Location insideBlock = blockLoc.clone().add(0.5, 0, 0.5);
            insideBlock.setYaw(player.getLocation().getYaw());
            insideBlock.setPitch(player.getLocation().getPitch());
            player.teleport(insideBlock);
        }
    }

    /**
     * Remove the solid block placed for a player.
     */
    private void removeSolidBlock(UUID uuid) {
        Location solidLoc = solidBlocks.remove(uuid);
        if (solidLoc != null) {
            // Only remove if the block is still the disguise material
            Material expected = disguiseMaterials.get(uuid);
            if (expected != null && solidLoc.getBlock().getType() == expected) {
                solidLoc.getBlock().setType(Material.AIR);
            }
        }

        // Make block display visible again
        BlockDisplay display = disguiseEntities.get(uuid);
        if (display != null && !display.isDead()) {
            display.setViewRange(1.0f);
        }
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

        for (Map.Entry<UUID, Location> entry : solidBlocks.entrySet()) {
            Location loc = entry.getValue();
            Material expected = disguiseMaterials.get(entry.getKey());
            if (expected != null && loc.getBlock().getType() == expected) {
                loc.getBlock().setType(Material.AIR);
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
