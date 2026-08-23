package com.onemb.cmiapi.xrayhunter.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

/** Owns sender-scoped investigation state for one XRayHunter feature lifecycle. */
public final class InvestigationSessionRegistry {
    private final Map<String, InvestigationSession> sessions = new ConcurrentHashMap<>();

    public InvestigationSession get(@NonNull CommandSender sender) {
        return sessions.computeIfAbsent(sender.getName(), ignored -> new InvestigationSession());
    }

    public int size() {
        return sessions.size();
    }

    public void clear() {
        sessions.clear();
    }
}
