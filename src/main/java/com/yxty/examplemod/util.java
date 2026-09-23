package com.yxty.examplemod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;


public class util {
    public static int Get_Base_Staff_Damage(Player player, Level level){//TODO:判断玩家手上的法杖的等级计算基础伤害
        if(!level.isClientSide){
            //player.getMainHandItem().is()
        }
        return 0;
    }
    /**
     * 获取周围最多 6 个无方块遮挡的实体
     *
     * @param source         施法实体 (Entity A)
     * @param level          当前世界
     * @param exemptEntities 豁免者列表，包含在内的实体将不会被选中 (允许传入 null 或空列表)
     * @return 满足条件的实体列表
     */
    public static List<LivingEntity> getVisibleTargets(LivingEntity source, Level level, List<LivingEntity> exemptEntities) {
        // 1. 定义搜索范围 (AABB)
        AABB searchBox = source.getBoundingBox().inflate(8.5D, 2.0D, 8.5D);

        // 2. 初步筛选：获取盒子里所有存活的生物（排除施法者自己、旁观者、以及豁免名单中的实体）
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                entity -> entity != source
                        && entity.isAlive()
                        && !entity.isSpectator()
                        // 【新增】如果传入了豁免列表，且该实体在名单中，则直接跳过
                        && (exemptEntities == null || !exemptEntities.contains(entity))
        );

        // 3. 精确筛选：无遮挡检测 + 距离排序 + 截取前6个
        return nearbyEntities.stream()
                .filter(target -> hasClearLineOfSight(level, source, target))
                .sorted(Comparator.comparingDouble(target -> source.distanceToSqr(target)))
                .limit(6)
                .toList();
    }

    /**
     * 射线检测：判断两实体之间是否有方块阻挡
     */
    private static boolean hasClearLineOfSight(Level level, LivingEntity source, LivingEntity target) {
        Vec3 start = source.getEyePosition();
        Vec3 end = target.getEyePosition();

        ClipContext context = new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                source
        );

        BlockHitResult result = level.clip(context);
        return result.getType() == HitResult.Type.MISS;
    }
}
