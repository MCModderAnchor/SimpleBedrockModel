package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** 局部网格包围盒转成世界包围盒，供 INSTANCE 视锥剔除。 */
public class WorldMeshTransforms {
    public WorldMeshTransforms() {}

    public static AABB bounds(AABB local, Matrix4f transform, Vec3 origin) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        Vector3f point = new Vector3f();
        for (int corner = 0; corner < 8; corner++) {
            point.set((float) ((corner & 1) == 0 ? local.minX : local.maxX),
                    (float) ((corner & 2) == 0 ? local.minY : local.maxY),
                    (float) ((corner & 4) == 0 ? local.minZ : local.maxZ)).mulPosition(transform);
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
        }
        return new AABB(origin.x + minX, origin.y + minY, origin.z + minZ,
                origin.x + maxX, origin.y + maxY, origin.z + maxZ);
    }
}
