package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh;

/** 在 submit 中选定网格，随后调用方才能生成与之对应的动态效果。 */
public enum MeshSubmitResult {
    QUEUED, QUEUED_PREVIOUS, PENDING, UNSUPPORTED;

    public boolean owned() { return this != UNSUPPORTED; }
    public boolean hasMesh() { return this == QUEUED || this == QUEUED_PREVIOUS; }
    public boolean usedPrevious() { return this == QUEUED_PREVIOUS; }
}
