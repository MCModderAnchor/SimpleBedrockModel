package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime;

import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.LocalCubeBounds;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Shared segment-vs-cube tracing math for model instances. Coordinates are converted from world space to model space once.
 */
public final class ModelRayTracer {

    private final double worldStartX;
    private final double worldStartY;
    private final double worldStartZ;
    private final double worldDeltaX;
    private final double worldDeltaY;
    private final double worldDeltaZ;
    private final double modelStartX;
    private final double modelStartY;
    private final double modelStartZ;
    private final double modelDeltaX;
    private final double modelDeltaY;
    private final double modelDeltaZ;
    private final Matrix3f modelNormalTransform;
    private final boolean valid;

    @Nullable
    private ModelRayTraceResult nearest;

    ModelRayTracer(Matrix4fc modelRotation, Vec3 modelOrigin, Vec3 rayStart, Vec3 rayEnd) {
        Objects.requireNonNull(modelRotation, "modelRotation");
        Objects.requireNonNull(modelOrigin, "modelOrigin");
        Objects.requireNonNull(rayStart, "rayStart");
        Objects.requireNonNull(rayEnd, "rayEnd");

        worldStartX = rayStart.x;
        worldStartY = rayStart.y;
        worldStartZ = rayStart.z;
        worldDeltaX = rayEnd.x - rayStart.x;
        worldDeltaY = rayEnd.y - rayStart.y;
        worldDeltaZ = rayEnd.z - rayStart.z;

        Matrix4f inverseModel = invert(modelRotation);
        modelNormalTransform = new Matrix3f(modelRotation);
        if (inverseModel == null) {
            modelStartX = 0;
            modelStartY = 0;
            modelStartZ = 0;
            modelDeltaX = 0;
            modelDeltaY = 0;
            modelDeltaZ = 0;
            valid = false;
            return;
        }

        double relativeStartX = rayStart.x - modelOrigin.x;
        double relativeStartY = rayStart.y - modelOrigin.y;
        double relativeStartZ = rayStart.z - modelOrigin.z;
        modelStartX = transformX(inverseModel, relativeStartX, relativeStartY, relativeStartZ, true);
        modelStartY = transformY(inverseModel, relativeStartX, relativeStartY, relativeStartZ, true);
        modelStartZ = transformZ(inverseModel, relativeStartX, relativeStartY, relativeStartZ, true);
        modelDeltaX = transformX(inverseModel, worldDeltaX, worldDeltaY, worldDeltaZ, false);
        modelDeltaY = transformY(inverseModel, worldDeltaX, worldDeltaY, worldDeltaZ, false);
        modelDeltaZ = transformZ(inverseModel, worldDeltaX, worldDeltaY, worldDeltaZ, false);
        valid = Double.isFinite(modelStartX) && Double.isFinite(modelStartY) && Double.isFinite(modelStartZ)
                && Double.isFinite(modelDeltaX) && Double.isFinite(modelDeltaY) && Double.isFinite(modelDeltaZ);
    }

    boolean isValid() {
        return valid;
    }

    public boolean traceGroup(@Nullable LocalCubeBounds bounds, Matrix4fc groupToModel) {
        if (!valid || bounds == null) {
            return false;
        }
        return intersect(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ(), groupToModel) != null;
    }

    public void traceCube(int attachmentBoneIndex, int cubeIndex,
                          float x, float y, float z, float width, float height, float depth,
                          Matrix4fc groupToModel, Matrix4fc cubeToGroup) {
        if (!valid) {
            return;
        }
        Matrix4f cubeToModel = new Matrix4f(groupToModel).mul(cubeToGroup);
        AabbIntersection intersection = intersect(x, y, z, x + width, y + height, z + depth, cubeToModel);
        if (intersection == null || (nearest != null && intersection.t() >= nearest.t())) {
            return;
        }

        double t = intersection.t();
        Vec3 location = new Vec3(
                worldStartX + worldDeltaX * t,
                worldStartY + worldDeltaY * t,
                worldStartZ + worldDeltaZ * t
        );
        Matrix4f inverseGroup = invert(groupToModel);
        if (inverseGroup == null) {
            return;
        }
        Vec3 modelLocation = new Vec3(
                modelStartX + modelDeltaX * t,
                modelStartY + modelDeltaY * t,
                modelStartZ + modelDeltaZ * t
        );
        Vec3 attachmentOffset = transformPosition(inverseGroup, modelLocation);

        @Nullable Vec3 normal = null;
        @Nullable Vec3 attachmentNormal = null;
        if (intersection.normalAxis() >= 0) {
            Vector3f localNormal = intersection.localNormal(new Vector3f());
            Matrix4f inverseCube = invert(cubeToModel);
            Matrix4f inverseCubeToGroup = invert(cubeToGroup);
            if (inverseCube != null && inverseCubeToGroup != null) {
                Vector3f modelNormal = new Vector3f(localNormal).mul(new Matrix3f(inverseCube).transpose());
                if (modelNormal.lengthSquared() > 1.0E-12f) {
                    modelNormal.normalize();
                    Vector3f worldNormal = new Vector3f(modelNormal).mul(modelNormalTransform);
                    if (worldNormal.lengthSquared() > 1.0E-12f) {
                        worldNormal.normalize();
                        normal = new Vec3(worldNormal.x, worldNormal.y, worldNormal.z);
                    }
                }
                Vector3f localAttachmentNormal = new Vector3f(localNormal).mul(new Matrix3f(inverseCubeToGroup).transpose());
                if (localAttachmentNormal.lengthSquared() > 1.0E-12f) {
                    localAttachmentNormal.normalize();
                    attachmentNormal = new Vec3(localAttachmentNormal.x, localAttachmentNormal.y, localAttachmentNormal.z);
                }
            }
        }

        nearest = new ModelRayTraceResult(
                location, t, attachmentBoneIndex, cubeIndex, normal, attachmentOffset, attachmentNormal, new Matrix4f(groupToModel), cubeToModel
        );
    }

    @Nullable
    ModelRayTraceResult result() {
        return nearest;
    }

    @Nullable
    private AabbIntersection intersect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Matrix4fc localToModel) {
        Matrix4f inverse = invert(localToModel);
        if (inverse == null) {
            return null;
        }
        double startX = transformX(inverse, modelStartX, modelStartY, modelStartZ, true);
        double startY = transformY(inverse, modelStartX, modelStartY, modelStartZ, true);
        double startZ = transformZ(inverse, modelStartX, modelStartY, modelStartZ, true);
        double deltaX = transformX(inverse, modelDeltaX, modelDeltaY, modelDeltaZ, false);
        double deltaY = transformY(inverse, modelDeltaX, modelDeltaY, modelDeltaZ, false);
        double deltaZ = transformZ(inverse, modelDeltaX, modelDeltaY, modelDeltaZ, false);
        if (!Double.isFinite(startX) || !Double.isFinite(startY) || !Double.isFinite(startZ)
                || !Double.isFinite(deltaX) || !Double.isFinite(deltaY) || !Double.isFinite(deltaZ)) {
            return null;
        }
        return intersectAabb(startX, startY, startZ, deltaX, deltaY, deltaZ, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Nullable
    private static AabbIntersection intersectAabb(double startX, double startY, double startZ, double deltaX, double deltaY, double deltaZ,
                                                  double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double tEnter = 0.0;
        double tExit = 1.0;
        int normalAxis = -1;
        float normalSign = 0.0f;

        if (deltaX == 0.0) {
            if (startX < minX || startX > maxX) return null;
        } else {
            double first = (minX - startX) / deltaX;
            double second = (maxX - startX) / deltaX;
            double axisEnter = Math.min(first, second);
            double axisExit = Math.max(first, second);
            if (axisEnter > tEnter || (axisEnter == tEnter && normalAxis < 0)) {
                normalAxis = 0;
                normalSign = first <= second ? -1.0f : 1.0f;
                tEnter = axisEnter;
            }
            tExit = Math.min(tExit, axisExit);
            if (tEnter > tExit) return null;
        }
        if (deltaY == 0.0) {
            if (startY < minY || startY > maxY) return null;
        } else {
            double first = (minY - startY) / deltaY;
            double second = (maxY - startY) / deltaY;
            double axisEnter = Math.min(first, second);
            double axisExit = Math.max(first, second);
            if (axisEnter > tEnter || (axisEnter == tEnter && normalAxis < 0)) {
                normalAxis = 1;
                normalSign = first <= second ? -1.0f : 1.0f;
                tEnter = axisEnter;
            }
            tExit = Math.min(tExit, axisExit);
            if (tEnter > tExit) return null;
        }
        if (deltaZ == 0.0) {
            if (startZ < minZ || startZ > maxZ) return null;
        } else {
            double first = (minZ - startZ) / deltaZ;
            double second = (maxZ - startZ) / deltaZ;
            double axisEnter = Math.min(first, second);
            double axisExit = Math.max(first, second);
            if (axisEnter > tEnter || (axisEnter == tEnter && normalAxis < 0)) {
                normalAxis = 2;
                normalSign = first <= second ? -1.0f : 1.0f;
                tEnter = axisEnter;
            }
            tExit = Math.min(tExit, axisExit);
            if (tEnter > tExit) return null;
        }
        return new AabbIntersection(tEnter, normalAxis, normalSign);
    }

    private static Vec3 transformPosition(Matrix4fc matrix, Vec3 position) {
        return new Vec3(
                transformX(matrix, position.x, position.y, position.z, true),
                transformY(matrix, position.x, position.y, position.z, true),
                transformZ(matrix, position.x, position.y, position.z, true)
        );
    }

    private record AabbIntersection(double t, int normalAxis, float normalSign) {
        Vector3f localNormal(Vector3f destination) {
            return switch (normalAxis) {
                case 0 -> destination.set(normalSign, 0.0f, 0.0f);
                case 1 -> destination.set(0.0f, normalSign, 0.0f);
                case 2 -> destination.set(0.0f, 0.0f, normalSign);
                default -> throw new IllegalStateException("intersection has no entry normal");
            };
        }
    }

    @Nullable
    private static Matrix4f invert(Matrix4fc matrix) {
        float determinant = matrix.determinant();
        if (!Float.isFinite(determinant) || determinant == 0.0f) {
            return null;
        }
        Matrix4f inverse = new Matrix4f(matrix).invert();
        return isFinite(inverse) ? inverse : null;
    }

    private static boolean isFinite(Matrix4fc matrix) {
        return Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01()) && Float.isFinite(matrix.m02()) && Float.isFinite(matrix.m03())
                && Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11()) && Float.isFinite(matrix.m12()) && Float.isFinite(matrix.m13())
                && Float.isFinite(matrix.m20()) && Float.isFinite(matrix.m21()) && Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m23())
                && Float.isFinite(matrix.m30()) && Float.isFinite(matrix.m31()) && Float.isFinite(matrix.m32()) && Float.isFinite(matrix.m33());
    }

    private static double transformX(Matrix4fc matrix, double x, double y, double z, boolean position) {
        return matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + (position ? matrix.m30() : 0.0);
    }

    private static double transformY(Matrix4fc matrix, double x, double y, double z, boolean position) {
        return matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + (position ? matrix.m31() : 0.0);
    }

    private static double transformZ(Matrix4fc matrix, double x, double y, double z, boolean position) {
        return matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + (position ? matrix.m32() : 0.0);
    }
}
