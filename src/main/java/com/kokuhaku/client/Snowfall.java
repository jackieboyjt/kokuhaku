package com.kokuhaku.client;

import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Schedule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * A snow day: it comes down all day, around you wherever you walk. The town's biome only
 * knows how to rain, so the flakes are drawn here, and only where there's sky overhead.
 * Everyone out in it breathes white. Once of an evening, so does somewhere behind you.
 */
public final class Snowfall {
    private static final int FLAKES_A_TICK = 14, REACH = 14, ABOVE = 9;
    private static final int BREATH_REACH = 24, BREATH_EVERY = 55;
    private static final int PUFFS = 3, PUFF_GAP = 20, LONE_CHANCE = 2000;
    private static final Random RANDOM = Random.create();
    private static int lastPuffDay = -1, puffsLeft = 0, puffIn = 0;
    private static double puffX, puffY, puffZ;

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
        breath(client);
        loneBreath(client);
    }

    /** Everyone outdoors breathes white, now and then. Never you: it would fog your eyes. */
    private static void breath(MinecraftClient client) {
        ClientPlayerEntity you = client.player;
        for (Entity e : client.world.getEntities()) {
            if (e == you || !e.isAlive() || e.isInvisible() || e.isSpectator() || e.hasVehicle() || e.getY() > 90) continue;
            if (e instanceof StudentEntity s) {
                if (s.getPeek() != 0) continue;
            } else if (!(e instanceof AbstractClientPlayerEntity)) {
                continue;
            }
            if ((e.age + e.getId() * 37) % BREATH_EVERY != 0) continue;
            if (e.squaredDistanceTo(you) > BREATH_REACH * BREATH_REACH) continue;
            if (!client.world.isSkyVisible(e.getBlockPos().up())) continue;
            Vec3d look = e.getRotationVec(1f);
            Vec3d at = e.getEyePos().add(look.multiply(0.35));
            client.world.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, look.x * 0.02, 0.01, look.z * 0.02);
        }
    }

    /** Once a snowy evening, out on your own: a breath in the cold air behind you, where nobody is. */
    private static void loneBreath(MinecraftClient client) {
        ClientPlayerEntity you = client.player;
        // A new run, or a puff left over from another night: forget it.
        if (ClientRun.day < lastPuffDay) lastPuffDay = -1;
        if (lastPuffDay != ClientRun.day) puffsLeft = 0;
        if (puffsLeft > 0) {
            if (--puffIn <= 0) {
                client.world.addParticle(ParticleTypes.CLOUD, puffX, puffY, puffZ, 0, 0.01, 0);
                if (puffsLeft == PUFFS) client.world.playSound(puffX, puffY, puffZ, SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.HOSTILE, 0.08f, 0.8f, false);
                puffsLeft--;
                puffIn = PUFF_GAP;
            }
            return;
        }
        if (lastPuffDay == ClientRun.day || ClientRun.minute < Schedule.EVENING) return;
        if (ClientRun.ghost || ClientRun.chase >= 0 || Cutscene.active() || client.currentScreen != null) return;
        if (!client.world.isSkyVisible(you.getBlockPos().up()) || RANDOM.nextInt(LONE_CHANCE) != 0) return;
        double back = 6 + RANDOM.nextInt(5);
        float yaw = you.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double x = you.getX() + MathHelper.sin(yaw) * back, y = you.getEyeY(), z = you.getZ() - MathHelper.cos(yaw) * back;
        BlockPos at = BlockPos.ofFloored(x, y, z);
        // Out in the open air, but up against something someone could stand behind.
        if (!client.world.getBlockState(at).getCollisionShape(client.world, at).isEmpty()) return;
        boolean against = false;
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos side = at.offset(d);
            if (!client.world.getBlockState(side).getCollisionShape(client.world, side).isEmpty()) against = true;
        }
        if (!against) return;
        Vec3d point = new Vec3d(x, y, z);
        if (!client.world.getOtherEntities(null, new Box(point, point).expand(3), e -> e.squaredDistanceTo(point) < 9).isEmpty()) return;
        lastPuffDay = ClientRun.day;
        puffX = x;
        puffY = y;
        puffZ = z;
        puffsLeft = PUFFS;
        puffIn = 0;
    }
}
