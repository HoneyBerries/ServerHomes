package me.pepmon270.serverhomes;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
final class HomeCommands {

    private static final String UNLIMITED_PERMISSION = "serverhomes.unlimited";

    private final ServerHomes plugin;
    private final HomeRepository repository;

    /**
     * @param plugin     owning plugin, used for scheduling and config
     * @param repository home storage
     */
    HomeCommands(ServerHomes plugin, HomeRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /**
     * Registers every command.
     *
     * @param registrar the registrar from the {@code COMMANDS} lifecycle event
     */
    void register(Commands registrar) {
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

    private void home(Player player, String name) {
        respond(player, repository.find(player.getUniqueId(), name), found -> {
            if (found.isEmpty()) {
                error(player, "You have no home named '" + name + "'.");
                return;
            }
            Home home = found.get();
            World world = Bukkit.getWorld(home.world());
            if (world == null) {
                error(player, "The world of home '" + name + "' is not loaded.");
                return;
            }
            player.teleportAsync(home.toLocation(world)).thenAccept(success -> {
                if (success) {
                    success(player, "Teleported to '" + name + "'.");
                } else {
                    error(player, "Teleport was cancelled.");
                }
            });
        });
    }

    private void setHome(Player player, String name) {
        Home home = Home.of(name, player.getLocation());
        int limit = player.hasPermission(UNLIMITED_PERMISSION) ? -1 : plugin.getConfig().getInt("max-homes", 5);
        respond(player, repository.set(player.getUniqueId(), home, limit), result -> {
            switch (result) {
                case CREATED -> success(player, "Home '" + name + "' set.");
                case UPDATED -> success(player, "Home '" + name + "' moved to your current location.");
                case LIMIT_REACHED -> error(player, "You can only have " + limit
                        + " homes. Delete one with /delhome <name> or overwrite an existing one.");
            }
        });
    }

    private void deleteHome(Player player, String name) {
        respond(player, repository.delete(player.getUniqueId(), name), deleted -> {
            if (deleted) {
                success(player, "Home '" + name + "' deleted.");
            } else {
                error(player, "You have no home named '" + name + "'.");
            }
        });
    }

    private void renameHome(Player player, String from, String to) {
        respond(player, repository.rename(player.getUniqueId(), from, to), result -> {
            switch (result) {
                case RENAMED -> success(player, "Home '" + from + "' renamed to '" + to + "'.");
                case NOT_FOUND -> error(player, "You have no home named '" + from + "'.");
                case TARGET_EXISTS -> error(player, "You already have a home named '" + to + "'.");
            }
        });
    }

    private void listHomes(Player player) {
        respond(player, repository.list(player.getUniqueId()), homes -> {
            if (homes.isEmpty()) {
                error(player, "You have no homes. Use /sethome <name>.");
                return;
            }
            String names = String.join(", ", homes.stream().map(Home::name).toList());
            player.sendMessage(Component.text("Your homes (" + homes.size() + "): ", NamedTextColor.GREEN)
                    .append(Component.text(names, NamedTextColor.WHITE)));
        });
    }

    // ---- command tree helpers ---------------------------------------------------------------

    /** Builds {@code /<literal> <name>} where the name argument is validated and tab-completed. */
    private LiteralCommandNode<CommandSourceStack> named(String literal, String permission,
                                                         BiConsumer<Player, String> action) {
        return Commands.literal(literal)
                .requires(source -> source.getSender().hasPermission(permission))
                .executes(context -> usage(context, "/" + literal + " <name>"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(this::suggestHomes)
                        .executes(context -> {
                            Player player = player(context);
                            String name = player == null ? null : validName(player, context, "name");
                            if (name != null) {
                                action.accept(player, name);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> renameCommand() {
        return Commands.literal("renamehome")
                .requires(source -> source.getSender().hasPermission("serverhomes.renamehome"))
                .executes(context -> usage(context, "/renamehome <name> <new name>"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(this::suggestHomes)
                        .executes(context -> usage(context, "/renamehome <name> <new name>"))
                        .then(Commands.argument("new", StringArgumentType.word())
                                .executes(context -> {
                                    Player player = player(context);
                                    if (player == null) {
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String from = validName(player, context, "name");
                                    String to = validName(player, context, "new");
                                    if (from != null && to != null) {
                                        renameHome(player, from, to);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    private CompletableFuture<Suggestions> suggestHomes(CommandContext<CommandSourceStack> context,
                                                        SuggestionsBuilder builder) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            return builder.buildFuture();
        }
        String typed = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        return repository.list(player.getUniqueId()).thenApply(homes -> {
            homes.stream().map(Home::name).filter(name -> name.startsWith(typed)).forEach(builder::suggest);
            return builder.build();
        });
    }

    private static int usage(CommandContext<CommandSourceStack> context, String usage) {
        context.getSource().getSender().sendMessage(Component.text("Usage: " + usage, NamedTextColor.RED));
        return Command.SINGLE_SUCCESS;
    }

    /** Returns the sending player, or tells the sender that players only may use this and returns null. */
    private static @Nullable Player player(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getSender() instanceof Player player) {
            return player;
        }
        error(context.getSource().getSender(), "Only players can use this command.");
        return null;
    }

    /** Returns the normalized value of a name argument, or tells the player it is invalid and returns null. */
    private static @Nullable String validName(Player player, CommandContext<CommandSourceStack> context, String arg) {
        String name = HomeNames.normalize(context.getArgument(arg, String.class));
        if (name == null) {
            error(player, "Home names may only contain letters, numbers, '-' and '_' (max "
                    + HomeNames.MAX_LENGTH + " characters).");
        }
        return name;
    }

    // ---- threading and messaging ------------------------------------------------------------

    /**
     * Runs {@code onSuccess} on the player's scheduler once {@code future} completes. If the
     * future failed, logs it and tells the player instead. Does nothing if the player left.
     */
    private <T> void respond(Player player, CompletableFuture<T> future, Consumer<T> onSuccess) {
        future.whenComplete((value, failure) -> player.getScheduler().run(plugin, task -> {
            if (failure != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Database error", failure);
                error(player, "A database error occurred. Please tell an admin.");
            } else {
                onSuccess.accept(value);
            }
        }, null));
    }

    private static void success(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    private static void error(org.bukkit.command.CommandSender target, String message) {
        target.sendMessage(Component.text(message, NamedTextColor.RED));
    }
}
