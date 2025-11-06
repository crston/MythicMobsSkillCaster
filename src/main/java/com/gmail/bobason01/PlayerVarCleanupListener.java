package com.gmail.bobason01;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerVarCleanupListener implements Listener {

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        ArgRegistry.get().clear(e.getPlayer());
    }
}
