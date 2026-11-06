package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Kimura Clothing, on the shopping street. What you buy you keep, and you change into it
 * after school; on a school day you're back in the uniform from the morning. Something
 * smart makes a difference on a date, and the long coat makes you hard to see at night.
 */
final class Clothes {
    private static final String NAME = "Kimura Clothing";
    private static final int OPENS = 10 * 60, CLOSES = 20 * 60, RESEND_EVERY = 30;
    /** How much less likely you are to be followed at night, all of you in black. */
    private static final float IN_THE_DARK = 0.7f;
    private static String lastSent = "";

    private Clothes() {
    }

    static void clear() {
        lastSent = "";
    }

    private static int slot(RunState s, ServerPlayerEntity p) {
        return Math.max(0, s.party.indexOf(p.getUuid()));
    }

    /** In uniform from waking until the end of school on a school day; otherwise what you chose. */
    static Outfit wearing(RunState s, int slot) {
        if (Schedule.isSchoolDay(s.day) && s.minute < Schedule.AFTER_SCHOOL) return Outfit.UNIFORM;
        return Outfit.of(s.outfit[slot]);
    }

    static Outfit wearing(RunState s, ServerPlayerEntity p) {
        return wearing(s, slot(s, p));
    }

    /** She notices you've made an effort. */
    static boolean dressedUp(RunState s, ServerPlayerEntity p) {
        return wearing(s, p).smart;
    }

    /** Everyone out at night in the coat: the one following you loses you more often. */
    static float darkFactor(RunState s) {
        if (s.party.isEmpty()) return 1f;
        for (int i = 0; i < s.party.size(); i++) if (wearing(s, i) != Outfit.COAT) return 1f;
        return IN_THE_DARK;
    }

    static void open(ServerPlayerEntity p, RunState s) {
        if (s.minute < OPENS || s.minute >= CLOSES) {
            Talk.say(p, NAME, "Closed. The mannequins in the window watch you go. Open " + Schedule.clock(OPENS) + " to " + Schedule.clock(CLOSES) + ".", Talk.leave("Okay."));
            return;
        }
        int i = slot(s, p);
        List<Talk.Option> options = new ArrayList<>();
        for (Outfit o : Outfit.values()) {
            boolean owned = o == Outfit.UNIFORM || (s.wardrobe[i] & 1 << o.ordinal()) != 0;
            if (o.ordinal() == s.outfit[i]) continue;
            if (owned) options.add(Talk.option("Wear the " + o.label.toLowerCase() + ".", q -> wear(q, s, o)));
            else options.add(Talk.option(o.label + " (¥" + String.format("%,d", o.price) + ")", q -> buy(q, s, o)));
        }
        options.add(Talk.leave("Just looking."));
        String now = Outfit.of(s.outfit[i]) == Outfit.UNIFORM ? "You're the only one in here in a school uniform." : "You've got the " + Outfit.of(s.outfit[i]).label.toLowerCase() + " for after school.";
        Talk.say(p, NAME, "\"Everything's on the rails. Changing room's at the back.\" " + now + " You've got ¥" + String.format("%,d", Math.max(0, s.yen)) + ".", options.toArray(new Talk.Option[0]));
    }

    private static void buy(ServerPlayerEntity p, RunState s, Outfit o) {
        if (s.yen < o.price) {
            Talk.say(p, NAME, "\"That's ¥" + String.format("%,d", o.price) + ".\" You've got ¥" + String.format("%,d", Math.max(0, s.yen)) + ". He hangs it back up for you.", Talk.leave("Sorry."));
            return;
        }
        s.yen -= o.price;
        s.wardrobe[slot(s, p)] |= 1 << o.ordinal();
        s.markDirty();
        Whispers.bought(o.label);
        Kokuhaku.LOG.info("Bought {}", o.label);
        wear(p, s, o);
    }

    private static void wear(ServerPlayerEntity p, RunState s, Outfit o) {
        s.outfit[slot(s, p)] = o.ordinal();
        s.markDirty();
        lastSent = "";
        String when = Schedule.isSchoolDay(s.day) && s.minute < Schedule.AFTER_SCHOOL ? " You'll change into it after school." : "";
        Talk.say(p, NAME, o.description + when, Talk.leave("Thanks."));
        send(p.server, s);
    }

    /** Each game minute: tells everyone what everyone's wearing, when it's changed. */
    static void minute(MinecraftServer server, RunState s) {
        StringBuilder now = new StringBuilder();
        for (int i = 0; i < s.party.size(); i++) now.append(wearing(s, i).ordinal()).append(',');
        if (now.toString().equals(lastSent) && s.minute % RESEND_EVERY != 0) return;
        send(server, s);
    }

    static void send(MinecraftServer server, RunState s) {
        StringBuilder now = new StringBuilder();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(s.party.size());
        for (int i = 0; i < s.party.size(); i++) {
            Outfit o = wearing(s, i);
            buf.writeUuid(s.party.get(i));
            buf.writeVarInt(o.ordinal());
            now.append(o.ordinal()).append(',');
        }
        lastSent = now.toString();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) ServerPlayNetworking.send(p, Net.OUTFITS, new PacketByteBuf(buf.copy()));
    }
}
