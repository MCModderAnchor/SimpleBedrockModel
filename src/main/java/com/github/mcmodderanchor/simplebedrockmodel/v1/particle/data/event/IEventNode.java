package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event;

/**
 * 粒子事件节点接口。
 * <p>
 * 事件节点定义在 {@code particle_effect.events} 中，由生命周期事件组件触发。
 */
public sealed interface IEventNode permits
        ParticleEffectEvent, SoundEffectEvent, EventSequence, EventRandomize, EventLog, MolangExpressionEvent {
}
