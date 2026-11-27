package com.kokuhaku.client;

import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster;
import com.kokuhaku.game.Roster.Personality;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Nobody stands perfectly still. What people do with their hands when they're
 * doing nothing, how they carry themselves when they walk, how they sit through
 * a lesson. It's all worked out from who they are and the time, so there's
 * nothing to send over the network and no two of them move in step.
 */
final class Mannerisms {
    private enum Gesture { NOTHING, PHONE, CROSSED, HIP, LOOK_ROUND, STRETCH, YAWN, FIDDLE, NECK, CHECK_WATCH }

    private enum Sitting { WRITING, CHIN, BOARD, WINDOW }

    private static final int IDLE_SLOT = 170, SEAT_SLOT = 130, CHAT_SLOT = 90, GLANCE_SLOT = 150, GLANCE_LENGTH = 26;
    private static final float WALK_STARTS = 0.04f, WALK_FULL = 0.26f, FADE = 0.16f;
    private static final float CHANGE_RATE = 7f, TURN_RATE = 9f, SHUFFLE_FROM = 45f, SHUFFLE_BELOW = 0.1f, STRIDE_RATE = 0.6662f;
    private static final float RIDING_ARM = 0.6283f;
    private static final float SIT_SECONDS = 1.5f, STAND_SECONDS = 0.6f, STEP_PART = 0.45f, SEAT_HEIGHT = 0.5f, OVER_THE_CHAIR = 0.42f, FURTHEST_STEP = 3.2f, STEP_RATE = 0.8f, STEP_SIZE = 0.42f;
    /** Startled: how close something has to come and how fast (blocks a tick); for a car, how near, how fast, and how close to hitting them its line runs; then how long it lasts and how long till the next. */
    private static final double STARTLE_REACH = 2.2, STARTLE_SPEED = 0.26, CAR_REACH = 4, CAR_SPEED = 0.35, CAR_MISS = 1.1;
    /** Come up behind someone: how close before they notice, how far to go before it can happen again, and what counts as behind (degrees off their face). */
    private static final double CREPT_REACH = 1.4, CREPT_GONE = 3.5;
    private static final float UNSEEN_FROM = 110f;
    private static final long STARTLE_RISE_MS = 90, STARTLE_FADE_MS = 450, STARTLE_REST_MS = 6000, STARTLE_LOOK_MS = 150;
    /** Afraid or grieving: now and then they look at you, for a moment, from across the room. */
    private static final int WATCH_SLOT = 220, WATCH_LENGTH = 60;
    private static final double WATCH_REACH = 12;
    /** Out in the snow: how quickly the cold comes over them, and the stamping and shivering. */
    private static final float COLD_RATE = 1.5f;
    private static final int LUNCH_SLOT = 120, HALL_FLOOR = 25, HALL_WEST = -601, HALL_EAST = -585, HALL_NORTH = -120, HALL_SOUTH = -102;

    /** Everyone goes from one thing to the next at their own pace, so each of them keeps track of how far they've got. */
    private static final class Easing {
        float sat, chat, speak, wave, turn, clock, lastTurn, lastMoved, cold;
        long last, lastFrame;
        /** When something last made them jump, when they last checked, and which way it came from (degrees). */
        long startledAt, lookedAt;
        float startleFrom;
        /** Who's close to them now, and so has already had their chance to make them jump. */
        final java.util.Set<java.util.UUID> crept = new java.util.HashSet<>();
        boolean wasSat;
        /** Where they last stood, and where they stepped off from to sit. */
        Vec3d stood, from;
    }

    private static final Map<StudentEntity, Easing> EASING = new WeakHashMap<>();

    /** Who's looked at their phone because yours went, and from when till when. Yours alone: nobody else sees it. */
    private static final Map<StudentEntity, long[]> BUZZED = new WeakHashMap<>();
    private static final Random BUZZ = new Random();

    private Mannerisms() {
    }

    /** Your phone goes, and a moment later somebody near you gets theirs out too. The class chat, and a few of them do. */
    static void buzz(boolean classChat) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.world == null || !ClientRun.active) return;
        long now = System.currentTimeMillis();
        BUZZED.values().removeIf(b -> now > b[1]);
        List<StudentEntity> near = new ArrayList<>(c.world.getEntitiesByClass(StudentEntity.class, c.player.getBoundingBox().expand(20),
                e -> e.getPeek() == 0 && e.getSkin() < Roster.TOTAL && !e.isSittingDown() && !e.isSpeaking() && !e.isChatting()));
        Collections.shuffle(near, BUZZ);
        List<StudentEntity> chosen = new ArrayList<>();
        if (classChat) {
            for (StudentEntity e : near) if (chosen.size() < 4 && BUZZ.nextFloat() < 0.35f) chosen.add(e);
        } else if (!near.isEmpty()) {
            chosen.addAll(near.subList(0, Math.min(near.size(), BUZZ.nextFloat() < 0.5f ? 2 : 1)));
        }
        for (StudentEntity e : chosen) {
            long start = now + 150 + BUZZ.nextInt(551);
            BUZZED.put(e, new long[]{start, start + 2500 + BUZZ.nextInt(1501)});
        }
    }

    private static float toward(float now, float want, float rate, float seconds) {
        return now + (want - now) * (1f - (float) Math.exp(-rate * seconds));
    }

    private static Easing easing(StudentEntity e) {
        return EASING.computeIfAbsent(e, k -> {
            Easing s = new Easing();
            s.sat = k.isSittingDown() ? 1f : 0f;
            s.clock = s.sat;
            s.wasSat = k.isSittingDown();
            s.last = s.lastFrame = System.currentTimeMillis();
            return s;
        });
    }

    /** How far down into the seat they've got, 0..1. */
    static float sat(StudentEntity e) {
        return easing(e).sat;
    }

    /** Each frame, before anything's drawn: step into the seat first, then lower onto it. Getting up is quicker. */
    static void frame(StudentEntity e, Vec3d at) {
        Easing s = easing(e);
        long now = System.currentTimeMillis();
        if (now - s.lastFrame > 250) {
            // Not drawn for a while, so whatever they did happened out of sight: they're simply where they are now.
            boolean sit = e.isSittingDown();
            s.clock = sit ? 1f : 0f;
            s.wasSat = sit;
            s.from = null;
            if (!sit) s.stood = at;
        }
        float seconds = Math.min(0.1f, (now - s.lastFrame) / 1000f);
        s.lastFrame = now;
        boolean sitting = e.isSittingDown();
        if (HEAD_TEST && sitting) headTest(e, s);
        if (sitting && !s.wasSat) {
            // Too far to have walked it: they were put there, so they're simply sat.
            s.from = s.stood != null && s.stood.squaredDistanceTo(at) < FURTHEST_STEP * FURTHEST_STEP ? s.stood : null;
            s.clock = s.from == null ? 1f : 0f;
        }
        s.wasSat = sitting;
        if (sitting) s.clock = Math.min(1f, s.clock + seconds / SIT_SECONDS);
        else {
            s.clock = Math.max(0f, s.clock - seconds / STAND_SECONDS);
            s.stood = at;
            s.from = null;
        }
        s.sat = ease((s.clock - STEP_PART) / (1f - STEP_PART));
    }

    /** Developer testing only: how often a seated head changes direction hard from one frame to the next. */
    private static final boolean HEAD_TEST = "lunchscene".equals(System.getenv("KOKUHAKU_STAGE"));
    private static int frames = 0, reversals = 0;

    private static void headTest(StudentEntity e, Easing s) {
        float turn = MathHelper.wrapDegrees(e.headYaw - e.bodyYaw), moved = turn - s.lastTurn;
        if (Math.abs(moved) > 2f && Math.abs(s.lastMoved) > 2f && Math.signum(moved) != Math.signum(s.lastMoved)) reversals++;
        s.lastMoved = moved;
        s.lastTurn = turn;
        if (++frames % 20000 == 0) com.kokuhaku.Kokuhaku.LOG.info("HEADS {} reversals in {} frames", reversals, frames);
    }

    /** Where they are on their way into the seat, from where they really are. A little up and over, so they don't go through the chair. */
    static Vec3d seatShift(StudentEntity e, Vec3d at) {
        Easing s = easing(e);
        if (!e.isSittingDown() || s.from == null || s.clock >= 1f) return null;
        Vec3d floor = at.add(0, -SEAT_HEIGHT, 0);
        if (s.clock >= STEP_PART) return new Vec3d(0, -SEAT_HEIGHT * (1f - s.sat), 0);
        float t = ease(s.clock / STEP_PART);
        return s.from.lerp(floor, t).add(0, MathHelper.sin(MathHelper.PI * t) * OVER_THE_CHAIR, 0).subtract(at);
    }

    private static float walkAmount(float limbSpeed) {
        return ease((limbSpeed - WALK_STARTS) / (WALK_FULL - WALK_STARTS));
    }

    private static int mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        return (int) (h & Integer.MAX_VALUE);
    }

    private static Personality kind(StudentEntity e) {
        return e.getSkin() < Roster.TOTAL ? Roster.get(e.getSkin()).personality() : Personality.POLITE;
    }

    private static float ease(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Up, held, and back down again over a slot. */
    private static float held(float t) {
        return ease(t / FADE) * ease((1f - t) / FADE);
    }

    private static void arm(ModelPart part, float weight, float pitch, float yaw, float roll) {
        part.pitch = MathHelper.lerp(weight, part.pitch, pitch);
        part.yaw = MathHelper.lerp(weight, part.yaw, yaw);
        part.roll = MathHelper.lerp(weight, part.roll, roll);
    }

    private static Gesture idleGesture(Personality kind, int roll) {
        Gesture[] mine = switch (kind) {
            case CHEERFUL -> new Gesture[]{Gesture.LOOK_ROUND, Gesture.PHONE, Gesture.STRETCH, Gesture.HIP};
            case SHY -> new Gesture[]{Gesture.FIDDLE, Gesture.FIDDLE, Gesture.NECK, Gesture.PHONE};
            case COLD -> new Gesture[]{Gesture.CROSSED, Gesture.CROSSED, Gesture.PHONE, Gesture.YAWN};
            case GOSSIP -> new Gesture[]{Gesture.PHONE, Gesture.PHONE, Gesture.HIP, Gesture.LOOK_ROUND};
            case POLITE -> new Gesture[]{Gesture.FIDDLE, Gesture.CHECK_WATCH, Gesture.LOOK_ROUND, Gesture.YAWN};
            case SPORTY -> new Gesture[]{Gesture.STRETCH, Gesture.NECK, Gesture.HIP, Gesture.YAWN};
        };
        // Often enough, nothing at all: that's most of what standing around is.
        return roll % 10 < 4 ? Gesture.NOTHING : mine[roll % mine.length];
    }

    private static void idle(PlayerEntityModel<StudentEntity> m, StudentEntity e, float clock, float age, float amount) {
        long slot = (long) (clock / IDLE_SLOT);
        float t = (clock % IDLE_SLOT) / IDLE_SLOT, w = held(t) * amount;
        // Feet turned out a little, the knee on the resting side gone soft, and eyes that don't stay in one place.
        float side = MathHelper.sin((age + Math.floorMod(e.getId(), 997) * 91f) * 0.017f);
        m.rightLeg.yaw += 0.07f * amount;
        m.leftLeg.yaw -= 0.07f * amount;
        m.rightLeg.pitch -= 0.08f * Math.max(0f, side) * amount;
        m.leftLeg.pitch -= 0.08f * Math.max(0f, -side) * amount;
        m.head.yaw += MathHelper.sin(clock * 0.013f) * 0.09f * amount;
        m.head.pitch += MathHelper.sin(clock * 0.019f) * 0.03f * amount;
        Gesture g = idleGesture(kind(e), mix(slot, e.getId()));
        if (e.getMood() == StudentEntity.GRIEVING && g != Gesture.NOTHING) g = Gesture.FIDDLE;
        switch (g) {
            case PHONE -> {
                arm(m.rightArm, w, -1.15f, -0.4f, 0f);
                arm(m.leftArm, w, -0.9f, 0.3f, 0f);
                m.head.pitch += 0.5f * w;
            }
            case CROSSED -> {
                arm(m.rightArm, w, -0.95f, -0.72f, 0f);
                arm(m.leftArm, w, -0.8f, 0.72f, 0f);
                m.head.yaw += MathHelper.sin(clock * 0.03f) * 0.35f * w;
            }
            case HIP -> arm(m.rightArm, w, -0.35f, 0.5f, 0.6f);
            case LOOK_ROUND -> {
                m.head.yaw += MathHelper.sin(clock * 0.05f) * 0.75f * w;
                m.head.pitch += MathHelper.sin(clock * 0.031f) * 0.14f * w;
            }
            case STRETCH -> {
                arm(m.rightArm, w, -2.95f, -0.15f, 0.12f);
                arm(m.leftArm, w, -2.95f, 0.15f, -0.12f);
                m.head.pitch -= 0.22f * w;
            }
            case YAWN -> {
                arm(m.rightArm, w, -2.05f, -0.55f, 0f);
                m.head.pitch -= 0.2f * w;
            }
            case FIDDLE -> {
                float fuss = MathHelper.sin(clock * 0.4f) * 0.06f;
                arm(m.rightArm, w, -0.55f + fuss, -0.42f, 0f);
                arm(m.leftArm, w, -0.55f - fuss, 0.42f, 0f);
                m.head.pitch += 0.28f * w;
            }
            case NECK -> {
                arm(m.rightArm, w, -2.55f, -0.1f, -0.4f);
                m.head.roll += 0.2f * w;
            }
            case CHECK_WATCH -> {
                arm(m.leftArm, w, -1.3f, 0.5f, 0f);
                m.head.pitch += 0.42f * w;
                m.head.yaw += 0.25f * w;
            }
            default -> {
            }
        }
    }

    /** The stride's own shape: the hips go round, the shoulders go the other way, and the head stays level. */
    private static void gait(PlayerEntityModel<StudentEntity> m, StudentEntity e, float limbPos, float amount) {
        float step = switch (kind(e)) {
            case SHY -> 0.72f;
            case COLD -> 0.84f;
            case SPORTY -> 1.06f;
            default -> 0.92f;
        };
        float phase = limbPos * STRIDE_RATE, turn = MathHelper.sin(phase) * 0.09f * amount;
        m.rightLeg.pitch = stride(m.rightLeg.pitch * MathHelper.lerp(amount, 1f, step), phase, amount);
        m.leftLeg.pitch = stride(m.leftLeg.pitch * MathHelper.lerp(amount, 1f, step), phase + MathHelper.PI, amount);
        // Feet turned out a touch, the way nobody walks with them dead straight.
        m.rightLeg.yaw += 0.06f * amount;
        m.leftLeg.yaw -= 0.06f * amount;
        m.body.yaw += turn;
        m.rightArm.pivotZ += MathHelper.sin(turn) * 5f;
        m.leftArm.pivotZ -= MathHelper.sin(turn) * 5f;
        // Arms swing further forward than back, and hang a little out from the hips rather than brushing them.
        m.rightArm.pitch = armSwing(m.rightArm.pitch, amount) - 0.07f * amount;
        m.leftArm.pitch = armSwing(m.leftArm.pitch, amount) - 0.07f * amount;
        m.rightArm.roll += 0.07f * amount;
        m.leftArm.roll -= 0.07f * amount;
        m.head.yaw -= turn * 0.6f;
    }

    /** A leg pushes back off the ground slowly and swings through quicker: weight on it, then none. */
    private static float stride(float pitch, float phase, float amount) {
        // Forward is negative. The swing through gets a little extra lift at its height, the push back a little less reach.
        float shaped = pitch < 0f ? pitch * (1f + 0.12f * Math.max(0f, MathHelper.sin(phase))) : pitch * 0.9f;
        return MathHelper.lerp(amount, pitch, shaped);
    }

    private static float armSwing(float pitch, float amount) {
        return MathHelper.lerp(amount, pitch, pitch < 0f ? pitch * 1.12f : pitch * 0.72f);
    }

    private static void walking(PlayerEntityModel<StudentEntity> m, StudentEntity e, float clock, float age, float amount, float speaking) {
        Personality kind = kind(e);
        float swing = switch (kind) {
            case SHY -> 0.45f;
            case COLD -> 0.22f;
            case SPORTY -> 1.2f;
            case CHEERFUL -> 1.05f;
            default -> 0.8f;
        };
        m.rightArm.pitch *= MathHelper.lerp(amount, 1f, swing);
        m.leftArm.pitch *= MathHelper.lerp(amount, 1f, swing);
        m.head.pitch += amount * switch (kind) {
            case SHY -> 0.24f;
            case COLD -> 0.08f;
            case SPORTY -> -0.04f;
            default -> 0.02f;
        };
        if (e.getMood() == StudentEntity.GRIEVING) m.head.pitch += 0.26f * amount;
        // By the third week, nobody walks anywhere without checking behind them.
        if (e.getMood() == StudentEntity.AFRAID) {
            long slot = (long) (clock / GLANCE_SLOT);
            float at = clock % GLANCE_SLOT;
            int roll = mix(slot, e.getId() * 31L);
            if (roll % 3 == 0 && at < GLANCE_LENGTH) m.head.yaw += (roll % 2 == 0 ? 1f : -1f) * 1.05f * held(at / GLANCE_LENGTH) * amount;
        }
        if (speaking > 0.01f) {
            arm(m.rightArm, 0.6f * speaking * amount, -0.65f + MathHelper.sin(age * 0.3f) * 0.3f, -0.2f, 0f);
            m.head.pitch += MathHelper.sin(age * 0.26f) * 0.04f * speaking * amount;
        }
    }

    /** At a table in the hall: picking at their food, talking with their mouth half full, watching whoever's speaking. */
    private static void atLunch(PlayerEntityModel<StudentEntity> m, StudentEntity e, float clock, float age, float amount, float speaking) {
        long slot = (long) (clock / LUNCH_SLOT);
        float t = (clock % LUNCH_SLOT) / LUNCH_SLOT, w = held(t) * amount * (1f - speaking);
        int roll = mix(slot, e.getId() * 13L);
        // Hands on the table to begin with: that's where they come back to.
        arm(m.rightArm, amount, -0.85f, -0.14f, 0f);
        arm(m.leftArm, amount, -0.8f, 0.14f, 0f);
        switch (roll % 5) {
            case 0, 1 -> {
                // A mouthful: down to the tray, up, and chew.
                float bite = Math.max(0f, MathHelper.sin(clock * 0.14f));
                arm(m.rightArm, w, MathHelper.lerp(bite * bite, -0.85f, -1.95f), -0.3f, 0f);
                m.head.pitch += (0.26f - 0.16f * bite) * w;
                m.head.pitch += MathHelper.sin(age * 0.85f) * 0.018f * w;
            }
            case 2 -> {
                arm(m.leftArm, w, -2.05f, 0.34f, 0f);
                m.head.pitch -= 0.26f * w;
            }
            case 3 -> m.head.yaw += (roll % 2 == 0 ? 0.65f : -0.65f) * w;
            default -> m.head.pitch += (0.2f + MathHelper.sin(clock * 0.1f) * 0.06f) * w;
        }
        if (speaking > 0.01f) {
            float talking = amount * speaking;
            arm(m.rightArm, talking, -1.05f + MathHelper.sin(age * 0.33f) * 0.3f, -0.25f + MathHelper.sin(age * 0.19f) * 0.18f, 0f);
            m.head.pitch += MathHelper.sin(age * 0.24f) * 0.05f * talking;
        }
    }

    private static boolean inLunchHall(StudentEntity e) {
        return e.getBlockY() >= HALL_FLOOR && e.getBlockY() <= HALL_FLOOR + 1 && e.getX() >= HALL_WEST && e.getX() <= HALL_EAST && e.getZ() >= HALL_NORTH && e.getZ() <= HALL_SOUTH;
    }

    private static void seated(PlayerEntityModel<StudentEntity> m, StudentEntity e, float clock, float age, float amount, float speaking) {
        if (inLunchHall(e)) {
            atLunch(m, e, clock, age, amount, speaking);
            return;
        }
        long slot = (long) (clock / SEAT_SLOT);
        float t = (clock % SEAT_SLOT) / SEAT_SLOT, w = held(t) * amount;
        int roll = mix(slot, e.getId() * 7L);
        Sitting what = Sitting.values()[kind(e) == Personality.SPORTY && roll % 2 == 0 ? 3 : roll % Sitting.values().length];
        switch (what) {
            case WRITING -> {
                arm(m.rightArm, w, -0.95f + MathHelper.sin(clock * 1.1f) * 0.04f, -0.25f + MathHelper.sin(clock * 0.45f) * 0.07f, 0f);
                arm(m.leftArm, w, -0.85f, 0.2f, 0f);
                m.head.pitch += 0.46f * w;
            }
            case CHIN -> {
                arm(m.leftArm, w, -2.05f, 0.5f, 0f);
                m.head.roll -= 0.14f * w;
                m.head.pitch += 0.08f * w;
            }
            case WINDOW -> m.head.yaw += (roll % 4 < 2 ? 0.7f : -0.7f) * w;
            default -> m.head.pitch += MathHelper.sin(clock * 0.02f) * 0.05f * amount;
        }
    }

    private static void chatting(PlayerEntityModel<StudentEntity> m, StudentEntity e, float clock, float age, float amount, float speaking) {
        long slot = (long) (clock / CHAT_SLOT);
        int roll = mix(slot, e.getId() * 11L);
        float talking = amount * speaking, listening = amount * (1f - speaking);
        if (talking > 0.01f) {
            float hands = (kind(e) == Personality.COLD || kind(e) == Personality.SHY ? 0.45f : 1f) * talking;
            arm(m.rightArm, hands, -0.6f + MathHelper.sin(age * 0.33f) * 0.34f, -0.25f + MathHelper.sin(age * 0.19f) * 0.2f, 0f);
            // The other hand joins in and drops out gently, not on the turn of the slot.
            float other = hands * held((clock % CHAT_SLOT) / CHAT_SLOT);
            if (roll % 3 == 0) arm(m.leftArm, other, -0.5f + MathHelper.sin(age * 0.27f + 1f) * 0.28f, 0.25f, 0f);
            m.head.pitch += MathHelper.sin(age * 0.24f) * 0.05f * talking;
        }
        if (listening > 0.01f) {
            // Listening: a nod now and then, and the odd laugh.
            float t = (clock % CHAT_SLOT) / CHAT_SLOT, w = held(t) * listening;
            if (roll % 3 == 0) m.head.pitch += MathHelper.sin(age * 0.42f) * 0.09f * w;
            if (roll % 7 == 1) m.head.pitch -= (0.2f + MathHelper.sin(age * 1.1f) * 0.05f) * w;
            m.head.roll += 0.07f * (roll % 2 == 0 ? 1f : -1f) * w;
        }
    }


    /** A hand up, and a couple of waggles. */
    private static void waving(PlayerEntityModel<StudentEntity> m, float age, float amount) {
        arm(m.leftArm, amount, -2.75f, 0f, -0.2f + MathHelper.sin(age * 0.8f) * 0.3f);
    }

    /** Everything but the head, which the caller has already pointed at what they're looking at. */
    static void apply(PlayerEntityModel<StudentEntity> m, StudentEntity e, float age, float limbPos, float limbSpeed, boolean sitting) {
        // The id only staggers them, so keep it small: a float clock in the millions loses the part-ticks and steps.
        float clock = age + Math.floorMod(e.getId(), 997) * 173f;
        Easing s = easing(e);
        long now = System.currentTimeMillis();
        float seconds = Math.min(0.1f, (now - s.last) / 1000f);
        s.last = now;

        s.chat = toward(s.chat, e.isChatting() ? 1f : 0f, CHANGE_RATE, seconds);
        s.speak = toward(s.speak, e.isSpeaking() ? 1f : 0f, CHANGE_RATE, seconds);
        s.wave = toward(s.wave, e.isWaving() ? 1f : 0f, CHANGE_RATE, seconds);
        // Degrees a second over the last tick, which comes out the same however often they're drawn.
        float turning = Math.abs(MathHelper.wrapDegrees(e.bodyYaw - e.prevBodyYaw)) * 20f;
        s.turn = toward(s.turn, turning > SHUFFLE_FROM && limbSpeed < SHUFFLE_BELOW ? 1f : 0f, TURN_RATE, seconds);

        // Sitting down and standing up go through the pose, not round it.
        float up = 1f - s.sat;
        if (up > 0.001f && m.riding) {
            m.rightLeg.pitch *= s.sat;
            m.leftLeg.pitch *= s.sat;
            m.rightLeg.yaw *= s.sat;
            m.leftLeg.yaw *= s.sat;
            m.rightArm.pitch += RIDING_ARM * up;
            m.leftArm.pitch += RIDING_ARM * up;
        }
        float walk = walkAmount(limbSpeed) * up;
        if (s.sat > 0.01f) seated(m, e, clock, age, s.sat, s.speak);
        if (walk > 0.001f) {
            walking(m, e, clock, age, walk, s.speak);
            gait(m, e, limbPos, walk);
        }
        float still = (1f - walk) * up;
        if (still > 0.001f) {
            idle(m, e, clock, age, still * (1f - s.chat));
            chatting(m, e, clock, age, still * s.chat, s.speak);
        }
        // Turning on the spot takes your feet with you.
        float stepping = sitting && s.from != null && s.clock < STEP_PART ? STEP_SIZE * MathHelper.sin(MathHelper.PI * s.clock / STEP_PART) : 0f;
        float shuffle = MathHelper.sin(age * 0.95f) * 0.32f * s.turn * up + MathHelper.sin(age * STEP_RATE) * stepping;
        m.rightLeg.pitch += shuffle;
        m.leftLeg.pitch -= shuffle;
        if (s.wave > 0.01f) waving(m, age, s.wave);
        // In the snow, standing about out under the sky, the cold gets into them.
        boolean outInSnow = com.kokuhaku.game.Schedule.isSnowDay(ClientRun.day) && e.getY() <= 90 && e.getWorld().isSkyVisible(e.getBlockPos().up(2));
        s.cold = toward(s.cold, outInSnow ? 1f : 0f, COLD_RATE, seconds);
        if (s.cold > 0.01f) cold(m, e, clock, age, s.cold * still * (1f - s.chat), s.cold * walk);
        watching(m, e, clock, sitting);
        startled(m, e, s, now, sitting);
        // Theirs went when yours did, or they only thought it had: a look, and back in the pocket.
        long[] b = BUZZED.get(e);
        if (b != null) {
            if (now > b[1]) BUZZED.remove(e);
            else if (now >= b[0] && !sitting) {
                float w = ease((now - b[0]) / 250f) * ease((b[1] - now) / 300f);
                arm(m.rightArm, w, -1.15f, -0.4f, 0f);
                arm(m.leftArm, w, -0.9f, 0.3f, 0f);
                m.head.pitch += 0.5f * w;
            }
        }

        m.jacket.copyTransform(m.body);
        m.rightSleeve.copyTransform(m.rightArm);
        m.leftSleeve.copyTransform(m.leftArm);
        m.rightPants.copyTransform(m.rightLeg);
        m.leftPants.copyTransform(m.leftLeg);
    }

    /**
     * Cold. Their free hand (the other one has the umbrella) hugged across their front, shoulders up and
     * chin down, a stamp of the feet now and then, and a shiver. Walking, they just keep their arms in.
     */
    private static void cold(PlayerEntityModel<StudentEntity> m, StudentEntity e, float clock, float age, float standing, float walking) {
        if (standing > 0.001f) {
            arm(m.leftArm, standing, -1.05f, 0.95f, 0f);
            m.head.pitch += 0.16f * standing;
            // A bout of stamping, then standing it out.
            float stamping = MathHelper.clamp((MathHelper.sin(clock * 0.045f + e.getId()) - 0.55f) * 4f, 0f, 1f) * standing;
            float stamp = MathHelper.sin(age * 0.7f) * 0.22f * stamping;
            m.rightLeg.pitch += stamp;
            m.leftLeg.pitch -= stamp;
            float shiver = MathHelper.sin(age * 2.6f + e.getId()) * 0.025f * standing;
            m.leftArm.roll += shiver;
            m.head.roll += shiver * 0.6f;
        }
        if (walking > 0.001f) {
            m.leftArm.pitch *= 1f - 0.6f * walking;
            m.leftArm.roll -= 0.08f * walking;
            m.head.pitch += 0.1f * walking;
        }
    }

    /** Your own player, if it's there to be looked at. */
    private static net.minecraft.entity.player.PlayerEntity you() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c.player != null && ClientRun.active && !ClientRun.ghost ? c.player : null;
    }

    /** How far round to turn the head (radians, from the body) to face a point, as far as a neck goes. */
    private static float headToward(StudentEntity e, double x, double z) {
        float yaw = (float) Math.toDegrees(Math.atan2(z - e.getZ(), x - e.getX())) - 90f;
        return MathHelper.clamp(MathHelper.wrapDegrees(yaw - e.bodyYaw), -75f, 75f) * ((float) Math.PI / 180f);
    }

    /**
     * Scared, or grieving, they keep an eye on everyone, and that includes you: a look from across the
     * room, held a moment too long, and away again. Each of them on their own time.
     */
    private static void watching(PlayerEntityModel<StudentEntity> m, StudentEntity e, float clock, boolean sitting) {
        if (e.getMood() == StudentEntity.AT_EASE || e.isSpeaking() || e.isChatting() || e.isTalking()) return;
        var p = you();
        if (p == null || p.squaredDistanceTo(e) > WATCH_REACH * WATCH_REACH) return;
        long slot = (long) (clock / WATCH_SLOT);
        float at = clock % WATCH_SLOT;
        if (mix(slot, e.getId() * 53L) % 4 != 0 || at >= WATCH_LENGTH) return;
        float w = held(at / WATCH_LENGTH);
        m.head.yaw = MathHelper.lerp(w, m.head.yaw, headToward(e, p.getX(), p.getZ()));
        double down = p.getEyeY() - e.getEyeY(), across = Math.sqrt(p.squaredDistanceTo(e.getX(), p.getY(), e.getZ()));
        m.head.pitch = MathHelper.lerp(w, m.head.pitch, MathHelper.clamp((float) -Math.atan2(down, Math.max(0.5, across)), -0.6f, 0.6f));
    }

    /**
     * Someone tears past close enough to touch, or a car comes too near: they jump. Hands up, head back
     * and round to it, and then it's gone and they feel a bit silly. Not again for a while.
     */
    private static void startled(PlayerEntityModel<StudentEntity> m, StudentEntity e, Easing s, long now, boolean sitting) {
        if (now - s.lookedAt > STARTLE_LOOK_MS) {
            s.lookedAt = now;
            boolean steady = Math.abs(e.getX() - e.prevX) + Math.abs(e.getZ() - e.prevZ) < STARTLE_SPEED;
            if (steady && now - s.startledAt > STARTLE_REST_MS) {
                for (net.minecraft.entity.Entity o : e.getWorld().getOtherEntities(e, e.getBoundingBox().expand(Math.max(STARTLE_REACH, CAR_REACH)))) {
                    double dx = o.getX() - o.prevX, dz = o.getZ() - o.prevZ, speed = Math.sqrt(dx * dx + dz * dz);
                    boolean car = "trepscars".equals(net.minecraft.registry.Registries.ENTITY_TYPE.getId(o.getType()).getNamespace());
                    double reach = car ? CAR_REACH : STARTLE_REACH;
                    if (speed < (car ? CAR_SPEED : STARTLE_SPEED) || o.squaredDistanceTo(e) > reach * reach) continue;
                    if (o instanceof StudentEntity other && other.getPeek() != 0) continue;
                    // Cars going by in their lane are just traffic. One coming straight at them isn't.
                    if (car) {
                        double ox = e.getX() - o.getX(), oz = e.getZ() - o.getZ();
                        boolean coming = ox * dx + oz * dz > 0;
                        double miss = Math.abs(dx * oz - dz * ox) / speed;
                        if (!coming || miss > CAR_MISS) continue;
                    }
                    startle(s, e, o, now);
                    break;
                }
            }
            // Come up right behind someone without them seeing, and they jump when they do. Once, till you've gone away again.
            // Not anyone sitting down: walking behind chairs is just getting about a classroom.
            if (!sitting) for (net.minecraft.entity.player.PlayerEntity p : e.getWorld().getPlayers()) {
                if (p.isSpectator() || p.isInvisible()) continue;
                double d = p.squaredDistanceTo(e);
                if (d > CREPT_GONE * CREPT_GONE) {
                    s.crept.remove(p.getUuid());
                    continue;
                }
                if (d > CREPT_REACH * CREPT_REACH || s.crept.contains(p.getUuid())) continue;
                float toThem = (float) Math.toDegrees(Math.atan2(p.getZ() - e.getZ(), p.getX() - e.getX())) - 90f;
                if (Math.abs(MathHelper.wrapDegrees(toThem - e.getHeadYaw())) < UNSEEN_FROM) {
                    // Seen coming: no surprise, now or while they stay close.
                    s.crept.add(p.getUuid());
                    continue;
                }
                s.crept.add(p.getUuid());
                if (now - s.startledAt > STARTLE_REST_MS) startle(s, e, p, now);
            }
        }
        long since = now - s.startledAt;
        if (s.startledAt == 0 || since > STARTLE_RISE_MS + STARTLE_FADE_MS * 5) return;
        float w = since < STARTLE_RISE_MS ? ease(since / (float) STARTLE_RISE_MS) : (float) Math.exp(-(since - STARTLE_RISE_MS) / (double) STARTLE_FADE_MS);
        // The quiet ones jump the most; the cold ones barely.
        w *= switch (kind(e)) {
            case SHY -> 1.15f;
            case COLD -> 0.55f;
            case SPORTY -> 0.8f;
            default -> 1f;
        };
        w = Math.min(1f, w);
        float to = MathHelper.clamp(MathHelper.wrapDegrees(s.startleFrom - e.bodyYaw), -75f, 75f) * ((float) Math.PI / 180f);
        m.head.yaw = MathHelper.lerp(w, m.head.yaw, to);
        m.head.pitch -= 0.28f * w;
        float arms = sitting ? 0.5f : 1f;
        arm(m.rightArm, w * arms, -1.45f, -0.55f, 0f);
        arm(m.leftArm, w * arms, -1.35f, 0.55f, 0f);
        if (!sitting) {
            // Back onto the heels.
            m.rightLeg.pitch -= 0.12f * w;
            m.leftLeg.pitch += 0.08f * w;
        }
    }

    private static void startle(Easing s, StudentEntity e, net.minecraft.entity.Entity from, long now) {
        s.startledAt = now;
        s.startleFrom = (float) Math.toDegrees(Math.atan2(from.getZ() - e.getZ(), from.getX() - e.getX())) - 90f;
    }

    /** Side to side, degrees: standing on one leg more than the other, or rolling from foot to foot as they walk. */
    static float lean(StudentEntity e, float age, float limbPos, float limbSpeed, boolean sitting) {
        if (sitting) return 0f;
        float walk = walkAmount(limbSpeed), sway = kind(e) == Personality.COLD ? 0.7f : kind(e) == Personality.SPORTY ? 1.8f : 1.3f;
        return MathHelper.lerp(walk, MathHelper.sin((age + Math.floorMod(e.getId(), 997) * 91f) * 0.017f) * 1.5f, MathHelper.cos(limbPos * STRIDE_RATE) * sway);
    }

    /** Up onto each step and down off it. */
    static float bob(StudentEntity e, float limbPos, float limbSpeed) {
        float height = kind(e) == Personality.SPORTY ? 0.05f : kind(e) == Personality.COLD ? 0.02f : 0.035f;
        // Frightened or grieving, nobody bounces along.
        if (e.getMood() == StudentEntity.AFRAID || e.getMood() == StudentEntity.GRIEVING) height *= 0.5f;
        // Highest over the foot that's taking the weight, a little higher on the stronger side.
        float s = MathHelper.sin(limbPos * STRIDE_RATE);
        return Math.abs(s) * height * (s > 0 ? 1f : 0.88f) * walkAmount(limbSpeed);
    }
}
