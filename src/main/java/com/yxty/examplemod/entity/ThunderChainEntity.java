package com.yxty.examplemod.entity;

import com.yxty.examplemod.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ThunderChainEntity extends Entity {
    // 同步数据
    private static final EntityDataAccessor<Vector3f> START_POS = SynchedEntityData.defineId(ThunderChainEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> END_POS = SynchedEntityData.defineId(ThunderChainEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(ThunderChainEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 5.0f;
    private final List<BlockPos> lightPositions = new ArrayList<>();
    private LivingEntity owner;
    private LivingEntity lockedTarget = null;
    // 客户端渲染缓存
    public final List<Vec3> segments = new ArrayList<>();
    private long lastSeed = -1;

    public ThunderChainEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public static void spawn_between_entity(Level level, LivingEntity owner, Vec3 start, Vec3 end, float width, float damage, LivingEntity target) {
        if (level.isClientSide) return;

        ThunderChainEntity bolt = new ThunderChainEntity(ModEntityTypes.THUNDER_CHAIN.get(), level);
        bolt.setOwner(owner);
        bolt.setStart(start);
        bolt.setEnd(end);
        bolt.setWidth(width);
        bolt.setDamage(damage);
        bolt.setPos(start);
        bolt.setLockedTarget(target);
        level.addFreshEntity(bolt);
    }
    private transient Consumer<List<Entity>> onBeforeHitEntityCallback = null;
    private transient BiConsumer<Entity, Float> onHitEntityCallback = null;

    private transient Consumer<Vec3> onTickCallback = null;

    public void setOnBeforeHitEntityCallback(Consumer<List<Entity>> callback) {
        this.onBeforeHitEntityCallback = callback;
    }

    public void setOnHitEntityCallback(BiConsumer<Entity, Float> callback) {
        this.onHitEntityCallback = callback;
    }

    public void setOnTickCallback(Consumer<Vec3> callback) {
        this.onTickCallback = callback;
    }

    public static ThunderChainEntity spawn_with_face(Level level, LivingEntity owner, Vec3 start, Vec3 start_dir, float width, float damage){
        if(level.isClientSide) return null;
        ThunderChainEntity bolt = new ThunderChainEntity(ModEntityTypes.THUNDER_CHAIN.get(), level);
        bolt.setOwner(owner);
        bolt.setStart(start);

        double maxRange = 20.0D;
        Vec3 lookVec = start_dir.normalize();
        Vec3 maxEnd = start.add(lookVec.scale(maxRange));
        BlockHitResult blockHit = level.clip(new ClipContext(
                start,
                maxEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                owner
        ));
        Vec3 effectiveEnd = maxEnd;
        if (blockHit.getType() != HitResult.Type.MISS) {
            effectiveEnd = blockHit.getLocation();
        }
        net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(start, effectiveEnd).inflate(3.0);
        net.minecraft.world.phys.EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                level,
                owner,
                start,
                effectiveEnd,
                searchBox,
                e -> !e.isSpectator() && e.isPickable() && e != owner
        );
        if (entityHit != null) {
            effectiveEnd = entityHit.getLocation();
            if (entityHit.getEntity() instanceof LivingEntity living) {
                bolt.setLockedTarget(living);
            }
        }else {
            // [新增] 模糊命中判定 (宽容度机制)
            // 如果精准射线没打中，但准星附近有敌人，也算打中
            // 查找 effectiveEnd (通常是方块位置或最大射程) 周围 1.5 格内的最近敌人
            Vec3 finalEffectiveEnd = effectiveEnd;
            LivingEntity nearbyEntity = level.getEntitiesOfClass(LivingEntity.class,
                            new net.minecraft.world.phys.AABB(effectiveEnd.x, effectiveEnd.y, effectiveEnd.z, effectiveEnd.x, effectiveEnd.y, effectiveEnd.z).inflate(2.0),
                            e -> e != owner && e.isPickable() && e.isAlive())
                    .stream()
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(finalEffectiveEnd)))
                    .orElse(null);

            if (nearbyEntity != null) {
                // 如果找到了，将终点吸附到该敌人身上
                effectiveEnd = nearbyEntity.getBoundingBox().getCenter();
                bolt.setLockedTarget(nearbyEntity);
            }
        }
        bolt.setEnd(effectiveEnd);
        bolt.setWidth(width);
        bolt.setDamage(damage);
        bolt.setPos(start);
        level.addFreshEntity(bolt);
        return bolt;
    }
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // 当客户端收到起点或终点的数据更新时，立即重新计算包围盒
        if (START_POS.equals(key) || END_POS.equals(key)) {
            updateBoundingBox();
        }
    }
    @Override
    protected void defineSynchedData() {
        this.entityData.define(START_POS, new Vector3f(0, 0, 0));
        this.entityData.define(END_POS, new Vector3f(0, 10, 0));
        this.entityData.define(WIDTH, 0.3f);
    }

    public LivingEntity getLockedTarget() {
        return this.lockedTarget;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            // 遍历所有记录的光源位置
            for (BlockPos pos : lightPositions) {
                // 双重检查：确保当前位置确实是我们放的 Light Block，防止误删玩家刚放的方块
                if (this.level().getBlockState(pos).is(Blocks.LIGHT)) {
                    this.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            lightPositions.clear();
        }
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.tickCount >= 6) {
            this.discard();
            return;
        }
        if (this.tickCount == 1 && !level().isClientSide && owner != null) {

            if (this.lockedTarget != null && this.lockedTarget.isAlive()) {

                // 1. 【先执行回调】：趁着怪物还没死，赶紧索敌并生成多重闪电链
                if (this.onBeforeHitEntityCallback != null) {
                    List<Entity> targets = new ArrayList<>();
                    targets.add(lockedTarget);
                    this.onBeforeHitEntityCallback.accept(targets);
                }

                // 2. 【后造成伤害】：记录包含吸收生命在内的真实伤害，再通知魔法层。
                float before = this.lockedTarget.getHealth() + this.lockedTarget.getAbsorptionAmount();
                this.lockedTarget.hurt(damageSources().magic(), damage);
                float after = this.lockedTarget.getHealth() + this.lockedTarget.getAbsorptionAmount();
                float actualDamage = Math.max(0.0f, before - after);
                if (this.onHitEntityCallback != null) {
                    this.onHitEntityCallback.accept(this.lockedTarget, actualDamage);
                }
            }

            // 3. 下面是你原本的播放声音和放置光源的代码，保持原样即可
            Vec3 start = getStartVec();
            Vec3 end = getEndVec();
            Vec3 dir = end.subtract(start);
            double totalDist = dir.length();
            level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.WEATHER,
                    1.5F,
                    0.8F + this.random.nextFloat() * 0.2F);

            dir = dir.normalize();
            for (double d = 0; d < totalDist; d += 2.0) {
                Vec3 point = start.add(dir.scale(d));
                BlockPos pos = BlockPos.containing(point);
                if (level().getBlockState(pos).isAir()) {
                    level().setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
                    lightPositions.add(pos);
                }
            }
        }

        updateBoundingBox();
    }
    private void updateBoundingBox() {
        Vec3 start = getStartVec();
        Vec3 end = getEndVec();

        // 创建一个包含起点和终点的包围盒，并向外扩充 1 格 (inflate) 以容纳闪电的抖动宽度
        AABB box = new AABB(start.x, start.y, start.z, end.x, end.y, end.z).inflate(1.0);
        this.setBoundingBox(box);
    }
    public void computeSegments(float partialTicks) {
        int phase = this.tickCount / 4;
        long seed = (long)this.getId() * 192837L + phase * 918273L;

        if (seed == lastSeed && !segments.isEmpty()) return;
        lastSeed = seed;

        segments.clear();
        Vec3 start = getStartVec();
        Vec3 end = getEndVec();
        segments.add(start);

        double totalDist = start.distanceTo(end);

        // [修改点] 判定阈值改为极小值 (0.01)，确保近距离也能生成
        if (totalDist < 0.01) {
            segments.add(end);
            return;
        }

        // [新增] 如果距离太近（比如小于2格），就不生成中间的噪点，直接连线
        // 这样避免近距离时闪电折叠在一起显得很乱
        if (totalDist < 2.0) {
            segments.add(end);
            return;
        }

        Vec3 dir = end.subtract(start);
        Random rand = new Random(seed);

        double traveledDist = 0;

        while (true) {
            double step = 1.0 + rand.nextDouble() * 1.8;
            traveledDist += step;

            if (traveledDist >= totalDist - 1.0) break;

            double progress = traveledDist / totalDist;
            Vec3 basePoint = start.add(dir.scale(progress));

            double jitterScale = 1.2 * Math.sin(progress * Math.PI);

            double theta = rand.nextDouble() * Math.PI * 2.0;
            double phi = (rand.nextDouble() - 0.5) * Math.PI;

            double offsetX = Math.cos(phi) * Math.cos(theta) * jitterScale;
            double offsetY = Math.sin(phi) * jitterScale;
            double offsetZ = Math.cos(phi) * Math.sin(theta) * jitterScale;

            segments.add(basePoint.add(offsetX * 0.5, offsetY * 0.5, offsetZ * 0.5));
        }

        segments.add(end);
    }

    public void setStart(Vec3 v) { this.entityData.set(START_POS, v.toVector3f()); }
    public Vec3 getStartVec() { return new Vec3(this.entityData.get(START_POS)); }

    public void setEnd(Vec3 v) { this.entityData.set(END_POS, v.toVector3f()); }
    public Vec3 getEndVec() { return new Vec3(this.entityData.get(END_POS)); }

    public void setWidth(float w) { this.entityData.set(WIDTH, w); }
    public float getWidth() { return this.entityData.get(WIDTH); }

    public void setOwner(LivingEntity owner) { this.owner = owner; }
    public void setDamage(float damage) { this.damage = damage; }
    public void setLockedTarget(LivingEntity target) { this.lockedTarget = target; }
    @Override protected void readAdditionalSaveData(CompoundTag pCompound) {}
    @Override protected void addAdditionalSaveData(CompoundTag pCompound) {}
}
