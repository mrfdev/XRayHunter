package com.onemb.cmiapi.xrayhunter.command;

import com.onemb.cmiapi.xrayhunter.XRayHunterFeature;
import com.onemb.cmiapi.xrayhunter.model.InvestigationSession;
import com.onemb.cmiapi.xrayhunter.model.LookupContext;
import com.onemb.cmiapi.xrayhunter.model.OreVein;
import com.onemb.cmiapi.xrayhunter.model.PlayerStats;
import com.onemb.cmiapi.xrayhunter.model.PlayerStatsComparator;
import com.onemb.cmiapi.xrayhunter.util.LocationUtil;
import com.onemb.cmiapi.xrayhunter.util.TimeUtil;
import java.text.MessageFormat;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

/**
 * Shows ore vein details for a selected player.
 */
public final class DetailCommand {
    private final XRayHunterFeature feature;

    public DetailCommand(XRayHunterFeature feature) {
        this.feature = feature;
    }

    public boolean execute(CommandSender sender, String @NonNull ... args) {
        final InvestigationSession session = feature.getSession(sender);
        if (args.length >= 1 && args[0].matches("\\d+")) {
            final int index = Integer.parseInt(args[0], 10);
            if (index < 1 || session.getLookupCache() == null || session.getLookupCache().size() < index) {
                sender.sendMessage("§cInvalid index supplied. Try running a lookup again.");
            } else {
                final PlayerStats playerStats = session.getLookupCache().get(index - 1);
                session.setPlayerStat(playerStats);
                final int page = args.length >= 2 && args[1].matches("\\d+") ? Integer.parseInt(args[1], 10) : 1;
                showDetails(sender, playerStats, session, page);
            }
            return true;
        }

        if (args.length >= 1) {
            final PlayerStats playerStats = session.getPlayerStats(args[0]);
            if (playerStats != null) {
                final int page = args.length >= 2 && args[1].matches("\\d+") ? Integer.parseInt(args[1], 10) : 1;
                showDetails(sender, playerStats, session, page);
            } else {
                sender.sendMessage(MessageFormat.format("§cNo player named {0} found in cache. Try running lookup again.", args[0]));
            }
            return true;
        }

        if (session.getVeins() != null && session.getPlayerStats() != null) {
            showVeins(sender, session.getPlayerStats(), session.getVeins(), 1);
            return true;
        }

        sender.sendMessage("§cNo player selected. Use a player name or lookup index first.");
        return true;
    }

    private void showDetails(CommandSender sender, @NonNull PlayerStats playerStats, @NonNull InvestigationSession session, int page) {
        final LookupContext lookupContext = session.getLookupContext();
        if (lookupContext == null) {
            sender.sendMessage(MessageFormat.format("§cNo lookup context found for {0}. Try running lookup again.", playerStats.getPlayer()));
            return;
        }

        session.setVeins(null);
        sender.sendMessage(MessageFormat.format("§7Loading cached detail data for §f{0}§7...", playerStats.getPlayer()));
        feature.getMiningHistory().performPlayerVeinLookup(lookupContext, playerStats.getPlayer(), result -> {
            if (result.hasError()) {
                sender.sendMessage("§c" + result.errorMessage());
                return;
            }
            session.setVeins(result.veins());
            showVeins(sender, playerStats, result.veins(), page);
        });
    }

    private void showVeins(CommandSender sender, @NonNull PlayerStats playerStats, @NonNull List<OreVein> veins, int page) {
        if (veins.isEmpty()) {
            sender.sendMessage(MessageFormat.format("§eNo tracked veins cached for {0}.", playerStats.getPlayer()));
            return;
        }

        final int pageSize = feature.getSettings().detailPageSize();
        final int maxPage = (veins.size() - 1) / pageSize + 1;
        final int currentPage = Math.max(1, Math.min(page, maxPage));
        final int startIndex = (currentPage - 1) * pageSize;
        final int endIndex = Math.min(startIndex + pageSize, veins.size());

        final StringBuilder builder = new StringBuilder();
        builder.append(MessageFormat.format(
                "Showing what {0} has found §9({1}/{2})",
                playerStats.getPlayer(),
                currentPage,
                maxPage
        )).append("\n");

        long previousTime = System.currentTimeMillis();
        int displayIndex = startIndex + 1;
        for (OreVein vein : veins.subList(startIndex, endIndex)) {
            builder.append(MessageFormat.format(
                    "§7#{5,number} {0}: §9found §e{1} {2}{3}§9 ores at {4}",
                    TimeUtil.millisAsString(previousTime - vein.getTime()),
                    vein.getSize(),
                    PlayerStatsComparator.getColor(vein.getType()),
                    PlayerStatsComparator.getShortLabel(vein.getType()),
                    LocationUtil.asShortString(vein.getLocation()),
                    displayIndex++
            )).append("\n");
            previousTime = vein.getTime();
        }

        sender.sendMessage(builder.toString().split("\n"));
    }
}
