package me.pepmon270.serverhomes;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * A saved home. Holds the world by name so it can be created and stored off the main thread.
 *
 * @param name  normalized home name (see {@link HomeNames})
 * @param world name of the world the home is in
 * @param x     x coordinate
 * @param y     y coordinate
 * @param z     z coordinate
 * @param yaw   horizontal rotation
 * @param pitch vertical rotation
 */
public record Home(@NotNull String name, @NotNull String world, double x, double y, double z, float yaw, float pitch) {

    /**
     * Captures a location as a home.
     *
     * @param name     normalized home name
     * @param location location to save; its world must be non-null
     * @return the new home
     */
    public static @NotNull Home of(@NotNull String name, @NotNull Location location) {
        return new Home(name, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    /**
     * Converts this home to a {@link Location}.
     *
     * @param world the loaded world named {@link #world()}
     * @return the location
     */
    public @NotNull Location toLocation(@NotNull World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }
}
