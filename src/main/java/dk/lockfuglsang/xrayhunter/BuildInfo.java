package dk.lockfuglsang.xrayhunter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Build metadata generated during resource processing.
 */
public record BuildInfo(
        String pluginName,
        String pluginVersion,
        String buildNumber,
        String fullVersion,
        String javaTarget,
        String paperTarget,
        String bukkitApiVersion,
        String coreProtectTarget
) {
    public static BuildInfo load(JavaPlugin plugin) {
        final Properties properties = new Properties();
        try (InputStream inputStream = plugin.getResource("build-info.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to read build-info.properties: " + exception.getMessage());
        }

        final String pluginMetaVersion = plugin.getPluginMeta().getVersion();
        return new BuildInfo(
                properties.getProperty("plugin-name", plugin.getName()),
                properties.getProperty("plugin-version", pluginMetaVersion),
                properties.getProperty("build-number", "unknown"),
                properties.getProperty("full-version", pluginMetaVersion),
                properties.getProperty("java-target", "unknown"),
                properties.getProperty("paper-target", "unknown"),
                properties.getProperty("bukkit-api-version", "unknown"),
                properties.getProperty("coreprotect-target", "unknown")
        );
    }
}
