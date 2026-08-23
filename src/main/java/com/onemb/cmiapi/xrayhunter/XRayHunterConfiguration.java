package com.onemb.cmiapi.xrayhunter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared config.yml loader/saver that preserves admin-edited values while refreshing defaults and comments.
 */
public final class XRayHunterConfiguration {
    private static final List<String> HEADER_COMMENTS = List.of(
            "1MB-XRayHunter configuration.",
            "Manual value edits are preserved when this file is re-saved.",
            "Manual file edits require /xrayhunter reload or a full server restart after saving.",
            "Changes made through /xrayhunter debug set and /xrayhunter debug whitelist save and reload immediately."
    );

    private final JavaPlugin plugin;
    private final String resourceName;
    private final File file;

    public XRayHunterConfiguration(JavaPlugin plugin, File file, String resourceName) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.file = Objects.requireNonNull(file, "file");
        this.resourceName = Objects.requireNonNull(resourceName, "resourceName");
    }

    public YamlConfiguration reload() {
        final YamlConfiguration template = loadResourceConfiguration();
        final YamlConfiguration diskConfiguration = loadDiskConfiguration();
        prepareConfiguration(template);
        mergeSections(template, diskConfiguration);
        save(template);
        return template;
    }

    public void save(YamlConfiguration configuration) {
        prepareConfiguration(configuration);
        try {
            final File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to save " + file.getPath() + ": " + exception.getMessage());
        }
    }

    private void prepareConfiguration(YamlConfiguration configuration) {
        configuration.options().parseComments(true);
        configuration.options().setHeader(HEADER_COMMENTS);
    }

    private void mergeSections(ConfigurationSection target, ConfigurationSection source) {
        for (String key : source.getKeys(false)) {
            final Object sourceValue = source.get(key);
            final ConfigurationSection sourceSection = source.getConfigurationSection(key);
            if (sourceSection != null && sourceValue instanceof ConfigurationSection) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection == null) {
                    targetSection = target.createSection(key);
                }
                applyComments(target, source, key);
                mergeSections(targetSection, sourceSection);
                continue;
            }

            target.set(key, sourceValue);
            applyComments(target, source, key);
        }
    }

    private void applyComments(ConfigurationSection target, ConfigurationSection source, String key) {
        final List<String> sourceComments = source.getComments(key);
        if (!sourceComments.isEmpty()) {
            target.setComments(key, sourceComments);
        }

        final List<String> sourceInlineComments = source.getInlineComments(key);
        if (!sourceInlineComments.isEmpty()) {
            target.setInlineComments(key, sourceInlineComments);
        }
    }

    private YamlConfiguration loadResourceConfiguration() {
        final InputStream resourceStream = Objects.requireNonNull(
                plugin.getResource(resourceName),
                "Missing bundled resource: " + resourceName
        );

        try (resourceStream) {
            return loadConfiguration(new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8), "bundled " + resourceName);
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to read bundled " + resourceName + ": " + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    private YamlConfiguration loadDiskConfiguration() {
        if (!file.exists()) {
            return new YamlConfiguration();
        }

        try {
            return loadConfiguration(Files.readString(file.toPath(), StandardCharsets.UTF_8), file.getName());
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to read " + file.getName() + ": " + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    private YamlConfiguration loadConfiguration(String content, String sourceLabel) {
        final YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().parseComments(true);

        if (content == null || content.isBlank()) {
            return configuration;
        }

        try {
            configuration.loadFromString(content);
        } catch (InvalidConfigurationException exception) {
            plugin.getLogger().warning("Unable to parse " + sourceLabel + ", using safe defaults: " + exception.getMessage());
        }
        return configuration;
    }
}
