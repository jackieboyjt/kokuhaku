package com.kokuhaku.game;

import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.game.Traits.Club;
import com.kokuhaku.game.Traits.District;
import com.kokuhaku.world.town.Town;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Detective work you do with your feet. Registers tell you who's in a club or
 * lives on a street, so a clue that says "art club" sends you to the art room.
 * And every few days she drops something, somewhere she's been. Now and then
 * so does a girl who isn't her.
 */
final class Sleuthing {
    /** Something on the floor, where it is, and what the morning says about it. */
    record Drop(BlockPos at, String text, String hint) {
    }

    private static final int FIRST_DROP = 2, DAYS_BETWEEN = 2;
    /** How many things she lets slip, by difficulty. */
    private static final int[] DROPS = {6, 5, 3, 2};
    /** How many things an innocent girl loses on the days between, by difficulty. */
    private static final int[] DECOYS = {0, 1, 2, 2};

    private Sleuthing() {
    }

    /** What a board says today: the dead are crossed out. */
    static String read(RunState s, String what) {
        String[] parts = what.split(":");
        Mystery m = s.mystery();
        boolean club = parts[0].equals("club");
        List<String> names = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) {
            boolean in = club ? m.clubs[g] == Club.valueOf(parts[1]) : m.districts[g] == District.valueOf(parts[1]);
            if (!in) continue;
            names.add(Roster.get(g).firstName() + (s.dead.contains(g) ? " (crossed out)" : ""));
            s.known.add(g);
        }
        String title = club ? Club.valueOf(parts[1]).label() + " club" : District.valueOf(parts[1]).label();
        String line = title + ": " + (names.isEmpty() ? "no girls listed." : String.join(", ", names) + ".");
        if (!s.notes.contains(line)) s.notes.add(line);
        s.markDirty();
        return line;
    }

    /** Whether today's the day something turns up, and if so, what. All of it's true. */
    static Drop todaysDrop(RunState s, Town town) {
        if (Living.active(s)) return null;
        int[][] days = schedule(s);
        // Some days it's someone else's.
        for (int k = 0; k < days[1].length; k++) {
            if (days[1][k] == s.day) return s.dead.contains(decoy(s)) ? null : place(s, town, decoyTemplate(s, s.mystery(), k));
        }
        int index = -1;
        for (int i = 0; i < days[0].length; i++) if (days[0][i] == s.day) index = i;
        if (index < 0) return null;
        Mystery m = s.mystery();
        Student her = Roster.get(s.freeYandere());
        int level = Math.max(1, s.difficulty.ordinal());
        String clubRoom = Routines.clubPlace(m.clubs[her.id()]);
        // Each is a place worth going back to, and something that could only have come from her.
        Object[][] drops = {
                {clubRoom, "her club's room", "A hair clip on the floor. " + her.hair().phrase(level)},
                {"foyer", "the entrance hall", "A pencil case left by the shoe lockers. " + her.homeroom().phrase(level)},
                {"center:Library", "the library", "A borrowed book with your name in the margins. " + her.length().phrase(level)},
                {"station", "the station", "A crumpled receipt. " + m.districts[her.id()].phrase(level)},
                {"park", "the park", (her.glasses() == Traits.Glasses.YES
                        ? "A lens cloth, folded very neatly. "
                        : "A hand mirror, polished spotless. ") + her.glasses().phrase(level)},
                {clubRoom, "her club's room", "A page of practice signatures. All of them are your surname with her first initial: " + her.firstName().charAt(0) + "."},
        };
        // Which of the first two comes first is the seed's call, as it is for the other girl's.
        if (index < 2) {
            List<Integer> order = new ArrayList<>(List.of(0, 1));
            Collections.shuffle(order, new Random(s.seed ^ 0x48455253L));
            index = order.get(index);
        }
        return place(s, town, drops[index]);
    }

    /**
     * The day each of hers turns up, then each of the other girl's. Her first two
     * and the other girl's share the opening days, dealt by the seed, so neither
     * the day nor the sort of thing says whose it was.
     */
    private static int[][] schedule(RunState s) {
        int n = DROPS[s.difficulty.ordinal()], dn = DECOYS[s.difficulty.ordinal()];
        int[] hers = new int[n], theirs = new int[dn];
        for (int i = 0; i < n; i++) hers[i] = FIRST_DROP + i * DAYS_BETWEEN;
        if (dn == 0) return new int[][]{hers, theirs};
        int shared = Math.min(2, n);
        // Days 2 to 2 + shared + dn - 1, all before her third (day 6).
        List<Integer> days = new ArrayList<>();
        for (int i = 0; i < shared + dn; i++) days.add(FIRST_DROP + i);
        Collections.shuffle(days, new Random(s.seed ^ 0x44415953L));
        List<Integer> other = new ArrayList<>(days.subList(0, dn));
        List<Integer> mine = new ArrayList<>(days.subList(dn, days.size()));
        Collections.sort(other);
        Collections.sort(mine);
        for (int k = 0; k < dn; k++) theirs[k] = other.get(k);
        for (int i = 0; i < shared; i++) hers[i] = mine.get(i);
        return new int[][]{hers, theirs};
    }

    /** Puts a drop a few steps from its place, with the morning's word on it. */
    private static Drop place(RunState s, Town town, Object[] drop) {
        Random r = new Random(s.seed ^ s.day * 4099L);
        BlockPos at = town.graph.pos(town.graph.wander(town.place((String) drop[0]), 2 + r.nextInt(3), r));
        String hint = switch (s.difficulty) {
            case EASY -> "Something's been dropped in " + drop[1] + ".";
            case MEDIUM -> "Someone was seen hurrying through " + drop[1] + " last night.";
            default -> "";
        };
        return new Drop(at, (String) drop[2], hint);
    }

    /** The girl who keeps losing things. Never her, and the same one all run. */
    static int decoy(RunState s) {
        Random r = new Random(s.seed ^ 0x4445434FL);
        Mystery m = s.mystery();
        List<Integer> girls = new ArrayList<>();
        // Sized by the party the run was dealt for (one of her each), so it holds if someone leaves.
        for (int g = 0; g < Roster.girlsFor(m.yanderes.size()); g++) if (!m.isYandere(g)) girls.add(g);
        return girls.get(r.nextInt(girls.size()));
    }

    /** What she loses, and where: the same sort of thing as hers, and true of the girl who lost it. */
    private static Object[] decoyTemplate(RunState s, Mystery m, int k) {
        Student d = Roster.get(decoy(s));
        int level = Math.max(1, s.difficulty.ordinal());
        List<Integer> order = new ArrayList<>(List.of(0, 1));
        Collections.shuffle(order, new Random(s.seed ^ 0x44524F50L));
        Object[][] templates = {
                {Routines.clubPlace(m.clubs[d.id()]), "her club's room", "A hair clip on the floor. " + d.hair().phrase(level)},
                {"foyer", "the entrance hall", "A pencil case left by the shoe lockers. " + d.homeroom().phrase(level)},
        };
        return templates[order.get(k)];
    }

    /** At the end: what you picked up that wasn't hers. */
    static List<String> reelLines(RunState s) {
        List<String> lines = new ArrayList<>();
        Mystery m = s.mystery();
        Student her = Roster.get(s.freeYandere());
        int level = Math.max(1, s.difficulty.ordinal());
        // If the other girl's matched one of hers word for word, there's no telling which you picked up.
        List<String> hers = List.of("A hair clip on the floor. " + her.hair().phrase(level),
                "A pencil case left by the shoe lockers. " + her.homeroom().phrase(level));
        for (int k = 0; k < DECOYS[s.difficulty.ordinal()]; k++) {
            Object[] t = decoyTemplate(s, m, k);
            String text = (String) t[2];
            if (!s.notes.contains(text) || hers.contains(text)) continue;
            lines.add("The " + (text.startsWith("A hair clip") ? "hair clip" : "pencil case") + " in "
                    + ((String) t[1]).replace("her club's room", "the club room") + " wasn't hers. It was "
                    + Roster.get(decoy(s)).firstName() + "'s. She'd been looking for it all week.");
        }
        return lines;
    }
}
