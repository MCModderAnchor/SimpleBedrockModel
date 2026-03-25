package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public final class CameraStateCache {

    private static float cameraRoll;

    private CameraStateCache() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        cameraRoll = event.getRoll();
    }

    /**
     * 获取当前帧的 camera roll（度）。
     */
    public static float getCameraRollDegrees() {
        return cameraRoll;
    }

    /**
     * 获取当前帧的 camera roll（弧度）。
     */
    public static float getCameraRollRadians() {
        return (float) Math.toRadians(cameraRoll);
    }
}
