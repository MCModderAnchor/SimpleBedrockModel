package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;

/**
 * 发射器运行时组件接口。每个 emitter 持有独立实例，可持有运行时状态。
 * <p>
 * 定义层请使用 {@link IEmitterComponentDefinition}。
 */
public interface IEmitterComponent extends IComponent {
    /**
     * 发射器创建时调用一次。
     */
    default void apply(ParticleEmitterInstance emitter) {}

    /**
     * 发射器每 tick 调用。
     */
    default void update(ParticleEmitterInstance emitter) {}
}
