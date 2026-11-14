package me.pepmon270.serverhomes;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Plugin entry point: wires up home storage and registers the {@code /home}
 * and {@code /sethome} commands.
 */
public final class ServerHomes extends JavaPlugin {

    @Override
    public void onEnable() {
        HomeStorage storage = new HomeStorage(getDataFolder().toPath().resolve("homes.yml"), getLogger());

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            registrar.register("home", "Teleport to your home", List.of(), new HomeCommand(storage));
            registrar.register("sethome", "Set your home to your current location", List.of(), new SetHomeCommand(storage));
        });
    }
}
