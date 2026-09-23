package com.yxty.examplemod.magic;

/**
 * 一次魔法事件所处的结算阶段。副魔法根据阶段决定是否响应。
 */
public enum MagicPhase {
    CAST_BEGIN,
    BUFF_APPLIED,
    BEFORE_DAMAGE,
    AFTER_DAMAGE,
    ENTITY_HIT,
    ENTITY_KILLED,
    POSITION_HIT,
    CAST_END
}
