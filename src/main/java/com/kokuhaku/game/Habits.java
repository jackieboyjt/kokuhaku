package com.kokuhaku.game;

import com.kokuhaku.game.Roster.Personality;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * She isn't the same girl every run. Who she is decides how she goes about it,
 * and she can't help writing the way she talks, so a careful reader can start
 * to guess what kind of girl they're looking for before any clue says so.
 * Someone else likes you too, though, and she has habits of her own.
 */
final class Habits {
    private static final int FIRST_NIGHT = 2;
    private static final float NIGHT_CHANCE = 0.2f, WATCHES_MORE = 1.6f, KILLS_MORE = 1.5f, RUNS_FASTER = 1.22f;
    private static final float BORROWS = 0.25f;
    private static final Random RANDOM = new Random();

    private static final Map<Personality, List<String>> NOTES = Map.of(
            Personality.CHEERFUL, List.of("Good morning!! I hope you slept well! I watched to make sure!", "I made you something! Don't be scared of it!", "Today's going to be a good day. I've decided. For both of us."),
            Personality.SHY, List.of("S-sorry. I only looked for a little while.", "You said hello to someone near me. I wrote it down. I'm sorry.", "I'm behind you more than you think. Please don't turn round. I couldn't bear it."),
            Personality.COLD, List.of("Stop asking about me.", "You don't need them.", "I don't like repeating myself. Sleep."),
            Personality.GOSSIP, List.of("Do you know what they say about you when you leave the room? I do. I know everything.", "Nobody tells you anything. I'd tell you everything.", "Everyone's talking about you. I made sure of it."),
            Personality.POLITE, List.of("Please forgive the intrusion. Your window latch wanted oiling.", "I hope you don't mind. I folded your clothes.", "Do take care on the way home. I shall be seeing to it."),
            Personality.SPORTY, List.of("You're slower than last week. Are you eating?", "I ran your route this morning. Eleven minutes. You could do better.", "You can't outrun me. It's sweet that you still try."));

    private static final Map<Personality, String> KINDS = Map.of(
            Personality.CHEERFUL, "She smiled all the way through. She thought she was being kind.",
            Personality.SHY, "She watched. Most of what she did to you, she did by looking.",
            Personality.COLD, "She never wasted a word, or anyone she didn't need.",
            Personality.GOSSIP, "She talked. By the end, half the school only knew what she'd told them.",
            Personality.POLITE, "She let herself in. She always wiped her feet.",
            Personality.SPORTY, "She ran your route every morning, to be sure of her time.");

    private Habits() {
    }

    private static Personality of(RunState s) {
        return Roster.get(s.freeYandere()).personality();
    }

    /** One of the modes where she's a known quantity, and none of this applies. */
    private static boolean setAside(RunState s) {
        return s.devoted || Living.active(s) || Kept.on(s);
    }

    /**
     * The girl who likes you and isn't her: the one waiting at the end of your
     * road if she's innocent, otherwise any innocent girl, fixed by the seed.
     * -1 when the run has gone somewhere this doesn't belong.
     */
    static int admirerOf(RunState s) {
        if (setAside(s)) return -1;
        int crush = Secrets.keeper(s, Secrets.Kind.CRUSH);
        if (crush >= 0 && !s.mystery().isYandere(crush)) return crush;
        Random r = new Random(s.seed ^ 0x41444D52L);
        List<Integer> girls = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(Math.max(1, s.party.size())); g++) if (!s.mystery().isYandere(g)) girls.add(g);
        return girls.isEmpty() ? -1 : girls.get(r.nextInt(girls.size()));
    }

    /** The admirer, while she's still free to admire anyone. */
    static int admirer(RunState s) {
        int a = admirerOf(s);
        return a < 0 || s.dead.contains(a) || s.caught.contains(a) ? -1 : a;
    }

    static float stalking(RunState s) {
        return of(s) == Personality.SHY ? WATCHES_MORE : 1f;
    }

    static float killing(RunState s) {
        return of(s) == Personality.COLD ? KILLS_MORE : 1f;
    }

    static float pace(RunState s) {
        return of(s) == Personality.SPORTY ? RUNS_FASTER : 1f;
    }

    /** A note in her own voice. Now and then in the admirer's: she'd noticed who else was looking. */
    static String note(RunState s) {
        int a = admirer(s);
        if (a >= 0 && Roster.get(a).personality() != of(s) && RANDOM.nextFloat() < BORROWS) {
            List<String> l = NOTES.get(Roster.get(a).personality());
            return l.get(RANDOM.nextInt(l.size()));
        }
        List<String> lines = NOTES.get(of(s));
        return lines.get(RANDOM.nextInt(lines.size()));
    }

    /** Whether that note was in the admirer's voice rather than her own. */
    static boolean borrowed(RunState s, String note) {
        int a = admirer(s);
        if (a < 0 || note == null) return false;
        Personality theirs = Roster.get(a).personality();
        return theirs != of(s) && NOTES.get(theirs).contains(note) && !NOTES.get(of(s)).contains(note);
    }

    static String kind(RunState s) {
        return KINDS.get(of(s));
    }

    /**
     * What happened at your door last night, for the morning, or nothing. One roll,
     * then her or the admirer evenly, so from your side of the door it looks the same.
     */
    static String night(RunState s) {
        if (setAside(s) || s.day < FIRST_NIGHT || RANDOM.nextFloat() > NIGHT_CHANCE) return "";
        int a = admirer(s);
        if (a >= 0 && RANDOM.nextBoolean()) return admirerNight(s, a);
        return act(s, of(s), true);
    }

    /** The admirer's night: it looks the same as hers, but the reel knows it wasn't. */
    private static String admirerNight(RunState s, int a) {
        String t = act(s, Roster.get(a).personality(), false);
        if (!t.isEmpty()) {
            s.admirerNights |= 1 << Math.min(30, s.day);
            s.markDirty();
        }
        return t;
    }

    /** For the reel, once her name's known: not all of it was her. */
    static String reelLine(RunState s) {
        int a = admirerOf(s);
        if (s.admirerNights == 0 || a < 0) return null;
        return "Not everything left at your door was hers. " + Roster.get(a).firstName() + " left some of it. She never signed any of it.";
    }

    /**
     * One night of a girl like this. hers: it was her, so it goes in the reel,
     * and the key she had cut means the lock won't hold.
     */
    private static String act(RunState s, Personality kind, boolean hers) {
        return switch (kind) {
            case CHEERFUL -> {
                s.gifts++;
                s.markDirty();
                if (hers) Shadow.saw(s, "She left sweets on your step, and waited to see you find them.");
                yield "There's a box of sweets on your doorstep, tied with a ribbon. There's no card.";
            }
            case SHY -> {
                if (hers) Shadow.saw(s, "She drew you through the window. It took her most of the night.");
                yield "Someone's drawn your face in the dust on the outside of your window.";
            }
            case GOSSIP -> {
                // The admirer doesn't talk about herself.
                int self = hers ? -1 : admirer(s);
                List<Integer> girls = new ArrayList<>();
                for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) if (!s.dead.contains(g) && !s.mystery().isYandere(g) && g != self) girls.add(g);
                if (girls.isEmpty()) yield "";
                int g = girls.get(RANDOM.nextInt(girls.size()));
                s.fondness[g] = Math.max(-5, s.fondness[g] - 1);
                s.markDirty();
                if (hers) Shadow.saw(s, "She told " + Roster.get(g).firstName() + " something about you that wasn't true.");
                yield Roster.get(g).firstName() + " has stopped answering your messages. Someone's been talking.";
            }
            case POLITE -> {
                // Either way the lock's been at: a broken lock alone mustn't say whose night it was.
                s.lockBrokenOn = s.day + 1;
                s.markDirty();
                if (hers) Shadow.saw(s, "She had a key cut, and put yours back exactly where she found it.");
                yield "Your room's been tidied. Your door key's been moved an inch to the left.";
            }
            case SPORTY -> {
                if (hers) Shadow.saw(s, "She ran past your house until she'd learned how long you take to wake.");
                yield "Someone ran past your house at three in the morning. Then back. Then past again.";
            }
            case COLD -> "";
        };
    }
}
