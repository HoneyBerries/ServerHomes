package me.pepmon270.serverhomes;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Builds and handles the home commands: {@code /home}, {@code /sethome}, {@code /delhome},
 * {@code /renamehome} and {@code /homes}.
 *
 * <p>Database work is asynchronous; results are delivered back on the player's own scheduler,
 * and teleports use {@link Player#teleportAsync(Location)}.
 */
public final class HomeCommands {

    /** Permission node that lifts the per-player home limit. */
    private static final @NotNull String UNLIMITED_PERMISSION = "serverhomes.unlimited";

    /** Owning plugin, used for scheduling, logging and config. */
    private final @NotNull ServerHomes plugin;
    /** Home storage used for every lookup and change. */
    private final @NotNull HomeRepository repository;

    /**
     * Creates the command handler.
     *
     * @param plugin     owning plugin, used for scheduling and config
     * @param repository home storage
     */
    public HomeCommands(@NotNull ServerHomes plugin, @NotNull HomeRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /**
     * Registers every command.
     *
     * @param registrar the registrar from the {@code COMMANDS} lifecycle event
     */
    public void register(@NotNull Commands registrar) {
        registrar.register(named("home", "serverhomes.home", this::home), "Teleport to one of your homes");
        registrar.register(named("sethome", "serverhomes.sethome", this::setHome), "Save your location as a home");
        registrar.register(named("delhome", "serverhomes.delhome", this::deleteHome), "Delete one of your homes");
        registrar.register(renameCommand(), "Rename one of your homes");
        registrar.register(Commands.literal("homes")
                .requires(source -> source.getSender().hasPermission("serverhomes.homes"))
                .executes(context -> {
                    Player player = player(context);
                    if (player != null) {
                        listHomes(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build(), "List your homes");
    }

    // ---- command handlers -------------------------------------------------------------------

    /**
     * Teleports the player to the named home, reporting a missing home, an unloaded world or a
     * cancelled teleport.
     *
     * @param player the player issuing the command
     * @param name   the normalized home name
     */
    private void home(@NotNull Player player, @NotNull String name) {
        respond(player, repository.find(player.getUniqueId(), name), found -> {
            if (found.isEmpty()) {
                sendErrorMessage(player, "You have no home named '" + name + "'.");
                return;
            }
            Home home = found.get();
            World world = Bukkit.getWorld(home.world());
            if (world == null) {
                sendErrorMessage(player, "The world of home '" + name + "' is not loaded.");
                return;
            }
            // Report from the player's own region thread (Folia): the future may complete elsewhere.
            player.teleportAsync(home.toLocation(world)).thenAccept(success -> player.getScheduler().run(plugin, task -> {
                if (success) {
                    sendSuccessMessage(player, "Teleported to '" + name + "'.");
                } else {
                    sendErrorMessage(player, "Teleport was cancelled.");
                }
            }, null));
        });
    }

    /**
     * Saves the player's current location as a home, enforcing the configured home limit unless
     * the player has the unlimited permission.
     *
     * @param player the player issuing the command
     * @param name   the normalized home name
     */
    private void setHome(@NotNull Player player, @NotNull String name) {
        Home home = Home.of(name, player.getLocation());
        int limit = player.hasPermission(UNLIMITED_PERMISSION) ? -1 : plugin.getConfig().getInt("max-homes", 5);
        respond(player, repository.set(player.getUniqueId(), home, limit), result -> {
            switch (result) {
                case CREATED -> sendSuccessMessage(player, "Home '" + name + "' set.");
                case UPDATED -> sendSuccessMessage(player, "Home '" + name + "' moved to your current location.");
                case LIMIT_REACHED -> sendErrorMessage(player, "You can only have " + limit
                        + " homes. Delete one with /delhome <name> or overwrite an existing one.");
            }
        });
    }

    /**
     * Deletes the named home, reporting if the player has no home with that name.
     *
     * @param player the player issuing the command
     * @param name   the normalized home name
     */
    private void deleteHome(@NotNull Player player, @NotNull String name) {
        respond(player, repository.delete(player.getUniqueId(), name), deleted -> {
            if (deleted) {
                sendSuccessMessage(player, "Home '" + name + "' deleted.");
            } else {
                sendErrorMessage(player, "You have no home named '" + name + "'.");
            }
        });
    }

    /**
     * Renames one of the player's homes, reporting a missing source or an existing target name.
     *
     * @param player the player issuing the command
     * @param from   the normalized current home name
     * @param to     the normalized new home name
     */
    private void renameHome(@NotNull Player player, @NotNull String from, @NotNull String to) {
        respond(player, repository.rename(player.getUniqueId(), from, to), result -> {
            switch (result) {
                case RENAMED -> sendSuccessMessage(player, "Home '" + from + "' renamed to '" + to + "'.");
                case NOT_FOUND -> sendErrorMessage(player, "You have no home named '" + from + "'.");
                case TARGET_EXISTS -> sendErrorMessage(player, "You already have a home named '" + to + "'.");
            }
        });
    }

    /**
     * Lists the names of all of the player's homes, or reports that there are none.
     *
     * @param player the player issuing the command
     */
    private void listHomes(@NotNull Player player) {
        respond(player, repository.list(player.getUniqueId()), homes -> {
            if (homes.isEmpty()) {
                sendErrorMessage(player, "You have no homes. Use /sethome <name>.");
                return;
            }
            String names = String.join(", ", homes.stream().map(Home::name).toList());
            player.sendMessage(Component.text("Your homes (" + homes.size() + "): ", NamedTextColor.GREEN)
                    .append(Component.text(names, NamedTextColor.WHITE)));
        });
    }

    // ---- command tree helpers ---------------------------------------------------------------

    /**
     * Builds {@code /<literal> <name>} where the name argument is validated and tab-completed.
     *
     * @param literal    the command label, without the slash
     * @param permission the permission required to run the command
     * @param action     the handler invoked with the player and the validated name
     * @return the built command node
     */
    private @NotNull LiteralCommandNode<CommandSourceStack> named(@NotNull String literal, @NotNull String permission,
                                                                  @NotNull BiConsumer<Player, String> action) {
        return Commands.literal(literal)
                .requires(source -> source.getSender().hasPermission(permission))
                .executes(context -> usage(context, "/" + literal + " <name>"))
                .then(Commands.argument("name", new HomeNameArgument(repository))
                        .executes(context -> {
                            Player player = player(context);
                            if (player != null) {
                                action.accept(player, context.getArgument("name", String.class));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    /**
     * Builds the {@code /renamehome <name> <new name>} command.
     *
     * @return the built command node
     */
    private @NotNull LiteralCommandNode<CommandSourceStack> renameCommand() {
        return Commands.literal("renamehome")
                .requires(source -> source.getSender().hasPermission("serverhomes.renamehome"))
                .executes(context -> usage(context, "/renamehome <name> <new name>"))
                .then(Commands.argument("name", new HomeNameArgument(repository))
                        .executes(context -> usage(context, "/renamehome <name> <new name>"))
                        .then(Commands.argument("new", new HomeNameArgument(repository))
                                .executes(context -> {
                                    Player player = player(context);
                                    if (player != null) {
                                        renameHome(player, context.getArgument("name", String.class),
                                                context.getArgument("new", String.class));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    /**
     * Sends a red usage line to the sender.
     *
     * @param context the command context, whose sender receives the message
     * @param usage   the usage text to show after {@code Usage: }
     * @return {@link Command#SINGLE_SUCCESS}
     */
    private static int usage(@NotNull CommandContext<CommandSourceStack> context, @NotNull String usage) {
        context.getSource().getSender().sendMessage(Component.text("Usage: " + usage, NamedTextColor.RED));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Returns the sending player, or tells the sender that players only may use this and returns null.
     *
     * @param context the command context
     * @return the sending player, or {@code null} if the sender is not a player
     */
    private static @Nullable Player player(@NotNull CommandContext<CommandSourceStack> context) {
        if (context.getSource().getSender() instanceof Player player) {
            return player;
        }
        sendErrorMessage(context.getSource().getSender(), "Only players can use this command.");
        return null;
    }

    // ---- threading and messaging ------------------------------------------------------------

    /**
     * Runs {@code onSuccess} on the player's scheduler once {@code future} completes. If the
     * future failed, logs it and tells the player instead. Does nothing if the player left.
     *
     * @param <T>       the type of value the future produces
     * @param player    the player to respond to
     * @param future    the database operation to wait for
     * @param onSuccess the callback run with the future's value when it succeeds
     */
    private <T> void respond(@NotNull Player player, @NotNull CompletableFuture<T> future, @NotNull Consumer<T> onSuccess) {
        future.whenComplete((value, failure) -> player.getScheduler().run(plugin, task -> {
            if (failure != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Database sendErrorMessage", failure);
                sendErrorMessage(player, "A database sendErrorMessage occurred. Please tell an admin.");
            } else {
                onSuccess.accept(value);
            }
        }, null));
    }

    /**
     * Sends a green sendSuccessMessage message to the player.
     *
     * @param player  the player to message
     * @param message the text to send
     */
    private static void sendSuccessMessage(@NotNull Player player, @NotNull String message) {
        player.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    /**
     * Sends a red sendErrorMessage message to the target.
     *
     * @param target  the command sender to message
     * @param message the text to send
     */
    private static void sendErrorMessage(@NotNull org.bukkit.command.CommandSender target, @NotNull String message) {
        target.sendMessage(Component.text(message, NamedTextColor.RED));
    }
}
