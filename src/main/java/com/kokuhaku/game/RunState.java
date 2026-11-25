package com.kokuhaku.game;

import com.kokuhaku.Difficulty;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Progress through one run, saved with the world. The mystery itself isn't
 * stored: it's rebuilt from the seed.
 */
public class RunState extends PersistentState {
    public enum Status { PLAYING, WON, LOST }

    /** False while the party is still in the menu room. */
    public boolean started = false;
    /** Everyone in the run, in the order they joined; their place here is their slot. */
    public final List<UUID> party = new ArrayList<>();
    public Difficulty difficulty = Difficulty.EASY;
    public long seed;
    /** How this run's clues are dealt. A save from before it was kept was dealt the old way. */
    public int mysteryVersion = Mystery.VERSION;
    public int day = 1;
    public int minute = Schedule.WAKE;
    /** The minute you went to bed last night: 0 if nobody did. */
    public int bedtime = 0;
    public int guessesLeft;
    public int yen = 1000;
    public Status status = Status.PLAYING;
    public boolean studentsSpawned = false;
    public boolean townBuilt = false;
    /** Index into Town.homes for each student, then each player from Roster.TOTAL; null until assigned. */
    public int[] homes = null;
    /** Clue ids in the order you found them. */
    public final Set<Integer> found = new LinkedHashSet<>();
    /** Students who've told you about themselves. */
    public final Set<Integer> known = new LinkedHashSet<>();
    public final Set<Integer> accused = new LinkedHashSet<>();
    /** Yanderes you've named correctly. */
    public final Set<Integer> caught = new LinkedHashSet<>();
    /** Students who have died. */
    public final Set<Integer> dead = new LinkedHashSet<>();
    /** Clues that can only be found at a crime scene now. */
    public final Set<Integer> atScene = new LinkedHashSet<>();
    /** Clues she made you give up. Nobody tells you them again. */
    public final Set<Integer> burned = new LinkedHashSet<>();
    /** What's still lying where it was left: crime-scene clues by id, and what she's dropped. Put back on Continue. */
    public final Map<BlockPos, Integer> evidence = new LinkedHashMap<>();
    public final Map<BlockPos, String> trail = new LinkedHashMap<>();
    /** How each girl feels about you, from -5 to 5. Notes in class move it. */
    public final int[] fondness = new int[Roster.TOTAL];
    /**
     * How she feels about you from what she did where you couldn't see who it was: the
     * texts, the bento, the dark. Kept apart from her fondness, so it never shows on her.
     */
    public int longing = 0;
    /** Each player's shoe locker, by slot. */
    public final int[] lockers = new int[Roster.MAX_PARTY];
    /** Wrong answers, absences and failed shifts. Enough of them and you're expelled. */
    public int strikes = 0;
    public boolean detention = false;
    /** Each player's front door, tonight, by slot. */
    public final boolean[] doorLocked = new boolean[Roster.MAX_PARTY];
    /** You can't work again before this day. */
    public int firedUntil = 0;
    /** The last day a teacher let you work off a strike, and how often you've asked them about Haruka. */
    public int madeUpOn = -2, harukaAsked = 0;
    /** Who paid your bill and means to collect, or -1; when her next note comes; how many she's sent. */
    public int blackmailer = -1, blackmailDue = 0, blackmailStage = 0;
    /** The day your bedroom lock doesn't work. */
    public int lockBrokenOn = 0;
    /** The mode where everyone knows who she is, and you only have to survive it; and whether you've said it today. */
    public boolean devoted = false, saidIt = false;
    /** Who you're going out with, or -1; since when; and the last day you went on a date. */
    public int dating = -1, datingSince = 0, dateOn = 0;
    /** The last girl you were seen with while going out with someone else, or -1, and the day. */
    public int seenWith = -1, seenWithDay = 0;
    /** What you've bought: nights of door chain left, sprays and boxes of sweets. */
    public int chains = 0, sprays = 0, gifts = 0;
    /** Nights the admirer left something at your door, one bit a day. */
    public int admirerNights = 0;
    /** Whose house you live in now, or -1. How many times she's caught you, and whether she's stopped forgiving it. */
    public int livingWith = -1, jealous = 0;
    public boolean bonk = false;
    /** Whoever made her jealous today. */
    public int livingVictim = -1;
    /** The girl the last note warned you off, until you go back to her: kept so a Continue still remembers. */
    public int warnedNow = -1;
    /** The last day something happened to you, and what's already happened. */
    public int scenarioDay = 0;
    public final Set<Integer> scenarios = new LinkedHashSet<>();
    /** Haruka's diary pages you've found. */
    public final Set<Integer> pages = new LinkedHashSet<>();
    /** Things in your bag that aren't part of the mystery's own clues. */
    public final List<String> notes = new ArrayList<>();
    /** The day each of those notes was written, or 0 for ones from before this was kept. */
    public final List<Integer> noteDays = new ArrayList<>();
    /**
     * Each player's suspect board, by slot then girl: their mark on her (0 none, 1 'Her?', 2 'Not sure',
     * 3 'Not her'), the day they last changed it, and a line to themselves (null for none). Only the bag reads these.
     */
    public final int[] marks = new int[Roster.MAX_PARTY * Roster.GIRLS], markedOn = new int[Roster.MAX_PARTY * Roster.GIRLS];
    public final String[] memos = new String[Roster.MAX_PARTY * Roster.GIRLS];
    /** How fed and watered each player is, by slot, 0 to 1000. */
    public final int[] food = new int[Roster.MAX_PARTY], water = new int[Roster.MAX_PARTY];
    /** What each player wears out of school, and the clothes they own (one bit each), by slot. */
    public final int[] outfit = new int[Roster.MAX_PARTY], wardrobe = new int[Roster.MAX_PARTY];
    /** Who's seen each player in what: one bit per outfit, by slot then student. */
    public final int[] seenOutfit = new int[Roster.MAX_PARTY * Roster.TOTAL];
    /** Getting to know a girl: how many days you've talked, the last of them, times you've hung out and the last, when you can ask her again, how often she's said no. */
    public final int[] talkDays = new int[Roster.TOTAL], lastTalk = new int[Roster.TOTAL], hangouts = new int[Roster.TOTAL], hungOutOn = new int[Roster.TOTAL], askAgain = new int[Roster.TOTAL], turnedDown = new int[Roster.TOTAL];
    /** The last night she leaves you alone after the fireworks. */
    public int festivalSafeUntil = 0;
    /** Everyone you've done a favour for. */
    public final Set<Integer> favoured = new LinkedHashSet<>();
    /** Everything she really did, for the reel at the end. */
    public final List<String> shadow = new ArrayList<>();
    /** The day each student died, or 0, so their friends still grieve after a Continue. */
    public final int[] diedOn = new int[Roster.TOTAL];
    /** Which days you spent time with each girl (one bit a day), and who she's told you to stay away from. */
    public final int[] daysWith = new int[Roster.TOTAL];
    public final Set<Integer> warnedAbout = new LinkedHashSet<>();
    /** The dead the teacher has already stopped at in the register, so each is only noticed once. */
    public final Set<Integer> registered = new LinkedHashSet<>();
    /** Lunch: school days in a row you've missed it, how often you've thanked Mrs. Mori, and what you've had, meal by meal. */
    public int lunchMissed = 0, lunchThanked = 0;
    public final int[] lunchOrdered = new int[4];
    /** The eateries: how often each player's eaten at each place, and what they had last (dish + 1, 0 for nothing), by slot then place. */
    public final int[] eatVisits = new int[3 * Roster.MAX_PARTY], lastDish = new int[3 * Roster.MAX_PARTY];
    /** The last day each player did a shift at each job, by job then slot; 0 for never. */
    public final int[] workedOn = new int[Job.values().length * Roster.MAX_PARTY];

    /** Fed and watered: where everyone starts the run. */
    public static final int FULL = 1000;

    {
        java.util.Arrays.fill(food, FULL);
        java.util.Arrays.fill(water, FULL);
    }

    private Mystery mystery;

    /** Every note is added just before this, so it dates whatever's new in the bag. */
    @Override
    public void markDirty() {
        while (noteDays.size() < notes.size()) noteDays.add(day);
        super.markDirty();
    }

    /** The yandere still out there, for the endings. */
    public int freeYandere() {
        return mystery().yanderes.stream().filter(y -> !caught.contains(y)).findFirst().orElse(mystery().yanderes.get(0));
    }

    /** Something she did where you couldn't see who it was. */
    public void longed(int change) {
        longing = Math.max(-5, Math.min(5, longing + change));
        markDirty();
    }

    /** How she really feels: what she shows you, and what she doesn't. */
    public int herFeeling() {
        return Math.max(-5, Math.min(5, fondness[freeYandere()] + longing));
    }

    public Mystery mystery() {
        if (mystery == null) {
            mystery = new Mystery(seed, difficulty, Math.max(1, party.size()), mysteryVersion);
        }
        return mystery;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("Started", started);
        NbtList members = new NbtList();
        for (UUID id : party) members.add(NbtHelper.fromUuid(id));
        nbt.put("Party", members);
        nbt.putInt("Difficulty", difficulty.ordinal());
        nbt.putLong("Seed", seed);
        nbt.putInt("MysteryVersion", mysteryVersion);
        nbt.putInt("Day", day);
        nbt.putInt("Minute", minute);
        nbt.putInt("Bedtime", bedtime);
        nbt.putInt("Guesses", guessesLeft);
        nbt.putInt("Yen", yen);
        nbt.putIntArray("Lockers", lockers);
        nbt.putIntArray("Fondness", fondness);
        nbt.putInt("Longing", longing);
        nbt.putInt("Strikes", strikes);
        nbt.putBoolean("Detention", detention);
        nbt.putInt("FiredUntil", firedUntil);
        nbt.putInt("MadeUpOn", madeUpOn);
        nbt.putInt("HarukaAsked", harukaAsked);
        nbt.putInt("Blackmailer", blackmailer);
        nbt.putInt("BlackmailDue", blackmailDue);
        nbt.putInt("BlackmailStage", blackmailStage);
        nbt.putInt("LockBroken", lockBrokenOn);
        nbt.putInt("FestivalSafe", festivalSafeUntil);
        nbt.putIntArray("Favoured", favoured.stream().mapToInt(Integer::intValue).toArray());
        NbtList seen = new NbtList();
        for (String line : shadow) seen.add(NbtString.of(line));
        nbt.put("Shadow", seen);
        nbt.putIntArray("DiedOn", diedOn);
        nbt.putIntArray("DaysWith", daysWith);
        nbt.putIntArray("WarnedAbout", warnedAbout.stream().mapToInt(Integer::intValue).toArray());
        nbt.putIntArray("Registered", registered.stream().mapToInt(Integer::intValue).toArray());
        nbt.putInt("LunchMissed", lunchMissed);
        nbt.putInt("LunchThanked", lunchThanked);
        nbt.putIntArray("LunchOrdered", lunchOrdered);
        nbt.putIntArray("EatVisits", eatVisits);
        nbt.putIntArray("LastDish", lastDish);
        nbt.putIntArray("WorkedOn", workedOn);
        nbt.putInt("LivingWith", livingWith);
        nbt.putInt("Jealous", jealous);
        nbt.putBoolean("Bonk", bonk);
        nbt.putInt("Chains", chains);
        nbt.putInt("Sprays", sprays);
        nbt.putInt("Gifts", gifts);
        nbt.putInt("AdmirerNights", admirerNights);
        nbt.putInt("Dating", dating);
        nbt.putInt("DatingSince", datingSince);
        nbt.putInt("DateOn", dateOn);
        nbt.putInt("SeenWith", seenWith);
        nbt.putInt("SeenWithDay", seenWithDay);
        nbt.putBoolean("Devoted", devoted);
        nbt.putBoolean("SaidIt", saidIt);
        nbt.putInt("LivingVictim", livingVictim);
        nbt.putInt("WarnedNow", warnedNow);
        nbt.putInt("ScenarioDay", scenarioDay);
        nbt.putIntArray("Scenarios", scenarios.stream().mapToInt(Integer::intValue).toArray());
        nbt.putIntArray("Pages", pages.stream().mapToInt(Integer::intValue).toArray());
        NbtList written = new NbtList();
        for (String note : notes) written.add(NbtString.of(note));
        nbt.put("Notes", written);
        nbt.putIntArray("NoteDays", noteDays.stream().mapToInt(Integer::intValue).toArray());
        nbt.putIntArray("Marks", marks);
        nbt.putIntArray("MarkedOn", markedOn);
        NbtList lines = new NbtList();
        for (int i = 0; i < memos.length; i++) {
            if (memos[i] == null) continue;
            NbtCompound c = new NbtCompound();
            c.putInt("Index", i);
            c.putString("Text", memos[i]);
            lines.add(c);
        }
        nbt.put("Memos", lines);
        nbt.putIntArray("Food", food);
        nbt.putIntArray("Water", water);
        nbt.putIntArray("Outfit", outfit);
        nbt.putIntArray("Wardrobe", wardrobe);
        nbt.putIntArray("SeenOutfit", seenOutfit);
        nbt.putIntArray("TalkDays", talkDays);
        nbt.putIntArray("LastTalk", lastTalk);
        nbt.putIntArray("Hangouts", hangouts);
        nbt.putIntArray("HungOutOn", hungOutOn);
        nbt.putIntArray("AskAgain", askAgain);
        nbt.putIntArray("TurnedDown", turnedDown);
        nbt.putIntArray("Dead", dead.stream().mapToInt(Integer::intValue).toArray());
        nbt.putIntArray("AtScene", atScene.stream().mapToInt(Integer::intValue).toArray());
        nbt.putIntArray("Burned", burned.stream().mapToInt(Integer::intValue).toArray());
        NbtList scenes = new NbtList();
        for (Map.Entry<BlockPos, Integer> e : evidence.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putLong("Pos", e.getKey().asLong());
            c.putInt("Clue", e.getValue());
            scenes.add(c);
        }
        nbt.put("Evidence", scenes);
        NbtList dropped = new NbtList();
        for (Map.Entry<BlockPos, String> e : trail.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putLong("Pos", e.getKey().asLong());
            c.putString("Text", e.getValue());
            dropped.add(c);
        }
        nbt.put("Trail", dropped);
        nbt.putInt("Status", status.ordinal());
        nbt.putBoolean("Spawned", studentsSpawned);
        nbt.putBoolean("TownBuilt", townBuilt);
        if (homes != null) nbt.putIntArray("Homes", homes);
        nbt.putIntArray("Found", found.stream().mapToInt(Integer::intValue).toArray());
        nbt.putIntArray("Known", known.stream().mapToInt(Integer::intValue).toArray());
        nbt.putIntArray("Accused", accused.stream().mapToInt(Integer::intValue).toArray());
        nbt.putIntArray("Caught", caught.stream().mapToInt(Integer::intValue).toArray());
        return nbt;
    }

    public static RunState fromNbt(NbtCompound nbt) {
        RunState s = new RunState();
        s.read(nbt);
        return s;
    }

    /** Everything from a saved run, over whatever this one held. */
    void read(NbtCompound nbt) {
        RunState s = this;
        for (java.util.Collection<?> c : List.of(party, found, known, accused, caught, dead, atScene, burned, scenarios, pages, notes, favoured, shadow, warnedAbout, registered)) c.clear();
        evidence.clear();
        trail.clear();
        java.util.Arrays.fill(fondness, 0);
        for (int[] a : new int[][]{outfit, wardrobe, seenOutfit, talkDays, lastTalk, hangouts, hungOutOn, askAgain, turnedDown, lunchOrdered, eatVisits, lastDish, workedOn, diedOn, daysWith, marks, markedOn}) java.util.Arrays.fill(a, 0);
        java.util.Arrays.fill(memos, null);
        noteDays.clear();
        java.util.Arrays.fill(food, FULL);
        java.util.Arrays.fill(water, FULL);
        java.util.Arrays.fill(lockers, 0);
        java.util.Arrays.fill(doorLocked, false);
        mystery = null;
        s.started = nbt.getBoolean("Started");
        for (NbtElement id : nbt.getList("Party", NbtElement.INT_ARRAY_TYPE)) s.party.add(NbtHelper.toUuid(id));
        s.difficulty = Difficulty.values()[nbt.getInt("Difficulty")];
        s.seed = nbt.getLong("Seed");
        s.mysteryVersion = nbt.contains("MysteryVersion") ? nbt.getInt("MysteryVersion") : 0;
        s.day = nbt.getInt("Day");
        s.minute = nbt.getInt("Minute");
        s.bedtime = nbt.contains("Bedtime") ? nbt.getInt("Bedtime") : 0;
        s.guessesLeft = nbt.getInt("Guesses");
        s.yen = nbt.getInt("Yen");
        int[] fond = nbt.getIntArray("Fondness");
        System.arraycopy(fond, 0, s.fondness, 0, Math.min(fond.length, s.fondness.length));
        // Older saves don't have it: what she did then is already in her fondness.
        s.longing = nbt.getInt("Longing");
        int[] lockers = nbt.getIntArray("Lockers");
        System.arraycopy(lockers, 0, s.lockers, 0, Math.min(lockers.length, s.lockers.length));
        s.strikes = nbt.getInt("Strikes");
        s.detention = nbt.getBoolean("Detention");
        s.firedUntil = nbt.getInt("FiredUntil");
        s.madeUpOn = nbt.contains("MadeUpOn") ? nbt.getInt("MadeUpOn") : -2;
        s.harukaAsked = nbt.getInt("HarukaAsked");
        s.blackmailer = nbt.contains("Blackmailer") ? nbt.getInt("Blackmailer") : -1;
        s.blackmailDue = nbt.getInt("BlackmailDue");
        s.blackmailStage = nbt.getInt("BlackmailStage");
        s.lockBrokenOn = nbt.getInt("LockBroken");
        // Older saves don't have these: no safe nights, nobody helped, nothing seen, lunch from scratch.
        s.festivalSafeUntil = nbt.getInt("FestivalSafe");
        for (int i : nbt.getIntArray("Favoured")) s.favoured.add(i);
        for (NbtElement line : nbt.getList("Shadow", NbtElement.STRING_TYPE)) s.shadow.add(line.asString());
        // Older saves don't have these either: nobody grieving, no girl you've been warned off.
        copy(nbt, "DiedOn", diedOn);
        copy(nbt, "DaysWith", daysWith);
        for (int i : nbt.getIntArray("WarnedAbout")) s.warnedAbout.add(i);
        s.lunchMissed = nbt.getInt("LunchMissed");
        s.lunchThanked = nbt.getInt("LunchThanked");
        copy(nbt, "LunchOrdered", lunchOrdered);
        // Older saves don't have these: nobody's a regular anywhere yet.
        copy(nbt, "EatVisits", eatVisits);
        copy(nbt, "LastDish", lastDish);
        // Older saves don't have it: nobody's done a shift today.
        copy(nbt, "WorkedOn", workedOn);
        s.livingWith = nbt.contains("LivingWith") ? nbt.getInt("LivingWith") : -1;
        s.jealous = nbt.getInt("Jealous");
        s.bonk = nbt.getBoolean("Bonk");
        s.chains = nbt.getInt("Chains");
        s.sprays = nbt.getInt("Sprays");
        s.gifts = nbt.getInt("Gifts");
        s.admirerNights = nbt.getInt("AdmirerNights");
        s.dating = nbt.contains("Dating") ? nbt.getInt("Dating") : -1;
        s.datingSince = nbt.getInt("DatingSince");
        s.dateOn = nbt.getInt("DateOn");
        // Older saves don't have these: nobody's seen you with anyone.
        s.seenWith = nbt.contains("SeenWith") ? nbt.getInt("SeenWith") : -1;
        s.seenWithDay = nbt.getInt("SeenWithDay");
        s.devoted = nbt.getBoolean("Devoted");
        s.saidIt = nbt.getBoolean("SaidIt");
        s.livingVictim = nbt.contains("LivingVictim") ? nbt.getInt("LivingVictim") : -1;
        s.warnedNow = nbt.contains("WarnedNow") ? nbt.getInt("WarnedNow") : -1;
        s.scenarioDay = nbt.getInt("ScenarioDay");
        for (int i : nbt.getIntArray("Scenarios")) s.scenarios.add(i);
        for (int i : nbt.getIntArray("Pages")) s.pages.add(i);
        for (NbtElement note : nbt.getList("Notes", NbtElement.STRING_TYPE)) s.notes.add(note.asString());
        // Older saves don't have these: the notes go undated, and nobody's marked anyone.
        for (int d : nbt.getIntArray("NoteDays")) s.noteDays.add(d);
        while (s.noteDays.size() < s.notes.size()) s.noteDays.add(0);
        while (s.noteDays.size() > s.notes.size()) s.noteDays.remove(s.noteDays.size() - 1);
        copy(nbt, "Marks", marks);
        copy(nbt, "MarkedOn", markedOn);
        for (NbtElement e : nbt.getList("Memos", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            int i = c.getInt("Index");
            if (i >= 0 && i < memos.length) memos[i] = c.getString("Text");
        }
        copy(nbt, "Food", food);
        copy(nbt, "Water", water);
        copy(nbt, "Outfit", outfit);
        copy(nbt, "Wardrobe", wardrobe);
        copy(nbt, "SeenOutfit", seenOutfit);
        copy(nbt, "TalkDays", talkDays);
        copy(nbt, "LastTalk", lastTalk);
        copy(nbt, "Hangouts", hangouts);
        copy(nbt, "HungOutOn", hungOutOn);
        copy(nbt, "AskAgain", askAgain);
        copy(nbt, "TurnedDown", turnedDown);
        // A save from before this was kept: give her the day's grace she'd have had.
        if (!nbt.contains("LastTalk") && s.dating >= 0 && s.dating < lastTalk.length) lastTalk[s.dating] = s.day;
        for (int i : nbt.getIntArray("Dead")) s.dead.add(i);
        for (int i : nbt.getIntArray("AtScene")) s.atScene.add(i);
        // Older saves don't have it: nothing was given up for good.
        for (int i : nbt.getIntArray("Burned")) s.burned.add(i);
        // Older saves don't have these: whatever was lying about then is gone, as it was.
        for (NbtElement e : nbt.getList("Evidence", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            s.evidence.put(BlockPos.fromLong(c.getLong("Pos")), c.getInt("Clue"));
        }
        for (NbtElement e : nbt.getList("Trail", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            s.trail.put(BlockPos.fromLong(c.getLong("Pos")), c.getString("Text"));
        }
        s.status = Status.values()[nbt.getInt("Status")];
        s.studentsSpawned = nbt.getBoolean("Spawned");
        s.townBuilt = nbt.getBoolean("TownBuilt");
        if (nbt.contains("Homes")) s.homes = nbt.getIntArray("Homes");
        for (int i : nbt.getIntArray("Found")) s.found.add(i);
        for (int i : nbt.getIntArray("Known")) s.known.add(i);
        for (int i : nbt.getIntArray("Accused")) s.accused.add(i);
        for (int i : nbt.getIntArray("Caught")) s.caught.add(i);
        // Older saves don't have it: the teacher has already stopped at everyone who was dead then.
        if (nbt.contains("Registered")) {
            for (int i : nbt.getIntArray("Registered")) s.registered.add(i);
        } else {
            s.registered.addAll(s.dead);
        }
    }

    /** A saved array over this one, if the save has it: older saves keep the defaults. */
    private static void copy(NbtCompound nbt, String key, int[] into) {
        if (!nbt.contains(key)) return;
        int[] saved = nbt.getIntArray(key);
        System.arraycopy(saved, 0, into, 0, Math.min(saved.length, into.length));
    }
}
