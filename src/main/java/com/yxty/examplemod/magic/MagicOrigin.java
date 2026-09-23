package com.yxty.examplemod.magic;

/**
 * 魔法事件的来源。只有允许分发的来源才能继续触发副槽。
 */
public enum MagicOrigin {
    PRIMARY(true),
    BLESSED_PROXY(true),
    SHIELD_COUNTER(true),
    SUB_EFFECT(false),
    SELF_COST(false),
    ENVIRONMENT(false);

    private final boolean allowsSubDispatch;

    MagicOrigin(boolean allowsSubDispatch) {
        this.allowsSubDispatch = allowsSubDispatch;
    }

    public boolean allowsSubDispatch() {
        return allowsSubDispatch;
    }
}
