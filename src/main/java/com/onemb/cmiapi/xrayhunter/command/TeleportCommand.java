package com.onemb.cmiapi.xrayhunter.command;

import com.onemb.cmiapi.xrayhunter.XRayHunterFeature;
import com.onemb.cmiapi.xrayhunter.model.InvestigationSession;
import com.onemb.cmiapi.xrayhunter.model.OreVein;
import com.onemb.cmiapi.xrayhunter.util.LocationUtil;
import java.text.MessageFormat;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

/**
 * Supports teleporting to a cached vein location.
 */
public final class TeleportCommand {
    private final XRayHunterFeature feature;

    public TeleportCommand(XRayHunterFeature feature) {
        this.feature = feature;
    }

    public boolean execute(CommandSender sender, String @NonNull ... args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c/xrayhunter teleport can only be used in-game.");
            return true;
        }

        final InvestigationSession session = feature.getSession(sender);
        if (args.length == 1 && args[0].matches("\\d+")) {
            final int index = Integer.parseInt(args[0], 10);
            if (index < 1 || session.getVeins() == null || session.getVeins().size() < index) {
                sender.sendMessage("§cInvalid index supplied. Try running lookup again.");
            } else {
                safeTeleport(player, session.getVeins().get(index - 1));
            }
            return true;
        }

        sender.sendMessage("§cUsage: /xrayhunter teleport <index>");
        return true;
    }

    private void safeTeleport(Player player, @NonNull OreVein oreVein) {
        final Location veinLocation = oreVein.getLocation();
        if (veinLocation.getWorld() == null) {
            player.sendMessage("§cThat vein belongs to a CoreProtect archive world that is not currently loaded.");
            player.sendMessage("§7Load the world before using /xrayhunter teleport.");
            return;
        }
        final Location teleportLocation = LocationUtil.findSafeLocation(veinLocation, 7);
        if (teleportLocation != null) {
            final Location directionTarget = veinLocation.clone().subtract(teleportLocation);
            teleportLocation.setDirection(directionTarget.toVector());
            player.teleport(teleportLocation.add(0.5, 0, 0.5));
            return;
        }

        player.sendMessage(MessageFormat.format(
                "§cNo safe teleport location found near {0}",
                LocationUtil.asString(veinLocation)
        ));
    }
}
