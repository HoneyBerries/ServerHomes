package me.pepmon270.serverhomes;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /home}: teleports the player to their saved home. */
final class HomeCommand implements BasicCommand {

    private final HomeStorage storage;

    /**
     * @param storage where homes are read from
     */
    HomeCommand(HomeStorage storage) {
        this.storage = storage;
    }

    /**
     * Teleports the sending player to their home, or tells them why that
     * isn't possible (not a player, no home set, or the home's world is unloaded).
     *
     * @param source the command source; its sender must be a {@link Player}
     * @param args   unused
     */
    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        storage.getHome(player.getUniqueId()).ifPresentOrElse(
                home -> player.teleportAsync(home).thenAccept(success -> player.sendMessage(success
                        ? Component.text("Teleported home.", NamedTextColor.GREEN)
                        : Component.text("Teleport was cancelled.", NamedTextColor.RED))),
                () -> player.sendMessage(Component.text(
                        "You have no home set, or its world is not loaded. Use /sethome.", NamedTextColor.RED)));
    }

    @Override
    public String permission() {
        return "serverhomes.home";
    }
}
