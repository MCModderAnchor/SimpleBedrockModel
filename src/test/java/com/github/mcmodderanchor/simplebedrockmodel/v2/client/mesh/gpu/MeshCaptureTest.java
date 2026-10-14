package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshSink;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.GeometryCollector;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.LightPassVertexRouter;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MeshCaptureTest {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int OVERLAY = 0x00020001;
    private static final int COLOR = 0x4E0C2238;

    @BeforeAll
    static void bootstrapMaterialRegistries() {
        // Plain JUnit has no mod discovery; vanilla feature flags still inspect the loading list.
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void capturesFinalChainedVertexWithPackedAttributes() {
        MeshSink mesh = new MeshSink().begin(VertexFormat.Mode.TRIANGLES);
        chainedVertex(mesh, 0, 0);
        chainedVertex(mesh, 1, 0);
        chainedVertex(mesh, 2, FULL_BRIGHT);

        List<Vertex> vertices = replay(mesh);
        assertEquals(3, vertices.size());
        assertEquals(new Vertex(2, 0, 0, COLOR, 0.25F, 0.75F, OVERLAY, FULL_BRIGHT, 0, 1, 0), vertices.get(2));
        assertEquals(2, mesh.bounds().maxX);
        assertEquals(2, mesh.lightRunCount());
        assertEquals(2, mesh.lightRunStart(1));
        assertEquals(1, mesh.lightRunLength(1));
        assertEquals(FULL_BRIGHT, mesh.lightRunValue(1));
    }

    @Test
    void collectorFlushesLastChainedVertexIntoTheEmissivePass() {
        GeometryCollector collector = new GeometryCollector();
        RenderType ordinary = material("ordinary");
        RenderType emissive = material("emissive");
        VertexConsumer out = collector.buffer(ordinary, emissive, VertexFormat.Mode.TRIANGLES);
        for (int i = 0; i < 6; i++) chainedVertex(out, i, i < 3 ? 0 : FULL_BRIGHT);

        Map<GeometryCollector.Pass, MeshSink> meshes = collector.snapshot();
        MeshSink ordinaryMesh = meshes.get(new GeometryCollector.Pass(ordinary, VertexFormat.Mode.TRIANGLES));
        MeshSink emissiveMesh = meshes.get(new GeometryCollector.Pass(emissive, VertexFormat.Mode.TRIANGLES));
        assertEquals(List.of(0F, 1F, 2F), replay(ordinaryMesh).stream().map(Vertex::x).toList());
        assertEquals(List.of(3F, 4F, 5F), replay(emissiveMesh).stream().map(Vertex::x).toList());
        assertEquals(WorldMeshPart.LightMode.UNIFORM, MeshLighting.classify(ordinaryMesh, MeshLighting.INSTANCE));
        assertEquals(WorldMeshPart.LightMode.FIXED, MeshLighting.classify(emissiveMesh, MeshLighting.INSTANCE));
        assertEquals(3, collector.snapshot().get(new GeometryCollector.Pass(emissive, VertexFormat.Mode.TRIANGLES)).vertexCount());
    }

    @Test
    void bulkVertexCallsKeepBothTopologiesAndLightPassesComplete() {
        for (VertexFormat.Mode mode : List.of(VertexFormat.Mode.QUADS, VertexFormat.Mode.TRIANGLES)) {
            MeshSink ordinary = new MeshSink().begin(mode);
            MeshSink emissive = new MeshSink().begin(mode);
            LightPassVertexRouter router = new LightPassVertexRouter(ordinary, emissive, mode);
            int size = mode == VertexFormat.Mode.QUADS ? 4 : 3;
            for (int i = 0; i < size * 2; i++) {
                router.addVertex(i, 0, 0, COLOR, 0.25F, 0.75F, OVERLAY, i < size ? 0 : FULL_BRIGHT, 0, 1, 0);
            }
            router.finish();
            router.finish();
            assertEquals(size, ordinary.vertexCount());
            assertEquals(size, emissive.vertexCount());
            assertEquals(size * 2 - 1, replay(emissive).getLast().x());
        }
    }

    @Test
    void rejectsDifferentLightSemanticsInsideOnePrimitive() {
        LightPassVertexRouter router = new LightPassVertexRouter(
                new MeshSink().begin(VertexFormat.Mode.TRIANGLES),
                new MeshSink().begin(VertexFormat.Mode.TRIANGLES), VertexFormat.Mode.TRIANGLES);
        chainedVertex(router, 0, 0);
        chainedVertex(router, 1, FULL_BRIGHT);
        assertThrows(IllegalArgumentException.class, router::finish);
    }

    @Test
    void repeatedBuffersKeepChainedVerticesInCaptureOrder() {
        GeometryCollector collector = new GeometryCollector();
        RenderType ordinary = material("ordinary");
        RenderType emissive = material("emissive");
        for (int batch = 0; batch < 2; batch++) {
            VertexConsumer out = collector.buffer(ordinary, emissive, VertexFormat.Mode.TRIANGLES);
            for (int i = 0; i < 3; i++) chainedVertex(out, batch * 3 + i, 0);
        }
        MeshSink mesh = collector.snapshot().get(new GeometryCollector.Pass(ordinary, VertexFormat.Mode.TRIANGLES));
        assertEquals(List.of(0F, 1F, 2F, 3F, 4F, 5F), replay(mesh).stream().map(Vertex::x).toList());
    }

    @Test
    void sectionReplayTransformsPositionsAndNormalsButKeepsPackedAttributes() {
        MeshSink mesh = new MeshSink().begin(VertexFormat.Mode.TRIANGLES);
        chainedVertex(mesh, 0, FULL_BRIGHT);
        chainedVertex(mesh, 1, FULL_BRIGHT);
        chainedVertex(mesh, 2, FULL_BRIGHT);
        RecordingConsumer out = new RecordingConsumer();
        mesh.emitTo(out, new Matrix4f().rotateZ((float) Math.PI / 2).scale(2, 3, 4), 8, 9, 10);

        Vertex vertex = out.vertices.get(1);
        assertEquals(8, vertex.x(), 1.0E-5);
        assertEquals(11, vertex.y(), 1.0E-5);
        assertEquals(10, vertex.z(), 1.0E-5);
        assertEquals(-1, vertex.nx(), 1.0E-5);
        assertEquals(0, vertex.ny(), 1.0E-5);
        assertEquals(COLOR, vertex.color());
        assertEquals(OVERLAY, vertex.overlay());
        assertEquals(FULL_BRIGHT, vertex.light());
    }

    @Test
    void clearingCollectorDiscardsPendingVerticesAndRouters() {
        GeometryCollector collector = new GeometryCollector();
        chainedVertex(collector.buffer(material("ordinary"), material("emissive"), VertexFormat.Mode.TRIANGLES), 0, 0);
        collector.clear();
        assertTrue(collector.snapshot().isEmpty());
        VertexConsumer out = collector.buffer(material("ordinary"), VertexFormat.Mode.TRIANGLES);
        for (int i = 0; i < 3; i++) chainedVertex(out, i, 0);
        assertEquals(3, collector.snapshot().values().iterator().next().vertexCount());
    }

    private static RenderType material(String name) {
        return RenderType.entityCutout(ResourceLocation.fromNamespaceAndPath("test", "textures/" + name + ".png"));
    }

    private static void chainedVertex(VertexConsumer out, float x, int light) {
        out.addVertex(x, 0, 0).setColor(12, 34, 56, 78).setUv(0.25F, 0.75F)
                .setOverlay(OVERLAY).setLight(light).setNormal(0, 1, 0);
    }

    private static List<Vertex> replay(MeshSink mesh) {
        RecordingConsumer out = new RecordingConsumer();
        mesh.emitTo(out);
        return out.vertices;
    }

    private record Vertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                          float nx, float ny, float nz) {}

    private static final class RecordingConsumer implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                              float nx, float ny, float nz) {
            vertices.add(new Vertex(x, y, z, color, u, v, overlay, light, nx, ny, nz));
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv(float u, float v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv1(int u, int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv2(int u, int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { throw new UnsupportedOperationException(); }
    }
}
