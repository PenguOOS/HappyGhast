
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
import org.bukkit.event.player.PlayerAnimationEvent;
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

    private static final double CALL_RANGE = 50.0;
    private static final double NORMAL_REACH = 3.0;

    private final Map<UUID, BukkitRunnable> activeCalls =
            new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            setInteractionRange(player);
        }

        getLogger().info(
                "GhastCall enabled. Call range: 50 blocks."
        );
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

    private boolean isAvailableHappyGhast(Entity entity) {
        return entity != null
                && entity.isValid()
                && entity.getType() == EntityType.HAPPY_GHAST
                && entity.getPassengers().isEmpty();
    }

    /*
     * Suppress the arm-swing animation when the player is calling
     * an available Happy Ghast, without a harness in their hand.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        Player player = event.getPlayer();

        if (holdingHarness(player)) return;

        var result = player.rayTraceEntities(CALL_RANGE, false);

        if (result == null) return;

        Entity target = result.getHitEntity();

        if (!isAvailableHappyGhast(target)) return;

        event.setCancelled(true);
    }

    /*
     * Distant right-clicks call an available Happy Ghast.
     * Distant interactions with other entities are cancelled.
     * At normal range, harness mounting remains available.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Entity target = event.getRightClicked();

        double distance = distanceToEntity(player, target);

        if (distance > NORMAL_REACH) {
            event.setCancelled(true);

            if (distance <= CALL_RANGE
                    && isAvailableHappyGhast(target)
                    && !holdingHarness(player)) {
                callGhast(player, target);
            }

            return;
        }

        // Preserve normal harness mounting and other close interactions.
        if (isAvailableHappyGhast(target)
                && !holdingHarness(player)) {
            callGhast(player, target);
        }
    }

    /*
     * Prevent the increased interaction attribute from allowing
     * long-range melee damage.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        if (distanceToEntity(player, event.getEntity()) > NORMAL_REACH) {
            event.setCancelled(true);
        }
    }

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
        if (!player.isOnline()
                || !isAvailableHappyGhast(ghast)) {
            return;
        }

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
                        || !currentGhast.getPassengers().isEmpty()
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
