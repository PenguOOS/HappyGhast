
package com.example.ghastcall;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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

    private static final int MAX_DISTANCE = 16;
    private static final double INTERACTION_RANGE = 16.0;

    private final Map<UUID, BukkitRunnable> activeCalls =
            new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            setInteractionRange(player);
        }

        getLogger().info("GhastCall enabled with a 16-block range.");
    }

    @Override
    public void onDisable() {
        activeCalls.values().forEach(BukkitRunnable::cancel);
        activeCalls.clear();
    }

    private void setInteractionRange(Player player) {
        var attribute =
                player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);

        if (attribute != null) {
            attribute.setBaseValue(INTERACTION_RANGE);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        setInteractionRange(event.getPlayer());
    }

    private boolean holdingHarness(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return item.getType().name().endsWith("HARNESS");
    }

    // Detect a right-click on the air while aiming at a Happy Ghast.
    @EventHandler
    public void onRightClickAir(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        // Preserve normal harness use.
        if (holdingHarness(player)) return;

        RayTraceResult result =
                player.rayTraceEntities(MAX_DISTANCE, false);

        if (result == null) return;

        Entity target = result.getHitEntity();

        if (target == null
                || target.getType() != EntityType.HAPPY_GHAST) {
            return;
        }

        callGhast(player, target);
    }

    // Handle close-range clicks without cancelling the interaction.
    @EventHandler
    public void onRightClickHappyGhast(
            PlayerInteractEntityEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity ghast = event.getRightClicked();
        Player player = event.getPlayer();

        if (ghast.getType() != EntityType.HAPPY_GHAST) return;
        if (holdingHarness(player)) return;

        callGhast(player, ghast);
    }

    private void callGhast(Player player, Entity ghast) {
        if (!player.isOnline() || !ghast.isValid()) return;

        UUID ghastId = ghast.getUniqueId();
        UUID playerId = player.getUniqueId();

        BukkitRunnable previous = activeCalls.remove(ghastId);
        if (previous != null) {
            previous.cancel();
        }

        BukkitRunnable task = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                Entity currentGhast = Bukkit.getEntity(ghastId);
                Player currentPlayer = Bukkit.getPlayer(playerId);

                if (currentGhast == null
                        || currentPlayer == null
                        || !currentGhast.isValid()
                        || !currentPlayer.isOnline()
                        || ticks++ >= 100) {

                    activeCalls.remove(ghastId, this);
                    cancel();
                    return;
                }

                Location from =
                        currentGhast.getLocation().add(0, 0.6, 0);
                Location to =
                        currentPlayer.getLocation().add(0, 1.0, 0);

                Vector delta =
                        to.toVector().subtract(from.toVector());

                double distance = delta.length();

                if (distance < 2.5) {
                    currentGhast.setVelocity(new Vector(0, 0, 0));
                    activeCalls.remove(ghastId, this);
                    cancel();
                    return;
                }

                Vector velocity = delta.normalize().multiply(
                        Math.min(0.65, 0.18 + distance * 0.025)
                );

                currentGhast.setVelocity(velocity);
            }
        };

        activeCalls.put(ghastId, task);
        task.runTaskTimer(this, 0L, 1L);
    }
}
