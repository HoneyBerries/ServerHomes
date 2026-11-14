package me.pepmon270.serverhomes;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Plugin entry point: opens the SQLite home database and registers the home commands.
 * Disables itself if the database cannot be opened.
 */
public final class ServerHomes extends JavaPlugin {

    /**
     * The SQLite-backed home store, opened in {@link #onEnable()} and closed in {@link #onDisable()}.
     * {@code null} until the database has been successfully opened.
     */
    @Nullable
    private HomeRepository repository;

    /**
     * Called by the server when the plugin is enabled.
     * Saves the default configuration, creates the data folder, opens the home database
     * ({@code homes.db}) and registers the home commands with the server's lifecycle manager.
     * If the database cannot be opened, the error is logged and this plugin is disabled.
     */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            Files.createDirectories(getDataFolder().toPath());
            repository = new HomeRepository(getDataFolder().toPath().resolve("homes.db"));
        } catch (IOException | SQLException e) {
            getLogger().log(Level.SEVERE, "Could not open the home database; disabling.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        HomeCommands commands = new HomeCommands(this, repository);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> commands.register(event.registrar()));
    }

    /**
     * Called by the server when the plugin is disabled.
     * Closes the home database if it was opened.
     */
    @Override
    public void onDisable() {
        if (repository != null) {
            repository.close();
        }
    }
}
