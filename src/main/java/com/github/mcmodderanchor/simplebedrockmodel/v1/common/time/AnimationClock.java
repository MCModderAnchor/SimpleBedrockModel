package com.github.mcmodderanchor.simplebedrockmodel.v1.common.time;

import com.maydaymemory.mae.util.LongSupplier;

/**
 * 统一动画时间源<br/>
 * 具体实现见{@link AnimationClocks}，在单人游戏下自动暂停
 */
public interface AnimationClock extends LongSupplier {
    long nowNanos();

    @Override
    default long getAsLong() {
        return nowNanos();
    }

    /**
     * 以ms为单位的时间戳<br/>
     * 此时间代表某种“经过的时间”，与任何系统时间无关<br/>
     * 详见{@link System#nanoTime()}的解释
     */
    default long nowMillis() {
        return nowNanos() / 1_000_000L;
    }

    /**
     * 提供给动画状态机等系统，用于判断当前是否应该更新
     */
    default boolean shouldTick() {
        return true;
    }
}
