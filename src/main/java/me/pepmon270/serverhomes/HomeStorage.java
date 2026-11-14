package me.pepmon270.serverhomes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists one home per player in a YAML file, keyed by player UUID.
 * The on-disk format is unchanged from 1.0.0, so existing {@code homes.yml} files keep working.
 */
final class HomeStorage {

    private final Path file;
    private final Logger logger;
    private final YamlConfiguration config;

    /**
     * Loads existing homes from {@code file}; the file is created on first save.
     *
     * @param file   path of the YAML file
     * @param logger used to report I/O failures
     */
    HomeStorage(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.config = YamlConfiguration.loadConfiguration(file.toFile());
    }

    /**
     * Stores {@code location} as the player's home (replacing any previous one) and saves to disk.
     *
     * @param player   the owner's UUID
     * @param location the home; its world must be non-null
     */
    synchronized void setHome(UUID player, Location location) {
        var section = config.createSection(player.toString());
        section.set("world", location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
        save();
    }

    /**
     * Looks up a player's home.
     *
     * @param player the owner's UUID
     * @return the home, or empty if none is set or its world is not currently loaded
     */
    synchronized Optional<Location> getHome(UUID player) {
        var section = config.getConfigurationSection(player.toString());
        if (section == null) {
            return Optional.empty();
        }
        String worldName = section.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")));
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            config.save(file.toFile());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save homes to " + file, e);
        }
    }
}
