package me.pepmon270.serverhomes;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /sethome}: saves the player's current location as their home. */
final class SetHomeCommand implements BasicCommand {

    private final HomeStorage storage;

    /**
     * @param storage where the home is written to
     */
    SetHomeCommand(HomeStorage storage) {
        this.storage = storage;
    }

    /**
     * Saves the sending player's location as their home, replacing any previous one.
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

        storage.setHome(player.getUniqueId(), player.getLocation());
        player.sendMessage(Component.text("Home set.", NamedTextColor.GREEN));
    }

    @Override
    public String permission() {
        return "serverhomes.sethome";
    }
}
