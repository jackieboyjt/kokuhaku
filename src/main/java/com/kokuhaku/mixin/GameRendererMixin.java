package com.kokuhaku.mixin;

import com.kokuhaku.client.Cutscene;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The camera has no roll of its own, so a cutscene's sideways tilt, and your own body's, go in here. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
    private void kokuhaku$cutsceneRoll(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        float roll = Cutscene.roll() + com.kokuhaku.client.BodyFeel.roll(client, tickDelta);
        if (roll != 0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        // Your own body turning the view a little: on the view, before the camera's own turn, so it's always relative to where you look.
        float[] turn = com.kokuhaku.client.BodyFeel.turn(client, tickDelta);
        if (turn[1] != 0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(turn[1]));
        if (turn[0] != 0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(turn[0]));
    }
}