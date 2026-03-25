package com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.binding;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.value.ObjectProperty;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.value.ObjectValue;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * A special {@link ObjectValue} implementation that indicates its value
 * is derived from an entity parameter at runtime via a method call.
 *
 * <p>In compiled mode, when the user interface method has an {@code @Entity}
 * parameter, the compiler generates bytecode to call the accessor method
 * on the entity to obtain the actual {@link ObjectValue} at runtime.</p>
 *
 * <p>In interpreted mode, the delegate is used as a fallback.</p>
 */
public class EntityDerivedBinding implements ObjectValue {
    private final Method accessor;
    private final ObjectValue delegate;

    public EntityDerivedBinding(final @NotNull Method accessor, final @NotNull ObjectValue delegate) {
        this.accessor = requireNonNull(accessor, "accessor");
        this.delegate = requireNonNull(delegate, "delegate");
    }

    /**
     * Returns the method on the entity class that produces the actual {@link ObjectValue}.
     */
    public @NotNull Method accessor() {
        return accessor;
    }

    /**
     * Returns the delegate used for interpreted mode fallback.
     */
    public @NotNull ObjectValue delegate() {
        return delegate;
    }

    @Override
    public @Nullable ObjectProperty getProperty(final @NotNull String name) {
        return delegate.getProperty(name);
    }

    @Override
    public @NotNull Value get(final @NotNull String name) {
        return delegate.get(name);
    }

    @Override
    public boolean set(final @NotNull String name, final @Nullable Value value) {
        return delegate.set(name, value);
    }

    @Override
    public @NotNull Map<String, ObjectProperty> entries() {
        return delegate.entries();
    }
}
