package com.gmail.bobason01;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class MythicMobsSkillCaster extends JavaPlugin {

    private MythicCommandExecutor executor;
    private MMSkillsExpansion expansion;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            getLogger().severe("MythicMobs not found");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 등록: /mmskills
        this.executor = new MythicCommandExecutor();
        if (getCommand("mmskills") != null) {
            getCommand("mmskills").setExecutor(executor);
            getCommand("mmskills").setTabCompleter(executor);
        }

        // 등록: /crston
        if (getCommand("crston") != null) {
            getCommand("crston").setExecutor(new CrstonCommand());
        }

        // 이벤트 등록
        Bukkit.getPluginManager().registerEvents(new PlayerVarCleanupListener(), this);

        // PlaceholderAPI 확장 등록
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.expansion = new MMSkillsExpansion();
            this.expansion.register();
            getLogger().info("MythicMobsSkillCaster PlaceholderAPI hook enabled");
        }
    }

    @Override
    public void onDisable() {
        if (this.expansion != null) this.expansion.unregister();
        ArgRegistry.get().clearAll();
    }
}
