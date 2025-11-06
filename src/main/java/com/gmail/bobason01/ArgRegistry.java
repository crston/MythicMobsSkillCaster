package com.gmail.bobason01;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArgRegistry {

    private static final ArgRegistry INSTANCE = new ArgRegistry();
    private final Map<UUID, Map<String, String>> data = new ConcurrentHashMap<>(128, 0.75f, 2);

    private ArgRegistry() { }

    public static ArgRegistry get() { return INSTANCE; }

    public void set(Player p, String key, String value) {
        if (p == null || key == null || value == null) return;
        data.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>(8, 0.75f, 2))
                .put(key, value);
        Bukkit.getLogger().info("[MMSkills] Saved variable " + key + "=" + value + " for " + p.getName());
    }


    public String get(Player p, String key) {
        Map<String, String> map = data.get(p.getUniqueId());
        if (map == null) return null;
        return map.get(key);
    }

    public void clear(Player p) {
        data.remove(p.getUniqueId());
    }

    public void clearAll() {
        data.clear();
    }
}
