package com.kokuhaku.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.Random;

/**
 * Plays one of her voice messages. There are no words in any of them: footsteps a little
 * way behind someone, breathing close to the microphone, or static with a heartbeat under
 * it. Which one, and how it goes, comes from the message, so it sounds the same each time.
 */
public final class VoicePlayer {
    private static long seed;
    private static int playing = -1, ticks, length;

    private VoicePlayer() {
    }

    /** The message's place in the phone, so its bubble can show it playing. */
    public static int playing() {
        return playing;
    }

    /** How far through, 0..1. */
    public static float progress() {
        return length <= 0 ? 0f : Math.min(1f, ticks / (length * 20f));
    }

    public static void toggle(int index, long messageSeed, int seconds) {
        if (playing == index) {
            playing = -1;
            return;
        }
        playing = index;
        seed = messageSeed;
        length = seconds;
        ticks = 0;
    }

    /** Cuts it off, as when you leave. */
    public static void stop() {
        playing = -1;
        ticks = 0;
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
    }

    public static void tick(MinecraftClient client) {
        if (playing < 0) return;
        if (client.world == null || ticks >= length * 20) {
            playing = -1;
            return;
        }
        // It waits while the game is paused.
        if (client.isPaused()) return;
        Random r = new Random(seed + ticks);
        switch ((int) Math.floorMod(seed, 3L)) {
            // Footsteps, yours, from a few steps back. Then they stop.
            case 0 -> {
                if (ticks % 9 == 0 && ticks < length * 20 - 30) play(SoundEvents.BLOCK_STONE_STEP, 0.75f + r.nextFloat() * 0.1f, 0.35f);
            }
            // Breathing, slow, right against the phone.
            case 1 -> {
                if (ticks % 34 == 0) play(SoundEvents.ENTITY_FOX_SLEEP, 0.45f + r.nextFloat() * 0.05f, 0.9f);
            }
            // Nothing but hiss, and under it, a heart going much too fast.
            default -> {
                if (ticks % 40 == 0) play(SoundEvents.BLOCK_FIRE_AMBIENT, 0.5f, 0.6f);
                if (ticks % 12 == 0) play(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.3f, 0.5f);
            }
        }
        // At the very end, a breath in, as if to speak. Nothing comes.
        if (ticks == length * 20 - 12) play(SoundEvents.ENTITY_FOX_SNIFF, 0.7f, 0.5f);
        ticks++;
    }
}
