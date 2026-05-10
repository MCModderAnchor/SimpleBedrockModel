package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.exclusion.ClientOnly;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import org.jetbrains.annotations.Nullable;

public class PolyMeshItem {
    @SerializedName("normalized_uvs")
    @ClientOnly
    private boolean normalizedUvs;

    @SerializedName("positions")
    @ClientOnly
    private float[][] positions;

    @SerializedName("normals")
    @ClientOnly
    private float[][] normals;

    @SerializedName("uvs")
    @ClientOnly
    private float[][] uvs;

    @SerializedName("polys")
    @ClientOnly
    private JsonElement polys;

    public boolean isNormalizedUvs() {
        return normalizedUvs;
    }

    @Nullable
    public float[][] getPositions() {
        return positions;
    }

    @Nullable
    public float[][] getNormals() {
        return normals;
    }

    @Nullable
    public float[][] getUvs() {
        return uvs;
    }

    @Nullable
    public JsonElement getPolys() {
        return polys;
    }
}
