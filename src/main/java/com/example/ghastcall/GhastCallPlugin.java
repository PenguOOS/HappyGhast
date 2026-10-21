
package com.example.ghastcall;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GhastCallPlugin extends JavaPlugin implements Listener {

    private static final double CALL_RANGE = 32.0;
    private static final double NORMAL_REACH = 3.0;

    private final Map<UUID, BukkitRunnable> activeCalls =
            new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            setInteractionRange(player);
        }

        getLogger().info("GhastCall enabled. Call range: 32 blocks.");
    }

    @Override
    public void onDisable() {
        activeCalls.values().forEach(BukkitRunnable::cancel);
        activeCalls.clear();
    }

    /*
     * The client needs extended interaction range to send a distant
     * entity-click packet. The event handlers below prevent long-range
     * attacks and long-range mounting/interactions.
     */
    private void setInteractionRange(Player player) {
        var attribute =
                player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);

        if (attribute != null) {
            attribute.setBaseValue(CALL_RANGE);
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

    /*
     * Handle clicks on Happy Ghasts.
     * Far away: call the ghast and cancel mounting.
     * Normal reach: preserve vanilla interactions and harness mounting.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Entity target = event.getRightClicked();

        double distance = distanceToEntity(player, target);

        if (distance > NORMAL_REACH) {
            // Block all distant entity interactions except calling
            // a Happy Ghast.
            event.setCancelled(true);

            if (target.getType() == EntityType.HAPPY_GHAST
                    && distance <= CALL_RANGE) {
                callGhast(player, target);
            }

            return;
        }

        // At normal range, preserve normal mounting with a harness.
        // Empty-hand clicks can still call the Happy Ghast.
        if (target.getType() == EntityType.HAPPY_GHAST
                && !holdingHarness(player)) {
            callGhast(player, target);
        }
    }

    /*
     * Prevent extended interaction range from allowing long-range
     * melee attacks against players or other entities.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        Entity target = event.getEntity();

        if (distanceToEntity(player, target) > NORMAL_REACH) {
            event.setCancelled(true);
        }
    }

    /*
     * Measure from the player's eyes to the closest point of the
     * target's bounding box rather than its center.
     */
    private double distanceToEntity(Player player, Entity target) {
        Location eye = player.getEyeLocation();
        BoundingBox box = target.getBoundingBox();

        double x = Math.max(box.getMinX(),
                Math.min(eye.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(),
                Math.min(eye.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(),
                Math.min(eye.getZ(), box.getMaxZ()));

        return eye.toVector().distance(new Vector(x, y, z));
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
