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
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class MythicCommandExecutor implements CommandExecutor, TabCompleter {

    private static final MythicBukkit mythic = MythicBukkit.inst();
    private static final Logger logger = Bukkit.getLogger();
    private static final long CACHE_DURATION = TimeUnit.SECONDS.toMillis(10);
    private long lastSkillCache = 0, lastPlayerCache = 0;
    private List<String> cachedSkills = Collections.emptyList();
    private List<String> cachedPlayers = Collections.emptyList();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("cast")) {
            return false;
        }

        String skillName = args[1];
        Player targetPlayer = (args.length >= 3)
                ? Bukkit.getPlayerExact(args[2])
                : (sender instanceof Player p ? p : null);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            return false;
        }

        double maxDistance = (args.length >= 4) ? parseDouble(args[3], 15.0) : 15.0;
        boolean checkBlocks = (args.length >= 5) ? Boolean.parseBoolean(args[4]) : true;

        Skill skill = mythic.getSkillManager().getSkill(skillName).orElse(null);
        if (skill == null) {
            return false;
        }

        try {
            castSkill(skill, targetPlayer, maxDistance, checkBlocks);
        } catch (Exception e) {
            logger.severe("Error executing skill '" + skillName + "' for player '" + targetPlayer.getName() + "': " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void castSkill(@NotNull Skill skill, @NotNull Player player, double maxDistance, boolean checkBlocks) {
        AbstractEntity casterEntity = BukkitAdapter.adapt(player);
        SkillCaster caster = mythic.getSkillManager().getCaster(casterEntity);

        TargetResult result = getTargetInFrontFast(player, maxDistance, checkBlocks);

        Set<AbstractEntity> targets = new HashSet<>(2);
        targets.add(casterEntity);
        if (result.entity != null) targets.add(BukkitAdapter.adapt(result.entity));

        AbstractLocation origin = BukkitAdapter.adapt(player.getLocation());

        Set<AbstractLocation> targetLocations = new HashSet<>(1);
        if (result.blockLocation != null) targetLocations.add(BukkitAdapter.adapt(result.blockLocation));

        SkillMetadata metadata = new SkillMetadataImpl(
                SkillTriggers.API,
                caster,
                casterEntity,
                origin,
                targets,
                targetLocations,
                1.0f
        );

        if (skill.isUsable(metadata)) {
            skill.execute(metadata);
        }
    }

    private static class TargetResult {
        final LivingEntity entity;
        final Location blockLocation;
        TargetResult(LivingEntity e, Location b) {
            this.entity = e;
            this.blockLocation = b;
        }
    }

    private @NotNull TargetResult getTargetInFrontFast(@NotNull Player player, double maxDistance, boolean checkBlocks) {
        Location eyeLoc = player.getEyeLocation();
        Vector eye = eyeLoc.toVector();
        Vector dir = eyeLoc.getDirection().normalize();
        World world = player.getWorld();

        LivingEntity best = null;
        double bestT = Double.MAX_VALUE;

        for (Entity e : world.getNearbyEntities(eyeLoc, maxDistance, maxDistance, maxDistance)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le == player) continue;
            if (!le.isValid()) continue;

            Location leEyeLoc = le.getLocation();
            if (le instanceof Player p && p.isOnline()) leEyeLoc = p.getEyeLocation();

            Vector v = leEyeLoc.toVector().subtract(eye);
            double t = v.dot(dir);
            if (t <= 0 || t > maxDistance) continue;

            double perpSq = v.lengthSquared() - t * t;
            double radius = 0.8;
            if (perpSq > radius * radius) continue;

            if (checkBlocks && !player.hasLineOfSight(le)) continue;

            if (t < bestT) {
                bestT = t;
                best = le;
            }
        }

        Location blockLoc = null;
        if (checkBlocks) {
            RayTraceResult r = world.rayTraceBlocks(eyeLoc, dir, maxDistance, FluidCollisionMode.NEVER, false);
            if (r != null && r.getHitBlock() != null) {
                blockLoc = r.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
            }
        }

        return new TargetResult(best, blockLoc);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String alias, @NotNull String[] args) {
        switch (args.length) {
            case 1: {
                String p = args[0].toLowerCase();
                return "cast".startsWith(p) ? List.of("cast") : Collections.emptyList();
            }
            case 2: {
                if (!args[0].equalsIgnoreCase("cast")) return Collections.emptyList();
                String p = args[1].toLowerCase();
                List<String> list = getSkillNames();
                ArrayList<String> out = new ArrayList<>(list.size());
                for (String s : list) {
                    if (s != null && s.toLowerCase().startsWith(p)) out.add(s);
                }
                return out;
            }
            case 3: {
                if (!args[0].equalsIgnoreCase("cast")) return Collections.emptyList();
                String p = args[2].toLowerCase();
                List<String> list = getPlayerNames();
                ArrayList<String> out = new ArrayList<>(list.size());
                for (String s : list) {
                    if (s != null && s.toLowerCase().startsWith(p)) out.add(s);
                }
                return out;
            }
            case 4: {
                return List.of("5", "10", "15", "30");
            }
            case 5: {
                return List.of("true", "false");
            }
            default:
                return Collections.emptyList();
        }
    }

    private List<String> getSkillNames() {
        long now = System.currentTimeMillis();
        if (now - lastSkillCache > CACHE_DURATION) {
            Collection<Skill> skills = mythic.getSkillManager().getSkills();
            ArrayList<String> list = new ArrayList<>(skills.size());
            for (Skill sk : skills) {
                String n = sk.getInternalName();
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
            ArrayList<String> list = new ArrayList<>(players.size());
            for (Player p : players) {
                if (p != null && p.isOnline()) list.add(p.getName());
            }
            cachedPlayers = list;
            lastPlayerCache = now;
        }
        return cachedPlayers;
    }

    private double parseDouble(String input, double fallback) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
