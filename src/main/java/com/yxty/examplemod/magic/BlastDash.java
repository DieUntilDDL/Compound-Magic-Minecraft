package com.yxty.examplemod.magic;

import com.yxty.examplemod.MagicType;
import com.yxty.examplemod.ModEntityTypes;
import com.yxty.examplemod.entity.BlastDashControllerEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** 爆破冲刺：碰撞安全的分帧冲刺，随后在施法者前方产生无方块破坏的魔法爆炸。 */
public class BlastDash extends BaseMagic {
    public static final double BASE_DASH_DISTANCE = 4.0D;
    public static final double DASH_DISTANCE_PER_EXTRA_STACK = 0.75D;
    public static final double EXPLOSION_FORWARD_OFFSET = 1.6D;
    public static final int DASH_TICKS = 6;
    public static final int END_DELAY_TICKS = 3;
    public static final double GROUND_GRACE_DISTANCE = 0.5D;
    public static final float AIRBORNE_OWNER_KNOCKBACK_MULTIPLIER = 2.0F;

    public static final float BASE_MAIN_RADIUS = 4.0F;
    public static final float MAIN_RADIUS_PER_EXTRA_STACK = 0.25F;
    public static final float BASE_MAIN_DAMAGE = 14.0F;
    public static final float MAIN_DAMAGE_PER_EXTRA_STACK = 4.0F;
    public static final float MAIN_KNOCKBACK = 1.6F;
    public static final double PURIFIED_DASH_MULTIPLIER = 1.5D;
    public static final float PURIFIED_DAMAGE_MULTIPLIER = 1.5F;
    public static final float PURIFIED_KNOCKBACK_MULTIPLIER = 1.5F;

    public static final float SUB_TRIGGER_CHANCE = 0.30F;
    public static final float SUB_RADIUS = 3.0F;
    public static final float SUB_DAMAGE = 8.0F;
    public static final float SUB_KNOCKBACK = 1.4F;

    @Override
    public int getBaseWindupTicks(ItemStack stack) {
        return 0;
    }

    @Override
    public boolean castsImmediately() {
        return true;
    }

    @Override
    public void castAsMain(MagicContext context) {
        if (!context.isServerSide()) {
            return;
        }

        Player caster = context.owner();
        Vec3 direction = context.castDirection();
        if (direction == null || direction.lengthSqr() < 1.0E-6D) {
            direction = caster.getLookAngle();
        }
        Vec3 dashDirection = direction.normalize();

        dispatchSubMagics(context.withPhase(MagicPhase.CAST_BEGIN));

        int intensity = Math.max(1, context.loadout().count(MagicType.BlastDashMagic));
        boolean purified = context.loadout().homogeneous();
        double baseDashDistance = BASE_DASH_DISTANCE
                + Math.max(0, intensity - 1) * DASH_DISTANCE_PER_EXTRA_STACK;
        double dashDistance = baseDashDistance
                * (purified ? PURIFIED_DASH_MULTIPLIER : 1.0D);
        float radius = BASE_MAIN_RADIUS
                + Math.max(0, intensity - 1) * MAIN_RADIUS_PER_EXTRA_STACK;
        float damage = context.scaleDamage((BASE_MAIN_DAMAGE
                + Math.max(0, intensity - 1) * MAIN_DAMAGE_PER_EXTRA_STACK)
                * (purified ? PURIFIED_DAMAGE_MULTIPLIER : 1.0F));
        float knockback = MAIN_KNOCKBACK
                * (purified ? PURIFIED_KNOCKBACK_MULTIPLIER : 1.0F);

        BlastDashControllerEntity controller = new BlastDashControllerEntity(
                ModEntityTypes.BLAST_DASH_CONTROLLER.get(), context.level());
        controller.setup(caster, dashDirection, dashDistance, DASH_TICKS, END_DELAY_TICKS,
                (dashOwner, endPosition) -> finishMainExplosion(
                        context, dashOwner, endPosition, dashDirection, radius, damage, knockback));
        context.level().addFreshEntity(controller);
        context.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS,
                0.65F, 1.25F);
    }

    private void finishMainExplosion(MagicContext context, Player caster, Vec3 endPosition,
                                     Vec3 direction, float radius, float damage, float knockback) {
        if (!caster.isAlive()) {
            return;
        }

        Vec3 explosionCenter = endPosition
                .add(0.0D, caster.getBbHeight() * 0.4D, 0.0D)
                .add(direction.scale(EXPLOSION_FORWARD_OFFSET));
        float ownerKnockbackMultiplier = isNearGround(caster)
                ? 1.0F
                : AIRBORNE_OWNER_KNOCKBACK_MULTIPLIER;
        MagicExplosion.Settings settings = new MagicExplosion.Settings(
                radius, damage, knockback, false, true, ownerKnockbackMultiplier);
        MagicExplosion.Result result = MagicExplosion.explode(
                context.level(), caster, explosionCenter, settings,
                target -> isEnemy(caster, target));

        UUID areaId = UUID.randomUUID();
        for (MagicExplosion.Hit hit : result.hits()) {
            MagicContext.DamageData damageData =
                    new MagicContext.DamageData(hit.requestedDamage(), hit.damageSource());
            damageData.recordActualDamage(hit.actualDamage());

            MagicContext hitContext = context.toBuilder()
                    .eventId(UUID.randomUUID())
                    .origin(MagicOrigin.PRIMARY)
                    .target(hit.target())
                    .directEntity(caster)
                    .position(hit.target().position())
                    .areaId(areaId)
                    .damage(damageData)
                    .hitConfirmed(true)
                    .killed(hit.killed())
                    .primaryCausedKill(hit.killed())
                    .build();
            dispatchEntityOutcome(hitContext);
        }
    }

    private static boolean isNearGround(Player caster) {
        if (caster.onGround()) {
            return true;
        }

        AABB bounds = caster.getBoundingBox();
        double horizontalInset = Math.min(0.05D, bounds.getXsize() * 0.2D);
        AABB groundProbe = new AABB(
                bounds.minX + horizontalInset,
                bounds.minY - GROUND_GRACE_DISTANCE,
                bounds.minZ + horizontalInset,
                bounds.maxX - horizontalInset,
                bounds.minY + 0.02D,
                bounds.maxZ - horizontalInset);
        return !caster.level().noCollision(caster, groundProbe);
    }

    @Override
    public void triggerAsSub(MagicContext context) {
        if (!context.isServerSide()
                || context.phase() != MagicPhase.ENTITY_KILLED
                || !context.primaryCausedKill()
                || context.target() == null
                || context.level().getRandom().nextFloat() >= SUB_TRIGGER_CHANCE) {
            return;
        }

        Vec3 center = context.position() != null
                ? context.position()
                : context.target().position();
        MagicExplosion.Settings settings = new MagicExplosion.Settings(
                SUB_RADIUS, context.scaleDamage(SUB_DAMAGE),
                SUB_KNOCKBACK, false, true);

        // 副魔法爆炸只结算效果，不再分发命中/击杀结果，避免递归连锁。
        MagicExplosion.explode(context.level(), context.owner(), center, settings,
                target -> isEnemy(context.owner(), target));
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
}
