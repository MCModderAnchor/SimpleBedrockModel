package com.github.mcmodderanchor.simplebedrockmodel.v1.common.molang;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.value.*;

import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 支持动态求值的 ObjectValue 实现。
 * <p>
 * 属性访问时（如 {@code query.is_on_ground}）会实时调用注册的 Supplier 求值，
 * 而不是返回静态缓存值。同时支持注册 {@link Function} 类型的属性用于带参数调用
 * （如 {@code query.position(0)}）。
 */
public class DynamicQueryBinding implements ObjectValue {
    private final Map<String, Object> bindings = new HashMap<>();

    /**
     * 注册一个动态属性，每次访问时通过 supplier 实时求值。
     * 适用于 {@code query.is_on_ground} 这种不带括号的属性访问。
     */
    public void registerProperty(String name, Supplier<Double> supplier) {
        bindings.put(name, supplier);
    }

    /**
     * 注册一个布尔动态属性，true 映射为 1.0，false 映射为 0.0。
     */
    public void registerBooleanProperty(String name, Supplier<Boolean> supplier) {
        bindings.put(name, (Supplier<Double>) () -> supplier.get() ? 1.0 : 0.0);
    }

    /**
     * 注册一个静态常量属性。
     */
    public void registerConstant(String name, double value) {
        bindings.put(name, NumberValue.of(value));
    }

    /**
     * 注册一个函数属性，适用于 {@code query.position(0)} 这种带括号的调用。
     */
    public void registerFunction(String name, Function<?> function) {
        bindings.put(name, function);
    }

    @Override
    @Nullable
    public ObjectProperty getProperty(String name) {
        Object binding = bindings.get(name);
        if (binding == null) return null;

        if (binding instanceof Supplier) {
            @SuppressWarnings("unchecked")
            Supplier<Double> supplier = (Supplier<Double>) binding;
            return ObjectProperty.property(NumberValue.of(supplier.get()), false);
        }
        if (binding instanceof Value) {
            return ObjectProperty.property((Value) binding, binding instanceof NumberValue);
        }
        return null;
    }

    @Override
    public boolean set(String name, @Nullable Value value) {
        return false; // query 命名空间只读
    }
}
