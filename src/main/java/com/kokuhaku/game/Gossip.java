package com.kokuhaku.game;

import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Mystery.Clue;
import com.kokuhaku.game.Roster.Student;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * What people say about the girls when they think you aren't listening, and
 * what they say when you ask. All of it is true of someone, and any of it
 * could be about anyone: none of it ever asks which girl is her.
 */
final class Gossip {
    private static final Random RANDOM = new Random();
    private static final float OVERHEAR_CHANCE = 0.05f;
    private static final int PER_DAY = 2;

    /** For each player: {day, how many they've overheard that day}. */
    private static final Map<UUID, int[]> heard = new HashMap<>();
    /** "player:day:speaker:girl", once asked. */
    private static final Set<String> asked = new HashSet<>();
    /** The girl each player means to ask about next. */
    static final Map<UUID, Integer> askAbout = new HashMap<>();

    private Gossip() {
    }

    static void clear() {
        heard.clear();
        asked.clear();
        askAbout.clear();
    }

    /** One thing true of her right now, the way people say it. Worked out the same way for every girl. */
    static String fragment(RunState s, int g, Random r) {
        Mystery m = s.mystery();
        String first = Roster.get(g).firstName();
        List<String> lines = new ArrayList<>();
        if (s.fondness[g] >= 3) lines.add(String.format("...%s? She only ever looks at one person. Guess who...", first));
        if (s.dating == g) lines.add(String.format("...%s and the new one. Since when?...", first));
        if (s.accused.contains(g) && !s.caught.contains(g)) lines.add(String.format("...%s hasn't sat with anyone since. Would you?...", first));
        if (People.grieving(s, g)) lines.add(String.format("...%s still saves her friend a seat. Every day...", first));
        // Older runs only ever pinned these on girls who weren't her, so they only come up in newer ones.
        if (m.version >= 1) {
            for (Clue c : m.clues) {
                if (c.kind() != Mystery.Kind.HERRING || c.about() != g || c.day() > s.day) continue;
                int i = Mystery.herringIndex(c.text(), first);
                if (i >= 0 && i < Mystery.HERRING_RUMOUR.length) lines.add(String.format(Mystery.HERRING_RUMOUR[i], first));
            }
        }
        lines.add(String.format("...%s'll be in the " + m.clubs[g].label().toLowerCase() + " club room till six...", first));
        lines.add(String.format("...%s lives " + m.districts[g].livesPhrase + ", doesn't she?...", first));
        String secret = Secrets.surface(s, g);
        if (secret != null) lines.add(secret);
        return lines.get(r.nextInt(lines.size()));
    }

    /** Somebody with nothing to tell you, talking about somebody else. True if you heard it. */
    static boolean overheard(ServerPlayerEntity p, RunState s, StudentEntity e) {
        int speakerId = e.getSkin();
        if (speakerId < 0 || speakerId >= Roster.TOTAL || RANDOM.nextFloat() >= OVERHEAR_CHANCE) return false;
        if (!s.party.contains(p.getUuid()) || RunManager.isGhost(p) || RunManager.isOut(p)) return false;
        int[] today = heard.get(p.getUuid());
        if (today == null || today[0] != s.day) {
            today = new int[]{s.day, 0};
            heard.put(p.getUuid(), today);
        }
        if (today[1] >= PER_DAY) return false;
        List<Integer> girls = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(Math.max(1, s.party.size())); g++) {
            if (g != speakerId && !s.dead.contains(g) && !s.caught.contains(g)) girls.add(g);
        }
        if (girls.isEmpty()) return false;
        int subject = girls.get(RANDOM.nextInt(girls.size()));
        String line = fragment(s, subject, RANDOM);
        String speaker = Roster.get(speakerId).firstName();
        p.sendMessage(Text.literal("You overhear " + speaker + ": " + line).formatted(Formatting.GRAY, Formatting.ITALIC), false);
        s.notes.add("Overheard " + speaker + ", about " + Roster.get(subject).firstName() + ": \"" + line + "\"");
        s.markDirty();
        RunManager.tell(p, "", "You make a note of that.");
        today[1]++;
        return true;
    }

    /** What someone makes of her, when you ask. Never anything they'd tell you as a clue. */
    static String about(RunState s, ServerPlayerEntity p, Student who, int g) {
        String key = p.getUuid() + ":" + s.day + ":" + who.id() + ":" + g;
        if (!asked.add(key)) return "I've told you what I think.";
        Random r = new Random(s.seed ^ s.day * 0x6A09E667L ^ who.id() * 31L ^ g * 7L);
        Mystery m = s.mystery();
        Student her = Roster.get(g);
        String first = her.firstName();
        if (Routines.group(who) == Routines.group(her)) {
            RunManager.learned(p, her);
            return first + "? We're in the same group. She's in the " + m.clubs[g].label().toLowerCase()
                    + " club and she lives " + m.districts[g].livesPhrase + ". Why?";
        }
        if (Secrets.kindOf(s, g) != null && r.nextBoolean()) {
            String secret = Secrets.surface(s, g);
            if (secret != null) return spoken(secret);
        }
        if (r.nextFloat() < 0.6f) return spoken(fragment(s, g, r));
        return switch (who.personality()) {
            case COLD -> "Never noticed her.";
            case GOSSIP -> "Her? Ask me again tomorrow. I'm still finding out.";
            case SHY -> "She was nice to me once.";
            case POLITE -> "I wouldn't like to say.";
            case SPORTY -> "Fast. Doesn't talk much.";
            case CHEERFUL -> first + "'s lovely! Is she? I think she is.";
        };
    }

    /** A rumour said to your face: without the trailing off. */
    private static String spoken(String line) {
        String out = line;
        if (out.startsWith("...")) out = out.substring(3);
        if (out.endsWith("...")) out = out.substring(0, out.length() - 3);
        out = out.trim();
        if (!out.isEmpty() && ".?!".indexOf(out.charAt(out.length() - 1)) < 0) out += ".";
        return out;
    }
}
