package com.kokuhaku.game;

import com.kokuhaku.entity.StudentEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Days off: in the morning two of the friend groups say in the class chat where they're
 * going, and if you turn up where two of them are, they wave you over. Which groups is
 * down to the seed, and every girl there is credited the same.
 */
final class Outings {
    private static final int PLAN_AT = Schedule.WAKE + 40;
    private static final int JOIN_FROM = 10 * 60 + 30, JOIN_UNTIL = 17 * 60;
    private static final int GROUPS = 6, PLANNED = 2;
    private static final double NEAR = 10;
    private static final int STAY = 40;
    private static final String[] TIMES = {
            "Someone's brought a ball. Nobody's any good at it.",
            "You share a bag of something too salty. Nobody mentions school.",
            "Someone tells a story about the caretaker. Nobody believes it. Everybody laughs.",
            "You sit on a wall and watch people go past. It's nice, for once.",
            "There's an argument about the best konbini. You lose it.",
            "Someone takes a photo of all of you. You're blinking."};
    private static final Random RANDOM = new Random();

    /** Today's two groups. Not saved: on Continue the morning post comes round again. */
    private static int[] planned = new int[0];
    private static int plannedDay = -1;
    private static final Set<UUID> joined = new HashSet<>();

    private Outings() {
    }

    static void clear() {
        planned = new int[0];
        plannedDay = -1;
        joined.clear();
    }

    static void minute(MinecraftServer server, RunState s, List<ServerPlayerEntity> living) {
        if (s.day > Schedule.DAYS || Schedule.isSchoolDay(s.day) || Festival.on(s) || Living.active(s) || s.devoted || Kept.on(s)) return;
        if (s.minute == PLAN_AT) plan(server, s);
        if (plannedDay != s.day || s.minute < JOIN_FROM || s.minute > JOIN_UNTIL) return;
        for (ServerPlayerEntity p : living) {
            if (RunManager.isGhost(p) || RunManager.inScene(p) || WalkScene.active(p) || Talk.waiting(p) || joined.contains(p.getUuid())) continue;
            List<Integer> present = new ArrayList<>();
            for (StudentEntity e : RunManager.studentsOf(server)) {
                int id = e.getSkin();
                if (id < 0 || id >= Roster.TOTAL || s.dead.contains(id) || s.caught.contains(id) || present.contains(id)) continue;
                if (!isPlanned(Routines.group(Roster.get(id))) || e.squaredDistanceTo(p) > NEAR * NEAR) continue;
                present.add(id);
            }
            if (present.size() < 2) continue;
            joined.add(p.getUuid());
            String a = Roster.get(present.get(0)).firstName(), b = Roster.get(present.get(1)).firstName();
            Talk.say(p, "Outing", a + " and " + b + " wave you over. \"You came!\"",
                    Talk.option("Stay a while.", q -> stay(q, s, present)), Talk.leave("Just passing."));
        }
    }

    private static void plan(MinecraftServer server, RunState s) {
        Random r = new Random(s.seed ^ s.day * 0x4F55L);
        int first = r.nextInt(GROUPS), second = r.nextInt(GROUPS - 1);
        if (second >= first) second++;
        planned = new int[]{first, second};
        plannedDay = s.day;
        joined.clear();
        boolean snow = Schedule.isSnowDay(s.day);
        for (int group : planned) {
            List<Integer> members = new ArrayList<>();
            for (int id : Roster.active(Math.max(1, s.party.size()))) {
                if (s.dead.contains(id) || s.caught.contains(id)) continue;
                if (Routines.group(Roster.get(id)) == group) members.add(id);
            }
            if (members.isEmpty()) continue;
            int sender = members.get(r.nextInt(members.size()));
            String place = Routines.hangoutFor(group, s);
            String body;
            if (snow) body = switch (place) {
                case "station" -> "station soba is the only warm place in this town. 11";
                case "shops" -> "shops at 11, hot drinks, it's on me (it's not on me)";
                default -> "anyone out? 11ish";
            };
            else body = switch (place) {
                case "station" -> "soba at the station then the arcade. 11ish. anyone";
                case "shops" -> "shops at 11. kimura's has a sale on";
                case "beach" -> "beach at 11. yes it's cold. come anyway";
                case "park" -> "park at 11, bring food";
                default -> "ball on the field at 11 if anyone's bored";
            };
            Phone.classPost(server, s, body, sender);
        }
    }

    private static boolean isPlanned(int group) {
        for (int g : planned) if (g == group) return true;
        return false;
    }

    /** As if you'd stopped to talk to each of them: a day known, nothing more. */
    private static void stay(ServerPlayerEntity q, RunState s, List<Integer> present) {
        for (int id : present) {
            if (!Roster.get(id).female()) continue;
            Romance.talked(s, id);
            Whispers.talkedTo(s, id);
        }
        if (s.party.size() == 1) RunManager.skipMinutes(q.server, s, STAY, q);
        String rumour = Rumours.line(s).trim();
        String text = TIMES[RANDOM.nextInt(TIMES.length)] + (rumour.isEmpty() ? "" : " Someone says, \"" + rumour + "\"");
        String bye = s.day >= Schedule.DAYS ? "See you." : Schedule.isSnowDay(s.day) ? "See you at school." : "See you Monday.";
        Talk.say(q, "Outing", text, Talk.leave(bye));
    }
}
