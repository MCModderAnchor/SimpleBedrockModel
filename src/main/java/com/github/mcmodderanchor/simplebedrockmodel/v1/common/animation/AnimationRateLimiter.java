package com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 动画求值频率限制器，同时充当缓存包装。
 *
 * <p>用于在高刷新率显示器上降低动画求值（关键帧插值 + Molang 表达式 + 混合）的 CPU 开销。
 * 不限制 tick 副作用（声音、粒子、状态转换），调用方自行决定副作用是否需要每帧执行。</p>
 *
 * @param <T> 缓存值的类型
 */
public final class AnimationRateLimiter<T> {
    public static final long FPS_30 = 33_333_333L;
    public static final long FPS_60 = 16_666_667L;
    public static final long FPS_120 = 8_333_333L;
    public static final long UNLIMITED = 0L;

    private final AnimationClock clock;
    private final LongSupplier intervalSupplier;
    private long lastUpdateNanos;
    private boolean hasUpdate;
    private T cachedValue;

    /**
     * 固定更新间隔。
     *
     * @param clock            时间源，通常使用 {@code AnimationClocks.client()}
     * @param minIntervalNanos 最小更新间隔（纳秒），可使用预设常量 {@link #FPS_60} 等
     */
    public AnimationRateLimiter(AnimationClock clock, long minIntervalNanos) {
        this(clock, () -> minIntervalNanos);
    }

    /**
     * 动态更新间隔，每次 {@link #shouldUpdate()} 都会查询 supplier 获取最新阈值。
     *
     * @param clock            时间源
     * @param intervalSupplier 每次查询时返回当前最小更新间隔（纳秒），返回 {@code <= 0} 表示不限制
     */
    public AnimationRateLimiter(AnimationClock clock, LongSupplier intervalSupplier) {
        this.clock = clock;
        this.intervalSupplier = intervalSupplier;
    }

    /**
     * 用给定的 supplier 求值并返回结果。
     *
     * <p>如果距离上次更新已超过阈值，则调用 supplier 并将结果缓存；否则直接返回缓存值，
     * supplier 不会被调用。</p>
     *
     * @param supplier 求值函数（仅在需要更新时调用，不会被缓存引用）
     * @return 当前帧应使用的值（可能是新求值或缓存）
     */
    public T update(Supplier<T> supplier) {
        if (shouldUpdate()) {
            cachedValue = supplier.get();
        }
        return cachedValue;
    }

    /**
     * 返回缓存的值，不触发求值。
     *
     * @return 上次 {@link #update(Supplier)} 缓存的值，可能为 {@code null}
     */
    public T getCachedValue() {
        return cachedValue;
    }

    /**
     * 直接设置缓存值并更新内部时间戳。
     * 适合需要预热缓存（如首帧手动设置为 bind pose）或外部已有计算结果直接注入的场景。
     *
     * @param value 要缓存的值
     */
    public void setCachedValue(T value) {
        this.cachedValue = value;
        this.lastUpdateNanos = clock.nowNanos();
        this.hasUpdate = true;
    }

    /**
     * 判断当前帧是否应执行完整的动画求值。
     *
     * <p>首次调用必然返回 {@code true}。当当前阈值为 {@code <= 0} 时始终返回 {@code true}。</p>
     *
     * @return true 表示应该重新求值；false 表示可以跳过
     */
    public boolean shouldUpdate() {
        long interval = intervalSupplier.getAsLong();
        if (interval <= 0) {
            return true;
        }
        long now = clock.nowNanos();
        if (!hasUpdate || now - lastUpdateNanos >= interval) {
            lastUpdateNanos = now;
            hasUpdate = true;
            return true;
        }
        return false;
    }

    /**
     * 重置内部计时状态和缓存，使下一次 {@link #shouldUpdate()} / {@link #update(Supplier)} 必然调用 supplier。
     */
    public void reset() {
        hasUpdate = false;
        lastUpdateNanos = 0;
    }
}
