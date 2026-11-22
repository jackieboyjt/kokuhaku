package com.kokuhaku.game;

import com.kokuhaku.Difficulty;
import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.game.Traits.Club;
import com.kokuhaku.game.Traits.District;
import com.kokuhaku.game.Traits.Phrased;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;

/**
 * One run's mystery, rebuilt identically from (seed, difficulty, party size):
 * who they are, every student's club and neighbourhood, and every clue with
 * who holds it and the day it becomes available.
 *
 * Solving is deduction: each trait clue is true of one yandere, and together
 * a yandere's clues single her out. There's one yandere per player; with more
 * than one, every clue says which of them it's about.
 */
public final class Mystery {
    public enum Kind { NOTE, TRAIT, HERRING, HINT, ALIBI }

    /** witness = student id who tells you, or -1 if it comes to you. about = girl id for herrings/hints. */
    public record Clue(int id, Kind kind, String text, int witness, int day, int about) {
    }

    private record Fact(Phrased value, IntPredicate matches) {
    }

    /** The last clue a difficulty hands out: you can't be sure before about this day. */
    private static final int[] LAST_CLUE_DAY = {7, 11, 13, 14};
    private static final String[] WHICH = {"The first girl: ", "The second girl: ", "The third girl: "};
    /** Someone vouches for an innocent girl. Fewer of them on harder difficulties. */
    private static final int[] ALIBIS = {7, 5, 3, 2};
    private static final String[] ALIBI_LINES = {
            "%s was at my house the night the note appeared. It wasn't her.",
            "%s has been helping at the clinic every evening. She's never around after dark.",
            "%s couldn't have written that note. Her handwriting's nothing like it.",
            "%s was away that whole week, visiting her grandmother.",
            "%s told me she likes someone else. I've seen them together.",
            "%s is scared of the dark. She gets picked up from school every day.",
            "I walk home with %s every night. She goes straight indoors.",
            "%s broke her wrist last month. She can't even carry her own bag.",
            "%s hardly knows who you are. She had to ask me your name.",
            "%s was in the nurse's office the whole time. I was there too.",
    };
    /** What turns up in your locker as the days go on. */
    private static final String[] LOCKER_NOTES = {
            "Another note: \"You looked tired today. Are you sleeping?\"",
            "Another note: \"I saw who you were talking to.\"",
            "Another note: \"You walk the long way home now. Why?\"",
            "Another note, in the same neat hand: \"I know you've been asking about me.\"",
            "Another note: \"Your curtains were open last night.\"",
            "Another note: \"Not long now.\"",
    };
    static final String[] HERRINGS = {
            "%s has been asking everyone which locker is yours.",
            "%s was crying behind the gym after you talked to another girl.",
            "%s keeps a photo in her pencil case. Nobody's seen who's in it.",
            "%s left school early the day the note appeared.",
            "%s wrote something on her hand and hid it when you walked past.",
            "%s knows your address. Nobody remembers telling her.",
    };
    /** More of the same, for runs dealt since version 1. HERRINGS stays as it was, so older runs rebuild exactly. */
    static final String[] HERRINGS_MORE = {
            "%s has your timetable copied into the back of her planner.",
            "%s asked the office for the class contact list. Only your number was ticked.",
            "%s was in the library the evening someone tore pages out of the old yearbook.",
            "%s has been walking past your house. She doesn't live that way.",
            "%s stayed behind in your classroom after everyone left. Nobody knows why.",
            "%s keeps a spare pair of indoor shoes in her bag. Your size.",
    };
    /** Every herring, the old ones first: an index here means the same thing in any run. */
    static final String[] ALL_HERRINGS = java.util.stream.Stream.concat(
            java.util.Arrays.stream(HERRINGS), java.util.Arrays.stream(HERRINGS_MORE)).toArray(String[]::new);
    /** What was really going on, for each of ALL_HERRINGS. */
    static final String[] HERRING_TRUTH = {
            "%s wanted to leave a birthday card in your locker. She never worked up the nerve.",
            "%s was crying behind the gym because her parents are splitting up.",
            "The photo in %s's pencil case was of her dog.",
            "%s left early that day for the dentist.",
            "What %s wrote on her hand was a phone number. Not yours.",
            "%s knows your address because her aunt lives three doors down.",
            "%s copies everyone's timetable. She's terrified of being in the wrong room.",
            "%s ticked your number by mistake. She wanted the girl above you.",
            "%s tore those pages out herself. There was a photo of her from before.",
            "%s walks past your house because her grandmother is in the home at the end of your road.",
            "%s stayed behind to cry where nobody would walk in.",
            "The spare shoes in %s's bag were her brother's.",
    };
    /** Each of ALL_HERRINGS as it goes round the class: half heard, and no better for it. */
    static final String[] HERRING_RUMOUR = {
            "...apparently %s keeps asking which locker's yours...",
            "...%s was crying behind the gym again. Over a boy, apparently...",
            "...%s won't let anyone see what's in her pencil case. Anyone...",
            "...%s went home early that day. Didn't say why...",
            "...%s had something written on her hand. She rubbed it off when I looked...",
            "...%s knows where everyone lives. It's creepy...",
            "...%s copies everyone's timetable. Everyone's...",
            "...%s was at the office asking for phone numbers...",
            "...someone tore up the old yearbook. %s was in the library that night...",
            "...%s walks the long way home. Past the new kid's house...",
            "...%s stays in the classroom after everyone's gone. Just sits there...",
            "...%s carries spare shoes around. Who does that?...",
    };
    /** More locker notes for version 1 runs. LOCKER_NOTES stays as it was. */
    static final String[] LOCKER_MORE = {
            "Another note: \"You hum when you think nobody's listening.\"",
            "Another note: \"I sewed the loose button back on your blazer. You didn't notice.\"",
            "Another note: \"Don't take the shortcut by the river. Please.\"",
            "Another note: \"Your shoelace was undone all through third period.\"",
            "Another note: \"I know which window is yours.\"",
            "Another note: \"Everyone's being so kind to you lately. I don't like it.\"",
    };
    /** Alibis from people who aren't quite sure. Nearly all of them are true. */
    private static final String[] HEDGED_LINES = {
            "%s was at my house the night the note appeared. I think. I'd fallen asleep by nine.",
            "I'm nearly sure %s went straight home that night. Nearly.",
            "%s says she was at cram school. I didn't see her, but it's a big room.",
            "%s was on the phone to me half the evening. Well. Some of it.",
            "My mum says %s's light was on all night. She'd have seen her leave. Probably.",
            "%s was with me at the station. Until my train came, anyway.",
            "I don't think %s could have. She's not like that. I don't think.",
            "%s was helping out at the shrine that evening. Someone said so.",
    };
    /** How many of the alibis are hedged, by difficulty. */
    private static final int[] HEDGED = {0, 1, 2, 2};
    /** Hard and up: the chance one hedged alibi is for her, and wrong. */
    private static final float FALSE_ALIBI = 0.08f;
    /** How clues are dealt. Saved with the run, so an older run is rebuilt the way it was dealt. */
    public static final int VERSION = 1;

    public final long seed;
    public final Difficulty difficulty;
    public final int party;
    public final List<Integer> yanderes;
    public final Club[] clubs = new Club[Roster.TOTAL];
    public final District[] districts = new District[Roster.TOTAL];
    public final List<Clue> clues;
    /** For each trait clue, which girls it could be true of. */
    private final java.util.Map<Integer, IntPredicate> tests = new java.util.HashMap<>();
    /** Which way the clues were dealt (see VERSION). */
    public final int version;
    /** Alibis the witness wasn't sure of. */
    private final java.util.Set<Integer> hedgedIds = new HashSet<>();
    /** The clue id of the alibi that's wrong, or -1. */
    public int falseAlibi = -1;

    public Mystery(long seed, Difficulty difficulty, int party, int version) {
        this.seed = seed;
        this.difficulty = difficulty;
        this.party = party;
        this.version = version;
        Random r = new Random(seed);

        int girls = Roster.girlsFor(party);
        assign(r, 0, girls);
        assign(r, Roster.GIRLS, Roster.GIRLS + Roster.boysFor(party));
        List<Integer> shuffled = new ArrayList<>();
        for (int g = 0; g < girls; g++) shuffled.add(g);
        Collections.shuffle(shuffled, r);
        this.yanderes = List.copyOf(shuffled.subList(0, party));
        // Each full profile must be unique, or no set of clues could single her out.
        for (int pick : yanderes) {
            for (int tries = 0; !isUnique(pick) && tries < 200; tries++) {
                clubs[pick] = Club.values()[r.nextInt(Club.values().length)];
                districts[pick] = District.values()[r.nextInt(District.values().length)];
            }
        }
        this.clues = version >= 1 ? buildCluesV1(r) : buildCluesV0(r);
    }

    /** Something that's true of somebody, and isn't of her. It sounds exactly like the real thing. */
    String falseRumour(Random r) {
        int yandere = yanderes.get(0), level = difficulty.ordinal();
        List<Integer> others = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(party); g++) if (!isYandere(g)) others.add(g);
        Collections.shuffle(others, r);
        for (int other : others) {
            List<Fact> facts = factsAbout(other);
            Collections.shuffle(facts, r);
            for (Fact f : facts) if (!f.matches.test(yandere)) return f.value.phrase(level);
        }
        return "She doesn't go home after school.";
    }

    /** Something that's true of her. On Impossible, only what her own clues have said by now, so it adds nothing. */
    String trueRumour(Random r, int day) {
        int yandere = yanderes.get(0), level = difficulty.ordinal();
        List<Fact> facts = factsAbout(yandere);
        Collections.shuffle(facts, r);
        for (Fact f : facts) {
            String said = f.value.phrase(level);
            if (difficulty != Difficulty.IMPOSSIBLE || clues.stream().anyMatch(c -> c.kind() == Kind.TRAIT
                    && c.about() == yandere && c.day() <= day && c.text().endsWith(said))) return said;
        }
        return falseRumour(r);
    }

    public boolean isYandere(int student) {
        return yanderes.contains(student);
    }

    /** Whether whoever gave this alibi wasn't sure of it. */
    public boolean hedged(int clueId) {
        return hedgedIds.contains(clueId);
    }

    /** Which of ALL_HERRINGS this text is, said about this girl, or -1. */
    static int herringIndex(String text, String firstName) {
        for (int i = 0; i < ALL_HERRINGS.length; i++) {
            if (String.format(ALL_HERRINGS[i], firstName).equals(text)) return i;
        }
        return -1;
    }

    public Clue clue(int id) {
        return clues.get(id);
    }

    /** Could she still be it, given the clues in your bag? With several of them, she only has to fit one's. */
    public boolean stillFits(int girl, java.util.Set<Integer> found) {
        for (int yandere : yanderes) {
            boolean fits = true;
            for (int id : found) {
                if (id >= clues.size() || !tests.containsKey(id) || clues.get(id).about() != yandere) continue;
                if (!tests.get(id).test(girl)) fits = false;
            }
            if (fits) return true;
        }
        return false;
    }

    /** The first day the trait clues dealt so far left only her (or only them) standing, or -1 if they never did. */
    public int knowableBy() {
        for (int d = 1; d <= Schedule.DAYS; d++) {
            java.util.Set<Integer> ids = new HashSet<>();
            for (Clue c : clues) if (c.kind() == Kind.TRAIT && c.day() <= d) ids.add(c.id());
            int count = 0;
            for (int g = 0; g < Roster.girlsFor(party); g++) if (stillFits(g, ids)) count++;
            if (count == yanderes.size()) return d;
        }
        return -1;
    }

    private void assign(Random r, int from, int to) {
        List<Club> c = new ArrayList<>();
        List<District> d = new ArrayList<>();
        for (int i = from; i < to; i++) {
            c.add(Club.values()[i % Club.values().length]);
            d.add(District.values()[i % District.values().length]);
        }
        Collections.shuffle(c, r);
        Collections.shuffle(d, r);
        for (int i = from; i < to; i++) {
            clubs[i] = c.get(i - from);
            districts[i] = d.get(i - from);
        }
    }

    private boolean isUnique(int girl) {
        for (int i = 0; i < Roster.girlsFor(party); i++) {
            if (i != girl && sameProfile(i, girl)) {
                return false;
            }
        }
        return true;
    }

    private boolean sameProfile(int a, int b) {
        Student x = Roster.get(a), y = Roster.get(b);
        return x.hair() == y.hair() && x.length() == y.length() && x.glasses() == y.glasses()
                && x.homeroom() == y.homeroom() && clubs[a] == clubs[b] && districts[a] == districts[b];
    }

    private List<Fact> factsAbout(int yandere) {
        Student her = Roster.get(yandere);
        List<Fact> facts = new ArrayList<>();
        facts.add(new Fact(her.hair(), g -> Roster.get(g).hair() == her.hair()));
        facts.add(new Fact(her.length(), g -> Roster.get(g).length() == her.length()));
        facts.add(new Fact(her.glasses(), g -> Roster.get(g).glasses() == her.glasses()));
        facts.add(new Fact(her.homeroom(), g -> Roster.get(g).homeroom() == her.homeroom()));
        facts.add(new Fact(clubs[yandere], g -> clubs[g] == clubs[yandere]));
        facts.add(new Fact(districts[yandere], g -> districts[g] == districts[yandere]));
        return facts;
    }

    /** How clues were dealt before version 1. Left exactly as it was, so older runs rebuild the same. */
    private List<Clue> buildCluesV0(Random r) {
        int level = difficulty.ordinal();
        int girls = Roster.girlsFor(party);
        List<Clue> out = new ArrayList<>();
        out.add(new Clue(0, Kind.NOTE, "A note in your locker: \"I love you.\"", -1, 1, -1));

        List<Integer> witnesses = new ArrayList<>(Roster.active(party));
        witnesses.removeAll(yanderes);
        Collections.shuffle(witnesses, r);
        int w = 0;

        int last = LAST_CLUE_DAY[level];
        for (int y = 0; y < yanderes.size(); y++) {
            int yandere = yanderes.get(y);
            // Pick facts greedily until only she fits; harder difficulties get no spare clues.
            List<Fact> facts = factsAbout(yandere);
            Collections.shuffle(facts, r);
            List<Fact> chosen = new ArrayList<>();
            List<Fact> spare = new ArrayList<>();
            List<Integer> candidates = new ArrayList<>();
            for (int g = 0; g < girls; g++) candidates.add(g);
            for (Fact f : facts) {
                if (candidates.size() > 1 && candidates.stream().anyMatch(g -> !f.matches.test(g))) {
                    chosen.add(f);
                    candidates.removeIf(g -> !f.matches.test(g));
                } else {
                    spare.add(f);
                }
            }
            int extras = switch (difficulty) {
                case EASY, MEDIUM -> spare.size();
                case HARD -> Math.min(1, spare.size());
                case IMPOSSIBLE -> 0;
            };
            chosen.addAll(spare.subList(0, extras));
            Collections.shuffle(chosen, r);

            String which = party > 1 ? WHICH[y] : "";
            for (int i = 0; i < chosen.size(); i++) {
                int first = 2 + level;
                int day = chosen.size() == 1 ? last : first + Math.round(i * (last - first) / (float) (chosen.size() - 1));
                tests.put(out.size(), chosen.get(i).matches);
                out.add(new Clue(out.size(), Kind.TRAIT, which + chosen.get(i).value.phrase(level), witnesses.get(w++), day, yandere));
            }
        }

        // Red herrings: true, suspicious, and about someone innocent.
        List<Integer> innocents = new ArrayList<>();
        for (int g = 0; g < girls; g++) if (!isYandere(g)) innocents.add(g);
        Collections.shuffle(innocents, r);
        List<String> herringText = new ArrayList<>(List.of(HERRINGS));
        Collections.shuffle(herringText, r);
        for (int i = 0; i < level; i++) {
            int about = innocents.get(i);
            String text = String.format(herringText.get(i), Roster.get(about).firstName());
            out.add(new Clue(out.size(), Kind.HERRING, text, witnesses.get(w++), 3 + r.nextInt(last - 2), about));
        }

        // Easy only: every third morning, a gut feeling rules someone out.
        if (difficulty.hints) {
            Collections.shuffle(innocents, r);
            for (int k = 1; k <= 5; k++) {
                int about = innocents.get(k - 1);
                out.add(new Clue(out.size(), Kind.HINT, "Gut feeling: it isn't " + Roster.get(about).firstName() + ".",
                        -1, k * 3, about));
            }
        }
        // Alibis: people swearing for the innocent, to narrow things down.
        List<String> alibis = new ArrayList<>(List.of(ALIBI_LINES));
        Collections.shuffle(alibis, r);
        Collections.shuffle(innocents, r);
        int vouched = Math.min(ALIBIS[level], Math.min(alibis.size(), innocents.size()));
        for (int i = 0; i < vouched && w < witnesses.size(); i++) {
            out.add(new Clue(out.size(), Kind.ALIBI, String.format(alibis.get(i), Roster.get(innocents.get(i)).firstName()),
                    witnesses.get(w++), 2 + r.nextInt(last - 1), innocents.get(i)));
        }

        // And now and then, something new in your locker.
        for (int i = 0; i < LOCKER_NOTES.length; i++) {
            out.add(new Clue(out.size(), Kind.NOTE, LOCKER_NOTES[i], -1, 3 + i * 2, -1));
        }
        return List.copyOf(out);
    }

    /** The first one that fits, or the first one left if nobody does; -1 when there's nobody. */
    private static int take(List<Integer> pool, IntPredicate ok) {
        for (int i = 0; i < pool.size(); i++) {
            if (ok.test(pool.get(i))) return pool.remove(i);
        }
        return pool.isEmpty() ? -1 : pool.remove(0);
    }

    /**
     * Version 1: anyone can know something, her included, and anyone can be the
     * subject of a herring. Some alibis are hedged, and on Hard and up one of
     * those, now and then, is for her.
     */
    private List<Clue> buildCluesV1(Random r) {
        int level = difficulty.ordinal();
        int girls = Roster.girlsFor(party);
        int last = LAST_CLUE_DAY[level];
        List<Clue> out = new ArrayList<>();
        out.add(new Clue(0, Kind.NOTE, "A note in your locker: \"I love you.\"", -1, 1, -1));

        List<Integer> pool = new ArrayList<>(Roster.active(party));
        Collections.shuffle(pool, r);

        for (int y = 0; y < yanderes.size(); y++) {
            int yandere = yanderes.get(y);
            // Pick facts greedily until only she fits; harder difficulties get no spare clues.
            List<Fact> facts = factsAbout(yandere);
            Collections.shuffle(facts, r);
            List<Fact> chosen = new ArrayList<>();
            List<Fact> spare = new ArrayList<>();
            List<Integer> candidates = new ArrayList<>();
            for (int g = 0; g < girls; g++) candidates.add(g);
            for (Fact f : facts) {
                if (candidates.size() > 1 && candidates.stream().anyMatch(g -> !f.matches.test(g))) {
                    chosen.add(f);
                    candidates.removeIf(g -> !f.matches.test(g));
                } else {
                    spare.add(f);
                }
            }
            int extras = switch (difficulty) {
                case EASY, MEDIUM -> spare.size();
                case HARD -> Math.min(1, spare.size());
                case IMPOSSIBLE -> 0;
            };
            chosen.addAll(spare.subList(0, extras));
            Collections.shuffle(chosen, r);

            String which = party > 1 ? WHICH[y] : "";
            for (int i = 0; i < chosen.size(); i++) {
                int first = 2 + level;
                int day = chosen.size() == 1 ? last : first + Math.round(i * (last - first) / (float) (chosen.size() - 1));
                // Nobody who tells you what she's like is her.
                int witness = take(pool, id -> !isYandere(id));
                if (witness < 0) continue;
                tests.put(out.size(), chosen.get(i).matches);
                out.add(new Clue(out.size(), Kind.TRAIT, which + chosen.get(i).value.phrase(level), witness, day, yandere));
            }
        }

        // Red herrings: true, suspicious, and about anyone at all.
        List<Integer> anyone = new ArrayList<>();
        for (int g = 0; g < girls; g++) anyone.add(g);
        Collections.shuffle(anyone, r);
        List<Integer> texts = new ArrayList<>();
        for (int i = 0; i < ALL_HERRINGS.length; i++) texts.add(i);
        Collections.shuffle(texts, r);
        for (int i = 0; i < level; i++) {
            final int about = anyone.get(i);
            int day = 3 + r.nextInt(last - 2);
            String text = String.format(ALL_HERRINGS[texts.get(i)], Roster.get(about).firstName());
            int witness = take(pool, id -> id != about);
            if (witness < 0) continue;
            out.add(new Clue(out.size(), Kind.HERRING, text, witness, day, about));
        }

        // Easy only: every third morning, a gut feeling rules someone out.
        if (difficulty.hints) {
            List<Integer> cleared = new ArrayList<>();
            for (int g = 0; g < girls; g++) if (!isYandere(g)) cleared.add(g);
            Collections.shuffle(cleared, r);
            for (int k = 1; k <= 5; k++) {
                int about = cleared.get(k - 1);
                out.add(new Clue(out.size(), Kind.HINT, "Gut feeling: it isn't " + Roster.get(about).firstName() + ".",
                        -1, Math.min(k * 3, Schedule.DAYS - 1), about));
            }
        }

        // Alibis: people swearing for someone. The last few aren't so sure of it.
        List<Integer> innocents = new ArrayList<>();
        for (int g = 0; g < girls; g++) if (!isYandere(g)) innocents.add(g);
        Collections.shuffle(innocents, r);
        List<String> firm = new ArrayList<>(List.of(ALIBI_LINES));
        Collections.shuffle(firm, r);
        List<String> hedgedLines = new ArrayList<>(List.of(HEDGED_LINES));
        Collections.shuffle(hedgedLines, r);
        int vouched = Math.min(ALIBIS[level], innocents.size());
        int h = Math.min(HEDGED[level], vouched);
        int falseAt = -1, falseYandere = -1;
        if (level >= Difficulty.HARD.ordinal() && h > 0 && r.nextFloat() < FALSE_ALIBI) {
            falseAt = vouched - h + r.nextInt(h);
            falseYandere = yanderes.get(r.nextInt(yanderes.size()));
        }
        for (int i = 0; i < vouched; i++) {
            final int subject = i == falseAt ? falseYandere : innocents.get(i);
            boolean isHedged = i >= vouched - h;
            String line = isHedged ? hedgedLines.get(i - (vouched - h)) : firm.get(i);
            int day = 2 + r.nextInt(last - 1);
            int witness = take(pool, id -> id != subject);
            if (witness < 0) continue;
            int id = out.size();
            out.add(new Clue(id, Kind.ALIBI, String.format(line, Roster.get(subject).firstName()), witness, day, subject));
            if (isHedged) hedgedIds.add(id);
            if (i == falseAt) falseAlibi = id;
        }

        // And now and then, something new in your locker. The last one is always the same.
        List<String> notes = new ArrayList<>(List.of(LOCKER_NOTES).subList(0, LOCKER_NOTES.length - 1));
        notes.addAll(List.of(LOCKER_MORE));
        Collections.shuffle(notes, r);
        for (int i = 0; i < 5; i++) {
            out.add(new Clue(out.size(), Kind.NOTE, notes.get(i), -1, 3 + i * 2, -1));
        }
        out.add(new Clue(out.size(), Kind.NOTE, LOCKER_NOTES[LOCKER_NOTES.length - 1], -1, 13, -1));
        return List.copyOf(out);
    }

    /** How many girls still about (not dead, caught or accused) this is true of. */
    public int sharedBy(Phrased value, RunState s) {
        int n = 0;
        for (int g = 0; g < Roster.girlsFor(party); g++) {
            if (s.dead.contains(g) || s.caught.contains(g) || s.accused.contains(g)) continue;
            Student x = Roster.get(g);
            Object theirs;
            if (value instanceof Traits.Hair) theirs = x.hair();
            else if (value instanceof Traits.Length) theirs = x.length();
            else if (value instanceof Traits.Homeroom) theirs = x.homeroom();
            else if (value instanceof Club) theirs = clubs[g];
            else if (value instanceof District) theirs = districts[g];
            else if (value instanceof Traits.Glasses) theirs = x.glasses();
            else theirs = null;
            if (value.equals(theirs)) n++;
        }
        return n;
    }

    /** Of what she might leave behind, something at least two other girls share, where there is one. */
    public Phrased trace(RunState s, List<Phrased> options, Random r) {
        List<Phrased> kept = new ArrayList<>();
        for (Phrased o : options) if (sharedBy(o, s) >= 3) kept.add(o);
        if (kept.isEmpty()) {
            int most = -1;
            for (Phrased o : options) {
                int n = sharedBy(o, s);
                if (n > most) {
                    most = n;
                    kept.clear();
                }
                if (n == most) kept.add(o);
            }
        }
        return kept.get(r.nextInt(kept.size()));
    }
}
