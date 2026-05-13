package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render;


import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(value = Dist.CLIENT)
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
