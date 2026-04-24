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
        String paperApiCompileTarget,
        String pluginApiCompatibilityFloor,
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

        final var pluginMeta = plugin.getPluginMeta();
        final String pluginMetaVersion = pluginMeta.getVersion();
        final String pluginMetaApiVersion = fallbackIfBlank(pluginMeta.getAPIVersion(), "unknown");
        return new BuildInfo(
                properties.getProperty("plugin-name", pluginMeta.getName()),
                properties.getProperty("plugin-version", pluginMetaVersion),
                properties.getProperty("build-number", "unknown"),
                properties.getProperty("full-version", pluginMetaVersion),
                properties.getProperty("java-target", "unknown"),
                properties.getProperty("paper-api-compile-target", "unknown"),
                properties.getProperty("plugin-api-compatibility-floor", pluginMetaApiVersion),
                properties.getProperty("coreprotect-target", "unknown")
        );
    }

    private static String fallbackIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
