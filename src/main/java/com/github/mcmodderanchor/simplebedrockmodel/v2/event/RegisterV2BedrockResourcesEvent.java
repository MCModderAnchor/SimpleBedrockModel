package com.github.mcmodderanchor.simplebedrockmodel.v2.event;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoaders;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockAnimationEntry;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockAnimationFactory;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelBakeContext;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelEntry;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelResource;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.ModelType;
import com.google.common.collect.Maps;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class RegisterV2BedrockResourcesEvent extends Event implements IModBusEvent {
    private final Map<ResourceLocation, BedrockModelEntry> modelRegistry;
    private final Map<ResourceLocation, BedrockAnimationEntry> animationRegistry;
    private final List<Consumer<Map<ResourceLocation, BedrockModelResource>>> reloadListeners;
    private final Dist dist;

    public RegisterV2BedrockResourcesEvent(Dist dist) {
        this.modelRegistry = Maps.newHashMap();
        this.animationRegistry = Maps.newHashMap();
        this.reloadListeners = new ArrayList<>();
        this.dist = dist;
    }

    public ModelBuilder treeModel(ResourceLocation modelId) {
        return treeModel(modelId, modelId);
    }

    public ModelBuilder treeModel(ResourceLocation modelId, ResourceLocation sourceId) {
        return treeModel(modelId, sourceId, RawResourceLoaders.COMMON_LOADER);
    }

    public ModelBuilder treeModel(ResourceLocation modelId, ResourceLocation sourceId, RawResourceLoader modelLoader) {
        return new ModelBuilder(modelId, sourceId, modelLoader, ModelType.TREE);
    }

    public ModelBuilder bakedModel(ResourceLocation modelId) {
        return bakedModel(modelId, modelId);
    }

    public ModelBuilder bakedModel(ResourceLocation modelId, ResourceLocation sourceId) {
        return bakedModel(modelId, sourceId, RawResourceLoaders.COMMON_LOADER);
    }

    public ModelBuilder bakedModel(ResourceLocation modelId, ResourceLocation sourceId, RawResourceLoader modelLoader) {
        return new ModelBuilder(modelId, sourceId, modelLoader, ModelType.BAKED);
    }

    /**
     * @deprecated Use {@link #bakedModel(ResourceLocation)} or {@link #treeModel(ResourceLocation)} to make the
     * runtime model type explicit.
     */
    @Deprecated
    public ModelBuilder model(ResourceLocation modelId) {
        return bakedModel(modelId);
    }

    /**
     * @deprecated Use {@link #bakedModel(ResourceLocation, ResourceLocation, RawResourceLoader)} or
     * {@link #treeModel(ResourceLocation, ResourceLocation, RawResourceLoader)} to make the runtime model type explicit.
     */
    @Deprecated
    public ModelBuilder model(ResourceLocation modelId, RawResourceLoader modelLoader) {
        return bakedModel(modelId, modelId, modelLoader);
    }

    public void onReload(Consumer<Map<ResourceLocation, BedrockModelResource>> listener) {
        reloadListeners.add(listener);
    }

    public Dist getDist() {
        return dist;
    }

    public Map<ResourceLocation, BedrockModelEntry> getModelRegistry() {
        return modelRegistry;
    }

    public Map<ResourceLocation, BedrockAnimationEntry> getAnimationRegistry() {
        return animationRegistry;
    }

    public List<Consumer<Map<ResourceLocation, BedrockModelResource>>> getReloadListeners() {
        return reloadListeners;
    }

    public final class ModelBuilder {
        private final ResourceLocation modelId;
        private final ResourceLocation sourceId;
        private final RawResourceLoader modelLoader;
        private final ModelType kind;
        private final LinkedHashMap<ResourceLocation, AnimationRegistration> animations = new LinkedHashMap<>();
        private Function<BedrockModelBakeContext, BakerOptions> optionsFactory;
        private boolean lazy;

        private ModelBuilder(ResourceLocation modelId, ResourceLocation sourceId, RawResourceLoader modelLoader, ModelType kind) {
            this.modelId = modelId;
            this.sourceId = sourceId;
            this.modelLoader = modelLoader;
            this.kind = kind;
        }

        public ModelBuilder lazy() {
            this.lazy = true;
            return this;
        }

        public ModelBuilder options(BakerOptions options) {
            return options(context -> options);
        }

        public ModelBuilder options(Function<BedrockModelBakeContext, BakerOptions> optionsFactory) {
            this.optionsFactory = optionsFactory;
            return this;
        }

        public ModelBuilder animation(ResourceLocation animationId) {
            return animation(animationId, RawResourceLoaders.COMMON_LOADER, BedrockAnimation::createAnimation);
        }

        public ModelBuilder animation(ResourceLocation animationId, BedrockAnimationFactory factory) {
            return animation(animationId, RawResourceLoaders.COMMON_LOADER, factory);
        }

        public ModelBuilder animation(ResourceLocation animationId, RawResourceLoader animationLoader, BedrockAnimationFactory factory) {
            animations.put(animationId, new AnimationRegistration(animationLoader, factory));
            return this;
        }

        public void register() {
            Function<BedrockModelBakeContext, BakerOptions> factory = optionsFactory != null
                    ? optionsFactory
                    : context -> animations.isEmpty() ? BakerOptions.defaults() : context.optionsFromAnimations();
            modelRegistry.put(modelId, new BedrockModelEntry(
                    modelLoader, sourceId, kind, factory, new ArrayList<>(animations.keySet()), lazy));
            for (Map.Entry<ResourceLocation, AnimationRegistration> entry : animations.entrySet()) {
                AnimationRegistration animation = entry.getValue();
                animationRegistry.put(entry.getKey(), new BedrockAnimationEntry(
                        animation.loader(), modelId, animation.factory(), lazy, true));
            }
        }
    }

    private record AnimationRegistration(
            RawResourceLoader loader,
            BedrockAnimationFactory factory
    ) {
    }
}
