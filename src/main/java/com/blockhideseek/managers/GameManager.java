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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
    private final Map<UUID, Long> sonarCooldowns = new HashMap<>();

    private BukkitTask gameTimer;
    private BukkitTask countdownTimer;
    private int timeRemaining;
    private int seekerCountdownRemaining;
    private long gameStartTime;

    // Custom item display names
    private static final String TRACKER_DISPLAY_NAME = "§6§lHider Tracker";
    private static final String SONAR_DISPLAY_NAME = "§b§lHider Sonar";

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
        sonarCooldowns.clear();

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
            seeker.setSaturation(20);
            seeker.setCollidable(false);
            seeker.setFreezeTicks(Integer.MAX_VALUE);
            // Infinite hunger so they never starve, no health regen
            seeker.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0, false, false, false));
            // Blind and slow them so they can't see or move
            int countdownTicks = plugin.getConfigManager().getSeekerCountdown() * 20 + 40; // extra buffer
            seeker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, countdownTicks, 255, false, false, false));
            seeker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, countdownTicks, 255, false, false, false));
            seeker.showTitle(Title.title(
                    Component.text("You are a SEEKER!", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Wait for the countdown...", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            ));
        }

        // Setup hiders
        for (Player hider : hiders) {
            hider.teleport(plugin.getConfigManager().getGameSpawn());
            hider.setGameMode(GameMode.ADVENTURE);
            hider.getInventory().clear();
            hider.setHealth(20);
            hider.setFoodLevel(20);
            hider.setSaturation(20);
            hider.setCollidable(false);
            // Infinite hunger so they never starve, no health regen
            hider.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0, false, false, false));

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
        seekerCountdownRemaining = seconds;

        countdownTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.HIDING) {
                    cancel();
                    return;
                }

                timeRemaining--;
                seekerCountdownRemaining--;
                plugin.getScoreboardManager().updateScoreboard(GameManager.this);

                if (seekerCountdownRemaining <= 5 && seekerCountdownRemaining > 0) {
                    broadcastMessage(Component.text("Seekers released in " + seekerCountdownRemaining + "...",
                            NamedTextColor.RED, TextDecoration.BOLD));
                    for (Player p : getPlayersInGame()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }

                if (seekerCountdownRemaining <= 0) {
                    releaseSeekers(seekers);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void releaseSeekers(List<Player> seekers) {
        state = GameState.PLAYING;

        Location gameSpawn = plugin.getConfigManager().getGameSpawn();
        for (Player seeker : seekers) {
            if (seeker.isOnline()) {
                seeker.teleport(gameSpawn);
                seeker.setFreezeTicks(0);
                seeker.removePotionEffect(PotionEffectType.BLINDNESS);
                seeker.removePotionEffect(PotionEffectType.SLOWNESS);
                // Speed 1 for seekers
                seeker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false, false));
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
     * Called when a hider is found/killed by a seeker.
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

        // Heal them up, give seeker items and speed
        hider.setHealth(20);
        hider.setFoodLevel(20);
        hider.getInventory().clear();
        hider.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false, false));
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
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.removePotionEffect(PotionEffectType.SPEED);
                p.removePotionEffect(PotionEffectType.SATURATION);
                p.setCollidable(true);
                p.setGameMode(GameMode.ADVENTURE);
            }
        }

        // Clean up scoreboard
        plugin.getScoreboardManager().removeScoreboard();

        // Reset state
        playerRoles.clear();
        foundTimes.clear();
        trackerCooldowns.clear();
        sonarCooldowns.clear();
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
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.removePotionEffect(PotionEffectType.SPEED);
                p.removePotionEffect(PotionEffectType.SATURATION);
                p.setCollidable(true);
                p.setGameMode(GameMode.ADVENTURE);
            }
        }

        playerRoles.clear();
        foundTimes.clear();
        trackerCooldowns.clear();
        sonarCooldowns.clear();
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

            // Spawn firework that flies up as a flare — no explosion, no damage
            Firework firework = hiderLoc.getWorld().spawn(hiderLoc, Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.RED)
                    .with(FireworkEffect.Type.BALL)
                    .trail(true)
                    .flicker(false)
                    .build());
            meta.setPower(1); // Flies up a bit before exploding
            firework.setFireworkMeta(meta);
            // Prevent firework from dealing damage to players
            firework.setShooter(seeker);
            firework.setSilent(true);
        }

        if (foundAny) {
            seeker.sendMessage(Component.text("Tracker activated! Fireworks launched near hiders!",
                    NamedTextColor.GOLD));
        } else {
            seeker.sendMessage(Component.text("No hiders to track!", NamedTextColor.GRAY));
        }

        return true;
    }

    /**
     * Use the sonar ability — makes every hider emit a sound from their location.
     */
    public boolean useSonar(Player seeker) {
        if (state != GameState.PLAYING) return false;
        if (getRole(seeker) != PlayerRole.SEEKER) return false;

        // Check cooldown (uses half of tracker cooldown)
        long now = System.currentTimeMillis();
        long cooldownMs = (plugin.getConfigManager().getTrackerCooldown() / 2) * 1000L;
        Long lastUse = sonarCooldowns.get(seeker.getUniqueId());
        if (lastUse != null && (now - lastUse) < cooldownMs) {
            long remaining = (cooldownMs - (now - lastUse)) / 1000;
            seeker.sendMessage(Component.text("Sonar on cooldown! " + remaining + "s remaining.",
                    NamedTextColor.RED));
            return false;
        }

        sonarCooldowns.put(seeker.getUniqueId(), now);

        boolean foundAny = false;
        for (Map.Entry<UUID, PlayerRole> entry : playerRoles.entrySet()) {
            if (entry.getValue() != PlayerRole.HIDER) continue;
            Player hider = Bukkit.getPlayer(entry.getKey());
            if (hider == null || !hider.isOnline()) continue;

            foundAny = true;
            Location hiderLoc = hider.getLocation();

            // Play a loud sound at the hider's location that nearby seekers can hear
            hiderLoc.getWorld().playSound(hiderLoc, Sound.BLOCK_NOTE_BLOCK_BELL, 2.0f, 1.0f);
        }

        if (foundAny) {
            seeker.sendMessage(Component.text("Sonar activated! Listen for the sounds!",
                    NamedTextColor.AQUA));
        } else {
            seeker.sendMessage(Component.text("No hiders to ping!", NamedTextColor.GRAY));
        }

        return true;
    }

    private void giveSeekerItems(Player seeker) {
        // Diamond sword
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta swordMeta = sword.getItemMeta();
        swordMeta.displayName(Component.text("Seeker Sword", NamedTextColor.RED, TextDecoration.BOLD));
        sword.setItemMeta(swordMeta);
        seeker.getInventory().addItem(sword);

        // Tracker item (firework star) - slot 8
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
        seeker.getInventory().setItem(8, tracker);

        // Sonar item (note block) - slot 7
        ItemStack sonar = new ItemStack(Material.NOTE_BLOCK);
        ItemMeta sonarMeta = sonar.getItemMeta();
        sonarMeta.displayName(Component.text(SONAR_DISPLAY_NAME));
        List<Component> sonarLore = new ArrayList<>();
        sonarLore.add(Component.text("Right-click to make hiders emit a sound!", NamedTextColor.GRAY));
        sonarLore.add(Component.text("Cooldown: " + (plugin.getConfigManager().getTrackerCooldown() / 2) + "s",
                NamedTextColor.DARK_GRAY));
        sonarMeta.lore(sonarLore);
        sonarMeta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        sonar.setItemMeta(sonarMeta);
        seeker.getInventory().setItem(7, sonar);

        // Red leather armor
        org.bukkit.Color redColor = org.bukkit.Color.fromRGB(180, 30, 30);

        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        org.bukkit.inventory.meta.LeatherArmorMeta helmetMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) helmet.getItemMeta();
        helmetMeta.setColor(redColor);
        helmetMeta.displayName(Component.text("Seeker Helmet", NamedTextColor.RED));
        helmet.setItemMeta(helmetMeta);

        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        org.bukkit.inventory.meta.LeatherArmorMeta chestMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) chestplate.getItemMeta();
        chestMeta.setColor(redColor);
        chestMeta.displayName(Component.text("Seeker Chestplate", NamedTextColor.RED));
        chestplate.setItemMeta(chestMeta);

        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        org.bukkit.inventory.meta.LeatherArmorMeta legMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) leggings.getItemMeta();
        legMeta.setColor(redColor);
        legMeta.displayName(Component.text("Seeker Leggings", NamedTextColor.RED));
        leggings.setItemMeta(legMeta);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        org.bukkit.inventory.meta.LeatherArmorMeta bootsMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) boots.getItemMeta();
        bootsMeta.setColor(redColor);
        bootsMeta.displayName(Component.text("Seeker Boots", NamedTextColor.RED));
        boots.setItemMeta(bootsMeta);

        seeker.getInventory().setHelmet(helmet);
        seeker.getInventory().setChestplate(chestplate);
        seeker.getInventory().setLeggings(leggings);
        seeker.getInventory().setBoots(boots);
    }

    private static final String DISGUISE_ITEM_NAME = "§a§lDisguise Wand";

    private void giveBlockSelector(Player hider) {
        // Give a single item — right-click to copy the block you're looking at
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(Component.text(DISGUISE_ITEM_NAME));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Look at a block and right-click to disguise!", NamedTextColor.GRAY));
        lore.add(Component.text("Right-click air to remove your disguise.", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        wand.setItemMeta(meta);
        hider.getInventory().setItem(0, wand);
    }

    public boolean isDisguiseWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return false;
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(meta.displayName()).contains("Disguise Wand");
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
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(meta.displayName()).contains("Hider Tracker");
    }

    public boolean isSonarItem(ItemStack item) {
        if (item == null || item.getType() != Material.NOTE_BLOCK) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return false;
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(meta.displayName()).contains("Hider Sonar");
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
    public int getSeekerCountdownRemaining() { return seekerCountdownRemaining; }

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
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.SATURATION);
        player.setCollidable(true);
        player.setGameMode(GameMode.SURVIVAL);

        plugin.getScoreboardManager().updateScoreboard(this);

        // Check if game should end
        if (state == GameState.PLAYING && getHiderCount() <= 0) {
            endGame(true);
        }
    }
}
