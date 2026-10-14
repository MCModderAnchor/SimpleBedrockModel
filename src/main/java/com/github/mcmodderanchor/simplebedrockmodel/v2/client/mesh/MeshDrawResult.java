package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh;

/** 即时绘制结果；PENDING 表示本次渲染调用仍由网格路径接管。 */
public enum MeshDrawResult {
    DRAWN, DRAWN_PREVIOUS, PENDING, UNSUPPORTED;

    public boolean owned() { return this != UNSUPPORTED; }
    public boolean drawn() { return this == DRAWN || this == DRAWN_PREVIOUS; }
    public boolean usedPrevious() { return this == DRAWN_PREVIOUS; }
}
