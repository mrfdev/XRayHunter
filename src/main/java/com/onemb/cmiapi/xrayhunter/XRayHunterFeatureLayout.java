package com.onemb.cmiapi.xrayhunter;

import java.io.File;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Maps feature-owned bundled resources to the installed configuration file.
 *
 * <p>A combined host can keep XRayHunter resources namespaced in its jar while
 * deliberately retaining or migrating the standalone configuration location.
 */
public record XRayHunterFeatureLayout(
        File configurationFile,
        String bundledConfigurationResource,
        String bundledBuildInfoResource
) {
    public XRayHunterFeatureLayout {
        Objects.requireNonNull(configurationFile, "configurationFile");
        bundledConfigurationResource = requireResourceName(
                bundledConfigurationResource,
                "bundledConfigurationResource"
        );
        bundledBuildInfoResource = requireResourceName(
                bundledBuildInfoResource,
                "bundledBuildInfoResource"
        );
    }

    public static XRayHunterFeatureLayout standalone(JavaPlugin host) {
        Objects.requireNonNull(host, "host");
        return new XRayHunterFeatureLayout(
                new File(host.getDataFolder(), "config.yml"),
                XRayHunterFeature.BUNDLED_CONFIGURATION_RESOURCE,
                XRayHunterFeature.BUNDLED_BUILD_INFO_RESOURCE
        );
    }

    public File dataFolder() {
        final File parent = configurationFile.getParentFile();
        return parent == null ? configurationFile.getAbsoluteFile().getParentFile() : parent;
    }

    private static String requireResourceName(String value, String label) {
        final String resourceName = Objects.requireNonNull(value, label).trim();
        if (resourceName.isEmpty() || resourceName.startsWith("/")) {
            throw new IllegalArgumentException(label + " must be a non-empty jar-relative path without a leading slash");
        }
        return resourceName;
    }
}
