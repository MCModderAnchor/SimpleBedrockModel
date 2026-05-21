package com.github.mcmodderanchor.simplebedrockmodel.v2.resource;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.BakedBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.bake.BakerOptions;
import com.google.common.collect.Maps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class BedrockModelResources extends SimplePreparableReloadListener<Map<ResourceLocation, Optional<BedrockModelPOJO>>> {
    private final Map<ResourceLocation, BedrockModelEntry> processors;
    private final List<Consumer<Map<ResourceLocation, BedrockModelResource>>> listeners;
    private final Map<ResourceLocation, Optional<BedrockModelPOJO>> pojoCache;
    private final Map<ResourceLocation, Optional<BedrockModelResource>> resourceCache;
    @Nullable
    private ResourceManager resourceManager;

    public static BedrockModelResources INSTANCE;

    public static BedrockModelResources getInstance() {
        return INSTANCE;
    }

    public BedrockModelResources(Map<ResourceLocation, BedrockModelEntry> processors,
                                 List<Consumer<Map<ResourceLocation, BedrockModelResource>>> listeners) {
        this.processors = Map.copyOf(processors);
        this.listeners = List.copyOf(listeners);
        this.pojoCache = Maps.newHashMap();
        this.resourceCache = Maps.newHashMap();
    }

    @Override
    @NotNull
    @ParametersAreNonnullByDefault
    protected Map<ResourceLocation, Optional<BedrockModelPOJO>> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Optional<BedrockModelPOJO>> result = Maps.newHashMap();
        processors.forEach((location, processor) -> {
            if (processor.lazy()) {
                return;
            }
            result.put(location, loadModelPojo(resourceManager, location, processor));
        });
        return result;
    }

    @Override
    @ParametersAreNonnullByDefault
    protected void apply(Map<ResourceLocation, Optional<BedrockModelPOJO>> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        this.resourceManager = resourceManager;
        pojoCache.clear();
        resourceCache.clear();
        pojoCache.putAll(prepared);
        processors.forEach((location, processor) -> {
            if (processor.lazy()) {
                return;
            }
            Optional<BedrockModelPOJO> pojo = pojoCache.get(location);
            if (pojo == null || pojo.isEmpty()) {
                return;
            }
            resourceCache.put(location, createResource(location, processor, pojo.get()));
        });
        Map<ResourceLocation, BedrockModelResource> resources = getAllResources();
        for (Consumer<Map<ResourceLocation, BedrockModelResource>> listener : listeners) {
            listener.accept(resources);
        }
    }

    @Nullable
    public synchronized BedrockModelResource getResource(ResourceLocation location) {
        Optional<BedrockModelResource> cached = resourceCache.get(location);
        if (cached != null) {
            return cached.orElse(null);
        }
        BedrockModelEntry processor = processors.get(location);
        if (processor == null) {
            SimpleBedrockModel.LOGGER.error("Not registered v2 model: {}", location);
            return null;
        }
        BedrockModelPOJO pojo = getModelPojo(location);
        if (pojo == null) {
            resourceCache.put(location, Optional.empty());
            return null;
        }
        Optional<BedrockModelResource> resource = createResource(location, processor, pojo);
        resourceCache.put(location, resource);
        return resource.orElse(null);
    }

    @Nullable
    public synchronized BakedBedrockModel getModel(ResourceLocation location) {
        BedrockModelResource resource = getResource(location);
        return resource == null ? null : resource.model();
    }

    @Nullable
    public synchronized List<BedrockAnimation> getAnimations(ResourceLocation modelId, ResourceLocation animationId) {
        BedrockModelResource resource = getResource(modelId);
        return resource == null ? null : resource.getAnimations(animationId);
    }

    @Nullable
    public synchronized BedrockModelPOJO getModelPojo(ResourceLocation location) {
        Optional<BedrockModelPOJO> cached = pojoCache.get(location);
        if (cached != null) {
            return cached.orElse(null);
        }
        BedrockModelEntry processor = processors.get(location);
        if (processor == null) {
            SimpleBedrockModel.LOGGER.error("Not registered v2 model: {}", location);
            return null;
        }
        if (resourceManager == null) {
            SimpleBedrockModel.LOGGER.error("Cannot lazy load v2 model before resource reload is applied: {}", location);
            return null;
        }
        Optional<BedrockModelPOJO> pojo = loadModelPojo(resourceManager, location, processor);
        pojoCache.put(location, pojo);
        return pojo.orElse(null);
    }

    public synchronized void clearLoaded(ResourceLocation location) {
        pojoCache.remove(location);
        resourceCache.remove(location);
    }

    public synchronized void clearLoaded() {
        pojoCache.clear();
        resourceCache.clear();
    }

    @UnmodifiableView
    public Map<ResourceLocation, BedrockModelResource> getAllResources() {
        Map<ResourceLocation, BedrockModelResource> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Optional<BedrockModelResource>> entry : resourceCache.entrySet()) {
            entry.getValue().ifPresent(resource -> result.put(entry.getKey(), resource));
        }
        return Collections.unmodifiableMap(result);
    }

    @UnmodifiableView
    public Map<ResourceLocation, BakedBedrockModel> getAllModels() {
        Map<ResourceLocation, BakedBedrockModel> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, BedrockModelResource> entry : getAllResources().entrySet()) {
            result.put(entry.getKey(), entry.getValue().model());
        }
        return Collections.unmodifiableMap(result);
    }

    @UnmodifiableView
    public Map<ResourceLocation, Optional<BedrockModelPOJO>> getAllModelPojos() {
        return Collections.unmodifiableMap(pojoCache);
    }

    private Optional<BedrockModelResource> createResource(ResourceLocation location, BedrockModelEntry processor, BedrockModelPOJO pojo) {
        try {
            List<BedrockAnimationFile> animationFiles = collectAnimationFiles(location, processor);
            BedrockModelBakeContext context = new BedrockModelBakeContext(location, pojo, animationFiles);
            BakerOptions options = processor.optionsFactory().apply(context);
            if (options == null) {
                options = BakerOptions.defaults();
            }
            BakedBedrockModel model = BakedBedrockModel.bake(pojo, options);
            Map<ResourceLocation, List<BedrockAnimation>> animations = createAnimations(processor, model);
            return Optional.of(new BedrockModelResource(model, animations));
        } catch (RuntimeException e) {
            SimpleBedrockModel.LOGGER.error("Failed to create v2 model resource: {}", location, e);
            return Optional.empty();
        }
    }

    private List<BedrockAnimationFile> collectAnimationFiles(ResourceLocation modelId, BedrockModelEntry processor) {
        if (processor.animationSourceIds().isEmpty()) {
            return List.of();
        }
        BedrockAnimationResources animationResourceSet = BedrockAnimationResources.getInstance();
        if (animationResourceSet == null) {
            SimpleBedrockModel.LOGGER.error("Cannot collect animation sources for v2 model {} before v2 animation resource set is initialized", modelId);
            return List.of();
        }
        ArrayList<BedrockAnimationFile> files = new ArrayList<>();
        for (ResourceLocation animationId : processor.animationSourceIds()) {
            BedrockAnimationFile file = animationResourceSet.getAnimationFile(animationId);
            if (file != null) {
                files.add(file);
            }
        }
        return files;
    }

    private Map<ResourceLocation, List<BedrockAnimation>> createAnimations(BedrockModelEntry modelProcessor, BakedBedrockModel model) {
        if (modelProcessor.animationSourceIds().isEmpty()) {
            return Map.of();
        }
        BedrockAnimationResources animationResourceSet = BedrockAnimationResources.getInstance();
        if (animationResourceSet == null) {
            return Map.of();
        }
        Map<ResourceLocation, List<BedrockAnimation>> animations = new LinkedHashMap<>();
        for (ResourceLocation animationId : modelProcessor.animationSourceIds()) {
            BedrockAnimationEntry animationProcessor = animationResourceSet.getProcessor(animationId);
            if (animationProcessor == null || !animationProcessor.createRuntimeAnimations() || animationProcessor.factory() == null) {
                continue;
            }
            BedrockAnimationFile file = animationResourceSet.getAnimationFile(animationId);
            if (file == null) {
                continue;
            }
            List<BedrockAnimation> created = animationProcessor.factory().create(file, model);
            if (created != null) {
                animations.put(animationId, created);
            }
        }
        return animations;
    }

    private static Optional<BedrockModelPOJO> loadModelPojo(ResourceManager resourceManager, ResourceLocation location,
                                                            BedrockModelEntry processor) {
        ResourceLocation path = modelPath(location);
        return resourceManager.getResource(path).map(resource -> {
            try (InputStream stream = resource.open()) {
                return Optional.ofNullable(processor.rawLoader().load(stream, BedrockModelPOJO.class));
            } catch (IOException | RuntimeException e) {
                SimpleBedrockModel.LOGGER.error("Failed to load v2 model file: {}", path, e);
                return Optional.<BedrockModelPOJO>empty();
            }
        }).orElseGet(() -> {
            SimpleBedrockModel.LOGGER.error("Not found v2 model file: {}", path);
            return Optional.empty();
        });
    }

    private static ResourceLocation modelPath(ResourceLocation location) {
        return new ResourceLocation(location.getNamespace(), "models/bedrock/" + location.getPath() + ".json");
    }
}
