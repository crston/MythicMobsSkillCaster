package com.gmail.bobason01;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class MythicMobsSkillCaster extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Plugin has been enabled.");
        Objects.requireNonNull(getCommand("mmskills")).setExecutor(new MythicCommandExecutor());
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin has been disabled.");
    }
}
