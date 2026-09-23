package com.yxty.examplemod.magic;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.ModEntityTypes;
import com.yxty.examplemod.ModMobEffects;
import com.yxty.examplemod.ProgramMagic;
import com.yxty.examplemod.entity.IceShieldEntity;
import com.yxty.examplemod.network.ModNetworking;
import com.yxty.examplemod.particle.LightningParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 自定义魔法状态；不依赖原版药水效果。 */
@Mod.EventBusSubscriber(modid = ProgramMagic.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicStatusManager {
    private static final Map<UUID, Long> FROZEN_UNTIL = new HashMap<>();
    private static final Map<UUID, FrozenAnchor> FROZEN_ANCHORS = new HashMap<>();
    /** 大于原版最大冻结刻数的同步标记，使客户端无需自定义数据包即可识别魔法冻结。 */
    private static final int MAGIC_FREEZE_MARKER = 10_000;
    private static final Map<UUID, BlessingData> BLESSINGS = new HashMap<>();
    private static final Map<UUID, List<ShieldLayer>> ICE_SHIELDS = new HashMap<>();
    private static final Map<UUID, Long> STUNNED_UNTIL = new HashMap<>();
    private static final int MAX_SHIELDS_PER_ENTITY = 4;
    private static final String BLUE_FLAME_TAG = ProgramMagic.MODID + ":blue_flame";
    private static final String BLUE_FLAME_STRENGTH_TAG =
            ProgramMagic.MODID + ":blue_flame_strength";
    private static final float BLUE_FLAME_DAMAGE = 2.0F;

    private MagicStatusManager() {
    }

    public static void freeze(LivingEntity target, int durationTicks) {
        if (target.level().isClientSide || durationTicks <= 0) {
            return;
        }
        long gameTime = target.level().getGameTime();
        Long previousEndTime = FROZEN_UNTIL.get(target.getUUID());
        boolean newlyFrozen = previousEndTime == null || gameTime >= previousEndTime;
        long endTime = gameTime + durationTicks;
        long effectiveEndTime = FROZEN_UNTIL.merge(target.getUUID(), endTime, Math::max);
        FROZEN_ANCHORS.putIfAbsent(target.getUUID(), new FrozenAnchor(
                target.position(), target.getYRot(), target.getXRot(),
                target.yBodyRot, target.yHeadRot, target.getPose()));
        int visualTicks = (int) Math.min(Integer.MAX_VALUE - MAGIC_FREEZE_MARKER,
                Math.max(1L, effectiveEndTime - gameTime));
        target.setTicksFrozen(MAGIC_FREEZE_MARKER + visualTicks);

        if (newlyFrozen && target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    36, target.getBbWidth() * 0.65D, target.getBbHeight() * 0.55D,
                    target.getBbWidth() * 0.65D, 0.035D);
            serverLevel.sendParticles(ParticleTypes.POOF,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    12, target.getBbWidth() * 0.45D, target.getBbHeight() * 0.4D,
                    target.getBbWidth() * 0.45D, 0.015D);
            serverLevel.playSound(null, target.blockPosition(), SoundEvents.GLASS_PLACE,
                    SoundSource.PLAYERS, 0.9F, 0.75F);
        }
    }

    public static boolean isFrozen(LivingEntity entity) {
        Long endTime = FROZEN_UNTIL.get(entity.getUUID());
        if (endTime == null) {
            return false;
        }
        if (entity.level().getGameTime() >= endTime || !entity.isAlive()) {
            FROZEN_UNTIL.remove(entity.getUUID());
            FROZEN_ANCHORS.remove(entity.getUUID());
            if (isMagicFreezeVisual(entity)) {
                entity.setTicksFrozen(0);
            }
            return false;
        }
        return true;
    }

    /** 该状态通过 Entity 的同步冻结刻数同时存在于服务端和客户端。 */
    public static boolean isMagicFreezeVisual(LivingEntity entity) {
        return entity.getTicksFrozen() > MAGIC_FREEZE_MARKER;
    }

    /** 客户端独立倒计时；即使结束同步包延迟或丢失，视觉冻结也会按期自行解除。 */
    public static void tickClientFreezeVisual(LivingEntity entity) {
        int frozenTicks = entity.getTicksFrozen();
        if (frozenTicks <= MAGIC_FREEZE_MARKER) {
            return;
        }
        int next = frozenTicks - 1;
        entity.setTicksFrozen(next > MAGIC_FREEZE_MARKER ? next : 0);
    }

    public static void applyBlessing(LivingEntity target, MagicContext context,
                                     int durationTicks, float damageBonus, float resistance) {
        if (target.level().isClientSide || durationTicks <= 0) {
            return;
        }
        BlessingData incoming = new BlessingData(
                context.owner().getUUID(), context.loadout(), context.castingStrength(),
                target.level().getGameTime() + durationTicks,
                Math.max(0.0F, damageBonus), Math.max(0.0F, Math.min(0.9F, resistance)));
        BlessingData effectiveBlessing = BLESSINGS.merge(target.getUUID(), incoming, (current, next) ->
                new BlessingData(next.casterUuid(), next.loadout(),
                        Math.max(current.castingStrength(), next.castingStrength()),
                        Math.max(current.endGameTime(), next.endGameTime()),
                        Math.max(current.damageBonus(), next.damageBonus()),
                        Math.max(current.resistance(), next.resistance())));
        syncBlessingIndicator(target, effectiveBlessing);
    }

    /** Persistent purified flame: milk and rain cannot remove it; entering water can. */
    public static void applyBlueFlame(LivingEntity target, float castingStrength) {
        if (target.level().isClientSide || !target.isAlive()) {
            return;
        }
        boolean newlyApplied = !target.getPersistentData().getBoolean(BLUE_FLAME_TAG);
        target.getPersistentData().putBoolean(BLUE_FLAME_TAG, true);
        float currentStrength = target.getPersistentData().getFloat(BLUE_FLAME_STRENGTH_TAG);
        target.getPersistentData().putFloat(
                BLUE_FLAME_STRENGTH_TAG,
                Math.max(currentStrength, Math.max(0.0F, castingStrength)));
        syncBlueFlameIndicator(target);
        if (newlyApplied) {
            ModNetworking.syncBlueFlame(target, true);
        }
    }

    public static void stun(LivingEntity target, int durationTicks) {
        if (target.level().isClientSide || durationTicks <= 0 || !target.isAlive()) {
            return;
        }
        long endTime = target.level().getGameTime() + durationTicks;
        STUNNED_UNTIL.merge(target.getUUID(), endTime, Math::max);
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, durationTicks, 255,
                false, false, false));
    }

    public static boolean isStunned(LivingEntity target) {
        Long endTime = STUNNED_UNTIL.get(target.getUUID());
        if (endTime == null) {
            return false;
        }
        if (!target.isAlive() || target.level().getGameTime() >= endTime) {
            STUNNED_UNTIL.remove(target.getUUID());
            MobEffectInstance slowing = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (slowing != null && slowing.getAmplifier() == 255) {
                target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
            return false;
        }
        return true;
    }

    /**
     * The vanilla MobEffect is display-only. Keeping it separate prevents its UI entry
     * from applying a second set of combat modifiers on top of the custom magic rules.
     */
    private static void syncBlessingIndicator(LivingEntity target, BlessingData blessing) {
        long remainingLong = blessing.endGameTime() - target.level().getGameTime();
        if (remainingLong <= 0L) {
            target.removeEffect(ModMobEffects.HOLY_BLESSING.get());
            return;
        }
        int remaining = (int) Math.min(Integer.MAX_VALUE, remainingLong);
        MobEffectInstance current = target.getEffect(ModMobEffects.HOLY_BLESSING.get());
        if (current == null || current.getDuration() + 2 < remaining) {
            target.addEffect(new MobEffectInstance(
                    ModMobEffects.HOLY_BLESSING.get(), remaining, 0,
                    false, false, true));
        }
    }

    private static BlessingData getBlessing(LivingEntity entity) {
        BlessingData data = BLESSINGS.get(entity.getUUID());
        if (data != null && (entity.level().getGameTime() >= data.endGameTime() || !entity.isAlive())) {
            BLESSINGS.remove(entity.getUUID());
            return null;
        }
        return data;
    }

    private static void syncBlueFlameIndicator(LivingEntity target) {
        if (!target.hasEffect(ModMobEffects.BLUE_FLAME.get())) {
            target.addEffect(new MobEffectInstance(
                    ModMobEffects.BLUE_FLAME.get(), Integer.MAX_VALUE, 0,
                    false, false, false));
        }
    }

    private static void tickBlueFlame(LivingEntity entity) {
        if (!entity.getPersistentData().getBoolean(BLUE_FLAME_TAG)) {
            return;
        }
        if (!entity.isAlive() || entity.isInWater()) {
            entity.getPersistentData().remove(BLUE_FLAME_TAG);
            entity.getPersistentData().remove(BLUE_FLAME_STRENGTH_TAG);
            entity.removeEffect(ModMobEffects.BLUE_FLAME.get());
            ModNetworking.syncBlueFlame(entity, false);
            return;
        }

        syncBlueFlameIndicator(entity);
        long gameTime = entity.level().getGameTime();
        if (gameTime % 20L == 0L) {
            ModNetworking.syncBlueFlame(entity, true);
        }
        if (gameTime % 20L == 0L) {
            entity.invulnerableTime = 0;
            float castingStrength = entity.getPersistentData().contains(BLUE_FLAME_STRENGTH_TAG)
                    ? Math.max(0.0F, entity.getPersistentData().getFloat(BLUE_FLAME_STRENGTH_TAG))
                    : 1.0F;
            entity.hurt(entity.damageSources().onFire(),
                    BLUE_FLAME_DAMAGE * castingStrength);
        }
        if (gameTime % 4L == 0L && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.45D, entity.getZ(),
                    4, entity.getBbWidth() * 0.45D, entity.getBbHeight() * 0.4D,
                    entity.getBbWidth() * 0.45D, 0.015D);
        }
    }

    private static void tickStun(LivingEntity entity) {
        if (!isStunned(entity)) {
            return;
        }

        Long endTime = STUNNED_UNTIL.get(entity.getUUID());
        int remaining = endTime == null ? 1 : (int) Math.max(1L,
                Math.min(Integer.MAX_VALUE, endTime - entity.level().getGameTime()));
        MobEffectInstance slowing = entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slowing == null || slowing.getAmplifier() != 255 || slowing.getDuration() + 2 < remaining) {
            entity.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, remaining, 255,
                    false, false, false));
        }

        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(0.0D, motion.y, 0.0D);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(serverPlayer));
        }

        if (entity.level().getGameTime() % 5L == 0L
                && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new LightningParticleOptions(0.42F),
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(),
                    2, entity.getBbWidth() * 0.55D, entity.getBbHeight() * 0.42D,
                    entity.getBbWidth() * 0.55D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.FIREWORK,
                    entity.getX(), entity.getY() + entity.getBbHeight() + 0.3D, entity.getZ(),
                    4, 0.25D, 0.12D, 0.25D, 0.01D);
        }
    }

    public static void addIceShields(LivingEntity target, MagicContext context,
                                     int layers, int durationTicks) {
        if (target.level().isClientSide || layers <= 0 || durationTicks <= 0) {
            return;
        }
        List<ShieldLayer> shields = ICE_SHIELDS.computeIfAbsent(
                target.getUUID(), ignored -> new ArrayList<>());
        cleanupShieldLayers(target, shields);

        int layersToAdd = Math.min(layers, MAX_SHIELDS_PER_ENTITY - shields.size());
        for (int i = 0; i < layersToAdd; i++) {
            IceShieldEntity shieldEntity = new IceShieldEntity(
                    ModEntityTypes.ICE_SHIELD.get(), target.level());
            shieldEntity.setup(target, 0.0F);
            target.level().addFreshEntity(shieldEntity);
            shields.add(new ShieldLayer(
                    shieldEntity.getUUID(), context.owner().getUUID(), context.loadout(),
                    context.castingStrength(),
                    target.level().getGameTime() + durationTicks));
        }
        redistributeShieldAngles(target.level(), shields);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        tickBlueFlame(entity);
        tickStun(entity);

        if (isFrozen(entity)) {
            FrozenAnchor anchor = FROZEN_ANCHORS.get(entity.getUUID());
            entity.setDeltaMovement(Vec3.ZERO);
            entity.hasImpulse = true;
            entity.hurtMarked = true;
            if (anchor != null) {
                entity.setYRot(anchor.yRot());
                entity.yRotO = anchor.yRot();
                entity.setXRot(anchor.xRot());
                entity.xRotO = anchor.xRot();
                entity.yBodyRot = anchor.bodyRot();
                entity.yBodyRotO = anchor.bodyRot();
                entity.yHeadRot = anchor.headRot();
                entity.yHeadRotO = anchor.headRot();
                entity.setPose(anchor.pose());
            }
            if (entity instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            if (entity instanceof ServerPlayer serverPlayer) {
                if (anchor != null) {
                    serverPlayer.connection.teleport(
                            anchor.position().x, anchor.position().y, anchor.position().z,
                            anchor.yRot(), anchor.xRot());
                }
            }

            if (entity.level().getGameTime() % 4L == 0L
                    && entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(),
                        12, entity.getBbWidth() * 0.55D, entity.getBbHeight() * 0.45D,
                        entity.getBbWidth() * 0.45D, 0.01D);
            }

            // LivingTickEvent occurs before mob AI. Merely clearing velocity here lets AI
            // write a new movement target later in the same tick (especially for Wardens).
            // Canceling the update keeps navigation, movement and attacks fully suspended.
            event.setCanceled(true);
        }

        BlessingData blessing = getBlessing(entity);
        if (blessing != null) {
            // Re-add the UI indicator if another effect-clearing mechanic removed it;
            // the actual custom blessing is deliberately not a vanilla potion effect.
            syncBlessingIndicator(entity, blessing);
            if (entity.tickCount % 10 == 0 && entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.WAX_ON,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(),
                        5, entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.35D,
                        entity.getBbWidth() * 0.4D, 0.01D);
            }
        }

        List<ShieldLayer> shields = ICE_SHIELDS.get(entity.getUUID());
        if (shields != null) {
            boolean changed = cleanupShieldLayers(entity, shields);
            if (shields.isEmpty()) {
                ICE_SHIELDS.remove(entity.getUUID());
            } else if (changed) {
                redistributeShieldAngles(entity.level(), shields);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity livingAttacker && isFrozen(livingAttacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIceShieldAttack(LivingAttackEvent event) {
        LivingEntity protectedEntity = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker == protectedEntity) {
            return;
        }
        List<ShieldLayer> shields = ICE_SHIELDS.get(protectedEntity.getUUID());
        if (shields == null) {
            return;
        }
        cleanupShieldLayers(protectedEntity, shields);
        if (shields.isEmpty()) {
            ICE_SHIELDS.remove(protectedEntity.getUUID());
            return;
        }

        ShieldLayer consumed = shields.remove(shields.size() - 1);
        discardShieldEntity(protectedEntity.level(), consumed.entityUuid());
        if (shields.isEmpty()) {
            ICE_SHIELDS.remove(protectedEntity.getUUID());
        } else {
            redistributeShieldAngles(protectedEntity.level(), shields);
        }
        event.setCanceled(true);
        playShieldBreak(protectedEntity);
        if (attacker instanceof LivingEntity livingAttacker
                && consumed.loadout().homogeneous()
                && consumed.loadout().mainMagicId() == MagicType.IceShieldMagic) {
            freeze(livingAttacker, 60);
        }
        dispatchShieldHit(protectedEntity, attacker, event.getSource(), consumed);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        BlessingData defenderBlessing = getBlessing(event.getEntity());
        if (defenderBlessing != null) {
            event.setAmount(event.getAmount() * (1.0F - defenderBlessing.resistance()));
        }

        Entity sourceEntity = event.getSource().getEntity();
        if (sourceEntity instanceof LivingEntity attacker && isBridgeableAttack(event.getSource())) {
            BlessingData attackerBlessing = getBlessing(attacker);
            if (attackerBlessing != null) {
                event.setAmount(event.getAmount() * (1.0F + attackerBlessing.damageBonus()));
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getAmount() <= 0.0F || !isBridgeableAttack(event.getSource())) {
            return;
        }
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) {
            return;
        }
        BlessingData data = getBlessing(attacker);
        if (data == null) {
            return;
        }
        MagicContext context = createBlessedAttackContext(
                attacker, event.getEntity(), event.getSource(), event.getAmount(), data, false);
        BaseMagic mainMagic = MagicType.getMagic(data.loadout().mainMagicId());
        if (context != null && mainMagic != null) {
            mainMagic.dispatchExternalEntityOutcome(context);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!isBridgeableAttack(event.getSource())) {
            return;
        }
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) {
            return;
        }
        BlessingData data = getBlessing(attacker);
        if (data == null) {
            return;
        }
        MagicContext context = createBlessedAttackContext(
                attacker, event.getEntity(), event.getSource(), 0.0F, data, true);
        BaseMagic mainMagic = MagicType.getMagic(data.loadout().mainMagicId());
        if (context != null && mainMagic != null) {
            mainMagic.dispatchExternalPhase(context.withPhase(MagicPhase.ENTITY_KILLED));
        }
    }

    private static MagicContext createBlessedAttackContext(
            LivingEntity attacker, LivingEntity target, DamageSource source,
            float actualDamage, BlessingData data, boolean killed) {
        if (!(attacker.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        ServerPlayer caster = serverLevel.getServer().getPlayerList().getPlayer(data.casterUuid());
        if (caster == null || !caster.isAlive() || caster.level() != attacker.level()) {
            return null;
        }
        MagicContext.DamageData damageData = new MagicContext.DamageData(actualDamage, source);
        damageData.recordActualDamage(actualDamage);
        return MagicContext.begin(attacker.level(), caster, data.loadout()).toBuilder()
                .castingStrength(data.castingStrength())
                .actor(attacker)
                .origin(MagicOrigin.PRIMARY)
                .target(target)
                .directEntity(attacker)
                .position(target.position())
                .damage(damageData)
                .hitConfirmed(true)
                .killed(killed)
                .primaryCausedKill(killed)
                .build();
    }

    private static boolean isBridgeableAttack(DamageSource source) {
        return !source.is(DamageTypes.MAGIC)
                && !source.is(DamageTypes.INDIRECT_MAGIC)
                && !source.is(DamageTypes.EXPLOSION)
                && !source.is(DamageTypes.PLAYER_EXPLOSION);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FROZEN_UNTIL.clear();
        FROZEN_ANCHORS.clear();
        STUNNED_UNTIL.clear();
        BLESSINGS.clear();
        ICE_SHIELDS.clear();
    }

    private static boolean cleanupShieldLayers(LivingEntity target, List<ShieldLayer> shields) {
        long gameTime = target.level().getGameTime();
        boolean changed = false;
        Iterator<ShieldLayer> iterator = shields.iterator();
        while (iterator.hasNext()) {
            ShieldLayer layer = iterator.next();
            boolean entityMissing = target.level() instanceof ServerLevel serverLevel
                    && serverLevel.getEntity(layer.entityUuid()) == null;
            if (!target.isAlive() || gameTime >= layer.endGameTime() || entityMissing) {
                discardShieldEntity(target.level(), layer.entityUuid());
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    /** 按当前实际层数重新均分一整圈，例如两层相差180度、四层相差90度。 */
    private static void redistributeShieldAngles(net.minecraft.world.level.Level level,
                                                 List<ShieldLayer> shields) {
        if (!(level instanceof ServerLevel serverLevel) || shields.isEmpty()) {
            return;
        }
        float angleStep = 360.0F / shields.size();
        for (int index = 0; index < shields.size(); index++) {
            Entity entity = serverLevel.getEntity(shields.get(index).entityUuid());
            if (entity instanceof IceShieldEntity iceShield) {
                iceShield.setOrbitOffsetDegrees(index * angleStep);
            }
        }
    }

    private static void discardShieldEntity(net.minecraft.world.level.Level level, UUID entityUuid) {
        if (level instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(entityUuid);
            if (entity != null) {
                entity.discard();
            }
        }
    }

    private static void playShieldBreak(LivingEntity protectedEntity) {
        protectedEntity.level().playSound(null,
                protectedEntity.getX(), protectedEntity.getY(), protectedEntity.getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9F, 1.35F);
        if (protectedEntity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    protectedEntity.getX(),
                    protectedEntity.getY() + protectedEntity.getBbHeight() * 0.55D,
                    protectedEntity.getZ(), 28,
                    protectedEntity.getBbWidth() * 0.75D,
                    protectedEntity.getBbHeight() * 0.45D,
                    protectedEntity.getBbWidth() * 0.75D, 0.09D);
            serverLevel.sendParticles(ParticleTypes.POOF,
                    protectedEntity.getX(),
                    protectedEntity.getY() + protectedEntity.getBbHeight() * 0.55D,
                    protectedEntity.getZ(), 12,
                    0.45D, 0.55D, 0.45D, 0.04D);
        }
    }

    private static void dispatchShieldHit(LivingEntity protectedEntity, Entity attacker,
                                          DamageSource source, ShieldLayer layer) {
        if (!(protectedEntity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerPlayer caster = serverLevel.getServer().getPlayerList().getPlayer(layer.casterUuid());
        if (caster == null || !caster.isAlive() || caster.level() != protectedEntity.level()) {
            return;
        }
        MagicContext.DamageData damageData = new MagicContext.DamageData(0.0F, source);
        damageData.recordActualDamage(0.0F);
        MagicContext hitContext = MagicContext.begin(
                        protectedEntity.level(), caster, layer.loadout()).toBuilder()
                .castingStrength(layer.castingStrength())
                .actor(protectedEntity)
                .origin(MagicOrigin.PRIMARY)
                .target(attacker)
                .directEntity(protectedEntity)
                .position(attacker.position())
                .damage(damageData)
                .hitConfirmed(true)
                .blocked(true)
                .killed(false)
                .primaryCausedKill(false)
                .build();
        BaseMagic mainMagic = MagicType.getMagic(layer.loadout().mainMagicId());
        if (mainMagic != null) {
            mainMagic.dispatchExternalEntityOutcome(hitContext);
        }
    }

    private record BlessingData(UUID casterUuid, MagicLoadoutSnapshot loadout,
                                float castingStrength,
                                long endGameTime, float damageBonus, float resistance) {
    }

    private record FrozenAnchor(Vec3 position, float yRot, float xRot,
                                float bodyRot, float headRot, Pose pose) {
    }

    private record ShieldLayer(UUID entityUuid, UUID casterUuid,
                               MagicLoadoutSnapshot loadout, float castingStrength,
                               long endGameTime) {
    }
}
