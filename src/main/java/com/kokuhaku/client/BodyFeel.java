package com.kokuhaku.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;

/**
 * Your own body, through your own eyes. A bad leg you favour on every other step,
 * the slow swim of the view when you haven't eaten or drunk, shivering out in the
 * snow, and the jolt when something makes you jump.
 *
 * Only ever the camera, only in first person, never in a scene (scenes have their
 * own camera), and all of it scaled by the game's own Distortion Effects setting,
 * so anyone who gets sick from it can turn it down or off.
 *
 * Every number worth tuning is up here.
 */
public final class BodyFeel {
    /** The leg: how far the view drops onto it, and how far it tips that way, in blocks and degrees. */
    private static final float LIMP_DIP = 0.07f, LIMP_ROLL = 1.8f;
    /** Below this much food or water you start to feel it (the HUD turns at the same point); at EMPTY it's at its worst. */
    private static final int LOW = 250, EMPTY = 60;
    /** Hungry or thirsty: how far the view wanders, in degrees, at its worst. */
    private static final float WEAK_YAW = 0.9f, WEAK_PITCH = 0.6f, WEAK_ROLL = 1.4f;
    /** Cold: the shiver, in degrees, and how quickly it comes and goes. */
    private static final float SHIVER = 0.22f, SHIVER_WAVES = 0.45f;
    /** Startled: the kick back, in degrees, the shake after it, and how quickly it's gone (per second). */
    private static final float JOLT_PITCH = -3.2f, JOLT_ROLL = 2.2f, JOLT_SHAKE = 1.1f, JOLT_FADE = 5f;
    /** How quickly each feeling comes on and wears off, per tick, so nothing starts or stops with a snap. */
    private static final float SETTLE = 0.04f;

    private static float limp = 0f, weak = 0f, cold = 0f;
    private static long joltAt = 0L;
    private static float joltSide = 1f, joltStrength = 0f;
    private static int lastChase = -1;

    private BodyFeel() {
    }

    /** Something made you jump. strength 0..1. */
    public static void startle(float strength) {
        long now = System.currentTimeMillis();
        // A second fright on top of the first doesn't kick twice as hard: it just starts again.
        joltStrength = Math.max(strength, joltStrength * joltLeft(now));
        joltAt = now;
        joltSide = Math.random() < 0.5 ? -1f : 1f;
    }

    private static float joltLeft(long now) {
        return (float) Math.exp(-(now - joltAt) / 1000f * JOLT_FADE);
    }

    public static void reset() {
        limp = weak = cold = 0f;
        joltStrength = 0f;
        lastChase = -1;
    }

    /** Once a tick: what you're feeling, eased towards what you should be. */
    public static void tick(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        boolean on = p != null && client.world != null && ClientRun.active && !ClientRun.ghost && !MenuScene.isActive();
        // Turning round to find her right behind you.
        if (on && ClientRun.chase >= 0 && lastChase < 0) startle(1f);
        lastChase = on ? ClientRun.chase : -1;
        float wantLimp = 0f, wantWeak = 0f, wantCold = 0f;
        if (on) {
            StatusEffectInstance slow = p.getStatusEffect(StatusEffects.SLOWNESS);
            int need = Math.min(ClientRun.food, ClientRun.water);
            // Going hungry slows you too, a few seconds at a time; that isn't a bad leg. A long one, or one with food in you, is.
            if (slow != null && (slow.getDuration() > 200 || need >= LOW)) wantLimp = 1f;
            wantWeak = MathHelper.clamp((LOW - need) / (float) (LOW - EMPTY), 0f, 1f);
            // Out under the sky on a snow day. Running keeps you warm, standing about doesn't.
            if (p.getY() < 90 && com.kokuhaku.game.Schedule.isSnowDay(ClientRun.day) && client.world.isSkyVisible(p.getBlockPos().up())) {
                wantCold = p.isSprinting() ? 0.4f : 1f;
            }
        }
        limp = approach(limp, wantLimp);
        weak = approach(weak, wantWeak);
        cold = approach(cold, wantCold);
    }

    private static float approach(float now, float want) {
        return now < want ? Math.min(want, now + SETTLE) : Math.max(want, now - SETTLE);
    }

    private static float scale(MinecraftClient client) {
        if (Cutscene.active() || !client.options.getPerspective().isFirstPerson()) return 0f;
        return client.options.getDistortionEffectScale().getValue().floatValue();
    }

    /** Degrees to add to the view this frame: yaw, pitch. */
    public static float[] turn(MinecraftClient client, float tickDelta) {
        float k = scale(client);
        if (k <= 0f || client.player == null) return new float[]{0f, 0f};
        float t = (client.player.age + tickDelta) / 20f;
        float yaw = 0f, pitch = 0f;
        if (weak > 0f) {
            yaw += (MathHelper.sin(t * 0.61f) + 0.5f * MathHelper.sin(t * 1.37f)) * WEAK_YAW * weak;
            pitch += (MathHelper.sin(t * 0.47f + 1f) + 0.4f * MathHelper.sin(t * 1.13f)) * WEAK_PITCH * weak;
        }
        if (cold > 0f) {
            // In waves: a bout of it, then a few seconds of just being cold.
            float bout = MathHelper.clamp((MathHelper.sin(t * SHIVER_WAVES) - 0.2f) * 2f, 0f, 1f);
            pitch += (MathHelper.sin(t * 38f) + 0.6f * MathHelper.sin(t * 53f)) * SHIVER * bout * cold;
            yaw += MathHelper.sin(t * 45f) * SHIVER * 0.5f * bout * cold;
        }
        long now = System.currentTimeMillis();
        float jolt = joltStrength * joltLeft(now);
        if (jolt > 0.01f) {
            float since = (now - joltAt) / 1000f;
            pitch += JOLT_PITCH * jolt + MathHelper.sin(since * 42f) * JOLT_SHAKE * jolt;
            yaw += MathHelper.sin(since * 37f + 1f) * JOLT_SHAKE * 0.7f * jolt;
        }
        return new float[]{yaw * k, pitch * k};
    }

    /** Degrees of sideways tilt this frame. */
    public static float roll(MinecraftClient client, float tickDelta) {
        float k = scale(client);
        if (k <= 0f || client.player == null) return 0f;
        ClientPlayerEntity p = client.player;
        float t = (p.age + tickDelta) / 20f, roll = 0f;
        if (limp > 0f) roll += LIMP_ROLL * onBadLeg(p, tickDelta) * limp;
        if (weak > 0f) roll += MathHelper.sin(t * 0.37f + 2f) * WEAK_ROLL * weak;
        float jolt = joltStrength * joltLeft(System.currentTimeMillis());
        if (jolt > 0.01f) roll += JOLT_ROLL * joltSide * jolt;
        return roll * k;
    }

    /** Blocks to lower the view by this frame. */
    public static float dip(MinecraftClient client, float tickDelta) {
        float k = scale(client);
        if (k <= 0f || client.player == null || limp <= 0f) return 0f;
        return LIMP_DIP * onBadLeg(client.player, tickDelta) * limp * k;
    }

    /**
     * 0..1: how much weight is on the bad leg right now. The view bobs once a step, in time
     * with the game's own view bobbing (one foot, then the other, each half of sin(walked * PI)),
     * so the bad leg is the one half of it, and only while you're actually walking.
     */
    private static float onBadLeg(ClientPlayerEntity p, float tickDelta) {
        float walked = MathHelper.lerp(tickDelta, p.prevHorizontalSpeed, p.horizontalSpeed);
        float stride = MathHelper.clamp(MathHelper.lerp(tickDelta, p.prevStrideDistance, p.strideDistance) * 12f, 0f, 1f);
        return Math.max(0f, MathHelper.sin(walked * MathHelper.PI)) * stride;
    }
}
