package com.blockhideseek.managers;

import com.blockhideseek.BlockHideSeek;
import com.blockhideseek.GameState;
import com.blockhideseek.PlayerRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class GameManager {

    private final BlockHideSeek plugin;
    private GameState state = GameState.WAITING;

    private final Map<UUID, PlayerRole> playerRoles = new HashMap<>();
    private final Map<UUID, Long> foundTimes = new LinkedHashMap<>(); // track order found
    private final Map<UUID, Long> trackerCooldowns = new HashMap<>();

    private BukkitTask gameTimer;
    private BukkitTask countdownTimer;
    private int timeRemaining;
    private long gameStartTime;

    // Custom item key for the tracker
    private static final String TRACKER_DISPLAY_NAME = "§6§lHider Tracker";

    public GameManager(BlockHideSeek plugin) {
        this.plugin = plugin;
    }

    /**
     * Start a new game. The command sender picks seekers, everyone else is a hider.
     */
    public boolean startGame(List<Player> seekers, List<Player> hiders) {
        if (state != GameState.WAITING) {
            return false;
        }

        playerRoles.clear();
        foundTimes.clear();
        trackerCooldowns.clear();

        // Assign roles
        for (Player seeker : seekers) {
            playerRoles.put(seeker.getUniqueId(), PlayerRole.SEEKER);
        }
        for (Player hider : hiders) {
            playerRoles.put(hider.getUniqueId(), PlayerRole.HIDER);
        }

        state = GameState.HIDING;
        gameStartTime = System.currentTimeMillis();

        // Teleport seekers to seeker spawn and freeze them
        Location seekerSpawn = plugin.getConfigManager().getSeekerSpawn();
        for (Player seeker : seekers) {
            seeker.teleport(seekerSpawn);
            seeker.setGameMode(GameMode.ADVENTURE);
            seeker.getInventory().clear();
            seeker.setHealth(20);
            seeker.setFoodLevel(20);
            seeker.setFreezeTicks(Integer.MAX_VALUE); // Freeze them during countdown
            seeker.showTitle(Title.title(
                    Component.text("You are a SEEKER!", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Wait for the countdown...", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            ));
        }

        // Setup hiders
        for (Player hider : hiders) {
            hider.setGameMode(GameMode.ADVENTURE);
            hider.getInventory().clear();
            hider.setHealth(20);
            hider.setFoodLevel(20);

            // Give hiders block selection
            giveBlockSelector(hider);

            hider.showTitle(Title.title(
                    Component.text("You are a HIDER!", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("Pick a block and hide!", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            ));
        }

        // Start the hiding countdown
        int countdown = plugin.getConfigManager().getSeekerCountdown();
        timeRemaining = plugin.getConfigManager().getGameTime() + countdown;

        // Update scoreboard
        plugin.getScoreboardManager().setupScoreboard(this);

        startCountdown(countdown, seekers);

        broadcastMessage(Component.text("Block Hide and Seek has started!", NamedTextColor.GOLD, TextDecoration.BOLD));
        broadcastMessage(Component.text("Hiders have " + countdown + " seconds to hide!", NamedTextColor.YELLOW));

        return true;
    }

    private void startCountdown(int seconds, List<Player> seekers) {
        countdownTimer = new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (state != GameState.HIDING) {
                    cancel();
                    return;
                }

                timeRemaining--;
                plugin.getScoreboardManager().updateScoreboard(GameManager.this);

                if (remaining <= 5 && remaining > 0) {
                    broadcastMessage(Component.text("Seekers released in " + remaining + "...",
                            NamedTextColor.RED, TextDecoration.BOLD));
                    for (Player p : getPlayersInGame()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }

                if (remaining <= 0) {
                    releaseSeekers(seekers);
                    cancel();
                    return;
                }

                remaining--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void releaseSeekers(List<Player> seekers) {
        state = GameState.PLAYING;

        for (Player seeker : seekers) {
            if (seeker.isOnline()) {
                seeker.setFreezeTicks(0);
                giveSeekerItems(seeker);
                seeker.showTitle(Title.title(
                        Component.text("GO HUNT!", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.text("Find all the hiders!", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
                ));
                seeker.playSound(seeker.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            }
        }

        broadcastMessage(Component.text("Seekers have been released!", NamedTextColor.RED, TextDecoration.BOLD));

        // Start main game timer
        startGameTimer();
    }

    private void startGameTimer() {
        gameTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PLAYING) {
                    cancel();
                    return;
                }

                timeRemaining--;
                plugin.getScoreboardManager().updateScoreboard(GameManager.this);

                if (timeRemaining <= 10 && timeRemaining > 0) {
                    for (Player p : getPlayersInGame()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                    }
                }

                if (timeRemaining <= 0) {
                    endGame(false); // Hiders win - time ran out
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Called when a hider is found (hit by a seeker).
     */
    public void hiderFound(Player hider, Player seeker) {
        if (state != GameState.PLAYING) return;
        if (getRole(hider) != PlayerRole.HIDER) return;

        // Record find time
        long elapsed = (System.currentTimeMillis() - gameStartTime) / 1000;
        foundTimes.put(hider.getUniqueId(), elapsed);

        // Convert hider to seeker
        playerRoles.put(hider.getUniqueId(), PlayerRole.SEEKER);
        plugin.getDisguiseManager().removeDisguise(hider);

        // Give them seeker items
        hider.getInventory().clear();
        giveSeekerItems(hider);

        hider.showTitle(Title.title(
                Component.text("YOU WERE FOUND!", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("You are now a Seeker!", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));

        broadcastMessage(Component.text(hider.getName() + " was found by " + seeker.getName() + "!",
                NamedTextColor.YELLOW));
        broadcastMessage(Component.text(getHiderCount() + " hider(s) remaining!", NamedTextColor.GOLD));

        // Check if all hiders found
        if (getHiderCount() <= 0) {
            endGame(true); // Seekers win
        }

        plugin.getScoreboardManager().updateScoreboard(this);
    }

    /**
     * End the game.
     * @param seekersWin true if seekers won (found all hiders), false if hiders win (time ran out)
     */
    public void endGame(boolean seekersWin) {
        state = GameState.ENDED;

        if (gameTimer != null) gameTimer.cancel();
        if (countdownTimer != null) countdownTimer.cancel();

        // Clean up disguises
        plugin.getDisguiseManager().cleanupAll();

        // Build stats summary
        Component header;
        if (seekersWin) {
            header = Component.text("SEEKERS WIN!", NamedTextColor.RED, TextDecoration.BOLD);
        } else {
            header = Component.text("HIDERS WIN!", NamedTextColor.GREEN, TextDecoration.BOLD);
        }

        broadcastMessage(Component.text(""));
        broadcastMessage(Component.text("=============================", NamedTextColor.GOLD));
        broadcastMessage(header);
        broadcastMessage(Component.text("=============================", NamedTextColor.GOLD));

        // Show surviving hiders
        if (!seekersWin) {
            List<String> survivors = new ArrayList<>();
            for (Map.Entry<UUID, PlayerRole> entry : playerRoles.entrySet()) {
                if (entry.getValue() == PlayerRole.HIDER) {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p != null) survivors.add(p.getName());
                }
            }
            broadcastMessage(Component.text("Surviving Hiders: " + String.join(", ", survivors),
                    NamedTextColor.GREEN));
        }

        // Show find order
        if (!foundTimes.isEmpty()) {
            broadcastMessage(Component.text("--- Found Order ---", NamedTextColor.YELLOW));
            int order = 1;
            for (Map.Entry<UUID, Long> entry : foundTimes.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                String name = p != null ? p.getName() : "Unknown";
                int minutes = (int) (entry.getValue() / 60);
                int secs = (int) (entry.getValue() % 60);
                broadcastMessage(Component.text(
                        "#" + order + " " + name + " - found at " + minutes + "m " + secs + "s",
                        NamedTextColor.GRAY));
                order++;
            }
        }

        broadcastMessage(Component.text("=============================", NamedTextColor.GOLD));
        broadcastMessage(Component.text(""));

        // Reset players
        for (Map.Entry<UUID, PlayerRole> entry : playerRoles.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                p.getInventory().clear();
                p.setFreezeTicks(0);
                p.setGameMode(GameMode.SURVIVAL);
            }
        }

        // Clean up scoreboard
        plugin.getScoreboardManager().removeScoreboard();

        // Reset state
        playerRoles.clear();
        foundTimes.clear();
        trackerCooldowns.clear();
        state = GameState.WAITING;
    }

    /**
     * Force stop without announcing a winner.
     */
    public void forceStop() {
        if (state == GameState.WAITING) return;

        if (gameTimer != null) gameTimer.cancel();
        if (countdownTimer != null) countdownTimer.cancel();

        plugin.getDisguiseManager().cleanupAll();
        plugin.getScoreboardManager().removeScoreboard();

        for (Map.Entry<UUID, PlayerRole> entry : playerRoles.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                p.getInventory().clear();
                p.setFreezeTicks(0);
                p.setGameMode(GameMode.SURVIVAL);
            }
        }

        playerRoles.clear();
        foundTimes.clear();
        trackerCooldowns.clear();
        state = GameState.WAITING;

        broadcastMessage(Component.text("Block Hide and Seek has been stopped!", NamedTextColor.RED));
    }

    /**
     * Use the firework tracker for a seeker.
     */
    public boolean useTracker(Player seeker) {
        if (state != GameState.PLAYING) return false;
        if (getRole(seeker) != PlayerRole.SEEKER) return false;

        // Check cooldown
        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfigManager().getTrackerCooldown() * 1000L;
        Long lastUse = trackerCooldowns.get(seeker.getUniqueId());
        if (lastUse != null && (now - lastUse) < cooldownMs) {
            long remaining = (cooldownMs - (now - lastUse)) / 1000;
            seeker.sendMessage(Component.text("Tracker on cooldown! " + remaining + "s remaining.",
                    NamedTextColor.RED));
            return false;
        }

        trackerCooldowns.put(seeker.getUniqueId(), now);

        // Launch fireworks near each hider
        int inaccuracy = plugin.getConfigManager().getTrackerInaccuracy();
        boolean foundAny = false;

        for (Map.Entry<UUID, PlayerRole> entry : playerRoles.entrySet()) {
            if (entry.getValue() != PlayerRole.HIDER) continue;
            Player hider = Bukkit.getPlayer(entry.getKey());
            if (hider == null || !hider.isOnline()) continue;

            foundAny = true;
            Location hiderLoc = hider.getLocation().clone();

            // Add random offset
            double offsetX = ThreadLocalRandom.current().nextDouble(-inaccuracy, inaccuracy);
            double offsetZ = ThreadLocalRandom.current().nextDouble(-inaccuracy, inaccuracy);
            hiderLoc.add(offsetX, 2, offsetZ);

            // Spawn firework
            Firework firework = hiderLoc.getWorld().spawn(hiderLoc, Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.RED, Color.ORANGE)
                    .withFade(Color.YELLOW)
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .trail(true)
                    .build());
            meta.setPower(0); // Explode quickly
            firework.setFireworkMeta(meta);
        }

        if (foundAny) {
            seeker.sendMessage(Component.text("Tracker activated! Fireworks launched near hiders!",
                    NamedTextColor.GOLD));
        } else {
            seeker.sendMessage(Component.text("No hiders to track!", NamedTextColor.GRAY));
        }

        return true;
    }

    private void giveSeekerItems(Player seeker) {
        // Iron sword
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta swordMeta = sword.getItemMeta();
        swordMeta.displayName(Component.text("Seeker Sword", NamedTextColor.RED, TextDecoration.BOLD));
        sword.setItemMeta(swordMeta);
        seeker.getInventory().addItem(sword);

        // Tracker item (firework star)
        ItemStack tracker = new ItemStack(Material.FIREWORK_STAR);
        ItemMeta trackerMeta = tracker.getItemMeta();
        trackerMeta.displayName(Component.text(TRACKER_DISPLAY_NAME));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Right-click to reveal hider locations!", NamedTextColor.GRAY));
        lore.add(Component.text("Cooldown: " + plugin.getConfigManager().getTrackerCooldown() + "s",
                NamedTextColor.DARK_GRAY));
        trackerMeta.lore(lore);
        trackerMeta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        tracker.setItemMeta(trackerMeta);
        seeker.getInventory().setItem(8, tracker); // Put in last slot
    }

    private void giveBlockSelector(Player hider) {
        List<Material> blocks = plugin.getConfigManager().getAllowedBlocks();
        // Give a selection of blocks (up to 9 for the hotbar)
        int count = Math.min(blocks.size(), 9);
        for (int i = 0; i < count; i++) {
            ItemStack item = new ItemStack(blocks.get(i));
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Click to disguise as " + formatName(blocks.get(i)),
                    NamedTextColor.GREEN));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Right-click to become this block!", NamedTextColor.GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
            hider.getInventory().setItem(i, item);
        }
        if (blocks.size() > 9) {
            hider.sendMessage(Component.text("More blocks available! Use /hs blocks to see the full list.",
                    NamedTextColor.YELLOW));
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

    public boolean isTrackerItem(ItemStack item) {
        if (item == null || item.getType() != Material.FIREWORK_STAR) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return false;
        // Check using legacy format since we set it that way
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(meta.displayName()).contains("Hider Tracker");
    }

    // Utility methods

    public void broadcastMessage(Component message) {
        for (UUID uuid : playerRoles.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(message);
            }
        }
    }

    public GameState getState() { return state; }

    public PlayerRole getRole(Player player) {
        return playerRoles.get(player.getUniqueId());
    }

    public boolean isInGame(Player player) {
        return playerRoles.containsKey(player.getUniqueId());
    }

    public int getHiderCount() {
        return (int) playerRoles.values().stream().filter(r -> r == PlayerRole.HIDER).count();
    }

    public int getSeekerCount() {
        return (int) playerRoles.values().stream().filter(r -> r == PlayerRole.SEEKER).count();
    }

    public int getTimeRemaining() { return timeRemaining; }

    public List<Player> getPlayersInGame() {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : playerRoles.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) players.add(p);
        }
        return players;
    }

    public Map<UUID, PlayerRole> getPlayerRoles() {
        return Collections.unmodifiableMap(playerRoles);
    }

    public void removePlayer(Player player) {
        PlayerRole role = playerRoles.remove(player.getUniqueId());
        if (role == PlayerRole.HIDER) {
            plugin.getDisguiseManager().removeDisguise(player);
        }
        player.getInventory().clear();
        player.setFreezeTicks(0);
        player.setGameMode(GameMode.SURVIVAL);

        plugin.getScoreboardManager().updateScoreboard(this);

        // Check if game should end
        if (state == GameState.PLAYING && getHiderCount() <= 0) {
            endGame(true);
        }
    }
}
