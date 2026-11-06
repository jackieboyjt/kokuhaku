package com.kokuhaku.client;

import com.kokuhaku.game.Schedule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * A snow day: it comes down all day, around you wherever you walk. The town's biome only
 * knows how to rain, so the flakes are drawn here, and only where there's sky overhead.
 */
public final class Snowfall {
    private static final int FLAKES_A_TICK = 14, REACH = 14, ABOVE = 9;
    private static final Random RANDOM = Random.create();

    private Snowfall() {
    }

    public static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null || client.isPaused() || !ClientRun.active || MenuScene.isActive()) return;
        if (client.player.getY() > 90 || !Schedule.isSnowDay(ClientRun.day)) return;
        for (int i = 0; i < FLAKES_A_TICK; i++) {
            double x = client.player.getX() + (RANDOM.nextDouble() * 2 - 1) * REACH;
            double z = client.player.getZ() + (RANDOM.nextDouble() * 2 - 1) * REACH;
            double y = client.player.getY() + 2 + RANDOM.nextDouble() * ABOVE;
            BlockPos column = BlockPos.ofFloored(x, y, z);
            if (client.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, column.getX(), column.getZ()) > y) continue;
            client.world.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, (RANDOM.nextDouble() - 0.5) * 0.04, -0.06 - RANDOM.nextDouble() * 0.05, (RANDOM.nextDouble() - 0.5) * 0.04);
        }
    }
}
