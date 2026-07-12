package dk.lockfuglsang.xrayhunter.command;

import dk.lockfuglsang.util.TimeUtil;
import dk.lockfuglsang.xrayhunter.BuildInfo;
import dk.lockfuglsang.xrayhunter.PluginSettings;
import dk.lockfuglsang.xrayhunter.XRayHunter;
import dk.lockfuglsang.xrayhunter.coreprotect.CoreProtectDatabaseLookup;
import dk.lockfuglsang.xrayhunter.model.HuntSession;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Primary /xrayhunter command dispatcher and debug/help surface.
 */
public class MainCommand implements CommandExecutor, TabCompleter {
    private static final String DOCS_URL = "https://docs.1moreblock.com/custom-server-plugins/1mb-xrayhunter/";
    private static final String DOCS_LABEL = "docs.1moreblock.com/custom-server-plugins/1mb-xrayhunter/";
    private static final String PERMISSION_USE = "xrayhunter.use";
    private static final String PERMISSION_ADMIN = "xrayhunter.admin";
    private static final String LEGACY_PERMISSION_USE = "xhunt.use";
    private static final String LEGACY_PERMISSION_ADMIN = "xhunt.admin";
    private static final List<String> DEBUG_SUBCOMMANDS = List.of("help", "permissions", "commands", "config", "set", "whitelist");
    private static final List<String> DEBUG_WHITELIST_ACTIONS = List.of("list", "add", "remove");

    private final XRayHunter plugin;
    private final LookupCommand lookupCommand;
    private final DetailCommand detailCommand;
    private final TeleportCommand teleportCommand;

    public MainCommand(XRayHunter plugin) {
        this.plugin = plugin;
        this.lookupCommand = new LookupCommand(plugin);
        this.detailCommand = new DetailCommand(plugin);
        this.teleportCommand = new TeleportCommand();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0 || isHelpToken(args[0])) {
            return sendHelp(sender);
        }

        if (isSubcommand(args[0], "info")) {
            return sendInfo(sender);
        }

        if (isSubcommand(args[0], "debug")) {
            return handleDebugCommand(sender, args);
        }

        if (isSubcommand(args[0], "reload")) {
            return handleReloadCommand(sender);
        }

        if (!hasUsePermission(sender)) {
            sender.sendMessage("§cYou do not have permission to use /xrayhunter.");
            return true;
        }

        if (isSubcommand(args[0], "lookup", "l")) {
            return lookupCommand.execute(sender, tail(args));
        }

        if (isSubcommand(args[0], "detail", "d")) {
            return detailCommand.execute(sender, tail(args));
        }

        if (isSubcommand(args[0], "teleport", "tp")) {
            return teleportCommand.execute(sender, tail(args));
        }

        if (lookupCommand.isTimeToken(args[0])) {
            return lookupCommand.execute(sender, args);
        }

        sender.sendMessage("§cUnknown subcommand: §f" + args[0]);
        sender.sendMessage("§7Try §f/xrayhunter help§7.");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            final List<String> suggestions = new ArrayList<>();
            suggestions.add("help");
            suggestions.add("info");
            if (hasUsePermission(sender)) {
                suggestions.addAll(List.of("lookup", "detail", "teleport"));
                suggestions.addAll(List.of("2d", "7d", "30d"));
            }
            if (hasAdminPermission(sender)) {
                suggestions.add("debug");
                suggestions.add("reload");
            }
            return filterSuggestions(suggestions, args[0]);
        }

        if (args.length >= 2 && isSubcommand(args[0], "debug")) {
            if (!hasAdminPermission(sender)) {
                return List.of();
            }
            if (args.length == 2) {
                return filterSuggestions(DEBUG_SUBCOMMANDS, args[1]);
            }
            if (args.length == 3 && isSubcommand(args[1], "set")) {
                return filterSuggestions(PluginSettings.logicalConfigKeys(), args[2]);
            }
            if (args.length == 4 && isSubcommand(args[1], "set")) {
                return filterSuggestions(getConfigValueSuggestions(args[2]), args[3]);
            }
            if (args.length == 3 && isSubcommand(args[1], "whitelist")) {
                final List<String> suggestions = new ArrayList<>(DEBUG_WHITELIST_ACTIONS);
                suggestions.addAll(plugin.getSettings().excludedPlayers());
                return filterSuggestions(suggestions, args[2]);
            }
            if (args.length == 4 && isSubcommand(args[1], "whitelist", "wl")) {
                if (isSubcommand(args[2], "remove", "delete", "del")) {
                    return filterSuggestions(plugin.getSettings().excludedPlayers(), args[3]);
                }
                return List.of();
            }
            return List.of();
        }

        if (args.length == 2 && isSubcommand(args[0], "lookup", "l")) {
            return filterSuggestions(buildLookupSuggestions(), args[1]);
        }

        if (args.length >= 3 && args.length <= 4 && isSubcommand(args[0], "lookup", "l")) {
            return filterSuggestions(buildLookupSuggestions(), args[args.length - 1]);
        }

        if (args.length == 2 && lookupCommand.isTimeToken(args[0])) {
            return filterSuggestions(buildLookupSuggestions(), args[1]);
        }

        if (args.length == 3 && lookupCommand.isTimeToken(args[0])) {
            return filterSuggestions(buildLookupSuggestions(), args[2]);
        }

        if (args.length == 2 && isSubcommand(args[0], "detail", "d")) {
            return filterSuggestions(List.of("1", "2", "3", "4", "5"), args[1]);
        }

        if (args.length == 2 && isSubcommand(args[0], "teleport", "tp")) {
            return filterSuggestions(List.of("1", "2", "3", "4", "5"), args[1]);
        }

        return List.of();
    }

    private boolean sendHelp(CommandSender sender) {
        final BuildInfo buildInfo = plugin.getBuildInfo();
        sender.sendMessage("§6# §e1MB-XRayHunter Commands");
        sender.sendMessage("§f/xrayhunter help §7- Show this command summary.");
        sender.sendMessage("§f/xrayhunter info §7- Show a short introduction, quick-start hints, and the docs link.");
        sender.sendMessage("§f/xrayhunter lookup [time|alltime] [world|allworlds] [-all] §7- Scan CoreProtect block breaks in one world or across all worlds, including database-only worlds.");
        sender.sendMessage("§f/xrayhunter <time|alltime> §7- Shortcut for the same lookup command.");
        sender.sendMessage("§f/xrayhunter detail <index|player> [page] §7- Show cached vein details for one lookup result in chat or console.");
        sender.sendMessage("§f/xrayhunter teleport <index> §7- Teleport to one cached vein location.");
        sender.sendMessage("§7Default lookup time: §f" + plugin.getSettings().defaultLookupTime());
        sender.sendMessage(MessageFormat.format(
                "§7Build metadata: §fPaper API {0} §7/ plugin api-version floor §f{1} §7/ CoreProtect §f{2} §7/ Java §f{3}",
                buildInfo.paperApiCompileTarget(),
                buildInfo.pluginApiCompatibilityFloor(),
                buildInfo.coreProtectTarget(),
                buildInfo.javaTarget()
        ));
        sender.sendMessage("§7Console implicit all-world limit: §f" + plugin.getSettings().consoleMaxAllWorldLookupTime());
        sender.sendMessage("§7Explicit archive scan: §f/xrayhunter lookup alltime allworlds");
        sender.sendMessage("§7Console compact view: §f" + (plugin.getSettings().consoleHighValueOnly() ? "high-value only §7(use §f-all§7 to expand)" : "all tracked columns"));
        sender.sendMessage("§7Known CoreProtect worlds: §f" + Integer.toString(lookupCommand.getWorldNameSuggestions().size()));
        sender.sendMessage("§7Aliases: §fxhunt§7, §fxr");
        if (hasAdminPermission(sender)) {
            sender.sendMessage("§f/xrayhunter debug §7- Show plugin and CoreProtect status details.");
            sender.sendMessage("§f/xrayhunter debug help §7- Show debug subcommands.");
            sender.sendMessage("§f/xrayhunter debug permissions §7- List permission nodes.");
            sender.sendMessage("§f/xrayhunter debug commands §7- List commands and examples.");
            sender.sendMessage("§f/xrayhunter debug config §7- Show active config values and tracked material lists.");
            sender.sendMessage("§f/xrayhunter debug set <key> <value> §7- Save a supported config value and reload it.");
            sender.sendMessage("§f/xrayhunter debug whitelist <player> §7- Add a vetted player to filters.excluded-players and reload.");
            sender.sendMessage("§f/xrayhunter reload §7- Reload config.yml.");
        }
        return true;
    }

    private boolean sendInfo(CommandSender sender) {
        final BuildInfo buildInfo = plugin.getBuildInfo();
        sender.sendMessage("§6# §e1MB-XRayHunter");
        sender.sendMessage("§7CoreProtect add-on for suspicious mining lookups, cached vein review, and archive-friendly X-ray triage.");
        sender.sendMessage("§7Start with §f/xrayhunter help§7 for the full command list.");
        sender.sendMessage("§7Quick start: §f/xrayhunter 2d§7, then §f/xrayhunter detail 1§7 after you have a result.");
        if (hasAdminPermission(sender)) {
            sender.sendMessage("§7Admin tools: §f/xrayhunter debug§7, §f/xrayhunter debug config§7, §f/xrayhunter debug whitelist <player>§7.");
        }
        sender.sendRichMessage("<gray>Docs: <click:open_url:'" + DOCS_URL + "'><aqua><u>" + DOCS_LABEL + "</u></aqua></click>");
        sender.sendMessage(MessageFormat.format(
                "§7Installed: §f{0} §7(build §f{1}§7) / Paper API §f{2}§7 / plugin api-version floor §f{3}",
                buildInfo.pluginVersion(),
                buildInfo.buildNumber(),
                buildInfo.paperApiCompileTarget(),
                buildInfo.pluginApiCompatibilityFloor()
        ));
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage("§cYou do not have permission to reload this plugin.");
            return true;
        }

        plugin.reloadPluginConfiguration();
        sender.sendMessage("§aReloaded §fplugins/1MB-XRayHunter/config.yml§a.");
        return true;
    }

    private boolean handleDebugCommand(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage("§cYou do not have permission to use /xrayhunter debug.");
            return true;
        }

        if (args.length == 1) {
            sendDebugOverview(sender);
            return true;
        }

        if (isHelpToken(args[1])) {
            sendDebugHelp(sender);
            return true;
        }

        if (isSubcommand(args[1], "permissions")) {
            sendDebugPermissions(sender);
            return true;
        }

        if (isSubcommand(args[1], "commands")) {
            sendDebugCommands(sender);
            return true;
        }

        if (isSubcommand(args[1], "config")) {
            sendDebugConfig(sender);
            return true;
        }

        if (isSubcommand(args[1], "whitelist", "wl")) {
            return handleDebugWhitelist(sender, args);
        }

        if (isSubcommand(args[1], "set")) {
            return handleDebugSet(sender, args);
        }

        sender.sendMessage("§cNo such debug page: §f" + args[1]);
        sender.sendMessage("§7Try §f/xrayhunter debug help§7.");
        return true;
    }

    private void sendDebugOverview(CommandSender sender) {
        final BuildInfo buildInfo = plugin.getBuildInfo();
        final Plugin coreProtectPlugin = plugin.getCoreProtectPlugin();
        final File configFile = new File(plugin.getDataFolder(), "config.yml");
        final @Nullable File databaseFile = plugin.getCoreProtectDatabaseFile();

        sender.sendMessage("§6# §e1MB-XRayHunter Debug");
        sender.sendMessage(cleanMetaLine("Plugin name", buildInfo.pluginName()));
        sender.sendMessage(cleanMetaLine("Plugin version", buildInfo.fullVersion()));
        sender.sendMessage(cleanMetaLine("Base version", buildInfo.pluginVersion()));
        sender.sendMessage(cleanMetaLine("Build number", buildInfo.buildNumber()));
        sender.sendMessage(cleanMetaLine("Compiled Java target", buildInfo.javaTarget()));
        sender.sendMessage(cleanMetaLine("Compiled against Paper API", buildInfo.paperApiCompileTarget()));
        sender.sendMessage(cleanMetaLine("Declared plugin api-version floor", buildInfo.pluginApiCompatibilityFloor()));
        sender.sendMessage(cleanMetaLine("Running Java", System.getProperty("java.version", "unknown")));
        sender.sendMessage(cleanMetaLine("Server", plugin.getServer().getVersion()));
        sender.sendMessage(cleanMetaLine("Data folder", plugin.getDataFolder().getAbsolutePath()));
        sender.sendMessage(cleanMetaLine("Config file", configFile.getAbsolutePath()));
        sender.sendMessage(cleanMetaLine("CoreProtect hooked", Boolean.toString(plugin.isCoreProtectHooked())));
        sender.sendMessage(cleanMetaLine("CoreProtect target", buildInfo.coreProtectTarget()));
        if (coreProtectPlugin != null) {
            sender.sendMessage(cleanMetaLine("CoreProtect version", coreProtectPlugin.getPluginMeta().getVersion()));
            sender.sendMessage(cleanMetaLine("CoreProtect API", XRayHunter.getCoreProtectAPI() == null ? "unavailable" : Integer.toString(XRayHunter.getCoreProtectAPI().APIVersion())));
            sender.sendMessage(cleanMetaLine("CoreProtect data folder", coreProtectPlugin.getDataFolder().getAbsolutePath()));
        }
        sender.sendMessage(cleanMetaLine("CoreProtect database", databaseFile == null ? "unavailable" : databaseFile.getAbsolutePath()));
        sender.sendMessage(cleanMetaLine("Latest tracked block data", formatTrackedTimestamp(CoreProtectDatabaseLookup.safeLatestTrackedTime(
                mergeMaterials(
                        plugin.getSettings().overworldLookupMaterials(),
                        plugin.getSettings().netherLookupMaterials()
                )
        ))));
        sender.sendMessage(cleanMetaLine("Console high-value-only", Boolean.toString(plugin.getSettings().consoleHighValueOnly())));
        sender.sendMessage(cleanMetaLine("Excluded players", plugin.getSettings().excludedPlayers().isEmpty() ? "none" : Integer.toString(plugin.getSettings().excludedPlayers().size())));
        sender.sendMessage(cleanMetaLine("Known CoreProtect worlds", Integer.toString(lookupCommand.getWorldNameSuggestions().size())));
        sender.sendMessage(cleanMetaLine("Cached hunt sessions", Integer.toString(HuntSession.getSessionCount())));
        sender.sendMessage(cleanMetaLine("Summary cache entries", Integer.toString(CoreProtectDatabaseLookup.getSummaryCacheSize())));
        sender.sendMessage(cleanMetaLine("Summary cache hits", Long.toString(CoreProtectDatabaseLookup.getSummaryCacheHitCount())));
        sender.sendMessage(cleanMetaLine("Summary cache misses", Long.toString(CoreProtectDatabaseLookup.getSummaryCacheMissCount())));
        sender.sendMessage(cleanMetaLine("Default lookup time", plugin.getSettings().defaultLookupTime()));
        sender.sendMessage(cleanMetaLine("Top results", Integer.toString(plugin.getSettings().topResults())));
        sender.sendMessage(cleanMetaLine("Detail page size", Integer.toString(plugin.getSettings().detailPageSize())));
    }

    private void sendDebugHelp(CommandSender sender) {
        sender.sendMessage("§6# §e1MB-XRayHunter Debug Help");
        sender.sendMessage("§f/xrayhunter debug §7- Show plugin, build, and CoreProtect status.");
        sender.sendMessage("§f/xrayhunter debug help §7- Show this page.");
        sender.sendMessage("§f/xrayhunter debug permissions §7- List permission nodes and aliases.");
        sender.sendMessage("§f/xrayhunter debug commands §7- Show commands and examples.");
        sender.sendMessage("§f/xrayhunter debug config §7- Show active config values and tracked materials.");
        sender.sendMessage("§f/xrayhunter debug set <key> <value> §7- Save a supported config value and reload.");
        sender.sendMessage("§f/xrayhunter debug whitelist [player|list|add|remove] §7- Manage filters.excluded-players and reload.");
    }

    private void sendDebugPermissions(CommandSender sender) {
        sender.sendMessage("§6# §e1MB-XRayHunter Permissions");
        sender.sendMessage("§fxrayhunter.use §7- Allows /xrayhunter lookup, detail, and teleport.");
        sender.sendMessage("§fxrayhunter.admin §7- Allows /xrayhunter debug and /xrayhunter reload.");
        sender.sendMessage("§fxhunt.use §7- Legacy alias for xrayhunter.use.");
        sender.sendMessage("§fxhunt.admin §7- Legacy alias for xrayhunter.admin.");
    }

    private void sendDebugCommands(CommandSender sender) {
        sender.sendMessage("§6# §e1MB-XRayHunter Commands");
        sender.sendMessage("§f/xrayhunter help");
        sender.sendMessage("§f/xrayhunter info");
        sender.sendMessage("§f/xrayhunter lookup [time|alltime] [world|allworlds] [-all]");
        sender.sendMessage("§f/xrayhunter <time|alltime>");
        sender.sendMessage("§f/xrayhunter detail <index|player> [page]");
        sender.sendMessage("§f/xrayhunter teleport <index>");
        sender.sendMessage("§f/xrayhunter debug");
        sender.sendMessage("§f/xrayhunter debug help");
        sender.sendMessage("§f/xrayhunter debug permissions");
        sender.sendMessage("§f/xrayhunter debug commands");
        sender.sendMessage("§f/xrayhunter debug config");
        sender.sendMessage("§f/xrayhunter debug set <key> <value>");
        sender.sendMessage("§f/xrayhunter debug whitelist <player>");
        sender.sendMessage("§f/xrayhunter debug whitelist list");
        sender.sendMessage("§f/xrayhunter debug whitelist remove <player>");
        sender.sendMessage("§f/xrayhunter reload");
        sender.sendMessage("§7Examples:");
        sender.sendMessage("§f/xrayhunter 7d");
        sender.sendMessage("§f/xrayhunter lookup 30d");
        sender.sendMessage("§f/xrayhunter lookup 7d §7(console = all worlds within safe limit)");
        sender.sendMessage("§f/xrayhunter lookup 30d -all §7(console = widen compact report for one query)");
        sender.sendMessage("§f/xrayhunter lookup alltime allworlds §7(console = explicit full archive scan)");
        sender.sendMessage("§f/xrayhunter lookup 365d allworlds §7(console = explicit large archive scan)");
        sender.sendMessage("§f/xrayhunter lookup 120d wild §7(console = database world)");
        sender.sendMessage("§f/xrayhunter lookup 120d wild -all §7(console = database world with every tracked column)");
        sender.sendMessage("§f/xrayhunter lookup 1000d spawn §7(console = one loaded world)");
        sender.sendMessage("§f/xrayhunter detail 1");
        sender.sendMessage("§f/xrayhunter teleport 2");
        sender.sendMessage("§f/xrayhunter debug set display.top-results 15");
        sender.sendMessage("§f/xrayhunter debug set console.high-value-only true");
        sender.sendMessage("§f/xrayhunter debug set console.max-all-world-lookup-time 7d");
        sender.sendMessage("§f/xrayhunter debug whitelist fumblehead");
        sender.sendMessage("§f/xrayhunter debug whitelist remove fumblehead");
    }

    private void sendDebugConfig(CommandSender sender) {
        sender.sendMessage("§6# §e1MB-XRayHunter Config");
        sender.sendMessage(cleanMetaLine("startup.self-check-enabled", Boolean.toString(plugin.getSettings().startupSelfCheckEnabled())));
        sender.sendMessage(cleanMetaLine("defaults.lookup-time", plugin.getSettings().defaultLookupTime()));
        sender.sendMessage(cleanMetaLine("display.top-results", Integer.toString(plugin.getSettings().topResults())));
        sender.sendMessage(cleanMetaLine("display.detail-page-size", Integer.toString(plugin.getSettings().detailPageSize())));
        sender.sendMessage(cleanMetaLine("console.allow-server-wide-lookups", Boolean.toString(plugin.getSettings().consoleAllowServerWideLookups())));
        sender.sendMessage(cleanMetaLine("console.high-value-only", Boolean.toString(plugin.getSettings().consoleHighValueOnly())));
        sender.sendMessage(cleanMetaLine("console.max-all-world-lookup-time", plugin.getSettings().consoleMaxAllWorldLookupTime()));
        sender.sendMessage(cleanMetaLine(
                "console.high-value-display-materials",
                joinMaterials(plugin.getSettings().consoleHighValueDisplayMaterials())
        ));
        sender.sendMessage(cleanMetaLine(
                "filters.excluded-players",
                joinValues(plugin.getSettings().excludedPlayers())
        ));
        sender.sendMessage(cleanMetaLine(
                "tracking.overworld.lookup-materials",
                joinMaterials(plugin.getSettings().overworldLookupMaterials())
        ));
        sender.sendMessage(cleanMetaLine(
                "tracking.overworld.display-materials",
                joinMaterials(plugin.getSettings().overworldDisplayMaterials())
        ));
        sender.sendMessage(cleanMetaLine(
                "tracking.nether.lookup-materials",
                joinMaterials(plugin.getSettings().netherLookupMaterials())
        ));
        sender.sendMessage(cleanMetaLine(
                "tracking.nether.display-materials",
                joinMaterials(plugin.getSettings().netherDisplayMaterials())
        ));
        sender.sendMessage(cleanMetaLine(
                "tracking.normalization",
                "deepslate ore variants are tracked in lookup-materials and merged into base ore rows for display"
        ));
    }

    private boolean handleDebugSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: §f/xrayhunter debug set <key> <value>");
            sender.sendMessage("§7Supported keys: §f" + String.join("§7, §f", PluginSettings.logicalConfigKeys()));
            return true;
        }

        final String key = args[2];
        final String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
        final @Nullable Object parsedValue = parseConfigValue(key, value);
        if (parsedValue == null) {
            sender.sendMessage("§cUnsupported key or invalid value for §f" + key + "§c.");
            sender.sendMessage("§7Supported keys: §f" + String.join("§7, §f", PluginSettings.logicalConfigKeys()));
            return true;
        }

        plugin.getPluginConfiguration().set(key, parsedValue);
        plugin.savePluginConfiguration();
        plugin.reloadPluginConfiguration();
        sender.sendMessage("§aSaved §f" + key + "§a = §f" + value + "§a and reloaded the config.");
        return true;
    }

    private boolean handleDebugWhitelist(CommandSender sender, String[] args) {
        if (args.length == 2 || (args.length == 3 && isHelpToken(args[2]))) {
            sendDebugWhitelistUsage(sender);
            return true;
        }

        if (args.length == 3 && isSubcommand(args[2], "list")) {
            sendWhitelistEntries(sender);
            return true;
        }

        if (args.length == 3) {
            return addWhitelistEntry(sender, args[2]);
        }

        if (args.length == 4 && isSubcommand(args[2], "add")) {
            return addWhitelistEntry(sender, args[3]);
        }

        if (args.length == 4 && isSubcommand(args[2], "remove", "delete", "del")) {
            return removeWhitelistEntry(sender, args[3]);
        }

        sender.sendMessage("§cUsage: §f/xrayhunter debug whitelist <player>");
        sender.sendMessage("§7Also available: §f/xrayhunter debug whitelist list§7, §f... add <player>§7, §f... remove <player>");
        return true;
    }

    private boolean addWhitelistEntry(CommandSender sender, String rawPlayerName) {
        final @Nullable String playerName = normalizeWhitelistEntry(rawPlayerName);
        if (playerName == null) {
            sender.sendMessage("§cWhitelist entries must be a normal player name without spaces or a leading #.");
            return true;
        }

        final List<String> excludedPlayers = new ArrayList<>(plugin.getSettings().excludedPlayers());
        if (excludedPlayers.stream().anyMatch(playerName::equalsIgnoreCase)) {
            sender.sendMessage("§e" + playerName + " is already in filters.excluded-players.");
            return true;
        }

        excludedPlayers.add(playerName);
        excludedPlayers.sort(String.CASE_INSENSITIVE_ORDER);
        saveExcludedPlayers(excludedPlayers);
        sender.sendMessage("§aAdded §f" + playerName + "§a to filters.excluded-players and reloaded the config.");
        return true;
    }

    private boolean removeWhitelistEntry(CommandSender sender, String rawPlayerName) {
        final @Nullable String playerName = normalizeWhitelistEntry(rawPlayerName);
        if (playerName == null) {
            sender.sendMessage("§cWhitelist entries must be a normal player name without spaces or a leading #.");
            return true;
        }

        final List<String> excludedPlayers = new ArrayList<>(plugin.getSettings().excludedPlayers());
        final boolean removed = excludedPlayers.removeIf(entry -> entry.equalsIgnoreCase(playerName));
        if (!removed) {
            sender.sendMessage("§e" + playerName + " is not currently in filters.excluded-players.");
            return true;
        }

        excludedPlayers.sort(String.CASE_INSENSITIVE_ORDER);
        saveExcludedPlayers(excludedPlayers);
        sender.sendMessage("§aRemoved §f" + playerName + "§a from filters.excluded-players and reloaded the config.");
        return true;
    }

    private void sendWhitelistEntries(CommandSender sender) {
        final List<String> excludedPlayers = plugin.getSettings().excludedPlayers();
        sender.sendMessage("§6# §e1MB-XRayHunter Whitelist");
        sender.sendMessage("§7Config key: §ffilters.excluded-players");
        sender.sendMessage("§7Pseudo-users with §f#§7 are ignored automatically and do not need to be listed.");
        if (excludedPlayers.isEmpty()) {
            sender.sendMessage("§7Entries: §fnone");
            return;
        }
        sender.sendMessage("§7Entries (§f" + excludedPlayers.size() + "§7): §f" + String.join("§7, §f", excludedPlayers));
    }

    private void sendDebugWhitelistUsage(CommandSender sender) {
        sender.sendMessage("§6# §e1MB-XRayHunter Whitelist");
        sender.sendMessage("§f/xrayhunter debug whitelist <player> §7- Add a vetted player to filters.excluded-players.");
        sender.sendMessage("§f/xrayhunter debug whitelist list §7- Show current whitelist entries.");
        sender.sendMessage("§f/xrayhunter debug whitelist add <player> §7- Explicit add form.");
        sender.sendMessage("§f/xrayhunter debug whitelist remove <player> §7- Remove a whitelist entry.");
        sender.sendMessage("§7Pseudo-users with §f#§7 are ignored automatically and do not need to be added.");
    }

    private void saveExcludedPlayers(List<String> excludedPlayers) {
        plugin.getPluginConfiguration().set("filters.excluded-players", excludedPlayers);
        plugin.savePluginConfiguration();
        plugin.reloadPluginConfiguration();
    }

    private @Nullable String normalizeWhitelistEntry(String rawPlayerName) {
        if (rawPlayerName == null || rawPlayerName.isBlank()) {
            return null;
        }

        final String normalized = rawPlayerName.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("#") || normalized.contains(" ")) {
            return null;
        }
        return normalized;
    }

    private @Nullable Object parseConfigValue(String key, String value) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "startup.self-check-enabled" -> parseBoolean(value);
            case "defaults.lookup-time" -> TimeUtil.millisFromString(value) > 0 ? value : null;
            case "display.top-results", "display.detail-page-size" -> parsePositiveInteger(value);
            case "console.allow-server-wide-lookups" -> parseBoolean(value);
            case "console.high-value-only" -> parseBoolean(value);
            case "console.max-all-world-lookup-time" -> TimeUtil.millisFromString(value) > 0 ? value : null;
            default -> null;
        };
    }

    private @Nullable Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        return null;
    }

    private @Nullable Integer parsePositiveInteger(String value) {
        try {
            final int parsed = Integer.parseInt(value, 10);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean hasUsePermission(CommandSender sender) {
        return sender.hasPermission(PERMISSION_USE)
                || sender.hasPermission(LEGACY_PERMISSION_USE)
                || sender.hasPermission(PERMISSION_ADMIN)
                || sender.hasPermission(LEGACY_PERMISSION_ADMIN);
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission(PERMISSION_ADMIN) || sender.hasPermission(LEGACY_PERMISSION_ADMIN);
    }

    private boolean isHelpToken(String token) {
        return isSubcommand(token, "help", "?");
    }

    private boolean isSubcommand(String input, String... names) {
        for (String name : names) {
            if (name.equalsIgnoreCase(input)) {
                return true;
            }
        }
        return false;
    }

    private String[] tail(String[] args) {
        return Arrays.copyOfRange(args, 1, args.length);
    }

    private List<String> filterSuggestions(List<String> suggestions, String token) {
        final String needle = token.toLowerCase(Locale.ROOT);
        return suggestions.stream()
                .filter(suggestion -> suggestion.toLowerCase(Locale.ROOT).startsWith(needle))
                .distinct()
                .toList();
    }

    private List<String> getConfigValueSuggestions(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "startup.self-check-enabled" -> List.of("true", "false");
            case "defaults.lookup-time" -> List.of("2d", "7d", "30d", "90d");
            case "display.top-results" -> List.of("5", "10", "15", "20");
            case "display.detail-page-size" -> List.of("5", "10", "15", "20");
            case "console.allow-server-wide-lookups" -> List.of("true", "false");
            case "console.high-value-only" -> List.of("true", "false");
            case "console.max-all-world-lookup-time" -> List.of("1d", "7d", "30d");
            default -> List.of();
        };
    }

    private List<String> buildLookupSuggestions() {
        final List<String> suggestions = new ArrayList<>(List.of("2d", "7d", "30d", "90d", "365d", "alltime", "allworlds", "-all"));
        suggestions.addAll(lookupCommand.getWorldNameSuggestions());
        return suggestions;
    }

    private String cleanMetaLine(String label, String value) {
        return "§7" + label + ": §f" + value;
    }

    private String formatTrackedTimestamp(long epochSeconds) {
        if (epochSeconds <= 0) {
            return "unknown";
        }
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                .format(java.time.Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneId.systemDefault()));
    }

    private List<Material> mergeMaterials(List<Material> primary, List<Material> secondary) {
        final List<Material> merged = new ArrayList<>(primary);
        for (Material value : secondary) {
            if (!merged.contains(value)) {
                merged.add(value);
            }
        }
        return merged;
    }

    private String joinMaterials(List<?> materials) {
        return materials.stream()
                .map(Object::toString)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    private String joinValues(List<?> values) {
        return values.stream()
                .map(Object::toString)
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }
}
