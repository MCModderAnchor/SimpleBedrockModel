package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.LocatorData;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.acceleratedrendering.AcceleratedBedrockGeometryCache;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.LocalCubeBounds;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

public final class TreeBoneDefinition implements BoneDefinition {
    private final String name;
    private final int index;
    private final int parentIndex;
    private final int[] children;
    @Nullable
    private TreeBoneDefinition parent;
    private TreeBoneDefinition[] childBones;
    private final float pivotX;
    private final float pivotY;
    private final float pivotZ;
    private final float bindX;
    private final float bindY;
    private final float bindZ;
    private final Quaternionf bindRotation;
    private final Vector3f bindEulerRotation;
    private final Map<String, LocatorData> locators;
    private final ICube[] cubes;
    @Nullable
    private final LocalCubeBounds ownCubeBounds;
    private final PolyMesh[] polyMeshes;
    private final boolean hasQuadsInTree;
    private final boolean hasVerticesInTree;

    @OnlyIn(Dist.CLIENT)
    private AcceleratedBedrockGeometryCache cache;

    public TreeBoneDefinition(String name, int index, int parentIndex, int[] children,
                              float pivotX, float pivotY, float pivotZ,
                              float bindX, float bindY, float bindZ,
                              Quaternionf bindRotation, Vector3f bindEulerRotation,
                              Map<String, LocatorData> locators, ICube[] cubes, PolyMesh[] polyMeshes,
                              boolean hasQuadsInTree, boolean hasVerticesInTree) {
        this(name, index, parentIndex, children, pivotX, pivotY, pivotZ, bindX, bindY, bindZ,
                bindRotation, bindEulerRotation, locators, cubes, null, polyMeshes, hasQuadsInTree, hasVerticesInTree);
    }

    public TreeBoneDefinition(String name, int index, int parentIndex, int[] children,
                              float pivotX, float pivotY, float pivotZ,
                              float bindX, float bindY, float bindZ,
                              Quaternionf bindRotation, Vector3f bindEulerRotation,
                              Map<String, LocatorData> locators, ICube[] cubes, @Nullable LocalCubeBounds ownCubeBounds, PolyMesh[] polyMeshes,
                              boolean hasQuadsInTree, boolean hasVerticesInTree) {
        this.name = name;
        this.index = index;
        this.parentIndex = parentIndex;
        this.children = children;
        this.childBones = new TreeBoneDefinition[children.length];
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.bindX = bindX;
        this.bindY = bindY;
        this.bindZ = bindZ;
        this.bindRotation = new Quaternionf(bindRotation);
        this.bindEulerRotation = new Vector3f(bindEulerRotation);
        this.locators = Map.copyOf(locators);
        this.cubes = cubes;
        this.ownCubeBounds = ownCubeBounds;
        this.polyMeshes = polyMeshes;
        this.hasQuadsInTree = hasQuadsInTree;
        this.hasVerticesInTree = hasVerticesInTree;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public int parentIndex() {
        return parentIndex;
    }

    @Override
    public int[] children() {
        return children;
    }

    @Nullable
    public TreeBoneDefinition parent() {
        return parent;
    }

    public TreeBoneDefinition[] childBones() {
        return childBones;
    }

    @Override
    public float pivotX() {
        return pivotX;
    }

    @Override
    public float pivotY() {
        return pivotY;
    }

    @Override
    public float pivotZ() {
        return pivotZ;
    }

    @Override
    public float bindX() {
        return bindX;
    }

    @Override
    public float bindY() {
        return bindY;
    }

    @Override
    public float bindZ() {
        return bindZ;
    }

    @Override
    public Quaternionf bindRotation() {
        return new Quaternionf(bindRotation);
    }

    @Override
    public Vector3f bindEulerRotation() {
        return new Vector3f(bindEulerRotation);
    }

    public Map<String, LocatorData> locators() {
        return locators;
    }

    public ICube[] cubes() {
        return cubes;
    }

    @Nullable
    public LocalCubeBounds ownCubeBounds() {
        return ownCubeBounds;
    }

    public PolyMesh[] polyMeshes() {
        return polyMeshes;
    }

    public boolean hasQuadsInTree() {
        return hasQuadsInTree;
    }

    public boolean hasVerticesInTree() {
        return hasVerticesInTree;
    }

    public boolean hasQuads() {
        return cubes.length > 0;
    }

    public boolean hasVertices() {
        return TreeGeometryWriter.hasTriangles(polyMeshes);
    }

    @Override
    public boolean rotateAroundPivot() {
        return false;
    }

    @ApiStatus.Internal
    void linkReferences(TreeBoneDefinition[] bones) {
        parent = parentIndex < 0 ? null : bones[parentIndex];
        TreeBoneDefinition[] linkedChildren = new TreeBoneDefinition[children.length];
        for (int i = 0; i < children.length; i++) {
            linkedChildren[i] = bones[children[i]];
        }
        childBones = linkedChildren;
    }

    @ApiStatus.Internal
    @OnlyIn(Dist.CLIENT)
    public AcceleratedBedrockGeometryCache getOrCreateCache() {
        if (cache == null) {
            cache = new AcceleratedBedrockGeometryCache();
        }
        return cache;
    }
}
