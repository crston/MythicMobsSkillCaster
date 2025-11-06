package com.gmail.bobason01;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/**
 * MMSkillsExpansion
 * - %mmskills_var_key% 플레이스홀더 제공
 * - MythicMobs이 null player context로 호출할 때도 fallback 처리
 * - 극한의 성능 중심 최적화 (동기 접근, 동적 캐시)
 */
public final class MMSkillsExpansion extends PlaceholderExpansion {

    private static volatile UUID lastCasterId;

    public static void setLastCaster(Player p) {
        if (p != null) lastCasterId = p.getUniqueId();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mmskills";
    }

    @Override
    public @NotNull String getAuthor() {
        return "crston";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.2.5";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player p, @NotNull String params) {
        // MythicMobs은 종종 null player로 요청하므로 fallback 처리
        if (p == null && lastCasterId != null) {
            p = Bukkit.getPlayer(lastCasterId);
        }
        if (p == null || params.isEmpty()) {
            return "§c<no player>";
        }

        if (params.startsWith("var_") || params.startsWith("var.")) {
            String key = params.substring(4).toLowerCase();
            String val = ArgRegistry.get().get(p, key);
            if (val != null) {
                Bukkit.getLogger().info("[MMSkills] Placeholder request: " + key + "=" + val + " (" + p.getName() + ")");
                return val;
            } else {
                Bukkit.getLogger().warning("[MMSkills] Missing variable: " + key + " for " + p.getName());
                return "§7<undefined>";
            }
        }
        return "";
    }
}
