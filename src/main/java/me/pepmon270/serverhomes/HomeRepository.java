package me.pepmon270.serverhomes;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SQLite-backed home storage.
 *
 * <p>All database work runs on a single dedicated thread that owns the only connection, so every
 * operation is atomic with respect to the others (no races on the home limit) and the server
 * thread is never blocked. Every method returns a future that completes on that thread.
 */
public final class HomeRepository implements AutoCloseable {

    /** Outcome of {@link #set}. */
    public enum SetResult { CREATED, UPDATED, LIMIT_REACHED }

    /** Outcome of {@link #rename}. */
    public enum RenameResult { RENAMED, NOT_FOUND, TARGET_EXISTS }

    /** Single daemon thread that owns the database connection and runs every database operation. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ServerHomes-DB");
        thread.setDaemon(true);
        return thread;
    });
    /** The single SQLite connection; only touched from the database thread ({@link #executor}). */
    private final Connection connection;

    /**
     * Opens (creating if needed) the database and its schema.
     *
     * @param file path of the SQLite file
     * @throws SQLException if the database cannot be opened
     */
    public HomeRepository(@NotNull Path file) throws SQLException {
        try {
            this.connection = CompletableFuture.supplyAsync(() -> {
                try {
                    Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
                    try (Statement s = c.createStatement()) {
                        s.execute("""
                                CREATE TABLE IF NOT EXISTS homes (
                                    uuid  TEXT NOT NULL,
                                    name  TEXT NOT NULL,
                                    world TEXT NOT NULL,
                                    x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
                                    yaw REAL NOT NULL, pitch REAL NOT NULL,
                                    PRIMARY KEY (uuid, name)
                                ) WITHOUT ROWID""");
                    }
                    return c;
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }, executor).join();
        } catch (RuntimeException e) {
            executor.shutdownNow();
            throw e.getCause() instanceof SQLException sql ? sql : new SQLException(e);
        }
    }

    /**
     * Lists a player's homes.
     *
     * @param owner the player's UUID
     * @return future of their homes, ordered by name
     */
    public @NotNull CompletableFuture<@NotNull List<@NotNull Home>> list(@NotNull UUID owner) {
        return run(() -> {
            try (PreparedStatement s = connection.prepareStatement(
                    "SELECT name, world, x, y, z, yaw, pitch FROM homes WHERE uuid = ? ORDER BY name")) {
                s.setString(1, owner.toString());
                try (ResultSet rs = s.executeQuery()) {
                    List<Home> homes = new ArrayList<>();
                    while (rs.next()) {
                        homes.add(read(rs));
                    }
                    return homes;
                }
            }
        });
    }

    /**
     * Finds one home.
     *
     * @param owner the player's UUID
     * @param name  normalized home name
     * @return future of the home, or empty if there is none with that name
     */
    public @NotNull CompletableFuture<@NotNull Optional<@NotNull Home>> find(@NotNull UUID owner, @NotNull String name) {
        return run(() -> {
            try (PreparedStatement s = connection.prepareStatement(
                    "SELECT name, world, x, y, z, yaw, pitch FROM homes WHERE uuid = ? AND name = ?")) {
                s.setString(1, owner.toString());
                s.setString(2, name);
                try (ResultSet rs = s.executeQuery()) {
                    return rs.next() ? Optional.of(read(rs)) : Optional.empty();
                }
            }
        });
    }

    /**
     * Creates a home, or overwrites one with the same name. Overwriting is always allowed;
     * creating is refused once the owner has {@code limit} homes.
     *
     * @param owner the player's UUID
     * @param home  the home to save
     * @param limit maximum number of homes, or a negative number for unlimited
     * @return future of the outcome
     */
    public @NotNull CompletableFuture<@NotNull SetResult> set(@NotNull UUID owner, @NotNull Home home, int limit) {
        return run(() -> {
            boolean exists = exists(owner, home.name());
            if (!exists && limit >= 0 && count(owner) >= limit) {
                return SetResult.LIMIT_REACHED;
            }
            try (PreparedStatement s = connection.prepareStatement(
                    "INSERT OR REPLACE INTO homes (uuid, name, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?,?)")) {
                s.setString(1, owner.toString());
                s.setString(2, home.name());
                s.setString(3, home.world());
                s.setDouble(4, home.x());
                s.setDouble(5, home.y());
                s.setDouble(6, home.z());
                s.setFloat(7, home.yaw());
                s.setFloat(8, home.pitch());
                s.executeUpdate();
            }
            return exists ? SetResult.UPDATED : SetResult.CREATED;
        });
    }

    /**
     * Deletes a home.
     *
     * @param owner the player's UUID
     * @param name  normalized home name
     * @return future of {@code true} if a home was deleted, {@code false} if none had that name
     */
    public @NotNull CompletableFuture<@NotNull Boolean> delete(@NotNull UUID owner, @NotNull String name) {
        return run(() -> {
            try (PreparedStatement s = connection.prepareStatement("DELETE FROM homes WHERE uuid = ? AND name = ?")) {
                s.setString(1, owner.toString());
                s.setString(2, name);
                return s.executeUpdate() > 0;
            }
        });
    }

    /**
     * Renames a home.
     *
     * @param owner the player's UUID
     * @param from  current normalized name
     * @param to    new normalized name
     * @return future of the outcome
     */
    public @NotNull CompletableFuture<@NotNull RenameResult> rename(@NotNull UUID owner, @NotNull String from, @NotNull String to) {
        return run(() -> {
            if (!exists(owner, from)) {
                return RenameResult.NOT_FOUND;
            }
            if (!from.equals(to) && exists(owner, to)) {
                return RenameResult.TARGET_EXISTS;
            }
            try (PreparedStatement s = connection.prepareStatement(
                    "UPDATE homes SET name = ? WHERE uuid = ? AND name = ?")) {
                s.setString(1, to);
                s.setString(2, owner.toString());
                s.setString(3, from);
                s.executeUpdate();
            }
            return RenameResult.RENAMED;
        });
    }

    /** Finishes queued operations, then closes the connection. Blocks for at most a few seconds. */
    @Override
    public void close() {
        executor.execute(() -> {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // Shutting down; nothing useful to do.
            }
        });
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Checks whether a player already has a home with the given name. Must run on the database thread.
     *
     * @param owner the player's UUID
     * @param name  normalized home name
     * @return {@code true} if such a home exists
     * @throws SQLException if the query fails
     */
    private boolean exists(@NotNull UUID owner, @NotNull String name) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement("SELECT 1 FROM homes WHERE uuid = ? AND name = ?")) {
            s.setString(1, owner.toString());
            s.setString(2, name);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Counts a player's homes. Must run on the database thread.
     *
     * @param owner the player's UUID
     * @return number of homes the player owns
     * @throws SQLException if the query fails
     */
    private int count(@NotNull UUID owner) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement("SELECT COUNT(*) FROM homes WHERE uuid = ?")) {
            s.setString(1, owner.toString());
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Builds a {@link Home} from the current row of a result set.
     *
     * @param rs result set positioned on a row with name, world, x, y, z, yaw and pitch columns
     * @return the home described by the current row
     * @throws SQLException if a column cannot be read
     */
    private static @NotNull Home read(@NotNull ResultSet rs) throws SQLException {
        return new Home(rs.getString("name"), rs.getString("world"),
                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                rs.getFloat("yaw"), rs.getFloat("pitch"));
    }

    /**
     * A database task that may throw {@link SQLException}.
     *
     * @param <T> the result type of the task
     */
    @FunctionalInterface
    private interface Task<T> {
        /**
         * Performs the database work. Runs on the database thread.
         *
         * @return the task's result
         * @throws SQLException if the database operation fails
         */
        public T run() throws SQLException;
    }

    /**
     * Runs a database task on the dedicated database thread. A {@link SQLException} thrown by the task
     * is wrapped in an {@link IllegalStateException}, so the returned future completes exceptionally.
     *
     * @param task the task to run
     * @param <T>  the result type of the task
     * @return future of the task's result
     */
    private <T> @NotNull CompletableFuture<T> run(@NotNull Task<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.run();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, executor);
    }
}
