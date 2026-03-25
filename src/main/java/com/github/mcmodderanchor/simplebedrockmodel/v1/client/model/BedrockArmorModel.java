package com.github.mcmodderanchor.simplebedrockmodel.v1.client.model;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockBone;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;

import org.jetbrains.annotations.Nullable;

/**
 * 专门用于盔甲的 BedrockModel 子类，缓存盔甲各部分的骨骼引用以提高性能
 */
public class BedrockArmorModel extends BedrockModel {
    // 缓存的盔甲部位骨骼
    @Nullable
    private final BedrockBone armorHead;
    @Nullable
    private final BedrockBone armorBody;
    @Nullable
    private final BedrockBone armorRightArm;
    @Nullable
    private final BedrockBone armorLeftArm;
    @Nullable
    private final BedrockBone armorRightLeg;
    @Nullable
    private final BedrockBone armorLeftLeg;
    @Nullable
    private final BedrockBone armorRightBoot;
    @Nullable
    private final BedrockBone armorLeftBoot;

    public BedrockArmorModel(BedrockModelPOJO pojo) {
        super(pojo);

        // 初始化时缓存所有盔甲部位的骨骼引用
        this.armorHead = boneMap.get("armorHead");
        this.armorBody = boneMap.get("armorBody");
        this.armorRightArm = boneMap.get("armorRightArm");
        this.armorLeftArm = boneMap.get("armorLeftArm");
        this.armorRightLeg = boneMap.get("armorRightLeg");
        this.armorLeftLeg = boneMap.get("armorLeftLeg");
        this.armorRightBoot = boneMap.get("armorRightBoot");
        this.armorLeftBoot = boneMap.get("armorLeftBoot");
    }

    @Nullable
    public BedrockBone getArmorHead() {
        return armorHead;
    }

    @Nullable
    public BedrockBone getArmorBody() {
        return armorBody;
    }

    @Nullable
    public BedrockBone getArmorRightArm() {
        return armorRightArm;
    }

    @Nullable
    public BedrockBone getArmorLeftArm() {
        return armorLeftArm;
    }

    @Nullable
    public BedrockBone getArmorRightLeg() {
        return armorRightLeg;
    }

    @Nullable
    public BedrockBone getArmorLeftLeg() {
        return armorLeftLeg;
    }

    @Nullable
    public BedrockBone getArmorRightBoot() {
        return armorRightBoot;
    }

    @Nullable
    public BedrockBone getArmorLeftBoot() {
        return armorLeftBoot;
    }

    /**
     * 根据骨骼名称获取缓存的盔甲部位骨骼
     * @param boneName 骨骼名称
     * @return 对应的骨骼，如果不存在则返回 null
     */
    @Nullable
    public BedrockBone getArmorBone(String boneName) {
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
