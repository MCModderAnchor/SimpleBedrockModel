package com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 客户端自动暂停时钟的实现
 */
@OnlyIn(Dist.CLIENT)
public final class PausedClientAnimationClock implements AnimationClock {
    private static final PausedClientAnimationClock INSTANCE = new PausedClientAnimationClock();

    private volatile long logicalNanos;
    private long lastRealNanos;
    private boolean initialized;

    private PausedClientAnimationClock() {}

    public static PausedClientAnimationClock getInstance() {
        return INSTANCE;
    }

    public void update() {
        long realNow = System.nanoTime();
        if (!initialized) {
            initialized = true;
            lastRealNanos = realNow;
            return;
        }
        if (Minecraft.getInstance().isPaused()) {
            lastRealNanos = realNow;
            return;
        }
        logicalNanos += realNow - lastRealNanos;
        lastRealNanos = realNow;
    }

    public void reset() {
        logicalNanos = 0L;
        lastRealNanos = 0L;
        initialized = false;
    }

    @Override
    public long nowNanos() {
        return logicalNanos;
    }

    @Override
    public boolean shouldTick() {
        return !Minecraft.getInstance().isPaused();
    }
}
