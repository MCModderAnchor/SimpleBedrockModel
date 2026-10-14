package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.MeshIntegerAttributes;

import org.junit.jupiter.api.Test;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

class ImmediateIntegerAttributesTest {
    @Test
    void everySupportedPackedPairAddressesItsExactRecord() {
        IntBuffer table = IntBuffer.allocate(MeshIntegerAttributes.TABLE_BYTES / Integer.BYTES);
        MeshIntegerAttributes.fillParameters(table);
        assertEquals(table.capacity(), table.position());
        for (int v = 0; v < 256; v++) {
            for (int u = 0; u < 256; u++) {
                long offset = MeshIntegerAttributes.parameterOffset((v << 16) | u);
                assertEquals(0, offset % MeshIntegerAttributes.STRIDE);
                assertTrue(offset >= 0 && offset + MeshIntegerAttributes.STRIDE <= MeshIntegerAttributes.TABLE_BYTES);
                int index = Math.toIntExact(offset / Integer.BYTES);
                assertEquals(u, table.get(index));
                assertEquals(v, table.get(index + 1));
            }
        }
        assertEquals(512 * 1024, MeshIntegerAttributes.TABLE_BYTES);
    }

    @Test
    void unsupportedComponentsCannotAliasValidRecordsByTruncation() {
        for (int invalid : new int[]{256, 257, 32768, 65535}) {
            assertEquals(-1, MeshIntegerAttributes.parameterOffset(invalid));
            assertEquals(-1, MeshIntegerAttributes.parameterOffset(invalid << 16));
            assertEquals(-1, MeshIntegerAttributes.parameterOffset((invalid << 16) | 255));
        }
        assertEquals(-1, MeshIntegerAttributes.parameterOffset(-1));
    }

    @Test
    void missingCoreDivisorCanUseArbButMissingIntegerPointerAlwaysRejectsBackend() {
        assertEquals(MeshIntegerAttributes.Backend.CORE,
                MeshIntegerAttributes.selectBackend(true, 1, true, 2, true, 3));
        assertEquals(MeshIntegerAttributes.Backend.ARB,
                MeshIntegerAttributes.selectBackend(true, 1, true, 0, true, 3));
        assertEquals(MeshIntegerAttributes.Backend.ARB,
                MeshIntegerAttributes.selectBackend(true, 1, false, 2, true, 3));
        assertEquals(MeshIntegerAttributes.Backend.UNSUPPORTED,
                MeshIntegerAttributes.selectBackend(true, 0, true, 2, true, 3));
        assertEquals(MeshIntegerAttributes.Backend.UNSUPPORTED,
                MeshIntegerAttributes.selectBackend(false, 1, true, 2, true, 3));
        assertEquals(MeshIntegerAttributes.Backend.UNSUPPORTED,
                MeshIntegerAttributes.selectBackend(true, 1, false, 2, true, 0));
        assertEquals(MeshIntegerAttributes.Backend.UNSUPPORTED,
                MeshIntegerAttributes.selectBackend(true, 1, false, 2, false, 3));
    }
}
