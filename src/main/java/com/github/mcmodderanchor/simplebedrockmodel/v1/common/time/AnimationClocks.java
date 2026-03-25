package com.github.mcmodderanchor.simplebedrockmodel.v1.common.time;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.PausedClientAnimationClock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class AnimationClocks {
    // 标准的nanotime时钟，适用于服务端
    private static final AnimationClock SYSTEM = System::nanoTime;
    // 在单人游戏中跟随游戏暂停的时钟
    private static final AnimationClock CLIENT_OR_SYSTEM = DistExecutor.safeCallWhenOn(Dist.CLIENT, () -> ClientClockSupplier::create);

    private AnimationClocks() {}

    public static AnimationClock system() {
        return SYSTEM;
    }

    public static AnimationClock client() {
        return CLIENT_OR_SYSTEM != null ? CLIENT_OR_SYSTEM : SYSTEM;
    }

    public static AnimationClock forEnvironment(boolean clientSide) {
        return clientSide ? client() : system();
    }

    private static final class ClientClockSupplier {
        private static AnimationClock create() {
            return PausedClientAnimationClock.getInstance();
        }
    }
}
