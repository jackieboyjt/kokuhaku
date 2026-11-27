package com.kokuhaku.mixin;

import com.kokuhaku.client.Cutscene;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During a cutscene the camera goes wherever the scene says. It stays in
 * first-person mode, so your body is never drawn.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    public abstract net.minecraft.util.math.Vec3d getPos();

    /** Your own body: a bad leg, an empty stomach, the cold, a fright. Nothing during a scene, which has its own camera. */
    @Inject(method = "update", at = @At("TAIL"))
    private void kokuhaku$bodyFeel(BlockView area, Entity focused, boolean third, boolean inverse, float tickDelta, CallbackInfo ci) {
        if (third || focused == null || com.kokuhaku.client.Cutscene.active()) return;
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (focused != client.player) return;
        // Only the height here. The turn and tilt go on the view itself (GameRendererMixin), like the game's own bobbing,
        // so the camera that culling, sound and aiming go by stays where you're really looking.
        float dip = com.kokuhaku.client.BodyFeel.dip(client, tickDelta);
        if (dip != 0f) {
            net.minecraft.util.math.Vec3d at = getPos();
            setPos(at.x, at.y - dip, at.z);
        }
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void kokuhaku$cutsceneCamera(BlockView area, Entity focused, boolean third, boolean inverse, float tickDelta, CallbackInfo ci) {
        if (!Cutscene.active() || focused == null) return;
        double[] c = Cutscene.camera(focused, tickDelta);
        setRotation((float) c[3], (float) c[4]);
        setPos(c[0], c[1], c[2]);
    }
}
