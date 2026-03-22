package com.blockhideseek.listeners;

import com.blockhideseek.BlockHideSeek;
import com.blockhideseek.GameState;
import com.blockhideseek.PlayerRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class PlayerListener implements Listener {

    private final BlockHideSeek plugin;

    public PlayerListener(BlockHideSeek plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle hiders selecting a block disguise by right-clicking blocks in their hotbar.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getGameManager().isInGame(player)) return;

        GameState state = plugin.getGameManager().getState();
        PlayerRole role = plugin.getGameManager().getRole(player);

        // Handle hider using disguise wand
        if (role == PlayerRole.HIDER &&
            (state == GameState.HIDING || state == GameState.PLAYING)) {

            ItemStack item = event.getItem();
            if (item != null && plugin.getGameManager().isDisguiseWand(item)) {
                if (event.getAction().name().contains("RIGHT")) {
                    event.setCancelled(true);

                    // Raycast to find the block the player is looking at
                    org.bukkit.block.Block targetBlock = player.getTargetBlockExact(5);

                    if (targetBlock != null && targetBlock.getType() != Material.AIR) {
                        // Looking at a block — disguise as it
                        Material blockMat = targetBlock.getType();
                        java.util.List<Material> allowed = plugin.getConfigManager().getAllowedBlocks();
                        if (allowed.contains(blockMat)) {
                            plugin.getDisguiseManager().disguisePlayer(player, blockMat);
                        } else {
                            player.sendMessage(Component.text("That block is not in the allowed list!",
                                    NamedTextColor.RED));
                        }
                    } else {
                        // Looking at air — remove disguise
                        if (plugin.getDisguiseManager().isDisguised(player)) {
                            plugin.getDisguiseManager().removeDisguise(player);
                            player.sendMessage(Component.text("Disguise removed!",
                                    NamedTextColor.YELLOW));
                        } else {
                            player.sendMessage(Component.text("Look at a block to disguise as it!",
                                    NamedTextColor.GRAY));
                        }
                    }
                }
            }
        }

        // Handle seeker using tracker or sonar
        if (role == PlayerRole.SEEKER && state == GameState.PLAYING) {
            ItemStack item = event.getItem();
            if (item != null && event.getAction().name().contains("RIGHT")) {
                if (plugin.getGameManager().isTrackerItem(item)) {
                    event.setCancelled(true);
                    plugin.getGameManager().useTracker(player);
                } else if (plugin.getGameManager().isSonarItem(item)) {
                    event.setCancelled(true);
                    plugin.getGameManager().useSonar(player);
                }
            }
        }
    }

    /**
     * Handle seekers hitting hiders (both entity and block hits).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (!plugin.getGameManager().isInGame(attacker)) return;
        if (!plugin.getGameManager().isInGame(victim)) return;

        GameState state = plugin.getGameManager().getState();
        if (state != GameState.PLAYING) {
            event.setCancelled(true);
            return;
        }

        PlayerRole attackerRole = plugin.getGameManager().getRole(attacker);
        PlayerRole victimRole = plugin.getGameManager().getRole(victim);

        if (attackerRole == PlayerRole.SEEKER && victimRole == PlayerRole.HIDER) {
            // Let the damage go through — hiders have to be killed, not just tapped
            // If they're in solid block mode, break them out first
            if (plugin.getDisguiseManager().isSolid(victim)) {
                plugin.getDisguiseManager().breakSolidBlock(victim);
            }
        } else {
            // Prevent friendly fire (seeker vs seeker, hider vs anyone)
            event.setCancelled(true);
        }
    }

    /**
     * Handle seekers breaking blocks that might be disguised hiders.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getGameManager().isInGame(player)) return;

        GameState state = plugin.getGameManager().getState();
        PlayerRole role = plugin.getGameManager().getRole(player);

        // Always cancel block breaking in game
        event.setCancelled(true);

        if (state != GameState.PLAYING) return;

        if (role == PlayerRole.SEEKER) {
            Block block = event.getBlock();
            // Check if this block is a disguised hider - break them out of solid mode
            Player hider = plugin.getDisguiseManager().getHiderAtSolidBlock(block.getLocation());
            if (hider != null) {
                plugin.getDisguiseManager().breakSolidBlock(hider);
                player.sendMessage(net.kyori.adventure.text.Component.text(
                        "You broke a hider's disguise! Chase them!",
                        net.kyori.adventure.text.format.NamedTextColor.GREEN));
            }
        }
    }

    /**
     * Handle left-clicking blocks (seekers attacking solid-disguise blocks).
     */
    @EventHandler
    public void onPlayerInteractBlock(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGameManager().isInGame(player)) return;

        GameState state = plugin.getGameManager().getState();
        PlayerRole role = plugin.getGameManager().getRole(player);

        if (state == GameState.PLAYING && role == PlayerRole.SEEKER) {
            if (event.getAction().name().contains("LEFT") && event.getClickedBlock() != null) {
                Block block = event.getClickedBlock();
                Player hider = plugin.getDisguiseManager().getHiderAtSolidBlock(block.getLocation());
                if (hider != null) {
                    plugin.getDisguiseManager().breakSolidBlock(hider);
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                            "You broke a hider's disguise! Chase them!",
                            net.kyori.adventure.text.format.NamedTextColor.GREEN));
                }
            }
        }
    }

    /**
     * Prevent all damage to game players except from seeker swords.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getGameManager().isInGame(player)) return;

        if (event instanceof EntityDamageByEntityEvent entityEvent) {
            // Block firework explosion damage
            if (entityEvent.getDamager() instanceof org.bukkit.entity.Firework) {
                event.setCancelled(true);
                return;
            }
            // Allow seeker-on-hider damage (handled in the other listener)
            return;
        }

        // Cancel all non-player damage (fall, fire, etc.)
        event.setCancelled(true);
    }

    /**
     * When a hider dies, they become a seeker. Respawn them instantly.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!plugin.getGameManager().isInGame(player)) return;

        PlayerRole role = plugin.getGameManager().getRole(player);
        if (role == PlayerRole.HIDER) {
            // Clear death drops and message
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.deathMessage(null);

            // Find who killed them
            Player killer = player.getKiller();

            // Schedule instant respawn and conversion to seeker
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.spigot().respawn();

                // Teleport to seeker spawn
                player.teleport(plugin.getConfigManager().getSeekerSpawn());

                // Convert to seeker through game manager
                plugin.getGameManager().hiderFound(player, killer != null ? killer : player);
            }, 1L);
        }
    }

    /**
     * Prevent opening chests, furnaces, and any other container during game.
     */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (plugin.getGameManager().isInGame(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent clicking armor slots so seekers can't remove their armor.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.getGameManager().isInGame(player)) return;

        // Block armor slot clicks (slots 36-39 in player inventory are armor)
        int slot = event.getRawSlot();
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent interacting with item frames, armor stands, etc.
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGameManager().isInGame(player)) return;

        // Allow seeker hitting hiders (handled elsewhere), block everything else
        org.bukkit.entity.Entity target = event.getRightClicked();
        if (target instanceof org.bukkit.entity.ItemFrame ||
            target instanceof org.bukkit.entity.ArmorStand) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent damaging item frames and armor stands during game.
     */
    @EventHandler
    public void onEntityDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!plugin.getGameManager().isInGame(player)) return;

        org.bukkit.entity.Entity target = event.getEntity();
        if (target instanceof org.bukkit.entity.ItemFrame ||
            target instanceof org.bukkit.entity.ArmorStand) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent hunger loss during game (saturation handles keeping it full).
     */
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getGameManager().isInGame(player)) {
            event.setFoodLevel(20);
        }
    }

    /**
     * Disable natural health regeneration during game.
     */
    @EventHandler
    public void onPlayerRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getGameManager().isInGame(player)) return;

        // Block natural and saturation regen, allow other sources (like potions if you add them)
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED ||
            event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle player disconnecting during a game.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGameManager().isInGame(player)) {
            plugin.getGameManager().broadcastMessage(
                    Component.text(player.getName() + " disconnected and was removed from the game.",
                            NamedTextColor.GRAY));
            plugin.getGameManager().removePlayer(player);
        }
    }

    /**
     * Prevent item drops during game.
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (plugin.getGameManager().isInGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
