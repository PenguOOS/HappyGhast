package com.example.ghastcall;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GhastCallPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, BukkitRunnable> activeCalls = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("GhastCall enabled. Right-click a Happy Ghast to call it toward you.");
    }

    @Override
    public void onDisable() {
        activeCalls.values().forEach(BukkitRunnable::cancel);
        activeCalls.clear();
    }

    @EventHandler
    public void onRightClickHappyGhast(PlayerInteractEntityEvent event) {
        Entity ghast = event.getRightClicked();
        Player player = event.getPlayer();

        if (ghast.getType() != EntityType.HAPPY_GHAST) return;
        if (!player.isOnline() || !ghast.isValid()) return;

        // Right-clicking the entity is the confirmation; the server only sends this
        // event when the player has targeted and interacted with that entity.
        event.setCancelled(true);

        BukkitRunnable previous = activeCalls.remove(ghast.getUniqueId());
        if (previous != null) previous.cancel();

        UUID ghastId = ghast.getUniqueId();
        UUID playerId = player.getUniqueId();
        BukkitRunnable task = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                Entity currentGhast = Bukkit.getEntity(ghastId);
                Player currentPlayer = Bukkit.getPlayer(playerId);
                if (currentGhast == null || currentPlayer == null || !currentGhast.isValid()
                        || !currentPlayer.isOnline() || ticks++ >= 100) {
                    activeCalls.remove(ghastId);
                    cancel();
                    return;
                }

                Location from = currentGhast.getLocation().add(0, 0.6, 0);
                Location to = currentPlayer.getLocation().add(0, 1.0, 0);
                Vector delta = to.toVector().subtract(from.toVector());
                double distance = delta.length();
                if (distance < 2.5) {
                    currentGhast.setVelocity(new Vector(0, 0, 0));
                    activeCalls.remove(ghastId);
                    cancel();
                    return;
                }

                // Reapply a modest velocity each tick so the ghast keeps approaching
                // rather than receiving only a single impulse. GhastQoL can remain
                // installed; this plugin does not change its speed configuration.
                Vector velocity = delta.normalize().multiply(Math.min(0.65, 0.18 + distance * 0.025));
                currentGhast.setVelocity(velocity);
            }
        };
        activeCalls.put(ghastId, task);
        task.runTaskTimer(this, 0L, 1L);
    }
}
