package dk.lockfuglsang.xrayhunter;

import dk.lockfuglsang.xrayhunter.command.MainCommand;
import java.io.File;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Bukkit plugin for hunting suspicious ore-mining patterns using CoreProtect data.
 */
public class XRayHunter extends JavaPlugin {
    private static @Nullable CoreProtectAPI api;

    private BuildInfo buildInfo;
    private PluginSettings settings;
    private ExecutorService lookupExecutor;

    public static @Nullable CoreProtectAPI getCoreProtectAPI() {
        return api;
    }

    @Override
    public void onEnable() {
        buildInfo = BuildInfo.load(this);
        lookupExecutor = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "1MB-XRayHunter-Lookup");
            thread.setDaemon(true);
            return thread;
        });
        saveDefaultConfig();
        reloadPluginConfiguration();

        api = getCoreProtect();
        if (api == null) {
            getLogger().severe("CoreProtect was not found or did not expose a supported API version.");
            getLogger().severe("This add-on requires CoreProtect API version 11 (CoreProtect 23.4 target).");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        final MainCommand mainCommand = new MainCommand(this);
        final PluginCommand command = Objects.requireNonNull(getCommand("xrayhunter"), "xrayhunter command missing");
        command.setExecutor(mainCommand);
        command.setTabCompleter(mainCommand);

        if (settings.startupSelfCheckEnabled()) {
            runStartupSelfCheck();
        }
    }

    @Override
    public void onDisable() {
        if (lookupExecutor != null) {
            lookupExecutor.shutdownNow();
            lookupExecutor = null;
        }
        api = null;
    }

    public void reloadPluginConfiguration() {
        reloadConfig();
        settings = PluginSettings.load(this);
        dk.lockfuglsang.xrayhunter.coreprotect.CoreProtectDatabaseLookup.clearCaches();
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    public void runLookupTaskAsync(Runnable task) {
        if (lookupExecutor == null || lookupExecutor.isShutdown()) {
            return;
        }
        lookupExecutor.execute(task);
    }

    public PluginSettings getSettings() {
        return settings;
    }

    public List<Material> getLookupMaterials(World.Environment environment) {
        return settings.getLookupMaterials(environment);
    }

    public List<Material> getDisplayMaterials(World.Environment environment) {
        return settings.getDisplayMaterials(environment);
    }

    public boolean isCoreProtectHooked() {
        return api != null;
    }

    public @Nullable Plugin getCoreProtectPlugin() {
        return getServer().getPluginManager().getPlugin("CoreProtect");
    }

    public @Nullable File getCoreProtectDatabaseFile() {
        final Plugin coreProtectPlugin = getCoreProtectPlugin();
        if (coreProtectPlugin == null) {
            return null;
        }
        return new File(coreProtectPlugin.getDataFolder(), "database.db");
    }

    public String getCoreProtectPluginVersion() {
        final Plugin coreProtectPlugin = getCoreProtectPlugin();
        return coreProtectPlugin == null ? "unavailable" : coreProtectPlugin.getPluginMeta().getVersion();
    }

    private void runStartupSelfCheck() {
        final @Nullable File databaseFile = getCoreProtectDatabaseFile();
        getLogger().info(MessageFormat.format(
                "Loaded {0} v{1} (build {2}) compiled against Paper API {3} and declaring plugin api-version floor {4}.",
                buildInfo.pluginName(),
                buildInfo.pluginVersion(),
                buildInfo.buildNumber(),
                buildInfo.paperApiCompileTarget(),
                buildInfo.pluginApiCompatibilityFloor()
        ));
        getLogger().info(MessageFormat.format(
                "CoreProtect hooked: {0} (plugin {1}, API {2}).",
                Boolean.toString(isCoreProtectHooked()),
                getCoreProtectPluginVersion(),
                api == null ? "unavailable" : Integer.toString(api.APIVersion())
        ));
        getLogger().info(MessageFormat.format(
                "Default lookup time: {0}; top results: {1}; detail page size: {2}.",
                settings.defaultLookupTime(),
                Integer.toString(settings.topResults()),
                Integer.toString(settings.detailPageSize())
        ));
        getLogger().info("Excluded players: " + (settings.excludedPlayers().isEmpty() ? "none" : String.join(", ", settings.excludedPlayers())));
        getLogger().info("Overworld tracking: " + joinMaterials(settings.overworldDisplayMaterials()));
        getLogger().info("Nether tracking: " + joinMaterials(settings.netherDisplayMaterials()));
        if (databaseFile != null) {
            getLogger().info("CoreProtect database: " + databaseFile.getAbsolutePath());
        }
    }

    private @Nullable CoreProtectAPI getCoreProtect() {
        final Plugin plugin = getCoreProtectPlugin();
        if (!(plugin instanceof CoreProtect coreProtectPlugin)) {
            return null;
        }

        final CoreProtectAPI coreProtectApi = coreProtectPlugin.getAPI();
        if (!coreProtectApi.isEnabled()) {
            return null;
        }

        return switch (coreProtectApi.APIVersion()) {
            case 7, 8, 9, 10, 11 -> coreProtectApi;
            default -> null;
        };
    }

    private String joinMaterials(List<Material> materials) {
        return materials.stream()
                .map(material -> material.name().toLowerCase())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }
}
