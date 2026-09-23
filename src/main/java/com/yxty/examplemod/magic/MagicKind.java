package com.yxty.examplemod.magic;

/**
 * 基础魔法的四种类别。
 * INSTANT   激发：蓄力完成后一次性释放
 * SUSTAINED 持续：按住右键维持，松手立刻中断
 * FORMATION 法阵：落地后在固定区域持续生效
 * BUFF      增益：给实体附加状态
 */
public enum MagicKind {
    INSTANT,
    SUSTAINED,
    FORMATION,
    BUFF
}
