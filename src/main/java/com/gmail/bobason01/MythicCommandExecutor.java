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
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class MythicCommandExecutor implements CommandExecutor, TabCompleter {

    private static final String USAGE_CAST = "§cUsage: /mmskills cast <skillName> [player]";
    private static final MythicBukkit mythic = MythicBukkit.inst();

    private static final long CACHE_INTERVAL = TimeUnit.SECONDS.toMillis(10);
    private long lastSkillCache = 0L, lastPlayerCache = 0L;
    private List<String> skillCache = Collections.emptyList();
    private List<String> playerCache = Collections.emptyList();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length < 2 || !args[0].equalsIgnoreCase("cast")) {
            sender.sendMessage(USAGE_CAST);
            return false;
        }

        final String skillName = args[1];
        final Player player = (args.length >= 3) ? Bukkit.getPlayerExact(args[2])
                : (sender instanceof Player ? (Player) sender : null);

        if (player == null || !player.isOnline()) {
            sender.sendMessage("§cPlayer not found or offline.");
            return false;
        }

        Optional<Skill> optSkill = mythic.getSkillManager().getSkill(skillName);
        if (optSkill.isEmpty()) {
            sender.sendMessage("§cSkill not found: §e" + skillName);
            return false;
        }

        AbstractEntity casterEntity = BukkitAdapter.adapt(player);
        SkillCaster caster = mythic.getSkillManager().getCaster(casterEntity);

        LivingEntity target = getEntityInFront(player);
        AbstractEntity targetEntity = (target != null) ? BukkitAdapter.adapt(target) : null;

        Set<AbstractEntity> entityTargets = (targetEntity != null)
                ? Set.of(targetEntity)
                : Collections.emptySet();

        AbstractLocation origin = BukkitAdapter.adapt(player.getLocation());

        SkillMetadata metadata = new SkillMetadataImpl(
                SkillTriggers.API,
                caster,
                targetEntity,
                origin,
                entityTargets,
                Collections.emptySet(),
                1.0f
        );

        optSkill.get().execute(metadata);
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return switch (args.length) {
            case 1 -> "cast".startsWith(args[0].toLowerCase())
                    ? List.of("cast")
                    : List.of();
            case 2 -> args[0].equalsIgnoreCase("cast") ? getSkillSuggestions(args[1]) : List.of();
            case 3 -> args[0].equalsIgnoreCase("cast") ? getPlayerSuggestions(args[2]) : List.of();
            default -> List.of();
        };
    }

    private List<String> getSkillSuggestions(String input) {
        long now = System.currentTimeMillis();
        if (now - lastSkillCache > CACHE_INTERVAL) {
            skillCache = mythic.getSkillManager().getSkills().stream()
                    .map(Skill::getInternalName)
                    .toList();
            lastSkillCache = now;
        }

        final String lowerInput = input.toLowerCase();
        return skillCache.stream()
                .filter(name -> name.toLowerCase().startsWith(lowerInput))
                .toList();
    }

    private List<String> getPlayerSuggestions(String input) {
        long now = System.currentTimeMillis();
        if (now - lastPlayerCache > CACHE_INTERVAL) {
            playerCache = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList();
            lastPlayerCache = now;
        }

        final String lowerInput = input.toLowerCase();
        return playerCache.stream()
                .filter(name -> name.toLowerCase().startsWith(lowerInput))
                .toList();
    }

    private @Nullable LivingEntity getEntityInFront(Player player) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        RayTraceResult result = player.getWorld().rayTraceEntities(
                eyeLoc, direction, 10,
                entity -> entity instanceof LivingEntity && !entity.equals(player)
        );

        return (result != null && result.getHitEntity() instanceof LivingEntity)
                ? (LivingEntity) result.getHitEntity()
                : null;
    }
}
