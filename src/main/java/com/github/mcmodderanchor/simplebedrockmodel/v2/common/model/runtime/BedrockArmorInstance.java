package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime;

import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBedrockModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * v2 盔甲用的运行时实例封装。
 * 直接继承 BedrockModelInstance，便于在外部继续按常规实例使用。
 */
public class BedrockArmorInstance extends BakedModelInstance {
    @Nullable
    private final BoneState armorHead;
    @Nullable
    private final BoneState armorBody;
    @Nullable
    private final BoneState armorRightArm;
    @Nullable
    private final BoneState armorLeftArm;
    @Nullable
    private final BoneState armorRightLeg;
    @Nullable
    private final BoneState armorLeftLeg;
    @Nullable
    private final BoneState armorRightBoot;
    @Nullable
    private final BoneState armorLeftBoot;

    public BedrockArmorInstance(BakedBedrockModel baseModel) {
        super(baseModel);
        this.armorHead = getBone("armorHead");
        this.armorBody = getBone("armorBody");
        this.armorRightArm = getBone("armorRightArm");
        this.armorLeftArm = getBone("armorLeftArm");
        this.armorRightLeg = getBone("armorRightLeg");
        this.armorLeftLeg = getBone("armorLeftLeg");
        this.armorRightBoot = getBone("armorRightBoot");
        this.armorLeftBoot = getBone("armorLeftBoot");
    }

    public void preparePose(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
        this.resetPose();
        copyModelPart(original.head, this.armorHead, 0, 24, 0);
        copyModelPart(original.body, this.armorBody, 0, 24, 0);
        copyModelPart(original.rightArm, this.armorRightArm, 5, 22, 0);
        copyModelPart(original.leftArm, this.armorLeftArm, -5, 22, 0);
        copyModelPart(original.rightLeg, this.armorRightLeg, 1.9f, 12, 0);
        copyModelPart(original.leftLeg, this.armorLeftLeg, -1.9f, 12, 0);
        copyModelPart(original.rightLeg, this.armorRightBoot, 1.9f, 12, 0);
        copyModelPart(original.leftLeg, this.armorLeftBoot, -1.9f, 12, 0);
        setVisibilityBySlot(equipmentSlot);
    }

    public void setVisibilityBySlot(EquipmentSlot slot) {
        setBoneVisible(this.armorHead, slot == EquipmentSlot.HEAD);
        setBoneVisible(this.armorBody, slot == EquipmentSlot.CHEST);
        setBoneVisible(this.armorRightArm, slot == EquipmentSlot.CHEST);
        setBoneVisible(this.armorLeftArm, slot == EquipmentSlot.CHEST);
        setBoneVisible(this.armorRightLeg, slot == EquipmentSlot.LEGS);
        setBoneVisible(this.armorLeftLeg, slot == EquipmentSlot.LEGS);
        setBoneVisible(this.armorRightBoot, slot == EquipmentSlot.FEET);
        setBoneVisible(this.armorLeftBoot, slot == EquipmentSlot.FEET);
    }

    private static void setBoneVisible(@Nullable BoneState bone, boolean visible) {
        if (bone != null) {
            bone.visible = visible;
        }
    }

    public void copyModelPart(ModelPart part, @Nullable BoneState bone, float initX, float initY, float initZ) {
        if (bone != null) {
            float deltaX = part.x - initX;
            float deltaY = part.y - initY;
            float deltaZ = part.z - initZ;

            bone.x += deltaX;
            bone.y += deltaY;
            bone.z += deltaZ;

            bone.rotation.rotationZYX(part.zRot, part.yRot, part.xRot);

            bone.xScale = -part.xScale;
            bone.yScale = -part.yScale;
            bone.zScale = part.zScale;
            bone.visible = part.visible;
        }
    }

    @Nullable
    public BoneState getArmorHead() {
        return armorHead;
    }

    @Nullable
    public BoneState getArmorBody() {
        return armorBody;
    }

    @Nullable
    public BoneState getArmorRightArm() {
        return armorRightArm;
    }

    @Nullable
    public BoneState getArmorLeftArm() {
        return armorLeftArm;
    }

    @Nullable
    public BoneState getArmorRightLeg() {
        return armorRightLeg;
    }

    @Nullable
    public BoneState getArmorLeftLeg() {
        return armorLeftLeg;
    }

    @Nullable
    public BoneState getArmorRightBoot() {
        return armorRightBoot;
    }

    @Nullable
    public BoneState getArmorLeftBoot() {
        return armorLeftBoot;
    }

    @Nullable
    public BoneState getArmorBone(String boneName) {
        return switch (boneName) {
            case "armorHead" -> armorHead;
            case "armorBody" -> armorBody;
            case "armorRightArm" -> armorRightArm;
            case "armorLeftArm" -> armorLeftArm;
            case "armorRightLeg" -> armorRightLeg;
            case "armorLeftLeg" -> armorLeftLeg;
            case "armorRightBoot" -> armorRightBoot;
            case "armorLeftBoot" -> armorLeftBoot;
            default -> null;
        };
    }
}
