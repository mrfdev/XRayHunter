package com.onemb.cmiapi.xrayhunter;

import com.onemb.cmiapi.xrayhunter.coreprotect.CoreProtectLookupService;
import com.onemb.cmiapi.xrayhunter.model.InvestigationSession;
import com.onemb.cmiapi.xrayhunter.model.InvestigationSessionRegistry;
import java.io.File;
import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Module-owned lifecycle and state for X-ray investigations.
 *
 * <p>This class owns all temporary sessions, caches, configuration, and worker
 * resources. The standalone plugin is only one possible host.
 */
public final class XRayHunterFeature {
    public static final String FEATURE_ID = "xrayhunter";
    public static final String BUNDLED_CONFIGURATION_RESOURCE = FEATURE_ID + "/config.yml";
    public static final String BUNDLED_BUILD_INFO_RESOURCE = FEATURE_ID + "/build-info.properties";

    private final JavaPlugin host;
    private final XRayHunterFeatureLayout layout;
    private final XRayHunterConfiguration configurationManager;
    private final InvestigationSessionRegistry sessions;
    private final MiningHistory miningHistory;
    private final AtomicLong lifecycleGeneration = new AtomicLong();

    private @Nullable BuildInfo buildInfo;
    private @Nullable YamlConfiguration configuration;
    private @Nullable XRayHunterSettings settings;
    private @Nullable ExecutorService lookupExecutor;
    private @Nullable CoreProtectAPI coreProtectApi;
    private volatile boolean active;

    public XRayHunterFeature(JavaPlugin host) {
        this(host, XRayHunterFeatureLayout.standalone(host), CoreProtectLookupService::new);
    }

    /**
     * Creates a feature for a standalone or combined host.
     *
     * @param host Paper plugin that owns scheduler tasks and bundled resources
     * @param layout mapping from bundled feature resources to installed config
     * @param miningHistoryFactory integration factory for authoritative history
     */
    public XRayHunterFeature(
            JavaPlugin host,
            XRayHunterFeatureLayout layout,
            MiningHistoryFactory miningHistoryFactory
    ) {
        this.host = Objects.requireNonNull(host, "host");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.configurationManager = new XRayHunterConfiguration(
                host,
                layout.configurationFile(),
                layout.bundledConfigurationResource()
        );
        this.sessions = new InvestigationSessionRegistry();
        this.miningHistory = Objects.requireNonNull(
                Objects.requireNonNull(miningHistoryFactory, "miningHistoryFactory").create(this),
                "miningHistoryFactory returned null"
        );
    }

    /** Starts this feature and returns whether its required CoreProtect hook is available. */
    public boolean start() {
        if (active) {
            return true;
        }

        try {
            buildInfo = BuildInfo.load(host, layout.bundledBuildInfoResource());
            lookupExecutor = Executors.newSingleThreadExecutor(runnable -> {
                final Thread thread = new Thread(runnable, "1MB-XRayHunter-Lookup");
                thread.setDaemon(true);
                return thread;
            });
            reloadConfiguration();

            coreProtectApi = findCoreProtectApi();
            if (coreProtectApi == null) {
                getLogger().severe("CoreProtect was not found or did not expose a supported API version.");
                getLogger().severe("This add-on accepts CoreProtect API 7 through 12; the maintained target is API 11 or 12 (CoreProtect 24.0-dev1).");
                shutdown();
                return false;
            }

            active = true;
            refreshMiningHistoryMetadata();
            if (getSettings().startupSelfCheckEnabled()) {
                runStartupSelfCheck();
            }
            return true;
        } catch (RuntimeException | LinkageError exception) {
            getLogger().log(Level.SEVERE, "Unable to start the XRayHunter feature", exception);
            shutdown();
            return false;
        }
    }

    /** Releases only XRayHunter-owned state so a future sibling module can remain active. */
    public void shutdown() {
        active = false;
        lifecycleGeneration.incrementAndGet();
        final ExecutorService executor = lookupExecutor;
        lookupExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        try {
            miningHistory.clearCaches();
        } catch (RuntimeException exception) {
            getLogger().log(Level.WARNING, "Unable to clear XRayHunter mining-history caches", exception);
        }
        sessions.clear();
        coreProtectApi = null;
        settings = null;
        configuration = null;
        buildInfo = null;
    }

    public void reloadConfiguration() {
        configuration = configurationManager.reload();
        settings = XRayHunterSettings.load(this, configuration);
        miningHistory.clearCaches();
        if (active) {
            refreshMiningHistoryMetadata();
        }
    }

    public YamlConfiguration getConfiguration() {
        return Objects.requireNonNull(configuration, "feature configuration is not loaded");
    }

    public void saveConfiguration() {
        if (configuration != null) {
            configurationManager.save(configuration);
        }
    }

    public BuildInfo getBuildInfo() {
        return Objects.requireNonNull(buildInfo, "feature build information is not loaded");
    }

    public XRayHunterSettings getSettings() {
        return Objects.requireNonNull(settings, "feature settings are not loaded");
    }

    public List<Material> getLookupMaterials(World.Environment environment) {
        return getSettings().getLookupMaterials(environment);
    }

    public List<Material> getDisplayMaterials(World.Environment environment) {
        return getSettings().getDisplayMaterials(environment);
    }

    public MiningHistory getMiningHistory() {
        return miningHistory;
    }

    public InvestigationSession getSession(CommandSender sender) {
        return sessions.get(sender);
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public boolean isActive() {
        return active && host.isEnabled();
    }

    public boolean isActive(long expectedLifecycleGeneration) {
        return lifecycleGeneration.get() == expectedLifecycleGeneration && isActive();
    }

    public long getLifecycleGeneration() {
        return lifecycleGeneration.get();
    }

    public boolean runLookupTaskAsync(Runnable task) {
        return runLookupTaskAsync(getLifecycleGeneration(), task);
    }

    public boolean runLookupTaskAsync(long expectedLifecycleGeneration, Runnable task) {
        final ExecutorService executor = lookupExecutor;
        if (!isActive(expectedLifecycleGeneration) || executor == null || executor.isShutdown()) {
            return false;
        }
        try {
            executor.execute(() -> {
                if (isActive(expectedLifecycleGeneration)) {
                    task.run();
                }
            });
            return true;
        } catch (RejectedExecutionException ignored) {
            return false;
        }
    }

    public void runOnMainThread(Runnable task) {
        runOnMainThread(getLifecycleGeneration(), task);
    }

    public void runOnMainThread(long expectedLifecycleGeneration, Runnable task) {
        if (!isActive(expectedLifecycleGeneration)) {
            return;
        }
        try {
            getServer().getScheduler().runTask(host, () -> {
                if (isActive(expectedLifecycleGeneration)) {
                    task.run();
                }
            });
        } catch (IllegalPluginAccessException ignored) {
            // The Paper host began disabling between the active check and task submission.
        }
    }

    public Server getServer() {
        return host.getServer();
    }

    public Logger getLogger() {
        return host.getLogger();
    }

    public File getDataFolder() {
        return layout.dataFolder();
    }

    public File getConfigurationFile() {
        return layout.configurationFile();
    }

    public @Nullable CoreProtectAPI getCoreProtectApi() {
        return coreProtectApi;
    }

    public boolean isCoreProtectHooked() {
        return coreProtectApi != null;
    }

    public @Nullable Plugin getCoreProtectPlugin() {
        return getServer().getPluginManager().getPlugin("CoreProtect");
    }

    public @Nullable File getCoreProtectDatabaseFile() {
        final Plugin coreProtectPlugin = getCoreProtectPlugin();
        return coreProtectPlugin == null ? null : new File(coreProtectPlugin.getDataFolder(), "database.db");
    }

    public String getCoreProtectPluginVersion() {
        final Plugin coreProtectPlugin = getCoreProtectPlugin();
        return coreProtectPlugin == null ? "unavailable" : coreProtectPlugin.getPluginMeta().getVersion();
    }

    private @Nullable CoreProtectAPI findCoreProtectApi() {
        final Plugin plugin = getCoreProtectPlugin();
        if (!(plugin instanceof CoreProtect coreProtectPlugin)) {
            return null;
        }

        final CoreProtectAPI api = coreProtectPlugin.getAPI();
        if (!api.isEnabled()) {
            return null;
        }

        return switch (api.APIVersion()) {
            case 7, 8, 9, 10, 11, 12 -> api;
            default -> null;
        };
    }

    private void runStartupSelfCheck() {
        final BuildInfo info = getBuildInfo();
        final XRayHunterSettings currentSettings = getSettings();
        final @Nullable File databaseFile = getCoreProtectDatabaseFile();
        getLogger().info(MessageFormat.format(
                "Loaded {0} v{1} (build {2}) targeting Paper {3}, compiled against Paper API {4}, and declaring plugin api-version {5}.",
                info.pluginName(),
                info.pluginVersion(),
                info.buildNumber(),
                info.paperApiTarget(),
                info.paperApiCompileVersion(),
                info.pluginApiVersion()
        ));
        getLogger().info(MessageFormat.format(
                "CoreProtect hooked: {0} (plugin {1}, API {2}).",
                Boolean.toString(isCoreProtectHooked()),
                getCoreProtectPluginVersion(),
                coreProtectApi == null ? "unavailable" : Integer.toString(coreProtectApi.APIVersion())
        ));
        getLogger().info(MessageFormat.format(
                "Default lookup time: {0}; top results: {1}; detail page size: {2}.",
                currentSettings.defaultLookupTime(),
                Integer.toString(currentSettings.topResults()),
                Integer.toString(currentSettings.detailPageSize())
        ));
        getLogger().info("Excluded players: " + (currentSettings.excludedPlayers().isEmpty()
                ? "none"
                : String.join(", ", currentSettings.excludedPlayers())));
        getLogger().info("Overworld tracking: " + joinMaterials(currentSettings.overworldDisplayMaterials()));
        getLogger().info("Nether tracking: " + joinMaterials(currentSettings.netherDisplayMaterials()));
        if (databaseFile != null) {
            getLogger().info("CoreProtect database: " + databaseFile.getAbsolutePath());
        }
    }

    private String joinMaterials(List<Material> materials) {
        return materials.stream()
                .map(material -> material.name().toLowerCase())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    private void refreshMiningHistoryMetadata() {
        final XRayHunterSettings currentSettings = getSettings();
        final LinkedHashSet<Material> materials = new LinkedHashSet<>(currentSettings.overworldLookupMaterials());
        materials.addAll(currentSettings.netherLookupMaterials());
        miningHistory.refreshMetadata(materials);
    }
}
