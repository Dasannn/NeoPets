package dev.dasannn.neopets.plugin;

import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.plugin.command.NeoPetsCommands;
import dev.dasannn.neopets.plugin.runtime.NeoPetsRuntime;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

/** Main entry point and owner of the NeoPets composition root. */
public final class NeoPetsPlugin extends JavaPlugin {
    private NeoPetsRuntime runtime;

    @Override
    public void onLoad() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> new NeoPetsCommands(this, this::runtime).register(event.registrar()));
    }

    @Override
    public void onEnable() {
        try {
            runtime = NeoPetsRuntime.start(this);
            getLogger().info("NeoPets enabled (version " + getPluginMeta().getVersion() + ")");
        } catch (Throwable failure) {
            getLogger().log(Level.SEVERE, "NeoPets could not start: " + conciseMessage(failure));
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        NeoPetsRuntime current = runtime;
        runtime = null;
        if (current != null) {
            try {
                current.close();
            } catch (Exception failure) {
                getLogger().log(Level.SEVERE, "NeoPets shutdown did not complete cleanly: "
                        + conciseMessage(failure));
            }
        }
        getLogger().info("NeoPets disabled");
    }

    /** Returns the active runtime. */
    public NeoPetsRuntime runtime() {
        if (runtime == null) {
            throw new IllegalStateException("NeoPets runtime is not available");
        }
        return runtime;
    }

    /** Returns the live configuration manager after successful enablement. */
    public ConfigurationManager configurationManager() {
        return runtime().configuration();
    }

    /** Returns the loaded plugin filename used by Paper's restart updater. */
    public String currentJarName() { return getFile().getName(); }

    private static String conciseMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName()
                : message.lines().findFirst().orElse(failure.getClass().getSimpleName());
    }
}
