
package com.example.ghastcall;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GhastCallPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, BukkitRunnable> activeCalls =
            new ConcurrentHashMap<>();

    private static final int MAX_DISTANCE = 16;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("GhastCall enabled.");
    }

    @Override
    public void onDisable() {
        activeCalls.values().forEach(BukkitRunnable::cancel);
        activeCalls.clear();
    }

    private boolean holdingHarness(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        Material material = item.getType();
        return material.name().endsWith("HARNESS");
    }

    // Distant right-click: ray-trace the entity the player is looking at.
    @EventHandler
    public void onRightClickAir(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        // Do not treat using a harness as a call command.
        if (holdingHarness(player)) return;

        RayTraceResult result = player.rayTraceEntities(MAX_DISTANCE, false);
        if (result == null) return;

        Entity target = result.getHitEntity();
        if (target == null || target.getType() != EntityType.HAPPY_GHAST) {
            return;
        }

        if (!player.hasLineOfSight(target)) return;

        callGhast(player, target);
    }

    // Close-range entity clicks still work normally; never cancel the event.
    @EventHandler
    public void onRightClickHappyGhast(PlayerInteractEntityEvent event) {
        Entity ghast = event.getRightClicked();
        Player player = event.getPlayer();

        if (ghast.getType() != EntityType.HAPPY_GHAST) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        // Preserve normal harness interactions.
        if (holdingHarness(player)) return;

        callGhast(player, ghast);
    }

    private void callGhast(Player player, Entity ghast) {
        if (!player.isOnline() || !ghast.isValid()) return;

        UUID ghastId = ghast.getUniqueId();
        UUID playerId = player.getUniqueId();

        BukkitRunnable previous = activeCalls.remove(ghastId);
        if (previous != null) previous.cancel();

        BukkitRunnable task = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                Entity currentGhast = Bukkit.getEntity(ghastId);
                Player currentPlayer = Bukkit.getPlayer(playerId);

                if (currentGhast == null || currentPlayer == null
                        || !currentGhast.isValid()
                        || !currentPlayer.isOnline()
                        || ticks++ >= 100) {
                    activeCalls.remove(ghastId, this);
                    cancel();
                    return;
                }

                Location from = currentGhast.getLocation()
                        .add(0, 0.6, 0);
                Location to = currentPlayer.getLocation()
                        .add(0, 1.0, 0);

                Vector delta = to.toVector().subtract(from.toVector());
                double distance = delta.length();

                if (distance < 2.5) {
                    currentGhast.setVelocity(new Vector(0, 0, 0));
                    activeCalls.remove(ghastId, this);
                    cancel();
                    return;
                }

                Vector velocity = delta.normalize()
                        .multiply(Math.min(0.65, 0.18 + distance * 0.025));

                currentGhast.setVelocity(velocity);
            }
        };

        activeCalls.put(ghastId, task);
        task.runTaskTimer(this, 0L, 1L);
    }
}
