package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.exclusion.ClientOnly;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import org.jetbrains.annotations.Nullable;
import java.util.Map;

public class BonesItem {
    @SerializedName("cubes")
    @ClientOnly
    private CubesItem[] cubes;

    @SerializedName("name")
    private String name;

    @SerializedName("pivot")
    private float[] pivot;

    @SerializedName("rotation")
    private float[] rotation;

    @SerializedName("parent")
    private String parent;

    @SerializedName("mirror")
    @ClientOnly
    private boolean mirror = false;

    @SerializedName("locators")
    @ClientOnly
    private Map<String, JsonElement> locators;

    @SerializedName("poly_mesh")
    @ClientOnly
    private PolyMeshItem polyMesh;

    @Nullable
    public CubesItem[] getCubes() {
        return cubes;
    }

    public String getName() {
        return name;
    }

    public float[] getPivot() {
        return pivot;
    }

    public float[] getRotation() {
        return rotation;
    }

    public String getParent() {
        return parent;
    }

    public boolean isMirror() {
        return mirror;
    }

    @Nullable
    public Map<String, JsonElement> getLocators() {
        return locators;
    }

    @Nullable
    public PolyMeshItem getPolyMesh() {
        return polyMesh;
    }
}
