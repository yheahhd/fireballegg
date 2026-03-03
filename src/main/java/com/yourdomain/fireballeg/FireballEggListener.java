package com.yourdomain.fireballeg;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

public class FireballEggListener implements Listener {

    private static final String FIREBALL_METADATA = "FireballEggProjectile";
    private static final String COOLDOWN_METADATA = "fireball_cooldown";
    private static final String IMMUNE_METADATA = "FireballEggKnockbackImmune";
    private static final String VELOCITY_METADATA = "FireballEggKnockbackVelocity";
    private static final String DAMAGE_METADATA = "FireballEggDamage";
    private static final String SHOOTER_PITCH_METADATA = "FireballEggShooterPitch";
    private static final String LOOK_UP_REACH_METADATA = "FireballEggLookUpReach";

    private int velocityTaskId = -1;

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.FIRE_CHARGE) {
            return;
        }

        if (!player.hasPermission("fireballeg.use")) {
            return;
        }

        event.setCancelled(true);

        if (player.hasMetadata(COOLDOWN_METADATA)) {
            long lastUse = player.getMetadata(COOLDOWN_METADATA).get(0).asLong();
            long cooldown = FireballEggPlugin.getInstance().getConfig().getLong("cooldown", 1000);

            if (System.currentTimeMillis() - lastUse < cooldown) {
                return;
            }
        }

        player.setMetadata(COOLDOWN_METADATA,
                new FixedMetadataValue(FireballEggPlugin.getInstance(), System.currentTimeMillis()));

        if (FireballEggPlugin.getInstance().getConfig().getBoolean("consume-item", true)) {
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }

        launchFireball(player);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball)) return;

        Fireball fireball = (Fireball) event.getEntity();
        if (!fireball.hasMetadata(FIREBALL_METADATA)) return;

        handleExplosionEffects(fireball, fireball.getLocation(), event);
        fireball.remove();
    }

    private void handleExplosionEffects(Fireball fireball, Location explosionLoc, ProjectileHitEvent event) {
        World world = explosionLoc.getWorld();

        double damageToOthers = FireballEggPlugin.getInstance().getConfig().getDouble("damage-to-others", 4.0);
        double damageToSelf = FireballEggPlugin.getInstance().getConfig().getDouble("damage-to-self", 0.25);
        double verticalKnockback = FireballEggPlugin.getInstance().getConfig().getDouble("vertical-knockback", 1.2);
        double baseHorizontalKnockback = FireballEggPlugin.getInstance().getConfig().getDouble("base-horizontal-knockback", 2.0);
        double safeExplosionRadius = FireballEggPlugin.getInstance().getConfig().getDouble("safe-explosion-radius", 2.0);
        double maxKnockbackDistance = FireballEggPlugin.getInstance().getConfig().getDouble("max-knockback-distance", 10.0);

        if (event != null && event.getHitEntity() != null && event.getHitEntity() instanceof LivingEntity) {
            LivingEntity hitEntity = (LivingEntity) event.getHitEntity();

            double damage = (hitEntity == fireball.getShooter()) ? damageToSelf : damageToOthers;

            applyCustomDamageWithKnockback(hitEntity, fireball, explosionLoc, damage,
                    verticalKnockback, baseHorizontalKnockback, safeExplosionRadius, maxKnockbackDistance);
        }

        for (Entity nearbyEntity : world.getNearbyEntities(explosionLoc, maxKnockbackDistance, maxKnockbackDistance, maxKnockbackDistance)) {
            if (!(nearbyEntity instanceof LivingEntity)) continue;

            LivingEntity livingEntity = (LivingEntity) nearbyEntity;

            if (event != null && event.getHitEntity() == livingEntity) continue;

            double baseDamage = (livingEntity == fireball.getShooter()) ? damageToSelf : damageToOthers;

            applyCustomDamageWithKnockback(livingEntity, fireball, explosionLoc, baseDamage,
                    verticalKnockback, baseHorizontalKnockback, safeExplosionRadius, maxKnockbackDistance);
        }
    }

    private void applyCustomDamageWithKnockback(LivingEntity entity, Fireball fireball, Location explosionLoc,
                                                double damage, double verticalKnockback, double baseHorizontalKnockback,
                                                double safeExplosionRadius, double maxKnockbackDistance) {

        Vector knockbackVector = calculateKnockbackVector(entity, explosionLoc, verticalKnockback,
                baseHorizontalKnockback, safeExplosionRadius, maxKnockbackDistance, fireball);

        entity.setMetadata(IMMUNE_METADATA,
                new FixedMetadataValue(FireballEggPlugin.getInstance(), System.currentTimeMillis()));
        entity.setMetadata(VELOCITY_METADATA,
                new FixedMetadataValue(FireballEggPlugin.getInstance(), knockbackVector));
        entity.setMetadata(DAMAGE_METADATA,
                new FixedMetadataValue(FireballEggPlugin.getInstance(), damage));

        double healthBefore = entity.getHealth();
        double healthAfter = healthBefore - damage;

        if (healthAfter <= 0) {
            EntityDamageByEntityEvent damageEvent = new EntityDamageByEntityEvent(
                    fireball.getShooter() instanceof Entity ? (Entity) fireball.getShooter() : fireball,
                    entity,
                    EntityDamageEvent.DamageCause.CUSTOM,
                    damage
            );
            Bukkit.getPluginManager().callEvent(damageEvent);

            if (!damageEvent.isCancelled()) {
                entity.setLastDamageCause(damageEvent);
                entity.damage(damageEvent.getDamage());
            }
        } else {
            entity.setHealth(healthAfter);

            entity.setLastDamage(damage);
        }

        Bukkit.getScheduler().runTask(FireballEggPlugin.getInstance(), () -> {
            if (entity.isValid() && entity.hasMetadata(VELOCITY_METADATA)) {
                Vector finalKnockback = (Vector) entity.getMetadata(VELOCITY_METADATA).get(0).value();
                entity.setVelocity(finalKnockback);

                entity.removeMetadata(VELOCITY_METADATA, FireballEggPlugin.getInstance());
                entity.removeMetadata(DAMAGE_METADATA, FireballEggPlugin.getInstance());
            }
        });
    }

    private Vector calculateKnockbackVector(LivingEntity entity, Location explosionLoc,
                                            double verticalKnockback, double baseHorizontalKnockback,
                                            double safeExplosionRadius, double maxKnockbackDistance,
                                            Fireball fireball) {
        Location entityLoc = entity.getLocation();

        double dx = entityLoc.getX() - explosionLoc.getX();
        double dz = entityLoc.getZ() - explosionLoc.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        Vector horizontalDir = new Vector(dx, 0, dz);
        if (horizontalDir.lengthSquared() > 0) {
            horizontalDir.normalize();
        }

        double horizontalKnockback = 0;

        if (horizontalDistance > safeExplosionRadius) {
            double distanceFactor = (horizontalDistance - safeExplosionRadius) / (maxKnockbackDistance - safeExplosionRadius);

            horizontalKnockback = baseHorizontalKnockback * distanceFactor;

            double minKnockback = baseHorizontalKnockback * 0.1;
            if (horizontalKnockback < minKnockback) {
                horizontalKnockback = minKnockback;
            }
        }
        if (fireball.hasMetadata(SHOOTER_PITCH_METADATA) && fireball.hasMetadata(LOOK_UP_REACH_METADATA)) {
            float pitch = (float) fireball.getMetadata(SHOOTER_PITCH_METADATA).get(0).value();
            boolean lookUpReach = (boolean) fireball.getMetadata(LOOK_UP_REACH_METADATA).get(0).value();

            double angleThreshold = FireballEggPlugin.getInstance().getConfig().getDouble("look-angle-threshold", 60.0);

            if (pitch > angleThreshold) {
                double multiplier = FireballEggPlugin.getInstance().getConfig().getDouble("look-down-multiplier", 1.5);
                horizontalKnockback *= multiplier;
            } else if (pitch < -angleThreshold && lookUpReach) {
                double multiplier = FireballEggPlugin.getInstance().getConfig().getDouble("look-up-reach-multiplier", 0.5);
                horizontalKnockback *= multiplier;
            }
        }

        Vector verticalKnockbackVec = new Vector(0, verticalKnockback, 0);
        Vector horizontalKnockbackVec = horizontalDir.multiply(horizontalKnockback);
        Vector totalKnockback = verticalKnockbackVec.add(horizontalKnockbackVec);

        double maxSpeed = FireballEggPlugin.getInstance().getConfig().getDouble("max-knockback-speed", 3.0);
        if (totalKnockback.length() > maxSpeed) {
            totalKnockback.normalize().multiply(maxSpeed);
        }

        return totalKnockback;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;

        LivingEntity entity = (LivingEntity) event.getEntity();

        if (entity.hasMetadata(IMMUNE_METADATA)) {
            long immuneTime = entity.getMetadata(IMMUNE_METADATA).get(0).asLong();
            long currentTime = System.currentTimeMillis();

            if (currentTime - immuneTime < 200) {
                if (entity.hasMetadata(VELOCITY_METADATA)) {
                    Bukkit.getScheduler().runTask(FireballEggPlugin.getInstance(), () -> {
                        if (entity.isValid() && entity.hasMetadata(VELOCITY_METADATA)) {
                            Vector ourKnockback = (Vector) entity.getMetadata(VELOCITY_METADATA).get(0).value();
                            entity.setVelocity(ourKnockback);

                            entity.removeMetadata(VELOCITY_METADATA, FireballEggPlugin.getInstance());
                            entity.removeMetadata(IMMUNE_METADATA, FireballEggPlugin.getInstance());
                        }
                    });
                }

                if (entity.hasMetadata(DAMAGE_METADATA)) {
                    double ourDamage = entity.getMetadata(DAMAGE_METADATA).get(0).asDouble();
                    event.setDamage(ourDamage);

                    entity.removeMetadata(DAMAGE_METADATA, FireballEggPlugin.getInstance());
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Fireball)) {
            return;
        }

        Fireball fireball = (Fireball) event.getEntity();
        Player player = (Player) event.getDamager();

        if (!fireball.hasMetadata(FIREBALL_METADATA)) {
            return;
        }

        event.setCancelled(true);

        Vector newDirection = player.getEyeLocation().getDirection();

        double speed = fireball.getVelocity().length();
        fireball.setVelocity(newDirection.normalize().multiply(speed));
    }

    private void launchFireball(Player player) {
        double speed = FireballEggPlugin.getInstance().getConfig().getDouble("fireball-speed", 1.2);

        Vector direction = player.getEyeLocation().getDirection().multiply(speed);

        Fireball fireball = player.getWorld().spawn(
                player.getEyeLocation().add(direction.normalize()),
                Fireball.class
        );

        fireball.setShooter(player);
        fireball.setDirection(direction);
        fireball.setVelocity(direction);

        fireball.setYield(0);
        fireball.setIsIncendiary(false);

        fireball.setMetadata(FIREBALL_METADATA,
                new FixedMetadataValue(FireballEggPlugin.getInstance(), true));

        float pitch = player.getLocation().getPitch();
        fireball.setMetadata(SHOOTER_PITCH_METADATA,
                new FixedMetadataValue(FireballEggPlugin.getInstance(), pitch));

        boolean lookUpReach = false;
        double angleThreshold = FireballEggPlugin.getInstance().getConfig().getDouble("look-angle-threshold", 60.0);
        if (pitch < -angleThreshold) {
            Block targetBlock = player.getTargetBlockExact(5); // 5格内是否能看到方块
            if (targetBlock != null && !targetBlock.getType().isAir()) {
                lookUpReach = true;
            }
        }
        fireball.setMetadata(LOOK_UP_REACH_METADATA,
                new FixedMetadataValue(FireballEggPlugin.getInstance(), lookUpReach));
    }

    public void startVelocityProtectionTask() {
        if (velocityTaskId != -1) {
            Bukkit.getScheduler().cancelTask(velocityTaskId);
        }

        velocityTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(FireballEggPlugin.getInstance(), () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof LivingEntity && entity.hasMetadata(IMMUNE_METADATA)) {
                        LivingEntity livingEntity = (LivingEntity) entity;

                        long immuneTime = livingEntity.getMetadata(IMMUNE_METADATA).get(0).asLong();
                        if (System.currentTimeMillis() - immuneTime > 1000) { // 1秒后过期
                            livingEntity.removeMetadata(IMMUNE_METADATA, FireballEggPlugin.getInstance());
                            livingEntity.removeMetadata(VELOCITY_METADATA, FireballEggPlugin.getInstance());
                            livingEntity.removeMetadata(DAMAGE_METADATA, FireballEggPlugin.getInstance());
                            continue;
                        }

                        if (livingEntity.hasMetadata(VELOCITY_METADATA)) {
                            Vector ourKnockback = (Vector) livingEntity.getMetadata(VELOCITY_METADATA).get(0).value();
                            livingEntity.setVelocity(ourKnockback);
                        }
                    }
                }
            }
        }, 0L, 1L);
    }
}