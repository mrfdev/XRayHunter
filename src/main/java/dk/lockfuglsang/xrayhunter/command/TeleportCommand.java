package dk.lockfuglsang.xrayhunter.command;

import dk.lockfuglsang.util.LocationUtil;
import dk.lockfuglsang.xrayhunter.model.HuntSession;
import dk.lockfuglsang.xrayhunter.model.OreVein;
import java.text.MessageFormat;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

/**
 * Supports teleporting to a cached vein location.
 */
public final class TeleportCommand {
    public boolean execute(CommandSender sender, String @NonNull ... args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c/xrayhunter teleport can only be used in-game.");
            return true;
        }

        final HuntSession session = HuntSession.getSession(sender);
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
