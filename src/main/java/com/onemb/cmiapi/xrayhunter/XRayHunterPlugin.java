package com.onemb.cmiapi.xrayhunter;

import com.onemb.cmiapi.xrayhunter.command.MainCommand;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Standalone Paper entry point for the X-ray investigation feature.
 *
 * <p>The feature runtime is deliberately separate so it can later be hosted by
 * the planned unified 1MB CoreProtect add-on without retaining a second plugin
 * lifecycle inside that jar.
 */
public final class XRayHunterPlugin extends JavaPlugin {
    private @Nullable XRayHunterFeature feature;

    @Override
    public void onEnable() {
        feature = new XRayHunterFeature(this);
        if (!feature.start()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        final MainCommand mainCommand = new MainCommand(feature);
        final PluginCommand command = Objects.requireNonNull(
                getCommand("xrayhunter"),
                "xrayhunter command missing"
        );
        command.setExecutor(mainCommand);
        command.setTabCompleter(mainCommand);
    }

    @Override
    public void onDisable() {
        if (feature != null) {
            feature.shutdown();
            feature = null;
        }
    }
}
