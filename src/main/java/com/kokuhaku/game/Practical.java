package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every other Science lesson's up in the lab with the brewing stands, and every
 * other Home Economics is in the kitchens. You're given a bench, not a question:
 * go to one, put your hand on the equipment, and do the thing. None of Minecraft's
 * own menus come into it.
 */
public final class Practical {
    private static final int LAB_WEST = -609, LAB_EAST = -593, LAB_NORTH = -92, LAB_SOUTH = -70, UPSTAIRS = 30;
    private static final int KITCHEN_WEST = -584, KITCHEN_EAST = -568, KITCHEN_NORTH = -120, KITCHEN_SOUTH = -100;
    private static final int GIVE_UP_TICKS = 20 * 45, SAFETY_TICKS = 20 * 60, TOP_MARKS_PRIZE = 100;

    /** One practical for one player. Compared by identity, so an old lesson's timer can't touch a new one. */
    private static final class Bench {
        final String subject;

        Bench(String subject) {
            this.subject = subject;
        }
    }

    // Not started yet, and started (the screen's up). Only the first can just stand there.
    private static final Map<UUID, Bench> waiting = new HashMap<>();
    private static final Map<UUID, Bench> working = new HashMap<>();

    private Practical() {
    }

    static void clear() {
        waiting.clear();
        working.clear();
    }

    public static boolean there(Vec3d p, String subject) {
        if (subject.equals("Science")) return p.x >= LAB_WEST && p.x <= LAB_EAST && p.z >= LAB_NORTH && p.z <= LAB_SOUTH && p.y >= UPSTAIRS;
        return p.x >= KITCHEN_WEST && p.x <= KITCHEN_EAST && p.z >= KITCHEN_NORTH && p.z <= KITCHEN_SOUTH && p.y < UPSTAIRS;
    }

    static void start(ServerPlayerEntity p, RunState s, String subject) {
        Bench bench = new Bench(subject);
        working.remove(p.getUuid());
        waiting.put(p.getUuid(), bench);
        boolean lab = subject.equals("Science");
        RunManager.tell(p, Teachers.nameFor(subject), lab ? "\"Practical today. Find a brewing stand and get on with it.\"" : "\"Practical today. Pick a stove. Nobody's eating this, so relax.\"");
        RunManager.later(GIVE_UP_TICKS, () -> {
            if (waiting.remove(p.getUuid(), bench)) RunManager.peDone(p, 0, 3, Teachers.nameFor(subject), "\"You stood there the whole lesson. I've noted that.\"", 0);
        });
        // If the screen never comes back, don't hold the lesson up for ever.
        RunManager.later(GIVE_UP_TICKS + SAFETY_TICKS, () -> {
            if (working.remove(p.getUuid(), bench)) RunManager.peDone(p, 0, 3, Teachers.nameFor(subject), "\"You stood there the whole lesson. I've noted that.\"", 0);
        });
    }

    /** The lab and kitchen gear never opens a Minecraft menu, lesson or no lesson. */
    static boolean use(ServerPlayerEntity p, RunState s, BlockState state) {
        if (!state.isOf(Blocks.BREWING_STAND) && !state.isOf(Blocks.FURNACE) && !state.isOf(Blocks.CAULDRON)) return false;
        boolean right = state.isOf(Blocks.FURNACE) ? there(p.getPos(), "Home Economics") : there(p.getPos(), "Science");
        if (!(waiting.containsKey(p.getUuid()) || working.containsKey(p.getUuid())) || !right) {
            boolean school = there(p.getPos(), "Science") || there(p.getPos(), "Home Economics");
            RunManager.tell(p, "", school ? "You shouldn't touch that without a teacher in the room." : "Best leave that alone.");
            return true;
        }
        // Once the screen's up you're doing it, so the give-up timer leaves you be.
        Bench bench = waiting.remove(p.getUuid());
        if (bench != null) working.put(p.getUuid(), bench);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(state.isOf(Blocks.FURNACE));
        buf.writeVarInt(s.difficulty.ordinal());
        ServerPlayNetworking.send(p, Net.PRACTICAL, buf);
        return true;
    }

    /** How it went. True if this was what was being waited for. */
    public static boolean finished(ServerPlayerEntity p, int done, int outOf) {
        Bench bench = waiting.remove(p.getUuid());
        Bench started = working.remove(p.getUuid());
        if (bench == null) bench = started;
        if (bench == null) return false;
        Kokuhaku.LOG.info("Practical: {} of {}", done, outOf);
        String line = done == outOf ? "\"Perfect. Don't look so surprised. Here.\"" : done > 0 ? "\"It'll do. Clean up after yourself.\"" : "\"I'm going to need you to open a window.\"";
        RunManager.peDone(p, done, outOf, Teachers.nameFor(bench.subject), done + " of " + outOf + ". " + line, done == outOf ? TOP_MARKS_PRIZE : 0);
        return true;
    }
}
