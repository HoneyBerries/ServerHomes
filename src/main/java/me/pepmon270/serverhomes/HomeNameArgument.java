package me.pepmon270.serverhomes;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * A Brigadier argument holding a home name. It rejects invalid names while the player types,
 * produces the normalized (lowercase) name, and suggests the sender's existing homes.
 */
public final class HomeNameArgument implements CustomArgumentType.Converted<String, String> {

    /** Error shown when the typed name is not a valid home name. */
    private static final @NotNull SimpleCommandExceptionType INVALID = new SimpleCommandExceptionType(
            new LiteralMessage("Home names may only contain letters, numbers, '-' and '_' (max "
                    + HomeNames.MAX_LENGTH + " characters)."));

    /** Home storage used to suggest the sender's existing homes. */
    private final @NotNull HomeRepository repository;

    /**
     * Creates the argument type.
     *
     * @param repository home storage used for tab completion
     */
    public HomeNameArgument(@NotNull HomeRepository repository) {
        this.repository = repository;
    }

    /**
     * Validates and normalizes the typed name.
     *
     * @param nativeType the raw word typed by the player
     * @return the normalized home name
     * @throws CommandSyntaxException if the name is not a valid home name
     */
    @Override
    public @NotNull String convert(@NotNull String nativeType) throws CommandSyntaxException {
        String name = HomeNames.normalize(nativeType);
        if (name == null) {
            throw INVALID.create();
        }
        return name;
    }

    /**
     * Returns the type the client parses, a single word.
     *
     * @return the single-word string argument type
     */
    @Override
    public @NotNull ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    /**
     * Suggests the sender's home names that start with the text typed so far.
     *
     * @param context the command context, whose sender is used to look up homes
     * @param builder the suggestions builder for the current argument
     * @param <S>     the command source type
     * @return a future completing with the suggestions; empty when the sender is not a player
     */
    @Override
    public <S> @NotNull CompletableFuture<Suggestions> listSuggestions(@NotNull CommandContext<S> context,
                                                                       @NotNull SuggestionsBuilder builder) {
        if (!(context.getSource() instanceof CommandSourceStack source)
                || !(source.getSender() instanceof Player player)) {
            return builder.buildFuture();
        }
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        return repository.list(player.getUniqueId()).thenApply(homes -> {
            homes.stream().map(Home::name).filter(name -> name.startsWith(typed)).forEach(builder::suggest);
            return builder.build();
        });
    }
}
