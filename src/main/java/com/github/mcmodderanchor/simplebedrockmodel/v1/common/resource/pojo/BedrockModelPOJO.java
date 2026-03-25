package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.exclusion.NeedForRootMotion;
import com.google.gson.annotations.SerializedName;

import org.jetbrains.annotations.Nullable;

public class BedrockModelPOJO {
    @SerializedName("format_version")
    @NeedForRootMotion
    private String formatVersion;

    @SerializedName("geometry.model")
    @Nullable
    @NeedForRootMotion
    private GeometryModelLegacy geometryModelLegacy;

    @SerializedName("minecraft:geometry")
    @Nullable
    @NeedForRootMotion
    private GeometryModelNew[] geometryModelNew;

    public String getFormatVersion() {
        return formatVersion;
    }

    @Nullable
    public GeometryModelLegacy getGeometryModelLegacy() {
        return geometryModelLegacy;
    }

    @Nullable
    public GeometryModelNew getGeometryModelNew() {
        if (geometryModelNew == null || geometryModelNew.length == 0) {
            return null;
        }
        return geometryModelNew[0];
    }
}