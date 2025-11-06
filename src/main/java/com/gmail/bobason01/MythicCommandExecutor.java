package com.gmail.bobason01;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.SkillMetadataImpl;
import io.lumine.mythic.core.skills.SkillTriggers;
import io.lumine.mythic.core.skills.variables.VariableRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class MythicCommandExecutor implements CommandExecutor, TabCompleter {

    private static final MythicBukkit MYTHIC = MythicBukkit.inst();
    private static final Logger LOG = Bukkit.getLogger();
    private static final long CACHE_DURATION = TimeUnit.SECONDS.toMillis(10);

    private long lastSkillCache = 0, lastPlayerCache = 0;
    private List<String> cachedSkills = Collections.emptyList();
    private List<String> cachedPlayers = Collections.emptyList();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 2 || !"cast".equalsIgnoreCase(args[0])) return false;
        if (!sender.hasPermission("mythicskills.use")) return true;

        String skillName = args[1];

        Player target = null;
        if (args.length >= 3) {
            String who = args[2];
            if ("self".equalsIgnoreCase(who) && sender instanceof Player sp) target = sp;
            else target = Bukkit.getPlayerExact(who);
        } else if (sender instanceof Player sp) {
            target = sp;
        }
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "player not found");
            return true;
        }

        double maxDistance = (args.length >= 4) ? fastParseDouble(args[3], 15.0) : 15.0;
        boolean checkBlocks = args.length >= 5 && Boolean.parseBoolean(args[4]);

        String[] extra = args.length > 5 ? Arrays.copyOfRange(args, 5, args.length) : new String[0];

        Skill skill = MYTHIC.getSkillManager().getSkill(skillName).orElse(null);
        if (skill == null) {
            sender.sendMessage(ChatColor.RED + "unknown skill " + skillName);
            return true;
        }

        try {
            parseVArgs(target, extra);
            MMSkillsExpansion.setLastCaster(target);
            castSkill(skill, target, maxDistance, checkBlocks, extra);
        } catch (Throwable t) {
            LOG.severe("error casting " + skillName + " for " + target.getName() + " " + t.getMessage());
            t.printStackTrace();
        }
        return true;
    }

    private void parseVArgs(@NotNull Player p, @NotNull String[] args) {
        for (String a : args) {
            if (a == null || a.length() < 3) continue;
            if (a.charAt(0) != 'v' && a.charAt(0) != 'V') continue;
            int idx = a.indexOf('=');
            if (idx <= 1 || idx >= a.length() - 1) continue;
            String key = a.substring(1, idx).trim().toLowerCase();
            String val = a.substring(idx + 1).trim();
            if (!key.isEmpty()) ArgRegistry.get().set(p, key, val);
        }
    }

    private void castSkill(@NotNull Skill skill, @NotNull Player player,
                           double maxDistance, boolean checkBlocks, @NotNull String[] argList) {

        AbstractEntity casterEntity = BukkitAdapter.adapt(player);
        SkillCaster caster = MYTHIC.getSkillManager().getCaster(casterEntity);
        if (caster == null) caster = new PlayerCaster(casterEntity);

        TargetResult hit = getTargetInFrontFast(player, maxDistance, checkBlocks);

        Set<io.lumine.mythic.api.adapters.AbstractEntity> targets = new HashSet<>(2, 1f);
        targets.add(casterEntity);
        if (hit.entity != null) targets.add(BukkitAdapter.adapt(hit.entity));

        AbstractLocation origin = BukkitAdapter.adapt(player.getLocation());
        Set<AbstractLocation> locs = hit.blockLocation != null
                ? Collections.singleton(BukkitAdapter.adapt(hit.blockLocation))
                : Collections.emptySet();

        SkillMetadata meta = new SkillMetadataImpl(
                SkillTriggers.API, caster, casterEntity, origin, targets, locs, 1.0f
        );

        if (argList.length > 0) injectArgN(meta.getVariables(), argList, player);

        if (skill.isUsable(meta)) skill.execute(meta);
    }

    private void injectArgN(@NotNull VariableRegistry vars, @NotNull String[] argList, @NotNull Player p) {
        for (int i = 0; i < argList.length; i++) {
            String raw = argList[i] == null ? "" : argList[i];
            if (raw.length() >= 2 && (raw.charAt(0) == 'v' || raw.charAt(0) == 'V') && raw.indexOf('=') > 1) continue;
            String parsed = parsePlaceholders(raw, p);
            String key = "arg" + i;
            vars.putString(key, parsed);
        }
    }

    private static String parsePlaceholders(String s, Player p) {
        if (s == null || s.isEmpty()) return "";
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, s);
        } catch (Throwable ignored) { }
        return s;
    }

    private @NotNull TargetResult getTargetInFrontFast(@NotNull Player p, double maxDist, boolean checkBlocks) {
        Location eye = p.getEyeLocation();
        Vector eyeVec = eye.toVector();
        Vector dir = eye.getDirection().normalize();
        World w = p.getWorld();

        LivingEntity best = null;
        double bestT = Double.MAX_VALUE;
        double max2 = maxDist * maxDist;

        for (Entity e : w.getNearbyEntities(eye, maxDist, maxDist, maxDist)) {
            if (!(e instanceof LivingEntity le) || le == p || !le.isValid()) continue;
            Vector center = le.getLocation().add(0, le.getHeight() * 0.5, 0).toVector();
            Vector delta = center.subtract(eyeVec);
            double t = delta.dot(dir);
            if (t <= 0 || t * t > max2) continue;
            double perp = delta.lengthSquared() - t * t;
            if (perp > 0.64) continue;
            if (checkBlocks && !p.hasLineOfSight(le)) continue;
            if (t < bestT) { bestT = t; best = le; }
        }

        Location block = null;
        if (checkBlocks) {
            RayTraceResult rt = w.rayTraceBlocks(eye, dir, maxDist, FluidCollisionMode.NEVER, false);
            if (rt != null && rt.getHitBlock() != null)
                block = rt.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        }
        return new TargetResult(best, block);
    }

    private record TargetResult(LivingEntity entity, Location blockLocation) { }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                                @NotNull String alias, @NotNull String[] args) {
        switch (args.length) {
            case 1 -> {
                String p = args[0].toLowerCase();
                return "cast".startsWith(p) ? List.of("cast") : List.of();
            }
            case 2 -> {
                if (!"cast".equalsIgnoreCase(args[0])) return List.of();
                String p = args[1].toLowerCase();
                List<String> list = getSkillNames();
                List<String> out = new ArrayList<>(list.size());
                for (String n : list) if (n.toLowerCase().startsWith(p)) out.add(n);
                return out;
            }
            case 3 -> {
                if (!"cast".equalsIgnoreCase(args[0])) return List.of();
                String p = args[2].toLowerCase();
                List<String> list = getPlayerNames();
                List<String> out = new ArrayList<>(list.size());
                out.add("self");
                for (String n : list) if (n.toLowerCase().startsWith(p)) out.add(n);
                return out;
            }
            case 4 -> { return List.of("5", "10", "15", "30"); }
            case 5 -> { return List.of("true", "false"); }
            default -> { return List.of("vDAMAGE=100", "vRANGE=5", "vCRIT=1.5"); }
        }
    }

    private List<String> getSkillNames() {
        long now = System.currentTimeMillis();
        if (now - lastSkillCache > CACHE_DURATION) {
            Collection<Skill> skills = MYTHIC.getSkillManager().getSkills();
            List<String> list = new ArrayList<>(skills.size());
            for (Skill s : skills) {
                String n = s.getInternalName();
                if (n != null && !n.isEmpty()) list.add(n);
            }
            cachedSkills = list;
            lastSkillCache = now;
        }
        return cachedSkills;
    }

    private List<String> getPlayerNames() {
        long now = System.currentTimeMillis();
        if (now - lastPlayerCache > CACHE_DURATION) {
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            List<String> list = new ArrayList<>(players.size());
            for (Player p : players) list.add(p.getName());
            cachedPlayers = list;
            lastPlayerCache = now;
        }
        return cachedPlayers;
    }

    private static double fastParseDouble(String s, double fallback) {
        try { return Double.parseDouble(s); } catch (Exception e) { return fallback; }
    }
}
