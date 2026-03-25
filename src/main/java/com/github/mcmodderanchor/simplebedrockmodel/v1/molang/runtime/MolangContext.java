package com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.molang.DynamicQueryBinding;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.value.MutableObjectBinding;

import org.jetbrains.annotations.Nullable;

/**
 * Molang 求值上下文。持有当前关联对象的可变引用、独立的 variable 存储和 query 绑定。
 * <p>
 * 使用方式：每个需要独立 Molang 变量空间的实例（如每个实体）持有自己的 MolangContext。
 * 调用方通过 {@link #getQueryBinding()} 注册自定义 query 属性来扩展上下文。
 * <p>
 * 动画系统中，由于 mae 库的 Keyframe 接口无参，使用 {@link #setCurrent(MolangContext)} /
 * {@link #getCurrent()} 通过 ThreadLocal 传递当前上下文。
 *
 * @param <T> 关联对象的类型（如 Entity、LivingEntity 等）
 */
public class MolangContext<T> {
    private static final ThreadLocal<MolangContext<?>> CURRENT = new ThreadLocal<>();

    private @Nullable T entity;
    private final DynamicQueryBinding queryBinding;
    private final MutableObjectBinding variableStorage;
    private double animTime;

    public MolangContext() {
        this(null, new DynamicQueryBinding(), new MutableObjectBinding());
    }

    public MolangContext(@Nullable T entity) {
        this(entity, new DynamicQueryBinding(), new MutableObjectBinding());
    }

    public MolangContext(@Nullable T entity, DynamicQueryBinding queryBinding, MutableObjectBinding variableStorage) {
        this.entity = entity;
        this.queryBinding = queryBinding;
        this.variableStorage = variableStorage;
        queryBinding.registerProperty("anim_time", this::getAnimTime);
    }

    @Nullable
    public T getEntity() {
        return entity;
    }

    public void setEntity(@Nullable T entity) {
        this.entity = entity;
    }

    /**
     * 获取当前动画播放时间（秒）。对应 Molang 中的 {@code query.anim_time}。
     */
    public double getAnimTime() {
        return animTime;
    }

    /**
     * 设置当前动画播放时间（秒）。在动画求值前调用。
     */
    public void setAnimTime(double animTime) {
        this.animTime = animTime;
    }

    /**
     * 获取此 context 的 query 绑定。
     * 调用方通过 registerProperty / registerFunction 等方法扩展 query 命名空间。
     */
    @SuppressWarnings("unused")
    public DynamicQueryBinding getQueryBinding() {
        return queryBinding;
    }

    /**
     * 获取此 context 独立的 variable 存储。
     * 对应 Molang 中的 variable / v 命名空间。
     */
    public MutableObjectBinding getVariableStorage() {
        return variableStorage;
    }

    /**
     * 设置当前线程的活跃 MolangContext（用于 ThreadLocal 传递）。
     * 动画系统在求值前调用此方法，关键帧内部通过 {@link #getCurrent()} 获取。
     */
    public static void setCurrent(@Nullable MolangContext<?> context) {
        if (context != null) {
            CURRENT.set(context);
        } else {
            CURRENT.remove();
        }
    }

    /**
     * 获取当前线程的活跃 MolangContext。
     */
    @Nullable
    public static MolangContext<?> getCurrent() {
        return CURRENT.get();
    }
}
