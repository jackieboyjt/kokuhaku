package com.kokuhaku.game;

import com.kokuhaku.game.Mystery.Clue;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything she really did this run, kept as it happened. When it's over the
 * mask comes off and you're shown what you walked past. Nothing here is made
 * up afterwards, so the mystery was always fair.
 */
final class Shadow {
    private static final int SHOWN = 11;
    private static final String FIRST = "Day 1: She was at your locker before the caretaker. She folded the note four times.";

    private Shadow() {
    }

    /** What she did is kept with the run now, so it lasts through a Continue. Nothing to forget here. */
    static void clear() {
    }

    /** Marks a line that would point to her (or clear someone) while friends are still out there. */
    private static final String NAMED = "\u0001";

    static void saw(RunState s, String what) {
        add(s, "Day " + s.day + ": " + what);
    }

    /** A line that gives away who she is, or who she isn't: left out of the masked reel. */
    static void sawNamed(RunState s, String what) {
        add(s, NAMED + "Day " + s.day + ": " + what);
    }

    private static void add(RunState s, String line) {
        if (s.shadow.contains(line)) return;
        s.shadow.add(line);
        s.markDirty();
    }

    /** Evenly through the month, always ending on the last thing she did. masked: nothing that names anyone. */
    private static List<String> chosen(RunState s, boolean masked) {
        List<String> events = new ArrayList<>();
        for (String line : s.shadow) {
            if (!line.startsWith(NAMED)) events.add(line);
            else if (!masked) events.add(line.substring(NAMED.length()));
        }
        List<String> picked = new ArrayList<>(List.of(FIRST));
        int room = SHOWN - 1, count = events.size();
        for (int i = 0; i < Math.min(room, count); i++) {
            picked.add(events.get(count <= room ? i : (int) Math.round(i * (count - 1) / (double) (room - 1))));
        }
        return picked;
    }

    /** masked: there are still friends out there, so you never learn her name. */
    static String reel(RunState s, boolean masked) {
        return reel(s, masked, -1);
    }

    /** slot: whose board to look back over, or -1 for nobody's. */
    static String reel(RunState s, boolean masked, int slot) {
        Mystery m = s.mystery();
        List<String> lines = new ArrayList<>();
        lines.add(masked ? "You never saw her face." : "It was " + String.join(" and ", m.yanderes.stream().map(y -> Roster.get(y).name()).toList()) + ".");
        lines.addAll(chosen(s, masked));
        if (!masked) {
            List<Clue> told = m.clues.stream().filter(c -> c.witness() >= 0).toList();
            List<Clue> missed = told.stream().filter(c -> !s.found.contains(c.id())).toList();
            lines.add("");
            lines.add(Habits.kind(s));
            lines.add(Story.lastLine());
            lines.add("You heard " + (told.size() - missed.size()) + " of the " + told.size() + " things people knew.");
            Clue unheard = missed.stream().filter(c -> c.kind() == Mystery.Kind.TRAIT).findFirst().orElse(null);
            if (unheard != null) lines.add(Roster.get(unheard.witness()).firstName() + " could have told you: " + unheard.text());
            else if (!missed.isEmpty()) lines.add(Roster.get(missed.get(0).witness()).firstName() + " knew something. Nobody ever asked.");
            List<String> extra = new ArrayList<>();
            int d = m.knowableBy();
            if (d > 0) extra.add("Everything you needed was out there by day " + d + ".");
            if (slot >= 0 && (slot + 1) * Roster.GIRLS <= s.marks.length) extra.addAll(board(s, m, slot));
            Clue herring = m.clues.stream().filter(c -> c.kind() == Mystery.Kind.HERRING && s.found.contains(c.id())
                    && c.about() >= 0 && c.about() < Roster.GIRLS).findFirst().orElse(null);
            if (herring != null) {
                String first = Roster.get(herring.about()).firstName();
                int idx = Mystery.herringIndex(herring.text(), first);
                if (m.isYandere(herring.about())) extra.add(String.format("What they said about %s was true. She had worse reasons.", first));
                else if (idx >= 0 && idx < Mystery.HERRING_TRUTH.length) extra.add(String.format(Mystery.HERRING_TRUTH[idx], first));
            }
            // The promised reveals (who left what, whose they were) come before the secrets, so the cap cuts secrets first.
            extra.addAll(Sleuthing.reelLines(s));
            String adm = Habits.reelLine(s);
            if (adm != null) extra.add(adm);
            extra.addAll(Secrets.reelLines(s));
            lines.addAll(extra.subList(0, Math.min(6, extra.size())));
            if (!s.dead.isEmpty()) lines.add(s.dead.size() + (s.dead.size() == 1 ? " person" : " people") + " didn't see the end of it.");
            String helper = Accomplice.reveal();
            if (helper != null) lines.add(helper);
        }
        return String.join("\n", lines);
    }

    /** What your own board said about her, and how many others you had down for it. */
    private static List<String> board(RunState s, Mystery m, int slot) {
        List<String> out = new ArrayList<>();
        boolean several = m.yanderes.size() > 1;
        for (int y : m.yanderes) {
            int i = slot * Roster.GIRLS + y;
            String who = several ? Roster.get(y).firstName() : "her";
            // Marks from before the day was kept read 0: say nothing of when.
            String on = s.markedOn[i] > 0 ? " on day " + s.markedOn[i] : "";
            switch (s.marks[i]) {
                case 1 -> out.add("You'd marked " + who + on + ".");
                case 3 -> out.add("You'd ruled " + who + " out" + on + ".");
                default -> out.add("You never settled on " + who + ".");
            }
        }
        int n = 0;
        for (int g = 0; g < Roster.girlsFor(m.party); g++) {
            if (!m.isYandere(g) && s.marks[slot * Roster.GIRLS + g] == 1) n++;
        }
        if (n > 0) out.add("You suspected " + n + (n == 1 ? " other girl." : " other girls."));
        return out;
    }

    static void send(ServerPlayerEntity p, RunState s, boolean masked) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(reel(s, masked, s.party.indexOf(p.getUuid())));
        ServerPlayNetworking.send(p, Net.REEL, buf);
    }
}
