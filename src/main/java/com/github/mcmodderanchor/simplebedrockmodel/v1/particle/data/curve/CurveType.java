package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.curve;

/**
 * 曲线插值类型。
 */
public enum CurveType {
    LINEAR,
    BEZIER,
    CATMULL_ROM,
    BEZIER_CHAIN;

    public static CurveType fromString(String str) {
        return switch (str.toLowerCase()) {
            case "bezier" -> BEZIER;
            case "catmull_rom" -> CATMULL_ROM;
            case "bezier_chain" -> BEZIER_CHAIN;
            default -> LINEAR;
        };
    }
}
