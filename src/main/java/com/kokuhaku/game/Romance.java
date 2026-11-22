package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Roster.Personality;
import com.kokuhaku.game.Roster.Student;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Asking someone out, which is as hard as it is anywhere. She has to know you: talk to her
 * on a good few different days, not just a lot on one. You hang out first, as friends. Ask
 * too soon and she says no, and everyone hears about it, and you can't ask again for days;
 * twice and that's that. Some girls only ever like you as a friend, however hard you try,
 * and nothing tells you which. Once you are together you have to keep talking to her, or
 * she'll end it.
 *
 * A girlfriend walks you in, tells you things, and makes the nights safer. She might be
 * the one you're looking for. If she isn't, someone's going to notice.
 */
final class Romance {
    /** What it takes before you can even ask: how much she likes you, and on how many days you've talked. */
    private static final int ASK_FROM = 2, DAYS_TO_ASK = 2;
    /** What a yes takes: days of talking, times you've hung out, how much she likes you. */
    private static final int DAYS_FOR_YES = 5, HANGOUTS_FOR_YES = 1, FONDNESS_FOR_YES = 4;
    /** Below this, she'll only ever see you as a friend. */
    private static final float FRIENDS_ONLY = 0.35f;
    /** After a no: days until you can ask again, and how many no's it takes to stop asking. */
    private static final int ASK_AGAIN_AFTER = 4, TURNED_DOWN_FOR_GOOD = 2;
    /** Two days without a word and she starts to wonder; this low and she's gone. */
    private static final int NEGLECTED_AFTER = 2, ENDS_IT_AT = 1;
    private static final int DATE_YEN = 500, DATE_MINUTES = 120, LATEST_DATE = DATE_MINUTES + 30, HANGOUT_MINUTES = 90, CREPES_YEN = 450;
    /** How long the dark lasts when a party's clock can't be spent on you: five seconds. */
    private static final int LATER_TICKS = 100;
    /** From this many nights in, she may decide your girlfriend's in the way. */
    private static final int JEALOUS_AFTER = 5;
    private static final float JEALOUS_CHANCE = 0.12f, SAFER = 0.5f;
    private static final Random RANDOM = new Random();

    static final String ASK = "Will you go out with me?", HANG = "Do you want to hang out sometime?", US = "About us...";

    private Romance() {
    }

    /** How she could feel about you, fixed for the run. Nothing in the game shows it. */
    private static float match(RunState s, int girl) {
        if (s.mystery().isYandere(girl)) return 1f;
        return new Random(s.seed ^ (girl + 1) * 0x9E3779B97F4A7C15L).nextFloat();
    }

    /** You stopped to talk to her: one more day she's known you, if it's a new day. */
    static void talked(RunState s, int girl) {
        if (s.lastTalk[girl] == s.day) return;
        s.lastTalk[girl] = s.day;
        s.talkDays[girl]++;
        s.markDirty();
    }

    static boolean canAsk(RunState s, Student who) {
        int g = who.id();
        return who.female() && !Living.active(s) && s.dating < 0 && s.fondness[g] >= ASK_FROM && s.talkDays[g] >= DAYS_TO_ASK
                && s.day >= s.askAgain[g] && s.turnedDown[g] < TURNED_DOWN_FOR_GOOD;
    }

    private static boolean canHangOut(RunState s, Student who) {
        int g = who.id();
        return who.female() && !Living.active(s) && s.dating != g && s.fondness[g] >= ASK_FROM && s.talkDays[g] >= DAYS_TO_ASK;
    }

    static boolean isDating(RunState s, Student who) {
        return s.dating == who.id();
    }

    static boolean datingHer(RunState s) {
        return s.dating >= 0 && s.mystery().isYandere(s.dating);
    }

    /** What your girlfriend does for your nights: she's the one you'd be taken by, or she's the one who walks you home. */
    static float nightChance(RunState s, float chance) {
        if (s.dating < 0) return chance;
        return datingHer(s) ? 0f : chance * SAFER;
    }

    /** Saying something kind: once a day to each girl, and each has one thing she really wants to hear. */
    static final String NICE = "Say something nice.";
    private static final String KIND = "You're really kind.", FUNNY = "You always make me laugh.", HARD_WORKING = "You work so hard.";
    private static final int[] NICE_ON = new int[Roster.TOTAL], HANG_ASKED_ON = new int[Roster.TOTAL], HANG_YES_ON = new int[Roster.TOTAL];
    private static final int FLUSTERED_TICKS = 90;
    /** How close your girlfriend has to be to see you being nice to someone else. */
    private static final double SEEN_NEAR = 16;

    static void clear() {
        java.util.Arrays.fill(NICE_ON, 0);
        java.util.Arrays.fill(HANG_ASKED_ON, 0);
        java.util.Arrays.fill(HANG_YES_ON, 0);
        devYes = -1;
    }

    private static String favourite(Personality p) {
        return switch (p) {
            case SHY, POLITE -> KIND;
            case CHEERFUL, GOSSIP -> FUNNY;
            case COLD, SPORTY -> HARD_WORKING;
        };
    }

    static void compliment(ServerPlayerEntity p, RunState s, Student who, StudentEntity her) {
        String name = who.firstName();
        if (NICE_ON[who.id()] == s.day) {
            Talk.say(p, name, "You already said that. ...You can stop looking so pleased with yourself.", Talk.leave("Sorry."));
            return;
        }
        NICE_ON[who.id()] = s.day;
        String wanted = favourite(who.personality());
        Talk.Option[] options = new Talk.Option[3];
        String[] all = {KIND, FUNNY, HARD_WORKING};
        for (int i = 0; i < all.length; i++) {
            String said = all[i];
            options[i] = Talk.option(said, q -> {
                boolean right = said.equals(wanted);
                // From someone she hardly knows, it's only words.
                if (right && s.talkDays[who.id()] >= DAYS_TO_ASK) s.fondness[who.id()] = Math.min(5, s.fondness[who.id()] + 1);
                // Your girlfriend, near enough to hear it.
                if (who.female() && s.dating >= 0 && s.dating != who.id() && s.seenWithDay != s.day && girlfriendNear(q, s)) seenWith(s, who.id());
                s.markDirty();
                Kokuhaku.LOG.info("Complimented {}: {} ({})", name, said, right ? "flustered" : "polite");
                if (right && her != null) her.fluster(FLUSTERED_TICKS);
                Talk.say(q, name, right ? flustered(who.personality()) : polite(who.personality()), Talk.leave("..."));
            });
        }
        Talk.say(p, name, "...Yes?", options);
    }

    /** Whether your girlfriend is close enough to see who you're talking to. */
    private static boolean girlfriendNear(ServerPlayerEntity p, RunState s) {
        for (StudentEntity e : RunManager.studentsOf(p.server)) {
            if (e.isAlive() && e.getSkin() == s.dating && e.getWorld() == p.getWorld() && e.squaredDistanceTo(p) <= SEEN_NEAR * SEEN_NEAR) return true;
        }
        return false;
    }

    /** Seen with another girl while you're going out with someone: once a day, it costs you a little with her. */
    private static void seenWith(RunState s, int girl) {
        if (s.dating < 0 || s.dating == girl || s.seenWithDay == s.day) return;
        s.seenWith = girl;
        s.seenWithDay = s.day;
        s.fondness[s.dating] = Math.max(-5, s.fondness[s.dating] - 1);
        s.markDirty();
    }

    private static String flustered(Personality p) {
        return switch (p) {
            case CHEERFUL -> "Wh- you can't just say that! I'm the one who says things! ...Do I really?";
            case SHY -> "I... n-nobody's ever... um. I have to go. I'm not going. Thank you.";
            case COLD -> "...Don't. I'm not... that isn't... Look somewhere else for a second.";
            case GOSSIP -> "Oh. I... I don't have anything to say to that. That's never happened before.";
            case POLITE -> "That's... very kind of you. I'm afraid I've forgotten what I was going to say.";
            case SPORTY -> "Ha! Yeah, well. Whatever. It's hot out, that's why my face is like this.";
        };
    }

    private static String polite(Personality p) {
        return switch (p) {
            case CHEERFUL -> "Aw, thanks! You're nice!";
            case SHY -> "O-oh. Thank you.";
            case COLD -> "Is that so.";
            case GOSSIP -> "I'll be telling people you said that.";
            case POLITE -> "Thank you. That's kind of you to say.";
            case SPORTY -> "Thanks, I guess!";
        };
    }

    /** The main line in her dialogue, if there is one: asking her out, or talking about the two of you. */
    static String option(RunState s, Student who) {
        if (canAsk(s, who)) return ASK;
        return isDating(s, who) ? US : null;
    }

    /** Every romantic line she'll hear from you today, in order. */
    static List<String> options(RunState s, Student who) {
        List<String> lines = new ArrayList<>();
        // Not your girlfriend (she has dates), but anyone else, girlfriend or no; she'll hear about it.
        if (canHangOut(s, who)) lines.add(HANG);
        String main = option(s, who);
        if (main != null) lines.add(main);
        return lines;
    }

    static boolean handles(String line) {
        return ASK.equals(line) || HANG.equals(line) || US.equals(line);
    }

    static void choose(ServerPlayerEntity p, RunState s, Student who, String line) {
        if (HANG.equals(line)) hangOut(p, s, who);
        else talk(p, s, who);
    }

    static void talk(ServerPlayerEntity p, RunState s, Student who) {
        if (canAsk(s, who)) ask(p, s, who);
        else if (isDating(s, who)) together(p, s, who);
    }

    /** Whether she'd say yes, if you asked now. */
    private static boolean wouldSayYes(ServerPlayerEntity p, RunState s, Student who) {
        int g = who.id();
        if (g == devYes) return true;
        if (match(s, g) < FRIENDS_ONLY) return false;
        if (s.talkDays[g] < DAYS_FOR_YES || s.hangouts[g] < HANGOUTS_FOR_YES || s.fondness[g] < FONDNESS_FOR_YES) return false;
        float score = s.fondness[g] + Math.min(8, s.talkDays[g]) * 0.5f + Math.min(3, s.hangouts[g]) * 1.2f + (Clothes.dressedUp(s, p) ? 0.5f : 0f);
        return score >= 9f + RANDOM.nextFloat() * 1.5f;
    }

    private static void ask(ServerPlayerEntity p, RunState s, Student who) {
        if (!wouldSayYes(p, s, who)) {
            turnedDown(p, s, who);
            return;
        }
        s.dating = who.id();
        s.datingSince = s.day;
        // Whoever you were seen with before her is someone else's business.
        s.seenWith = -1;
        s.lastTalk[who.id()] = s.day;
        s.known.add(who.id());
        s.fondness[who.id()] = Math.min(5, s.fondness[who.id()] + 1);
        s.markDirty();
        Kokuhaku.LOG.info("Now going out with {}", who.firstName());
        Talk.say(p, who.firstName(), yes(who.personality()), Talk.leave("..."));
    }

    /** No. It's awkward for days, and by lunch everybody knows. */
    private static void turnedDown(ServerPlayerEntity p, RunState s, Student who) {
        int g = who.id();
        boolean friend = match(s, g) < FRIENDS_ONLY;
        // Before the fondness drops: did you ask too soon, or just not yet?
        boolean early = s.talkDays[g] < DAYS_FOR_YES || s.hangouts[g] < HANGOUTS_FOR_YES || s.fondness[g] < FONDNESS_FOR_YES;
        s.turnedDown[g]++;
        s.askAgain[g] = s.day + ASK_AGAIN_AFTER;
        s.fondness[g] = Math.max(-5, s.fondness[g] - 2);
        s.markDirty();
        Kokuhaku.LOG.info("{} said no ({} time{}, friends only: {})", who.firstName(), s.turnedDown[g], s.turnedDown[g] == 1 ? "" : "s", friend);
        String line = friend ? "I really like you. Just... not like that. I'm sorry. Can we still be friends?" : early ? no(who.personality()) : notYet(who.personality());
        if (s.turnedDown[g] >= TURNED_DOWN_FOR_GOOD) line = "You asked me already. The answer's the same. Please don't ask again.";
        Talk.say(p, who.firstName(), line, Talk.leave("...Okay."));
        // The gossip is about today. If the night has come first, it's old news.
        int day = s.day;
        boolean school = Schedule.isSchoolDay(s.day) && s.minute < Schedule.AFTER_SCHOOL + 30;
        MinecraftServer server = p.server;
        RunManager.later(20 * 60, () -> {
            if (s.day != day || s.status != RunState.Status.PLAYING) return;
            Phone.text(server, s, "Class 2-A", school ? "someone got turned down by the shoe lockers today. brutal" : "someone got turned down today. brutal");
        });
    }

    /** She knows you well enough. She just isn't there yet. */
    private static String notYet(Personality p) {
        return switch (p) {
            case CHEERFUL -> "I like you! I do! I just... need a bit longer. Is that okay?";
            case SHY -> "I... I'm glad it's you. But I'm not ready. S-sorry.";
            case COLD -> "No. ...Not now. Don't read anything into that.";
            case GOSSIP -> "Okay, I did not see that coming. I need to think. Don't tell anyone you asked.";
            case POLITE -> "I'm very fond of you. But I'd like a little more time, if you don't mind.";
            case SPORTY -> "Ha. Okay. Not yet, alright? Don't give up on me.";
        };
    }

    private static String no(Personality p) {
        return switch (p) {
            case CHEERFUL -> "Oh! Oh no. I mean- you're so nice! I just don't know you that well yet. Is that awful?";
            case SHY -> "I... I'm sorry. I c-can't. Not yet. Please don't be angry.";
            case COLD -> "No. We've barely spoken. Did you think that would work?";
            case GOSSIP -> "Wait, seriously? Here? No! I mean, no. Sorry. Oh, people are looking.";
            case POLITE -> "That's very kind of you. But I'm afraid I don't feel that way. Not yet, at least.";
            case SPORTY -> "Whoa. Uh. No? We've hung out like, never. Ask me again when I actually know you.";
        };
    }

    /** As friends, first: an hour and a half after school, if she's got the time. */
    private static void hangOut(ServerPlayerEntity p, RunState s, Student who) {
        int g = who.id();
        String name = who.firstName();
        boolean free = (!Schedule.isSchoolDay(s.day) || s.minute >= Schedule.AFTER_SCHOOL) && s.minute < RunManager.curfewOf(s) - HANGOUT_MINUTES - 30;
        if (!free) {
            Talk.say(p, name, s.minute < Schedule.AFTER_SCHOOL ? "Ask me after school. We'll get in trouble." : "It's late. Another day?", Talk.leave("Okay."));
            return;
        }
        if (s.hungOutOn[g] == s.day) {
            Talk.say(p, name, "We just did! ...Tomorrow, maybe.", Talk.leave("Okay."));
            return;
        }
        if (HANG_ASKED_ON[g] == s.day) {
            Talk.say(p, name, "I said I can't today. Sorry.", Talk.leave("Okay."));
            return;
        }
        // A yes holds for the day, so backing out and asking again doesn't turn it into a no.
        boolean yes = HANG_YES_ON[g] == s.day || s.fondness[g] >= 3 || s.fondness[g] >= ASK_FROM && match(s, g) >= 0.5f && RANDOM.nextBoolean();
        if (!yes) {
            HANG_ASKED_ON[g] = s.day;
            Talk.say(p, name, busy(who.personality()), Talk.leave("Another time."));
            return;
        }
        HANG_YES_ON[g] = s.day;
        Talk.say(p, name, "Sure. Where?",
                Talk.option("Crepes by the station (¥" + CREPES_YEN + ").", q -> {
                    if (s.yen < CREPES_YEN) {
                        hungOut(q, s, who, 0, () -> Talk.say(q, name, "You've not got enough. She pays, and says it's fine. It's a bit awkward.", Talk.leave("Sorry.")));
                        return;
                    }
                    s.yen -= CREPES_YEN;
                    Body.eat(q, s, 220, 80);
                    hungOut(q, s, who, 1, () -> Talk.say(q, name, "She gets strawberry and cream and makes you try it. You talk about nothing for an hour and a half.", Talk.leave("That was nice.")));
                }),
                Talk.option("A walk along the sea.", q ->
                        hungOut(q, s, who, 1, () -> Talk.say(q, name, "You walk to the end of the sea wall and back. She tells you about her family, a little.", Talk.leave("That was nice.")))),
                Talk.leave("Actually, never mind."));
    }

    private static void hungOut(ServerPlayerEntity p, RunState s, Student who, int fondness, Runnable then) {
        int g = who.id();
        if (Clothes.dressedUp(s, p)) fondness++;
        s.hungOutOn[g] = s.day;
        s.hangouts[g]++;
        s.fondness[g] = Math.min(5, s.fondness[g] + fondness);
        s.lastTalk[g] = s.day;
        seenWith(s, g);
        s.markDirty();
        Kokuhaku.LOG.info("Hung out with {}: {} times, fondness {}", who.firstName(), s.hangouts[g], s.fondness[g]);
        spend(p, s, HANGOUT_MINUTES, then);
    }

    /**
     * The time out with her. Alone, the clock runs on and you're told how long it took.
     * In a party the clock's shared, so, like a meal, it costs the others nothing: a moment's dark instead.
     */
    private static void spend(ServerPlayerEntity p, RunState s, int minutes, Runnable then) {
        if (s.party.size() == 1) {
            RunManager.skipMinutes(p.server, s, minutes, p);
            then.run();
            return;
        }
        RunManager.setBusy(p, true);
        RunManager.sendBlack(p, RunManager.homeFor(p, s), LATER_TICKS, "Later.");
        RunManager.later(LATER_TICKS, () -> {
            RunManager.setBusy(p, false);
            if (!p.isRemoved() && !RunManager.isOut(p) && s.status == RunState.Status.PLAYING) then.run();
        });
    }

    private static String busy(Personality p) {
        return switch (p) {
            case CHEERFUL -> "Aaa, I can't today! I've got a thing. Ask me again?";
            case SHY -> "Oh... um. I have to go home. S-sorry.";
            case COLD -> "I'm busy.";
            case GOSSIP -> "Today? Can't. I'm meeting people. Other people.";
            case POLITE -> "I'm terribly sorry, I've promised to help at home.";
            case SPORTY -> "Practice. Every day this week. Sorry!";
        };
    }

    private static void together(ServerPlayerEntity p, RunState s, Student who) {
        String name = who.firstName();
        boolean free = s.dateOn != s.day && (!Schedule.isSchoolDay(s.day) || s.minute >= Schedule.AFTER_SCHOOL) && s.minute < RunManager.curfewOf(s) - LATEST_DATE;
        // If she's heard who you were with, that comes first, date or no date.
        String seen = Sweethearts.seenOpener(s, who);
        if (!free) {
            Sweethearts.talk(p, s, who, seen != null ? seen : s.dateOn == s.day ? "I had a really nice time. Get home safe, okay?" : s.minute < Schedule.AFTER_SCHOOL ? "Hey, you. Quick, before the bell." : "It's getting late. Another day?",
                    Talk.option("I want to break up.", q -> breakUp(q, s, who)), Talk.leave("See you."));
            return;
        }
        Talk.say(p, name, (seen != null ? seen + "\n\n" : "") + "Do you want to do something? Just us, for a couple of hours.",
                Talk.option("Soba by the station (¥" + DATE_YEN + ").", q -> {
                    if (tooLate(q, s, who)) return;
                    // The purse is shared, so check it now, not when the menu opened.
                    if (s.yen < DATE_YEN) {
                        Talk.say(q, name, "You count your change. It's not enough. She says the sea's free.", Talk.leave("Sorry."));
                        return;
                    }
                    s.yen -= DATE_YEN;
                    Body.eat(q, s, 600, 150);
                    date(q, s, who, "You split the tempura. She steals yours and thinks you don't notice.", 2);
                }),
                Talk.option("Walk along the sea.", q -> date(q, s, who, "The wind takes most of what you say to each other. It doesn't seem to matter.", 1)),
                Talk.option("Sit on the school roof.", q -> date(q, s, who, "She puts her head on your shoulder and counts the lights coming on across town.", 1)),
                Talk.leave("Not today."));
    }

    /** Two hours go by, and at the end of them she trusts you with something. */
    private static void date(ServerPlayerEntity p, RunState s, Student who, String scene, int fondness) {
        if (tooLate(p, s, who)) return;
        boolean dressed = Clothes.dressedUp(s, p);
        s.dateOn = s.day;
        s.lastTalk[who.id()] = s.day;
        s.fondness[who.id()] = Math.min(5, s.fondness[who.id()] + fondness + (dressed ? 1 : 0));
        s.markDirty();
        String told = RunManager.askedAboutStrange(s, who);
        RunManager.tellClueFound(p, s, who);
        String noticed = dressed ? "\"You dressed up.\" She looks pleased about it the whole time.\n\n" : "";
        spend(p, s, DATE_MINUTES, () -> Talk.say(p, who.firstName(), noticed + scene + "\n\nOn the way back she goes quiet. \"Can I tell you something? " + told + "\"", Talk.leave("Thank you.")));
    }

    /** Too close to curfew for two hours out: the clock kept going while you made up your mind. */
    private static boolean tooLate(ServerPlayerEntity p, RunState s, Student who) {
        if (s.minute < RunManager.curfewOf(s) - LATEST_DATE) return false;
        Talk.say(p, who.firstName(), "It's getting late. Another day?", Talk.leave("Okay."));
        return true;
    }

    private static void breakUp(ServerPlayerEntity p, RunState s, Student who) {
        s.dating = -1;
        s.fondness[who.id()] = -3;
        s.markDirty();
        Kokuhaku.LOG.info("Broke up with {}", who.firstName());
        Talk.say(p, who.firstName(), datingHerWas(s, who) ? "...No you don't." : "Oh. Okay. I think I knew.", Talk.leave("I'm sorry."));
    }

    private static boolean datingHerWas(RunState s, Student who) {
        return s.mystery().isYandere(who.id());
    }

    /**
     * Night by night. Leave her two days without a word and she cools; leave it long enough
     * and she ends it, by text. And she's been seen with you, which doesn't take long to be noticed.
     */
    static void night(MinecraftServer server, RunState s) {
        if (s.dating < 0) return;
        if (s.dead.contains(s.dating)) {
            s.dating = -1;
            s.markDirty();
            return;
        }
        int g = s.dating;
        if (s.day - s.lastTalk[g] >= NEGLECTED_AFTER) {
            s.fondness[g]--;
            s.markDirty();
            if (s.fondness[g] <= ENDS_IT_AT) {
                Kokuhaku.LOG.info("{} ended it: you never talked", Roster.get(g).firstName());
                s.dating = -1;
                s.fondness[g] = 0;
                s.askAgain[g] = s.day + ASK_AGAIN_AFTER * 2;
                Phone.text(server, s, Roster.get(g).firstName(), "I don't think this is working. You never talk to me any more. I'm sorry.");
                return;
            }
        }
        if (datingHer(s) || s.day - s.datingSince < JEALOUS_AFTER || RANDOM.nextFloat() > JEALOUS_CHANCE) return;
        Kokuhaku.LOG.info("{} has been noticed", Roster.get(g).firstName());
        s.livingVictim = g;
        s.dating = -1;
        s.markDirty();
    }

    /** Developer testing only: she knows you well enough to say yes. */
    private static int devYes = -1;

    static void devReady(RunState s, int girl) {
        devYes = girl;
        s.fondness[girl] = 5;
        s.talkDays[girl] = 8;
        s.hangouts[girl] = 2;
    }

    private static String yes(Personality p) {
        return switch (p) {
            case CHEERFUL -> "Yes! Yes yes yes! I've been waiting for you to ask, you've got no idea!";
            case SHY -> "M-me? You mean me? ...Yes. Please. Yes.";
            case COLD -> "...I suppose so. Don't look so pleased with yourself.";
            case GOSSIP -> "Oh, everyone's going to be so annoyed. Yes. Obviously yes.";
            case POLITE -> "I would be very happy to. Thank you for asking me properly.";
            case SPORTY -> "Took you long enough. Yeah. Race you to the gate?";
        };
    }
}
