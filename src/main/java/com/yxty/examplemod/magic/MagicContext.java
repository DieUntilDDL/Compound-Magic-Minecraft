package com.yxty.examplemod.magic;

import com.yxty.examplemod.item.WandCastingStrength;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * 一次魔法事件的上下文。
 *
 * 除 DamageData 的伤害修正阶段外，本类不可变。区域魔法应为每个目标创建一个 Context，
 * 不要把目标列表放进同一个 Context。
 */
public final class MagicContext {
    private final UUID castId;
    private final UUID eventId;
    private final Level level;
    private final Player owner;
    private final LivingEntity actor;
    private final MagicLoadoutSnapshot loadout;
    private final MagicPhase phase;
    private final MagicOrigin origin;
    private final Entity target;
    private final Entity directEntity;
    private final Vec3 castOrigin;
    private final Vec3 castDirection;
    private final Vec3 position;
    private final Vec3 hitNormal;
    private final UUID areaId;
    private final DamageData damage;
    private final boolean hitConfirmed;
    private final boolean blocked;
    private final boolean killed;
    private final boolean primaryCausedKill;
    private final boolean allowSubDispatch;
    private final int pulseIndex;
    private final int subSlotIndex;
    private final int depth;
    private final long startedGameTime;
    private final float castingStrength;

    private MagicContext(Builder builder) {
        this.castId = builder.castId;
        this.eventId = builder.eventId;
        this.level = Objects.requireNonNull(builder.level, "level");
        this.owner = Objects.requireNonNull(builder.owner, "owner");
        this.actor = Objects.requireNonNull(builder.actor, "actor");
        this.loadout = Objects.requireNonNull(builder.loadout, "loadout");
        this.phase = Objects.requireNonNull(builder.phase, "phase");
        this.origin = Objects.requireNonNull(builder.origin, "origin");
        this.target = builder.target;
        this.directEntity = builder.directEntity;
        this.castOrigin = builder.castOrigin;
        this.castDirection = builder.castDirection;
        this.position = builder.position;
        this.hitNormal = builder.hitNormal;
        this.areaId = builder.areaId;
        this.damage = builder.damage;
        this.hitConfirmed = builder.hitConfirmed;
        this.blocked = builder.blocked;
        this.killed = builder.killed;
        this.primaryCausedKill = builder.primaryCausedKill;
        this.allowSubDispatch = builder.allowSubDispatch && builder.origin.allowsSubDispatch();
        this.pulseIndex = builder.pulseIndex;
        this.subSlotIndex = builder.subSlotIndex;
        this.depth = builder.depth;
        this.startedGameTime = builder.startedGameTime;
        this.castingStrength = WandCastingStrength.clamp(builder.castingStrength);
    }

    public static MagicContext forWand(Level level, Player owner, ItemStack stack,
                                       BaseMagic mainMagic) {
        return new Builder(level, owner,
                MagicLoadoutSnapshot.fromStack(stack, mainMagic.getKind()))
                .castingStrength(WandCastingStrength.get(stack))
                .build();
    }

    public static MagicContext single(Level level, Player owner, int mainMagicId, MagicKind mainKind) {
        return begin(level, owner, MagicLoadoutSnapshot.single(mainMagicId, mainKind));
    }

    public static MagicContext begin(Level level, Player owner, MagicLoadoutSnapshot loadout) {
        return new Builder(level, owner, loadout).build();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public MagicContext withPhase(MagicPhase newPhase) {
        return toBuilder().phase(newPhase).eventId(UUID.randomUUID()).build();
    }

    public MagicContext forSubSlot(int slotIndex) {
        return toBuilder().subSlotIndex(slotIndex).depth(depth + 1).build();
    }

    public UUID castId() { return castId; }
    public UUID eventId() { return eventId; }
    public Level level() { return level; }
    public Player owner() { return owner; }
    public LivingEntity actor() { return actor; }
    public MagicLoadoutSnapshot loadout() { return loadout; }
    public MagicPhase phase() { return phase; }
    public MagicOrigin origin() { return origin; }
    public Entity target() { return target; }
    public Entity directEntity() { return directEntity; }
    public Vec3 castOrigin() { return castOrigin; }
    public Vec3 castDirection() { return castDirection; }
    public Vec3 position() { return position; }
    public Vec3 hitNormal() { return hitNormal; }
    public UUID areaId() { return areaId; }
    public DamageData damage() { return damage; }
    public boolean hitConfirmed() { return hitConfirmed; }
    public boolean blocked() { return blocked; }
    public boolean killed() { return killed; }
    public boolean primaryCausedKill() { return primaryCausedKill; }
    public boolean allowSubDispatch() { return allowSubDispatch; }
    public int pulseIndex() { return pulseIndex; }
    public int subSlotIndex() { return subSlotIndex; }
    public int depth() { return depth; }
    public long startedGameTime() { return startedGameTime; }
    public float castingStrength() { return castingStrength; }
    public long gameTime() { return level.getGameTime(); }

    public float scaleDamage(float baseDamage) {
        return Math.max(0.0F, baseDamage) * castingStrength;
    }

    public float scaleBuffValue(float baseValue) {
        return Math.max(0.0F, baseValue) * castingStrength;
    }

    public boolean isServerSide() {
        return !level.isClientSide;
    }

    public static final class Builder {
        private UUID castId;
        private UUID eventId;
        private Level level;
        private Player owner;
        private LivingEntity actor;
        private MagicLoadoutSnapshot loadout;
        private MagicPhase phase;
        private MagicOrigin origin;
        private Entity target;
        private Entity directEntity;
        private Vec3 castOrigin;
        private Vec3 castDirection;
        private Vec3 position;
        private Vec3 hitNormal;
        private UUID areaId;
        private DamageData damage;
        private boolean hitConfirmed;
        private boolean blocked;
        private boolean killed;
        private boolean primaryCausedKill;
        private boolean allowSubDispatch;
        private int pulseIndex;
        private int subSlotIndex;
        private int depth;
        private long startedGameTime;
        private float castingStrength;

        private Builder(Level level, Player owner, MagicLoadoutSnapshot loadout) {
            this.castId = UUID.randomUUID();
            this.eventId = UUID.randomUUID();
            this.level = level;
            this.owner = owner;
            this.actor = owner;
            this.loadout = loadout;
            this.phase = MagicPhase.CAST_BEGIN;
            this.origin = MagicOrigin.PRIMARY;
            this.castOrigin = owner.position();
            this.castDirection = owner.getLookAngle();
            this.position = owner.position();
            this.allowSubDispatch = true;
            this.pulseIndex = 0;
            this.subSlotIndex = -1;
            this.depth = 0;
            this.startedGameTime = level.getGameTime();
            this.castingStrength = WandCastingStrength.DEFAULT;
        }

        private Builder(MagicContext source) {
            this.castId = source.castId;
            this.eventId = source.eventId;
            this.level = source.level;
            this.owner = source.owner;
            this.actor = source.actor;
            this.loadout = source.loadout;
            this.phase = source.phase;
            this.origin = source.origin;
            this.target = source.target;
            this.directEntity = source.directEntity;
            this.castOrigin = source.castOrigin;
            this.castDirection = source.castDirection;
            this.position = source.position;
            this.hitNormal = source.hitNormal;
            this.areaId = source.areaId;
            this.damage = source.damage;
            this.hitConfirmed = source.hitConfirmed;
            this.blocked = source.blocked;
            this.killed = source.killed;
            this.primaryCausedKill = source.primaryCausedKill;
            this.allowSubDispatch = source.allowSubDispatch;
            this.pulseIndex = source.pulseIndex;
            this.subSlotIndex = source.subSlotIndex;
            this.depth = source.depth;
            this.startedGameTime = source.startedGameTime;
            this.castingStrength = source.castingStrength;
        }

        public Builder eventId(UUID eventId) { this.eventId = eventId; return this; }
        public Builder actor(LivingEntity actor) { this.actor = actor; return this; }
        public Builder phase(MagicPhase phase) { this.phase = phase; return this; }
        public Builder origin(MagicOrigin origin) { this.origin = origin; return this; }
        public Builder target(Entity target) { this.target = target; return this; }
        public Builder directEntity(Entity directEntity) { this.directEntity = directEntity; return this; }
        public Builder position(Vec3 position) { this.position = position; return this; }
        public Builder hitNormal(Vec3 hitNormal) { this.hitNormal = hitNormal; return this; }
        public Builder areaId(UUID areaId) { this.areaId = areaId; return this; }
        public Builder damage(DamageData damage) { this.damage = damage; return this; }
        public Builder hitConfirmed(boolean hitConfirmed) { this.hitConfirmed = hitConfirmed; return this; }
        public Builder blocked(boolean blocked) { this.blocked = blocked; return this; }
        public Builder killed(boolean killed) { this.killed = killed; return this; }
        public Builder primaryCausedKill(boolean primaryCausedKill) { this.primaryCausedKill = primaryCausedKill; return this; }
        public Builder allowSubDispatch(boolean allowSubDispatch) { this.allowSubDispatch = allowSubDispatch; return this; }
        public Builder pulseIndex(int pulseIndex) { this.pulseIndex = pulseIndex; return this; }
        public Builder subSlotIndex(int subSlotIndex) { this.subSlotIndex = subSlotIndex; return this; }
        public Builder depth(int depth) { this.depth = depth; return this; }
        public Builder castingStrength(float castingStrength) { this.castingStrength = castingStrength; return this; }

        public MagicContext build() {
            return new MagicContext(this);
        }
    }

    /**
     * 一次伤害结算的数据。BEFORE_DAMAGE 阶段可累计修正，伤害完成后记录真实结果。
     */
    public static final class DamageData {
        private final float baseDamage;
        private final DamageSource damageSource;
        private float additiveMultiplier;
        private float flatBonus;
        private float actualDamage;
        private float executionHealthRemoved;
        private boolean convertibleDamage = true;

        public DamageData(float baseDamage, DamageSource damageSource) {
            this.baseDamage = Math.max(0.0f, baseDamage);
            this.damageSource = damageSource;
        }

        public float baseDamage() { return baseDamage; }
        public DamageSource damageSource() { return damageSource; }
        public float additiveMultiplier() { return additiveMultiplier; }
        public float flatBonus() { return flatBonus; }
        public float actualDamage() { return actualDamage; }
        public float executionHealthRemoved() { return executionHealthRemoved; }
        public boolean convertibleDamage() { return convertibleDamage; }

        public float requestedDamage() {
            return Math.max(0.0f, baseDamage * (1.0f + additiveMultiplier) + flatBonus);
        }

        public void addMultiplier(float multiplier) {
            this.additiveMultiplier += multiplier;
        }

        public void addFlatBonus(float bonus) {
            this.flatBonus += bonus;
        }

        public void recordActualDamage(float actualDamage) {
            this.actualDamage = Math.max(0.0f, actualDamage);
        }

        public void recordExecution(float removedHealth, boolean convertibleDamage) {
            this.executionHealthRemoved = Math.max(0.0f, removedHealth);
            this.convertibleDamage = convertibleDamage;
        }

        public void setConvertibleDamage(boolean convertibleDamage) {
            this.convertibleDamage = convertibleDamage;
        }
    }
}
