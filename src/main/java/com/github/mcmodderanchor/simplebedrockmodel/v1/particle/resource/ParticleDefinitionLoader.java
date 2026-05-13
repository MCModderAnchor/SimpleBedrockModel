package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDeserializer;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * 粒子定义资源加载器。
 * <p>
 * 扫描 {@code assets/<namespace>/particle_definitions/<name>.json} 路径，
 * 解析基岩版粒子效果定义并缓存。
 * <p>
 * 每个粒子效果定义在加载时使用共享的 {@link ParticleMolangEnvironment} 编译 Molang 表达式。
 */
public class ParticleDefinitionLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {
    private static final String DIRECTORY = "particle_definitions";
    private static final ParticleEffectDeserializer DESERIALIZER = new ParticleEffectDeserializer();

    private static ParticleDefinitionLoader INSTANCE;

    private final ParticleMolangEnvironment molang;
    private final Map<ResourceLocation, ParticleEffectDefinition> cache = Maps.newHashMap();
    private final Map<ResourceLocation, ParticleEffectDefinition> identifierIndex = Maps.newHashMap();

    public ParticleDefinitionLoader(ParticleMolangEnvironment molang) {
        this.molang = molang;
    }

    public static ParticleDefinitionLoader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ParticleDefinitionLoader(new ParticleMolangEnvironment());
        }
        return INSTANCE;
    }

    @Override
    @NotNull
    @ParametersAreNonnullByDefault
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> result = Maps.newHashMap();
        resourceManager.listResources(DIRECTORY, loc -> loc.getPath().endsWith(".json"))
                .forEach((location, resource) -> {
                    try (InputStream stream = resource.open()) {
                        JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                        String path = location.getPath();
                        String name = path.substring(DIRECTORY.length() + 1, path.length() - 5);
                        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(location.getNamespace(), name);
                        result.put(id, json);
                    } catch (IOException e) {
                        SimpleBedrockModel.LOGGER.error("Failed to read particle definition: {}", location, e);
                    }
                });
        return result;
    }

    @Override
    @ParametersAreNonnullByDefault
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        cache.clear();
        identifierIndex.clear();
        prepared.forEach((id, json) -> {
            try {
                ParticleEffectDefinition definition = DESERIALIZER.parse(json, molang);
                if (definition != null) {
                    cache.put(id, definition);
                    identifierIndex.put(definition.getIdentifier(), definition);
                    SimpleBedrockModel.LOGGER.debug("Loaded particle definition: {}", id);
                }
            } catch (Exception e) {
                SimpleBedrockModel.LOGGER.error("Failed to parse particle definition: {}", id, e);
            }
        });
        SimpleBedrockModel.LOGGER.info("Loaded {} particle definitions", cache.size());
    }

    @Nullable
    public ParticleEffectDefinition getDefinition(ResourceLocation id) {
        ParticleEffectDefinition def = cache.get(id);
        if (def != null) return def;
        return identifierIndex.get(id);
    }

    public Map<ResourceLocation, ParticleEffectDefinition> getAllDefinitions() {
        return Collections.unmodifiableMap(cache);
    }

    public ParticleMolangEnvironment getMolang() {
        return molang;
    }
}
