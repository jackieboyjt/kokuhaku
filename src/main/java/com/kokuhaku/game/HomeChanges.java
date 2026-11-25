package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.world.town.Town;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LightBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * You come home and something isn't how you left it. Never much: a chair turned
 * to face the bed, the radio moved along the desk, a door you shut standing
 * open, a flower. Nothing's ever explained, and you start checking.
 */
final class HomeChanges {
    private static final int FROM_DAY = 3, ONE_IN = 3, REACH = 8, HEIGHT = 2, SAYS_SO_TIMES = 2, TELL_AFTER = 70;
    private static final Random RANDOM = new Random();
    private static final Set<UUID> out = new HashSet<>();
    private static int lastDay = 0, told = 0;

    // The light in your room, on while you're out. One each at a time, two a run in all.
    private record Lit(UUID player, ServerWorld world, BlockPos at, int day) {
    }

    private static final int LIGHT_FROM_DAY = 4, LIGHTS_A_RUN = 2, LIGHT_ONE_IN = 25, FAR_FROM_DOOR = 30, AT_DOOR = 8, NO_GIRL_WITHIN = 48;
    private static final List<Lit> lit = new ArrayList<>();
    private static int litThisRun = 0;
    private static boolean toldOnce = false;
    // Whoever heard it go off: something's been moved when they get in.
    private static final Set<UUID> expect = new HashSet<>();

    private HomeChanges() {
    }

    static void clear() {
        out.clear();
        lastDay = 0;
        told = 0;
        for (Lit l : lit) off(l);
        lit.clear();
        litThisRun = 0;
        toldOnce = false;
        expect.clear();
    }

    private static void off(Lit l) {
        if (l.world().isChunkLoaded(l.at()) && l.world().getBlockState(l.at()).isOf(Blocks.LIGHT)) l.world().setBlockState(l.at(), Blocks.AIR.getDefaultState());
    }

    private static Lit litFor(ServerPlayerEntity p) {
        for (Lit l : lit) if (l.player().equals(p.getUuid())) return l;
        return null;
    }

    /** A girl about, in the open: then it could have been any of them, and it's left alone. */
    private static boolean girlNear(ServerPlayerEntity p) {
        return !p.getServerWorld().getEntitiesByClass(StudentEntity.class, p.getBoundingBox().expand(NO_GIRL_WITHIN),
                e -> e.getPeek() == 0 && !Townsfolk.isOne(e) && e.getSkin() < Roster.GIRLS && e.distanceTo(p) < NO_GIRL_WITHIN).isEmpty();
    }

    /** Your light on while you're out, and off again as you get to the door. */
    private static void light(ServerPlayerEntity p, RunState s, Town.Home room) {
        Lit mine = litFor(p);
        boolean inside = room.contains(p.getPos());
        ServerWorld world = p.getServerWorld();
        if (mine == null) {
            if (inside || s.day < LIGHT_FROM_DAY || litThisRun >= LIGHTS_A_RUN) return;
            int curfew = RunManager.curfewOf(s);
            if (s.minute < Schedule.EVENING + 30 || s.minute > curfew - 30) return;
            BlockPos door = RunManager.frontDoor(world, room);
            if (door == null || p.getPos().distanceTo(Vec3d.ofCenter(door)) < FAR_FROM_DOOR) return;
            if (Stalker.closeness(p) != 0 || Chase.active(p) || Kept.on(s) || Living.active(s) || s.devoted || girlNear(p) || RANDOM.nextInt(LIGHT_ONE_IN) != 0) return;
            List<BlockPos> spots = new ArrayList<>();
            for (BlockPos centre : List.of(room.bedHead().up(2), room.bedHead().up(1))) {
                spots.add(centre);
                for (Direction side : Direction.Type.HORIZONTAL) spots.add(centre.offset(side));
            }
            for (BlockPos at : spots) {
                if (!room.containsBlock(at) || !world.isAir(at)) continue;
                world.setBlockState(at, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, 12));
                lit.add(new Lit(p.getUuid(), world, at.toImmutable(), s.day));
                litThisRun++;
                return;
            }
            return;
        }
        if (inside) {
            // In before it went off: maybe you did leave it on.
            off(mine);
            lit.remove(mine);
            RunManager.tell(p, "", "You don't remember leaving the light on.");
            return;
        }
        BlockPos door = RunManager.frontDoor(world, room);
        if (door == null || mine.world() != world || p.getPos().distanceTo(Vec3d.ofCenter(door)) > AT_DOOR) return;
        if (girlNear(p) || Stalker.closeness(p) != 0 || RunManager.hushed(p)) return;
        off(mine);
        lit.remove(mine);
        RunManager.soundFor(p, SoundEvents.BLOCK_STONE_BUTTON_CLICK_OFF, Vec3d.ofCenter(mine.at()), 1.0f, 0.9f);
        expect.add(p.getUuid());
        if (!toldOnce) {
            toldOnce = true;
            RunManager.later(40, () -> RunManager.tell(p, "", "The light in your room just went off."));
        }
        Shadow.saw(s, "She was sitting on your bed with the light on. She switched it off when she heard you at the door.");
    }

    static void minute(ServerPlayerEntity p, RunState s) {
        // Lights from another day, or this close to curfew, go off.
        int curfew = RunManager.curfewOf(s);
        lit.removeIf(l -> {
            if (l.day() == s.day && s.minute < curfew - 5) return false;
            off(l);
            return true;
        });
        // Your own room, not wherever you're sleeping tonight (a friend's, the ward).
        Town.Home room = Radio.homeOf(p);
        if (room != null) light(p, s, room);
        boolean home = room != null && room.contains(p.getPos());
        if (!home) {
            out.add(p.getUuid());
            return;
        }
        // After the light's gone off, something has always been moved.
        boolean expected = expect.remove(p.getUuid());
        if (!out.remove(p.getUuid()) || s.day < FROM_DAY || !expected && lastDay == s.day || Kept.on(s) || Living.active(s) || !expected && RANDOM.nextInt(ONE_IN) != 0) return;
        lastDay = s.day;
        ServerWorld world = p.getServerWorld();
        List<Runnable> could = new ArrayList<>();
        for (BlockPos at : BlockPos.iterateOutwards(room.bedHead(), REACH, HEIGHT, REACH)) {
            if (!room.containsBlock(at)) continue;
            BlockState state = world.getBlockState(at);
            BlockPos here = at.toImmutable();
            if (state.getBlock() instanceof StairsBlock && could.stream().noneMatch(r -> r instanceof Chair)) could.add(new Chair(world, here, room.bedHead()));
            if (state.getBlock() instanceof DoorBlock door && state.get(Properties.DOUBLE_BLOCK_HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER && !door.isOpen(state)) could.add(() -> door.setOpen(null, world, state, here, true));
        }
        BlockPos desk = Radio.at(p);
        if (desk != null) {
            for (Direction side : Direction.Type.HORIZONTAL) {
                BlockPos beside = desk.offset(side);
                if (world.isAir(beside) && !world.isAir(beside.down())) {
                    could.add(() -> LunchHall.loose(world, Vec3d.ofBottomCenter(beside).add(0, 0.03, 0), "minecraft:white_tulip", 0.4f));
                    could.add(() -> Radio.shift(p, beside));
                    break;
                }
            }
        }
        if (could.isEmpty()) return;
        could.get(RANDOM.nextInt(could.size())).run();
        Kokuhaku.LOG.info("Something's been moved at home on day {}", s.day);
        Shadow.saw(s, "She let herself in while you were out. She always puts things back. Nearly.");
        if (told++ < SAYS_SO_TIMES) RunManager.later(TELL_AFTER, () -> RunManager.tell(p, "", "Something in here isn't how you left it."));
    }

    /** Round to face the bed. */
    private record Chair(ServerWorld world, BlockPos at, BlockPos bed) implements Runnable {
        @Override
        public void run() {
            Direction to = Direction.getFacing(bed.getX() - at.getX(), 0, bed.getZ() - at.getZ());
            world.setBlockState(at, world.getBlockState(at).with(StairsBlock.FACING, to.getOpposite()));
        }
    }
}
