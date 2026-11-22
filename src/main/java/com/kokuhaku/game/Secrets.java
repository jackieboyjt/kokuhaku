package com.kokuhaku.game;

import com.kokuhaku.world.town.Town;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Three girls with something to hide, and none of it is you. One goes to the
 * hospital every evening, one walks alone late at night, one waits at the end
 * of your road. Any of them might be her. Nothing is saved: it all comes from
 * the seed, so a Continue deals the same three.
 */
final class Secrets {
    enum Kind { HOSPITAL, NIGHT_WALKS, CRUSH }

    private Secrets() {
    }

    /** Who keeps this secret, or -1 once the run has gone somewhere else. */
    static int keeper(RunState s, Kind k) {
        if (s.devoted || Living.active(s) || Kept.on(s)) return -1;
        Random r = new Random(s.seed ^ 0x5345435245L);
        List<Integer> girls = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(Math.max(1, s.party.size())); g++) girls.add(g);
        Collections.shuffle(girls, r);
        return girls.get(k.ordinal());
    }

    static Kind kindOf(RunState s, int id) {
        for (Kind k : Kind.values()) if (keeper(s, k) == id) return k;
        return null;
    }

    private static List<Integer> nights(RunState s, int id) {
        List<Integer> days = new ArrayList<>();
        for (int d = 3; d <= 13; d++) days.add(d);
        Collections.shuffle(days, new Random(s.seed ^ id ^ 0x4E49474854L));
        return days.subList(0, 4);
    }

    private static List<Integer> crushDays(RunState s, int id) {
        List<Integer> days = new ArrayList<>();
        for (int d = 2; d <= 13; d++) if (Schedule.isSchoolDay(d) && d != Festival.DAY) days.add(d);
        Collections.shuffle(days, new Random(s.seed ^ id ^ 0x435255L));
        return days.subList(0, Math.min(3, days.size()));
    }

    static String nightPlace(RunState s, int id) {
        return new Random(s.seed ^ s.day * 131L ^ id * 977L).nextBoolean() ? "park" : "beach";
    }

    /** Her secret, if she's keeping it right now. */
    static Kind kindNow(RunState s, int id) {
        Kind k = kindOf(s, id);
        if (k == null || s.dead.contains(id) || People.stayingHome(s, id)) return null;
        int m = s.minute;
        boolean open = switch (k) {
            case HOSPITAL -> (Schedule.isSchoolDay(s.day) || Schedule.weekday(s.day) == 5) && m >= 17 * 60 + 40 && m < 19 * 60 + 20;
            case NIGHT_WALKS -> nights(s, id).contains(s.day) && m >= 20 * 60 + 30 && m < 21 * 60 + 50;
            case CRUSH -> crushDays(s, id).contains(s.day) && m >= Schedule.AFTER_SCHOOL + 20 && m < Schedule.AFTER_SCHOOL + 60;
        };
        return open ? k : null;
    }

    /** Whose road the crush stands on today, as a party slot. */
    static int crushSlot(RunState s) {
        return s.day % Math.max(1, s.party.size());
    }

    /** Where she goes instead of her usual. Its own random, so nobody else's day moves. */
    static Routines.Target out(int id, RunState s, Town town) {
        Kind k = kindNow(s, id);
        if (k == null) return null;
        Random r = new Random(s.seed ^ s.day * 131L ^ id * 977L);
        return switch (k) {
            case HOSPITAL -> new Routines.Target("secret:hospital", town.graph.wander(town.place("hospital"), 3, r), null, Float.NaN);
            case NIGHT_WALKS -> {
                String place = nightPlace(s, id);
                yield new Routines.Target("secret:" + place, town.graph.wander(town.place(place), 4, r), null, Float.NaN);
            }
            case CRUSH -> {
                // A different player's road each day, near enough to see the door.
                int slot = crushSlot(s);
                if (s.homes == null || Roster.TOTAL + slot >= s.homes.length) yield null;
                int inside = town.homes.get(s.homes[Roster.TOTAL + slot]).inside();
                BlockPos door = town.graph.pos(inside);
                int node = -1;
                for (int n : town.graph.path(inside, town.place("station"))) {
                    BlockPos at = town.graph.pos(n);
                    double dx = at.getX() - door.getX(), dz = at.getZ() - door.getZ();
                    if (at.getY() == 22 && dx * dx + dz * dz >= 14 * 14) {
                        node = n;
                        break;
                    }
                }
                if (node < 0) node = town.graph.wander(inside, 3, r);
                yield new Routines.Target("secret:road", node, null, Float.NaN);
            }
        };
    }

    /** What people say about her, for the gossip. Null if she's got nothing to hide. */
    static String surface(RunState s, int g) {
        Kind k = kindOf(s, g);
        if (k == null) return null;
        String line = switch (k) {
            case HOSPITAL -> "...%s goes to the hospital most evenings. Don't ask her about it...";
            case NIGHT_WALKS -> "...%s was out walking on her own after dark. Not the first time...";
            case CRUSH -> "...%s keeps going home the long way. Past someone's house...";
        };
        return String.format(line, Roster.get(g).firstName());
    }

    /** At the end, what each of them was really doing. */
    static List<String> reelLines(RunState s) {
        List<String> lines = new ArrayList<>();
        for (Kind k : Kind.values()) {
            int g = keeper(s, k);
            if (g < 0) return new ArrayList<>();
            String name = Roster.get(g).firstName();
            if (s.mystery().isYandere(g)) {
                lines.add(switch (k) {
                    case HOSPITAL -> "She went to the hospital every evening to learn the nurses' rota.";
                    case NIGHT_WALKS -> "She wasn't out walking. She was timing your street.";
                    case CRUSH -> "She stood at the end of your road because she couldn't stand anywhere else.";
                });
            } else {
                lines.add(switch (k) {
                    case HOSPITAL -> name + " went to the hospital every evening. Her little brother is on the second floor.";
                    case NIGHT_WALKS -> name + " couldn't sleep. She walked until she could.";
                    case CRUSH -> name + " only ever liked you. She never found a way to say it.";
                });
            }
        }
        return lines;
    }
}
