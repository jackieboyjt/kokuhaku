package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * You have to eat. Food and water run down through the day, water faster, and a night's
 * sleep takes some of both. Breakfast at home puts some back; the rest is lunch, the
 * vending machines and the places in town. Run low and you slow down; run out and you can
 * barely stand. Out of 1000: a full stomach lasts about a waking day, water most of one.
 */
public final class Body {
    /** Lost a game minute, in thousandths. Water goes half as fast again as food. */
    private static final int FOOD_A_MINUTE = 1, WATER_EVERY_OTHER = 1;
    /** What a night takes, and what breakfast puts back, up to a point. */
    private static final int NIGHT_FOOD = 120, NIGHT_WATER = 160, BREAKFAST_FOOD = 380, BREAKFAST_WATER = 320, BREAKFAST_UP_TO = 800;
    /** Below these you feel it; at the second you can barely stand. */
    public static final int LOW = 250, EMPTY = 60;
    private static final int GRUMBLE_EVERY = 45;

    private Body() {
    }

    /** Whether it's her who feeds you, so none of this is yours to manage. */
    private static boolean fedForYou(RunState s) {
        return Living.active(s) || Kept.on(s);
    }

    private static int slot(RunState s, ServerPlayerEntity p) {
        return Math.max(0, s.party.indexOf(p.getUuid()));
    }

    public static int food(RunState s, ServerPlayerEntity p) {
        return s.food[slot(s, p)];
    }

    public static int water(RunState s, ServerPlayerEntity p) {
        return s.water[slot(s, p)];
    }

    /** Something to eat or drink: how much food, how much water. */
    public static void eat(ServerPlayerEntity p, RunState s, int food, int water) {
        int i = slot(s, p);
        s.food[i] = Math.min(RunState.FULL, s.food[i] + food);
        s.water[i] = Math.min(RunState.FULL, s.water[i] + water);
        s.markDirty();
        Kokuhaku.LOG.info("{} ate: food {} water {}", p.getGameProfile().getName(), s.food[i], s.water[i]);
    }

    /** The night, then breakfast at home. */
    static void morning(RunState s, List<ServerPlayerEntity> living) {
        for (ServerPlayerEntity p : living) {
            int i = slot(s, p);
            if (fedForYou(s)) {
                s.food[i] = Math.max(s.food[i], BREAKFAST_UP_TO);
                s.water[i] = Math.max(s.water[i], BREAKFAST_UP_TO);
                continue;
            }
            s.food[i] = Math.max(0, s.food[i] - NIGHT_FOOD);
            s.water[i] = Math.max(0, s.water[i] - NIGHT_WATER);
            if (s.food[i] < BREAKFAST_UP_TO) s.food[i] = Math.min(BREAKFAST_UP_TO, s.food[i] + BREAKFAST_FOOD);
            if (s.water[i] < BREAKFAST_UP_TO) s.water[i] = Math.min(BREAKFAST_UP_TO, s.water[i] + BREAKFAST_WATER);
        }
        s.markDirty();
    }

    /** Each game minute. */
    static void minute(RunState s, List<ServerPlayerEntity> living) {
        boolean fed = fedForYou(s);
        for (ServerPlayerEntity p : living) {
            if (RunManager.isGhost(p) || p.getY() > 90) continue;
            int i = slot(s, p);
            if (!fed) {
                s.food[i] = Math.max(0, s.food[i] - FOOD_A_MINUTE);
                s.water[i] = Math.max(0, s.water[i] - FOOD_A_MINUTE - (s.minute % 2 == 0 ? WATER_EVERY_OTHER : 0));
            }
            feel(p, s, s.food[i], s.water[i]);
        }
    }

    /** Hunger and thirst slow you down, and say so now and then. */
    private static void feel(ServerPlayerEntity p, RunState s, int food, int water) {
        int worst = Math.min(food, water);
        if (worst >= LOW) return;
        boolean empty = worst < EMPTY;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 4, empty ? 1 : 0, false, false));
        if (empty) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 20 * 4, 0, false, false));
            if (s.minute % 20 == 0) p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 20 * 6, 0, false, false));
        }
        if (s.minute % GRUMBLE_EVERY != 0) return;
        String line;
        if (water <= food) line = empty ? "Your head's pounding. You need water, now." : "Your mouth's dry. You can't think about anything but a drink.";
        else line = empty ? "Your hands are shaking. When did you last eat?" : "Your stomach growls. Loudly.";
        RunManager.tell(p, "", line);
    }
}
