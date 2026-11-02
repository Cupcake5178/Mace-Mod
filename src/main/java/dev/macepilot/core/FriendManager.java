package dev.macepilot.core;

import dev.macepilot.config.Settings;
import java.util.*;

public final class FriendManager {
    private final Settings settings;
    private String lastFriends, lastBlacklist;
    private Set<String> excluded = Set.of();
    public FriendManager(Settings settings) { this.settings = settings; }
    public boolean excluded(PlayerSnapshot player) {
        if (!Objects.equals(lastFriends, settings.friends) || !Objects.equals(lastBlacklist, settings.blacklist)) {
            lastFriends=settings.friends; lastBlacklist=settings.blacklist;
            Set<String> names = new HashSet<>();
            for (String s : (lastFriends+","+lastBlacklist).split("[,;\\s]+")) {
                if (!s.isBlank()) names.add(s.toLowerCase(Locale.ROOT));
            }
            excluded = Set.copyOf(names);
        }
        return excluded.contains(player.id().toString()) || excluded.contains(player.name().toLowerCase(Locale.ROOT));
    }
}

