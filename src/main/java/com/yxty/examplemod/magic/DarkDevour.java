package com.yxty.examplemod.magic;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.ModEntityTypes;
import com.yxty.examplemod.entity.DarkDevourFieldEntity;
import com.yxty.examplemod.entity.DarkFangEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 黑暗吞噬：在施法者前方的定向长方体中，以当前生命值预算处决多个敌人。
 */
public class DarkDevour extends BaseMagic {
    // 区域：从施法者前方 1.5 格开始，向前延伸 10 格，总宽 7 格。
    public static final double FORWARD_START = 1.5D;
    public static final double FORWARD_LENGTH = 10.0D;
    public static final double HALF_WIDTH = 3.5D;
    public static final double VERTICAL_DOWN = 2.0D;
    public static final double VERTICAL_UP = 4.0D;

    // 一层可吞噬 40 点当前生命；每个同类副槽额外增加 20 点预算。
    public static final float BASE_HEALTH_BUDGET = 40.0F;
    public static final float HEALTH_BUDGET_PER_EXTRA_STACK = 20.0F;
    public static final float BACKLASH_RATIO = 0.25F;
    // 激发类通常只产生一次命中事件，因此概率较高；其余类型可能持续或反复产生命中事件。
    public static final float SUB_CHANCE_INSTANT = 0.25F;
    public static final float SUB_CHANCE_REPEATED = 0.05F;

    private static final double FANG_SPACING = 2.0D;
    private static final int FIELD_EFFECT_TICKS = 36;

    @Override
    public void castAsMain(MagicContext context) {
        if (!context.isServerSide()) {
            return;
        }

        Level level = context.level();
        Player caster = context.owner();
        Vec3 forward = horizontalForward(caster);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

        // 祝福一类的施法阶段副魔法只在这里执行一次。
        dispatchSubMagics(context.withPhase(MagicPhase.CAST_BEGIN));

        spawnDarkField(level, caster, forward);
        spawnFangFormation(level, caster, forward, right);

        int intensity = Math.max(1, context.loadout().count(MagicType.DarkDevourMagic));
        float healthBudget = context.scaleDamage(BASE_HEALTH_BUDGET
                + Math.max(0, intensity - 1) * HEALTH_BUDGET_PER_EXTRA_STACK);

        List<LivingEntity> candidates = findTargets(level, caster, forward, right);
        // 纯化时保留敌我筛选，但跳过生命预算：区域中的每一个有效目标都会被吞噬。
        List<LivingEntity> selected = context.loadout().homogeneous()
                ? candidates
                : selectWithinBudget(candidates, healthBudget);

        float consumedHealth = 0.0F;
        UUID areaId = UUID.randomUUID();
        for (LivingEntity target : selected) {
            if (!target.isAlive()) {
                continue;
            }

            float healthBefore = target.getHealth();
            Vec3 targetPos = target.position();
            DamageSource executionSource = caster.damageSources().indirectMagic(caster, caster);
            boolean killed = execute(target, executionSource);
            float removedHealth = Math.max(0.0F, healthBefore - target.getHealth());

            MagicContext.DamageData damageData = new MagicContext.DamageData(0.0F, executionSource);
            damageData.recordActualDamage(0.0F);
            damageData.recordExecution(killed ? healthBefore : removedHealth, false);

            MagicContext targetContext = context.toBuilder()
                    .eventId(UUID.randomUUID())
                    .origin(MagicOrigin.PRIMARY)
                    .target(target)
                    .directEntity(caster)
                    .position(targetPos)
                    .areaId(areaId)
                    .damage(damageData)
                    .hitConfirmed(true)
                    .killed(killed)
                    .primaryCausedKill(killed)
                    .build();
            dispatchEntityOutcome(targetContext);

            if (killed) {
                consumedHealth += healthBefore;
            }
        }

        applyBacklash(caster, consumedHealth);
    }

    @Override
    public void triggerAsSub(MagicContext context) {
        if (!context.isServerSide()
                || context.phase() != MagicPhase.ENTITY_HIT
                || !(context.target() instanceof LivingEntity target)
                || !isEnemy(context.owner(), target)
                || !target.isAlive()) {
            return;
        }

        // 每个黑暗吞噬副槽独立判定；概率由主魔法类型决定，内部永远按单层计算。
        float triggerChance = getSubTriggerChance(context.loadout().mainKind());
        if (context.level().getRandom().nextFloat() >= triggerChance) {
            return;
        }

        Player caster = context.owner();
        float healthBefore = target.getHealth();
        spawnFangAtGround(
                context.level(), caster,
                target.getX(), target.getY() + 1.0D, target.getZ(),
                target.getY() - 2.0D,
                caster.getYRot() * ((float) Math.PI / 180.0F),
                0);
        spawnInkBurst(context.level(), target.position(), 18);

        DamageSource executionSource = caster.damageSources().indirectMagic(caster, caster);
        float healthBudget = context.scaleDamage(BASE_HEALTH_BUDGET);
        if (healthBefore <= healthBudget) {
            if (execute(target, executionSource)) {
                // 副魔法处决不会调用 dispatchEntityOutcome，因此不会触发后续击杀副魔法。
                applyBacklash(caster, healthBefore);
            }
        } else {
            // 单层副魔法无法吞噬高生命目标时，改为造成吞噬上限的伤害。
            target.invulnerableTime = 0;
            target.hurt(executionSource, healthBudget);
            // 副魔法伤害不继续分发结果，避免由副魔法递归触发其它副魔法。
            applyBacklash(caster, healthBudget);
        }
    }

    private static float getSubTriggerChance(MagicKind mainKind) {
        return switch (mainKind) {
            case INSTANT -> SUB_CHANCE_INSTANT;
            case FORMATION, SUSTAINED, BUFF -> SUB_CHANCE_REPEATED;
        };
    }

    private static List<LivingEntity> findTargets(Level level, Player caster, Vec3 forward, Vec3 right) {
        Vec3 center = caster.position().add(forward.scale(FORWARD_START + FORWARD_LENGTH * 0.5D));
        double horizontalRadius = Math.sqrt(
                FORWARD_LENGTH * FORWARD_LENGTH * 0.25D + HALF_WIDTH * HALF_WIDTH) + 2.0D;
        AABB searchBox = new AABB(
                center.x - horizontalRadius,
                caster.getY() - VERTICAL_DOWN,
                center.z - horizontalRadius,
                center.x + horizontalRadius,
                caster.getY() + VERTICAL_UP,
                center.z + horizontalRadius);

        return level.getEntitiesOfClass(LivingEntity.class, searchBox, entity -> {
                    if (!isEnemy(caster, entity)) {
                        return false;
                    }

                    Vec3 delta = entity.getBoundingBox().getCenter().subtract(caster.position());
                    double forwardDistance = delta.x * forward.x + delta.z * forward.z;
                    double sideDistance = delta.x * right.x + delta.z * right.z;
                    double verticalDistance = delta.y;
                    return forwardDistance >= FORWARD_START
                            && forwardDistance <= FORWARD_START + FORWARD_LENGTH
                            && Math.abs(sideDistance) <= HALF_WIDTH
                            && verticalDistance >= -VERTICAL_DOWN
                            && verticalDistance <= VERTICAL_UP;
                }).stream()
                .sorted(Comparator
                        .comparingDouble(LivingEntity::getHealth)
                        .thenComparingDouble(caster::distanceToSqr)
                        .thenComparingInt(LivingEntity::getId))
                .toList();
    }

    private static List<LivingEntity> selectWithinBudget(
            List<LivingEntity> candidates, float budget) {
        List<LivingEntity> selected = new ArrayList<>();
        float used = 0.0F;
        for (LivingEntity target : candidates) {
            float health = Math.max(0.0F, target.getHealth());
            if (used + health <= budget + 1.0E-4F) {
                selected.add(target);
                used += health;
            }
        }
        return selected;
    }

    private static boolean isEnemy(Player caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isInvulnerable()) {
            return false;
        }
        if (caster.isAlliedTo(target) || target.isAlliedTo(caster)) {
            return false;
        }
        return !(target instanceof Player player) || (!player.isCreative() && !player.isSpectator());
    }

    private static boolean execute(LivingEntity target, DamageSource source) {
        if (!target.isAlive()) {
            return false;
        }
        target.invulnerableTime = 0;
        target.hurt(source, Float.MAX_VALUE);
        return !target.isAlive();
    }

    private static void applyBacklash(Player caster, float consumedHealth) {
        if (consumedHealth <= 0.0F || !caster.isAlive()) {
            return;
        }
        caster.hurt(caster.damageSources().magic(), consumedHealth * BACKLASH_RATIO);
    }

    private static Vec3 horizontalForward(Player caster) {
        Vec3 look = caster.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() > 1.0E-6D) {
            return horizontal.normalize();
        }

        double yaw = Math.toRadians(caster.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    private static void spawnFangFormation(Level level, Player caster, Vec3 forward, Vec3 right) {
        float yaw = (float) (Math.atan2(forward.z, forward.x) - Math.PI * 0.5D);
        int row = 0;
        for (double forwardDistance = FORWARD_START;
             forwardDistance <= FORWARD_START + FORWARD_LENGTH + 1.0E-4D;
             forwardDistance += FANG_SPACING) {
            for (double sideDistance = -3.0D; sideDistance <= 3.0D; sideDistance += FANG_SPACING) {
                Vec3 point = caster.position()
                        .add(forward.scale(forwardDistance))
                        .add(right.scale(sideDistance));
                spawnFangAtGround(
                        level, caster,
                        point.x, caster.getY() + VERTICAL_UP + 1.0D, point.z,
                        caster.getY() - VERTICAL_DOWN,
                        yaw,
                        row * 2);
            }
            row++;
        }
    }

    private static void spawnDarkField(Level level, Player caster, Vec3 forward) {
        Vec3 center = caster.position().add(forward.scale(FORWARD_START + FORWARD_LENGTH * 0.5D));
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        DarkDevourFieldEntity field = new DarkDevourFieldEntity(ModEntityTypes.DARK_DEVOUR_FIELD.get(), level);
        field.setup(center, yaw, (float) (HALF_WIDTH * 2.0D), (float) FORWARD_LENGTH, FIELD_EFFECT_TICKS);
        level.addFreshEntity(field);
        spawnInkBurst(level, center.add(0.0D, 0.25D, 0.0D), 36);
    }

    private static void spawnInkBurst(Level level, Vec3 position, int count) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.SQUID_INK,
                    position.x, position.y + 0.2D, position.z,
                    count,
                    1.25D, 0.35D, 1.25D,
                    0.04D);
        }
    }

    private static boolean spawnFangAtGround(
            Level level, LivingEntity owner,
            double x, double startY, double z, double minimumY,
            float yawRadians, int warmupTicks) {
        BlockPos pos = BlockPos.containing(x, startY, z);
        int minimumBlockY = (int) Math.floor(minimumY) - 1;

        while (pos.getY() >= minimumBlockY) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.isFaceSturdy(level, below, Direction.UP)) {
                double collisionHeight = 0.0D;
                BlockState stateAtPos = level.getBlockState(pos);
                if (!stateAtPos.isAir()) {
                    VoxelShape shape = stateAtPos.getCollisionShape(level, pos);
                    if (!shape.isEmpty()) {
                        collisionHeight = shape.max(Direction.Axis.Y);
                    }
                }

                DarkFangEntity fang = new DarkFangEntity(
                        level, x, pos.getY() + collisionHeight, z,
                        yawRadians, warmupTicks, owner);
                level.addFreshEntity(fang);
                return true;
            }
            pos = below;
        }
        return false;
    }
}
