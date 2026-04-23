package dk.lockfuglsang.xrayhunter.command;

import dk.lockfuglsang.util.TimeUtil;
import dk.lockfuglsang.xrayhunter.XRayHunter;
import dk.lockfuglsang.xrayhunter.coreprotect.Callback;
import dk.lockfuglsang.xrayhunter.coreprotect.CoreProtectHandler;
import dk.lockfuglsang.xrayhunter.model.HuntSession;
import dk.lockfuglsang.xrayhunter.model.PlayerStats;
import dk.lockfuglsang.xrayhunter.model.PlayerStatsComparator;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * Looks up suspicious players within the requested time window.
 */
public final class LookupCommand {
    private static final double WORLD_LOOKUP_ANCHOR_X = 0.0D;
    private static final double WORLD_LOOKUP_ANCHOR_Y = 64.0D;
    private static final double WORLD_LOOKUP_ANCHOR_Z = 0.0D;

    private final XRayHunter plugin;

    public LookupCommand(XRayHunter plugin) {
        this.plugin = plugin;
    }

    public boolean execute(final CommandSender sender, String @NonNull ... args) {
        if (args.length > 2) {
            sender.sendMessage("§cUsage: §f/xrayhunter lookup [time] [world]");
            return true;
        }

        final String timeArgument = args.length >= 1 ? args[0] : plugin.getSettings().defaultLookupTime();
        final long millis = TimeUtil.millisFromString(timeArgument);
        if (millis == 0) {
            sender.sendMessage("§cInvalid time argument. Try §f2d§c, §f12h§c, or §f30m§c.");
            return true;
        }

        final @Nullable String worldName = args.length == 2 ? resolveWorldName(sender, args[1]) : null;
        if (args.length == 2 && worldName == null) {
            return true;
        }

        final Player player = sender instanceof Player ? (Player) sender : null;
        final World targetWorld = worldName != null
                ? plugin.getServer().getWorld(worldName)
                : player != null ? player.getWorld() : null;
        final boolean restrictWorld = targetWorld != null;
        final World.Environment environment = targetWorld == null ? null : targetWorld.getEnvironment();
        final List<Material> lookupMaterials = restrictWorld
                ? plugin.getLookupMaterials(environment)
                : combineMaterials(
                        plugin.getSettings().overworldLookupMaterials(),
                        plugin.getSettings().netherLookupMaterials()
                );
        final List<Material> displayMaterials = restrictWorld
                ? plugin.getDisplayMaterials(environment)
                : combineMaterials(
                        plugin.getSettings().overworldDisplayMaterials(),
                        plugin.getSettings().netherDisplayMaterials()
                );

        if (player == null && !restrictWorld) {
            if (!plugin.getSettings().consoleAllowServerWideLookups()) {
                sender.sendMessage("§cServer-wide console lookups are disabled in config.");
                sender.sendMessage("§7Use §f/xrayhunter lookup <time> <world>§7 instead.");
                return true;
            }
            if (millis > plugin.getSettings().consoleMaxAllWorldLookupMillis()) {
                sender.sendMessage("§cThat console lookup is too large for an all-world scan.");
                sender.sendMessage("§7Max all-world console window: §f" + plugin.getSettings().consoleMaxAllWorldLookupTime());
                sender.sendMessage("§7Use §f/xrayhunter lookup " + timeArgument + " <world>§7 for longer history.");
                return true;
            }
        }

        final Location lookupLocation = buildLookupLocation(targetWorld, player);

        if (player == null) {
            if (restrictWorld) {
                sender.sendMessage("§7Running a world-scoped lookup for §f" + targetWorld.getName() + "§7.");
            } else {
                sender.sendMessage("§7Running a server-wide lookup across all worlds.");
            }
        }

        CoreProtectHandler.performLookup(
                plugin,
                TimeUtil.millisAsSeconds(millis),
                lookupMaterials,
                lookupLocation,
                restrictWorld,
                new LookupCallback(sender, worldName, displayMaterials)
        );
        return true;
    }

    private List<Material> combineMaterials(List<Material> primary, List<Material> secondary) {
        final Set<Material> combined = new LinkedHashSet<>(primary);
        combined.addAll(secondary);
        return List.copyOf(combined);
    }

    private @Nullable String resolveWorldName(CommandSender sender, String rawWorldName) {
        final World directMatch = plugin.getServer().getWorld(rawWorldName);
        if (directMatch != null) {
            return directMatch.getName();
        }

        for (World world : plugin.getServer().getWorlds()) {
            if (world.getName().equalsIgnoreCase(rawWorldName)) {
                return world.getName();
            }
        }

        sender.sendMessage("§cThat world is not loaded on this server: §f" + rawWorldName);
        if (!plugin.getServer().getWorlds().isEmpty()) {
            sender.sendMessage("§7Loaded worlds: §f" + worldNameList());
        }
        return null;
    }

    private String worldNameList() {
        return plugin.getServer().getWorlds().stream()
                .map(World::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((left, right) -> left + "§7, §f" + right)
                .orElse("none");
    }

    private @Nullable Location buildLookupLocation(@Nullable World targetWorld, @Nullable Player player) {
        if (player != null && (targetWorld == null || targetWorld.equals(player.getWorld()))) {
            return player.getLocation();
        }
        if (targetWorld != null) {
            return new Location(targetWorld, WORLD_LOOKUP_ANCHOR_X, WORLD_LOOKUP_ANCHOR_Y, WORLD_LOOKUP_ANCHOR_Z);
        }
        return null;
    }

    private static void updateMap(Map<Material, Integer> blockCount, Material blockId) {
        final Material normalized = PlayerStatsComparator.normalize(blockId);
        blockCount.put(normalized, blockCount.getOrDefault(normalized, 0) + 1);
    }

    private static @NonNull String getBlockKey(CoreProtectAPI.@NonNull ParseResult parse) {
        return parse.worldName() + ":" + parse.getX() + "," + parse.getY() + "," + parse.getZ();
    }

    private final class LookupCallback extends Callback {
        private final CommandSender sender;
        private final @Nullable String worldName;
        private final List<Material> displayMaterials;

        private LookupCallback(CommandSender sender, @Nullable String worldName, List<Material> displayMaterials) {
            this.sender = sender;
            this.worldName = worldName;
            this.displayMaterials = List.copyOf(displayMaterials);
        }

        @Override
        public void run() {
            final List<String[]> result = getData();
            if (result == null || result.isEmpty()) {
                sendNoActivityMessage(sender);
                return;
            }

            final CoreProtectAPI coreProtectApi = XRayHunter.getCoreProtectAPI();
            if (coreProtectApi == null) {
                sender.sendMessage("§cCoreProtect is not currently hooked.");
                return;
            }

            final Map<String, Map<Material, Integer>> playerCount = new HashMap<>();
            final Map<String, List<CoreProtectAPI.ParseResult>> dataMap = new HashMap<>();
            final Map<String, Boolean> userPlacedBlocks = new HashMap<>();

            Collections.reverse(result);
            for (String[] line : result) {
                final CoreProtectAPI.ParseResult parse = coreProtectApi.parseResult(line);
                final int actionId = parse.getActionId();
                final String blockKey = getBlockKey(parse);
                if (actionId == CoreProtectHandler.ACTION_PLACE) {
                    userPlacedBlocks.put(blockKey, Boolean.TRUE);
                    continue;
                }

                if (actionId != CoreProtectHandler.ACTION_BREAK || userPlacedBlocks.containsKey(blockKey)) {
                    continue;
                }

                final String playerName = parse.getPlayer();
                if (worldName != null && (parse.worldName() == null || !parse.worldName().equalsIgnoreCase(worldName))) {
                    continue;
                }
                final Material blockType = parse.getType();
                playerCount.computeIfAbsent(playerName, ignored -> new HashMap<>());
                dataMap.computeIfAbsent(playerName, ignored -> new ArrayList<>());
                updateMap(playerCount.get(playerName), blockType);
                dataMap.get(playerName).add(parse);
            }

            final List<PlayerStats> topPlayers = new ArrayList<>();
            for (String playerName : playerCount.keySet()) {
                topPlayers.add(new PlayerStats(playerName, playerCount.get(playerName)));
            }

            if (topPlayers.isEmpty()) {
                sendNoActivityMessage(sender);
                return;
            }

            topPlayers.sort(new PlayerStatsComparator(displayMaterials));
            HuntSession.getSession(sender).setLookupCache(topPlayers).setUserData(dataMap);

            final StringBuilder builder = new StringBuilder("Listing");
            if (worldName != null) {
                builder.append(" §7(").append(worldName).append(")");
            } else if (!(sender instanceof Player)) {
                builder.append(" §7(all worlds)");
            }
            for (Material material : displayMaterials) {
                builder.append(PlayerStatsComparator.getColor(material))
                        .append("§l ")
                        .append(PlayerStatsComparator.getShortLabel(material));
            }
            builder.append("\n");

            int place = 1;
            final int maxResults = Math.min(topPlayers.size(), plugin.getSettings().topResults());
            for (PlayerStats stats : topPlayers.subList(0, maxResults)) {
                builder.append(MessageFormat.format("§7#{0}", place));
                for (Material material : displayMaterials) {
                    builder.append(PlayerStatsComparator.getColor(material))
                            .append(MessageFormat.format(" §l{0,number,##}§7({1,number,##}%)",
                                    stats.getCount(material),
                                    100 * stats.getRatio(material)));
                }
                builder.append(" §9").append(stats.getPlayer()).append("\n");
                place++;
            }

            sender.sendMessage(builder.toString().split("\n"));
        }

        private void sendNoActivityMessage(CommandSender sender) {
            if (sender instanceof Player player) {
                sender.sendMessage(MessageFormat.format(
                        "No suspicious activity within that time frame in {0}!",
                        worldName != null ? worldName : player.getWorld().getName()
                ));
            } else if (worldName != null) {
                sender.sendMessage("No suspicious activity within that time frame in " + worldName + "!");
            } else {
                sender.sendMessage("No suspicious activity within that time frame across any world.");
            }
        }
    }
}
