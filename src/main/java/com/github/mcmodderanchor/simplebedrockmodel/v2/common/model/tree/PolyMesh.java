package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree;

public record PolyMesh(Triangle[] triangles, float x, float y, float z, float width, float height, float depth) {
    public PolyMesh {
        triangles = triangles == null ? new Triangle[0] : triangles;
    }

    public boolean hasTriangles() {
        return triangles.length > 0;
    }

    public record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
    }

    public record Triangle(Vertex a, Vertex b, Vertex c) {
        public Vertex vertex(int index) {
            return switch (index) {
                case 0 -> a;
                case 1 -> b;
                case 2 -> c;
                default -> throw new IndexOutOfBoundsException(index);
            };
        }
    }
}
