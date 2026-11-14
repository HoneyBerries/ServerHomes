package me.pepmon270.serverhomes;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Plugin entry point: opens the SQLite home database and registers the home commands.
 * Disables itself if the database cannot be opened.
 */
public final class ServerHomes extends JavaPlugin {

    private HomeRepository repository;

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

    @Override
    public void onDisable() {
        if (repository != null) {
            repository.close();
        }
    }
}
