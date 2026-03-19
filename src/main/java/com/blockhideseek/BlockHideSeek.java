package com.blockhideseek;

import com.blockhideseek.commands.HideSeekCommand;
import com.blockhideseek.commands.HideSeekTabCompleter;
import com.blockhideseek.listeners.PlayerListener;
import com.blockhideseek.managers.ConfigManager;
import com.blockhideseek.managers.DisguiseManager;
import com.blockhideseek.managers.GameManager;
import com.blockhideseek.managers.ScoreboardManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BlockHideSeek extends JavaPlugin {

    private static BlockHideSeek instance;
    private ConfigManager configManager;
    private GameManager gameManager;
    private DisguiseManager disguiseManager;
    private ScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize managers
        configManager = new ConfigManager(this);
        disguiseManager = new DisguiseManager(this);
        scoreboardManager = new ScoreboardManager(this);
        gameManager = new GameManager(this);

        // Register commands
        HideSeekCommand commandExecutor = new HideSeekCommand(this);
        getCommand("hideseek").setExecutor(commandExecutor);
        getCommand("hideseek").setTabCompleter(new HideSeekTabCompleter());

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        getLogger().info("Block Hide and Seek has been enabled!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.forceStop();
        }
        if (disguiseManager != null) {
            disguiseManager.cleanupAll();
        }
        getLogger().info("Block Hide and Seek has been disabled!");
    }

    public static BlockHideSeek getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public DisguiseManager getDisguiseManager() {
        return disguiseManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
}
