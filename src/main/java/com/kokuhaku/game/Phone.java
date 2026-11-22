package com.kokuhaku.game;

import com.kokuhaku.Difficulty;
import com.kokuhaku.Kokuhaku;
import com.kokuhaku.net.Net;
import com.kokuhaku.world.town.Town;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Your phone. The class chat never shuts up, whoever you're going out with says
 * goodnight, and from the third day there's a number you don't know. It always
 * knows where you are and what you did today. You can answer it, if you like.
 *
 * She types before she sends, and stops, and starts again; some nights she types
 * and nothing comes. Some of what she sends she takes back a minute later, and late
 * in the month something you sent is gone too, though you never deleted it. She
 * sends photos of you, taken from behind. The dead post in the class chat now and
 * then, and so do ghosts, a few words at a time, most of the letters lost.
 */
public final class Phone {
    /** One message. A deleted one keeps its place in the thread; a photo carries the id of the picture each phone took. */
    private static final class Message {
        final int day, minute, photo;
        final String body, sender;
        /** The chat it's in: her contact's name changes, and her messages go with it. */
        String from;
        final boolean mine, dead;
        boolean deleted;
        /** TEXT, CALL (a missed one), LOCATION (body is the place) or VOICE (length is its seconds). */
        int kind = TEXT, length;

        Message(int day, int minute, String from, String body, boolean mine, String sender, boolean dead, int photo) {
            this.day = day;
            this.minute = minute;
            this.from = from;
            this.body = body;
            this.mine = mine;
            this.sender = sender;
            this.dead = dead;
            this.photo = photo;
        }
    }

    private record Reply(String label, String answer, int fondness) {
    }

    private static final String UNKNOWN = "Unknown number", CHAT = "Class 2-A";
    private static final int LUNCH_TEXT = Schedule.LUNCH + 15, AFTER_TEXT = Schedule.AFTER_SCHOOL + 35, LATE_TEXT = Schedule.EVENING + 70;
    private static final int PHOTO_TEXT = AFTER_TEXT + 50, SILENT_TEXT = LATE_TEXT + 40, UNSEND_AT = Schedule.EVENING;
    private static final int UNKNOWN_FROM = 3, HARUKA_DAY = 9, NEAR_PLACE = 26;
    /** The days things start: she takes messages back, sends photos, types and sends nothing, unsends yours, the dead post. */
    private static final int DELETES_FROM = 5, PHOTOS_FROM = 4, SILENT_FROM = 5, UNSENDS_FROM = 8, DEAD_POST_FROM = 6;
    private static final float UNKNOWN_CHANCE = 0.75f, DELETE_CHANCE = 0.25f;
    /** In ticks: how long before a photo she took arrives, and how often a ghost can post. */
    private static final int PHOTO_DELAY = 20 * 45, GHOST_EVERY = 20 * 30;
    private static final String[][] PLACES = {{"station", "the station"}, {"shops", "the shops"}, {"park", "the park"}, {"beach", "the beach"},
            {"field", "the sports field"}, {"gym", "the gym"}, {"hospital", "the hospital"}, {"center:Library", "the library"}};
    private static final List<String> CHATTER = List.of(
            "does anyone have the maths answers", "who keeps leaving the art room window open", "the vending machine by the gate ate my money again",
            "is it just me or is the caretaker never actually here", "why is the third floor toilet always locked", "someone drew that face on the board again",
            "the last train was running at 3am again. i live by the line", "who took the class photo down from the corridor");
    private static final List<String> AFTER_A_DEATH = List.of(
            "has anyone heard from %s", "they took %s's name off the seating plan already", "please stop posting about %s. her mum is in this chat",
            "the teachers won't say anything about %s", "there were flowers at %s's gate this morning. no card");
    /** From a classmate who's dead. Nobody answers these. */
    private static final List<String> FROM_THE_DEAD = List.of(
            "is anyone still at school", "why did everyone go home", "it's so cold in here", "can someone come and get me", "i can hear you all talking");
    private static final List<String> ANY_DAY = List.of(
            "You look tired. Are you eating?", "I like the way you hold your bag.", "You never look up on the stairs. You should. I'm usually there.",
            "Good night. I'll know if you're not asleep.", "I've saved this number under your whole name. It looks so nice.");
    private static final List<String> LATE_DAYS = List.of(
            "There are fewer people for you to worry about now.", "I've tidied everything up. You'll see.", "Do you still have my note? Don't lie. I can see the corner of it.");
    private static final List<String> AT_HOME = List.of(
            "Your light's on.", "That's a nice shirt. Is it new?", "You should close your curtains properly. Other people might look in.");
    private static final List<String> HER_ROOM = List.of(
            "I'm only downstairs. I just like sending these.", "Who are you looking at?", "Dinner's at seven. I made it the way you like.");
    private static final List<String> CAPTIONS = List.of(
            "You didn't see me.", "I was so close.", "You walk so nicely when you think no one's there.", "Turn around next time.", "I'm keeping this one.");
    /** A girlfriend's goodnight: the first week, and after. */
    private static final List<String> GOODNIGHT = List.of(
            "Goodnight. Lock your door, okay? I worry.", "Goodnight. Today was nice.", "Get home safe? Text me back. Goodnight.");
    private static final List<String> GOODNIGHT_LATE = List.of(
            "Text me when you're in. I'll wait up.", "Who were you with after school? ...Never mind. Goodnight.", "Goodnight. Don't go back out. Promise me.");
    /** From girls who like you, each in her own way. */
    private static final Map<Roster.Personality, List<String>> FRIEND_TEXTS = Map.of(
            Roster.Personality.CHEERFUL, List.of("did you get home ok!! the bus was so weird today", "you looked so serious today lol. smile", "goodnight!! don't stay up"),
            Roster.Personality.SHY, List.of("sorry, wrong chat. ...hi though", "i just wanted to say goodnight. sorry", "are you walking home on your own? you don't have to answer"),
            Roster.Personality.COLD, List.of("You left your pen on your desk. I've got it.", "Lock your door.", "You were out late again."),
            Roster.Personality.GOSSIP, List.of("ok who were you talking to at lunch. tell me everything", "someone asked me where you live. i didn't say. probably", "do NOT go to the shops tomorrow. i'll explain"),
            Roster.Personality.POLITE, List.of("Thank you for today. Sleep well.", "Good evening. I hope you got home safely.", "Please don't walk past the shrine after dark."),
            Roster.Personality.SPORTY, List.of("run tomorrow before school? no? fine", "you walk so slow lol i saw you from the bus", "stretch before bed. i mean it"));
    /** Later in the month, from any of them. */
    private static final List<String> FRIEND_LATE = List.of(
            "are you walking home alone?", "someone was asking which house was yours. i didn't say", "is your door locked", "i saw your light on. couldn't sleep either?");
    private static final List<Reply> REPLIES = List.of(
            new Reply("Who is this?", "You know who it is. You've always known.", 1),
            new Reply("Leave me alone.", "Okay. I'll be very quiet. You won't hear me at all.", -1),
            new Reply("How did you get this number?", "You gave it to the school office. I was standing behind you.", 0));
    /** All a ghost can say. Nothing that names anyone: the dead can't remember her face. */
    public static final List<String> GHOST_WORDS = List.of(
            "behind", "you", "she", "her", "is", "not", "don't", "go", "run", "home", "now", "alone", "trust", "watching",
            "locker", "lies", "stay", "help", "late", "sorry");
    public static final int GHOST_MOST = 4;

    /** What a message is. */
    public static final int TEXT = 0, CALL = 1, LOCATION = 2, VOICE = 3;
    /** The days things start: missed calls, voice messages, your location sent back to you, and her renaming herself in your phone. */
    private static final int CALLS_FROM = 4, VOICE_FROM = 5, LOCATION_FROM = 6, HEART_FROM = 7, YOURS_FROM = 10;
    /** By difficulty: the day she starts letting things slip, and how many she lets slip all month. */
    private static final int[] CLUES_FROM = {4, 5, 8, 10}, MOST_SLIPS = {3, 2, 1, 1};
    private static final int CALL_TEXT = LATE_TEXT + 25, VOICE_TEXT = AFTER_TEXT + 80, LOCATION_TEXT = Schedule.EVENING + 20;
    /** Rings before it gives up, and ticks between them. */
    private static final int RINGS = 6, RING_EVERY = 50;
    /** How close she has to be, in blocks, to show as online. */
    private static final int ONLINE_WITHIN = 48;
    /**
     * Her phone's not a compass. For a quarter of an hour at a time she can be offline
     * though she's close, or online from wherever she is, so walking up to one girl after
     * another doesn't tell you which. Mostly it still follows her.
     */
    private static final int ONLINE_SPELL = 15;
    private static final float HIDDEN_CHANCE = 0.2f, ONLINE_ANYWAY_CHANCE = 0.15f;
    private static final long ONLINE_SALT = new Random().nextLong();
    private static final float CLUE_CHANCE = 0.35f;
    /**
     * Things she lets slip, true of her and nobody else's business. Never her name, her
     * face or her hair colour: only what she does and where she goes, and only now and then.
     */
    private static String slip(RunState s, int her, int which) {
        Roster.Student st = Roster.get(her);
        return switch (which) {
            case 0 -> switch (s.mystery().clubs[her]) {
                case ART -> "There's paint under my nails again. It won't come off.";
                case COOKING -> "I made too much again today. One day I'll make it just for you.";
                case DRAMA -> "I practised what I'd say to you. I know all the lines by heart now.";
                case KENDO -> "My hands ache tonight. I hit the practice dummy far too hard.";
                case LITERATURE -> "I wrote about you again. Three pages. I'll read them to you one day.";
                case MUSIC -> "I played your song four times today. Nobody else knew it was yours.";
                case PHOTOGRAPHY -> "I spent all afternoon in the dark, watching you come up out of the paper.";
                case TENNIS -> "My arm's so sore. I hit everything as hard as I could today.";
            };
            case 1 -> switch (s.mystery().districts[her]) {
                case NORTH_HILL -> "The hill was so steep tonight. I didn't mind. I was thinking about you.";
                case RIVERSIDE -> "The river's loud at night. It sounds like someone whispering your name.";
                case STATION -> "The trains kept me up again. I counted them instead of sheep.";
                case SHRINE -> "I rang the bell at the shrine for you on my way home.";
            };
            case 2 -> st.glasses() == Traits.Glasses.YES ? "I took my glasses off so I'd stop staring at you. It didn't help." : null;
            case 3 -> st.length() == Traits.Length.LONG ? "My hair caught in my bag strap again. Should I cut it? Would you like it short?" : "I trimmed my fringe myself. Did you notice?";
            default -> st.homeroom() == Traits.Homeroom.A ? "You dropped your eraser in third period. I didn't give it back." : "I can hear your class through the wall. I can always pick out your voice.";
        };
    }
    private static final int SLIPS = 5;

    private static final List<Message> feed = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Integer> ghostSaid = new HashMap<>();
    private static int photos = 0;
    /** Every message that's come in, and how many of them each of you had when you last looked. */
    private static int received = 0;
    private static final Map<UUID, Integer> seenUpTo = new HashMap<>();
    private static List<Reply> waiting = List.of();
    /** Where she's typing, until when (server ticks), and the seed for when she stops and starts. */
    private static String typingIn;
    private static int typingUntil;
    private static long typingSeed;
    /** What she's doing in it: typing, or recording a voice message. */
    private static int typingWhat = TEXT;
    /** Someone in the class chat typing: who, whether they're dead, until when (server ticks), and the seed. */
    private static String chatTyper;
    private static boolean chatTyperDead;
    private static int chatTypingUntil;
    private static long chatTypingSeed;
    /** Things she's let slip already; the name her contact had last; when she was last near you. */
    private static final java.util.Set<Integer> slipped = new java.util.HashSet<>();
    private static String lastName = UNKNOWN;
    private static int seenDay = -1, seenMinute;
    /** Her call: still ringing, picked up, and the server tick it stops ringing. */
    private static boolean ringing, answered;
    private static int ringEnd;

    private Phone() {
    }

    static void clear() {
        feed.clear();
        received = 0;
        seenUpTo.clear();
        waiting = List.of();
        typingIn = null;
        chatTyper = null;
        ghostSaid.clear();
        slipped.clear();
        lastName = UNKNOWN;
        seenDay = -1;
        ringing = false;
        answered = false;
        ringEnd = 0;
    }

    /** For the save: every message, what she's let slip, and when she was last near. Typing and replies don't keep. */
    static NbtCompound write(RunState s) {
        NbtCompound nbt = new NbtCompound();
        NbtList messages = new NbtList();
        for (Message m : feed) {
            NbtCompound e = new NbtCompound();
            e.putInt("Day", m.day);
            e.putInt("Minute", m.minute);
            e.putInt("Photo", m.photo);
            e.putString("From", m.from);
            e.putString("Body", m.body);
            e.putString("Sender", m.sender);
            e.putBoolean("Mine", m.mine);
            e.putBoolean("Dead", m.dead);
            e.putBoolean("Deleted", m.deleted);
            e.putInt("Kind", m.kind);
            e.putInt("Length", m.length);
            messages.add(e);
        }
        nbt.put("Messages", messages);
        // Names can change on Continue, so it keeps the most any of you hadn't seen, for all of you.
        int most = 0;
        for (UUID id : s.party) most = Math.max(most, unread(id));
        nbt.putInt("Unread", most);
        nbt.putInt("Photos", photos);
        nbt.putIntArray("Slipped", new ArrayList<>(slipped));
        nbt.putString("LastName", lastName);
        nbt.putInt("SeenDay", seenDay);
        nbt.putInt("SeenMinute", seenMinute);
        return nbt;
    }

    /** The phone as it was saved. A photo this game never took shows as a grey square. */
    static void read(NbtCompound nbt) {
        clear();
        for (NbtElement el : nbt.getList("Messages", NbtElement.COMPOUND_TYPE)) {
            NbtCompound e = (NbtCompound) el;
            Message m = new Message(e.getInt("Day"), e.getInt("Minute"), e.getString("From"), e.getString("Body"), e.getBoolean("Mine"), e.getString("Sender"), e.getBoolean("Dead"), e.getInt("Photo"));
            m.deleted = e.getBoolean("Deleted");
            m.kind = e.getInt("Kind");
            m.length = e.getInt("Length");
            feed.add(m);
        }
        received = nbt.getInt("Unread");
        // Never hand out an id the album may already have a picture under.
        photos = Math.max(photos, nbt.getInt("Photos"));
        for (int i : nbt.getIntArray("Slipped")) slipped.add(i);
        if (nbt.contains("LastName")) lastName = nbt.getString("LastName");
        seenDay = nbt.contains("SeenDay") ? nbt.getInt("SeenDay") : -1;
        seenMinute = nbt.getInt("SeenMinute");
    }

    /** How many came in since this one of you last looked. */
    static int unread(ServerPlayerEntity p) {
        return unread(p.getUuid());
    }

    private static int unread(UUID id) {
        return Math.max(0, received - seenUpTo.getOrDefault(id, 0));
    }

    private static Message receive(MinecraftServer server, RunState s, String from, String body, String sender, boolean dead, int photo) {
        return receive(server, s, from, body, sender, dead, photo, TEXT, 0);
    }

    private static Message receive(MinecraftServer server, RunState s, String from, String body, String sender, boolean dead, int photo, int kind, int length) {
        Message m = new Message(s.day, s.minute, from, body, false, sender, dead, photo);
        m.kind = kind;
        m.length = length;
        feed.add(m);
        received++;
        Kokuhaku.LOG.info("Phone: {}{}: {}", from, sender.isEmpty() ? "" : " (" + sender + ")", photo >= 0 ? "[photo] " + body : body);
        PacketByteBuf ping = PacketByteBufs.create();
        ping.writeString(from);
        ping.writeString(sender);
        ping.writeString(body);
        ping.writeBoolean(dead);
        ping.writeBoolean(photo >= 0);
        ping.writeVarInt(kind);
        ping.writeBoolean(from.equals(herName(s)));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 0.5f, 0.6f);
            RunManager.later(6, () -> p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 0.5f, 0.6f));
            ServerPlayNetworking.send(p, Net.PHONE_PING, new PacketByteBuf(ping.copy()));
        }
        return m;
    }

    /** Someone other than her, or her with something to say at a particular moment. */
    static void text(MinecraftServer server, RunState s, String from, String body) {
        if (from.equals(CHAT)) classChat(server, s, body, null);
        else receive(server, s, from, body, "", false, -1);
    }

    static void fromHer(MinecraftServer server, RunState s, String body) {
        type(server, s, body, -1, null);
    }

    static void fromHer(MinecraftServer server, RunState s, String body, Runnable then) {
        type(server, s, body, -1, then);
    }

    private static void type(MinecraftServer server, RunState s, String body, int photo, Runnable then) {
        type(server, s, body, photo, TEXT, 0, then);
    }

    /**
     * She types in her chat for a few seconds, on and off, then it arrives; with no body,
     * nothing ever does. Then runs once it's arrived. Now and then, a minute later, she
     * takes it back.
     */
    private static void type(MinecraftServer server, RunState s, String body, int photo, int kind, int length, Runnable then) {
        String chat = herName(s);
        if (typingIn != null || chatTyping(server)) {
            // Already typing something else, or someone in the class chat is: this one just arrives.
            if (body != null) receive(server, s, chat, body, "", false, photo, kind, length);
            if (then != null) then.run();
            return;
        }
        int ticks = kind == VOICE ? length * 20 + 20 : body == null ? 140 + RANDOM.nextInt(160) : 80 + Math.min(160, body.length() * 3) + RANDOM.nextInt(60);
        typingIn = chat;
        typingWhat = kind;
        typingUntil = server.getTicks() + ticks;
        typingSeed = RANDOM.nextLong();
        RunManager.later(ticks, () -> {
            typingIn = null;
            if (body == null) return;
            // Her name as it is now: her contact may have been renamed while she typed.
            Message m = receive(server, s, herName(s), body, "", false, photo, kind, length);
            if (then != null) then.run();
            if (s.day >= DELETES_FROM && photo < 0 && kind == TEXT && RANDOM.nextFloat() < DELETE_CHANCE) RunManager.later(400 + RANDOM.nextInt(800), () -> m.deleted = true);
        });
    }

    private static boolean chatTyping(MinecraftServer server) {
        return chatTyper != null && chatTypingUntil > server.getTicks();
    }

    /**
     * Someone in the class chat starts typing. Any classmate still about, her too, or now
     * and then one of the dead, who never sends. Most of the others send something; some
     * think better of it. Never while she's typing in hers.
     */
    private static void classTyping(MinecraftServer server, RunState s) {
        String sender = someone(s, null);
        boolean dead = false;
        if (s.day >= DEAD_POST_FROM && !s.dead.isEmpty() && RANDOM.nextInt(8) == 0) {
            List<Integer> gone = new ArrayList<>(s.dead);
            sender = shortName(Roster.get(gone.get(RANDOM.nextInt(gone.size()))));
            dead = true;
        }
        if (sender.isEmpty()) return;
        int ticks = 100 + RANDOM.nextInt(220);
        long seed = RANDOM.nextLong();
        chatTyper = sender;
        chatTyperDead = dead;
        chatTypingUntil = server.getTicks() + ticks;
        chatTypingSeed = seed;
        RunManager.later(ticks, () -> {
            // A new run, or someone else typing now: this one's long gone.
            if (chatTyper == null || chatTypingSeed != seed) return;
            String who = chatTyper;
            boolean d = chatTyperDead;
            chatTyper = null;
            if (d || RANDOM.nextFloat() >= 0.55f || s.status != RunState.Status.PLAYING) return;
            // Died while typing: it never sends.
            for (int id : s.dead) if (shortName(Roster.get(id)).equals(who)) return;
            String dn = RunManager.lastDeadName(s);
            String body = dn != null && RANDOM.nextBoolean() ? String.format(pick(AFTER_A_DEATH), dn) : pick(CHATTER);
            receive(server, s, CHAT, body, who, false, -1);
        });
    }

    /** What her messages come from: a number you don't know, until you're living with her. */
    private static String herName(RunState s) {
        if (Living.active(s)) return Roster.get(s.livingWith).firstName();
        // She's been at your phone. You never saved her number, and now it's saved twice over.
        if (s.day >= YOURS_FROM) return "Yours ❤";
        if (s.day >= HEART_FROM) return "❤";
        return UNKNOWN;
    }

    /** Her contact's been renamed: every message of hers, and every one you sent her, moves with it. */
    private static void rename(RunState s) {
        String now = herName(s);
        if (now.equals(lastName)) return;
        for (Message m : feed) if (m.from.equals(lastName)) m.from = now;
        if (lastName.equals(typingIn)) typingIn = now;
        Kokuhaku.LOG.info("Phone: her contact is now \"{}\"", now);
        lastName = now;
    }

    /**
     * She calls. It rings, if you were awake to hear it, and if you get your phone out
     * while it's ringing you've picked up; if not, it's a missed call. Where she is
     * standing has nothing to do with it, so a call never clears anyone in sight.
     */
    private static void call(MinecraftServer server, RunState s, List<ServerPlayerEntity> living, boolean ring) {
        if (ring) {
            ringing = true;
            answered = false;
            ringEnd = server.getTicks() + RINGS * RING_EVERY;
            for (int r = 0; r < RINGS; r++) {
                RunManager.later(r * RING_EVERY, () -> {
                    if (answered) return;
                    for (ServerPlayerEntity p : living) {
                        if (p.isRemoved()) continue;
                        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.7f, 1.2f);
                        RunManager.later(5, () -> {
                            if (!answered) p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.7f, 0.95f);
                        });
                    }
                });
            }
        }
        RunManager.later(ring ? RINGS * RING_EVERY : 0, () -> {
            if (answered) return;
            ringing = false;
            receive(server, s, herName(s), "Missed voice call", "", false, -1, CALL, 0);
        });
    }

    /**
     * You picked up. Nobody speaks: a few seconds of someone breathing, somewhere with a
     * fire going, now and then a step. Only the one who answered hears it.
     */
    private static void answer(ServerPlayerEntity p, RunState s) {
        answered = true;
        ringing = false;
        int seconds = 6 + RANDOM.nextInt(7), ticks = seconds * 20;
        boolean step = RANDOM.nextInt(3) == 0;
        Kokuhaku.LOG.info("Phone: {} picked up ({}s)", p.getGameProfile().getName(), seconds);
        for (int t = 0; t < ticks; t += 34) {
            float pitch = 0.7f + RANDOM.nextFloat() * 0.05f;
            RunManager.later(t, () -> p.playSound(SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.MASTER, 0.5f, pitch));
        }
        for (int t = 0; t < ticks; t += 40) RunManager.later(t, () -> p.playSound(SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.MASTER, 0.25f, 1f));
        if (step) RunManager.later(ticks / 2, () -> p.playSound(SoundEvents.BLOCK_STONE_STEP, SoundCategory.MASTER, 0.3f, 0.75f));
        RunManager.later(ticks, () -> {
            p.playSound(SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.MASTER, 0.6f, 1.8f);
            // An open phone shows it the next time it asks.
            receive(p.server, s, herName(s), "Voice call, 0:" + String.format("%02d", seconds), "", false, -1, CALL, 0);
            s.longed(1);
            Shadow.saw(s, "You picked up. She didn't say anything. She only wanted to hear you breathe.");
            if (!p.isRemoved()) RunManager.tell(p, "", "Nobody said anything. You could hear them listening.");
        });
    }

    /** Three in the morning, while you slept: one call, or a few, a minute or two apart. */
    private static void callsInTheNight(RunState s) {
        String chat = herName(s);
        int calls = 1 + RANDOM.nextInt(3), at = 2 * 60 + 50 + RANDOM.nextInt(40);
        for (int i = 0; i < calls; i++) {
            Message m = new Message(s.day, at, chat, "Missed voice call", false, "", false, -1);
            m.kind = CALL;
            feed.add(m);
            received++;
            // Each one a little after the last, never before it.
            at += 1 + RANDOM.nextInt(3);
        }
        Kokuhaku.LOG.info("Phone: {} missed calls in the night", calls);
    }

    /** Where you are right now, on a map, from her. */
    private static void location(MinecraftServer server, RunState s, ServerPlayerEntity p, Town town) {
        String where = RunManager.isHomeNow(p, s) ? "Your house" : whereYouAre(p, town);
        if (where == null) where = "Somewhere near you";
        else if (!where.equals("Your house")) where = where.substring(0, 1).toUpperCase() + where.substring(1);
        type(server, s, where, -1, LOCATION, 0, null);
    }

    /** A few seconds of sound, recorded somewhere near you. */
    private static void voice(MinecraftServer server, RunState s) {
        type(server, s, "", -1, VOICE, 5 + RANDOM.nextInt(11), null);
    }

    /** Whether her chat shows her online, for that phone: when she's near you, mostly, but not always. */
    private static boolean online(ServerPlayerEntity p, RunState s) {
        // Once she's living with you there's nothing left to give away.
        if (Stalker.closeness(p) > 0 || Living.active(s)) return nearby(p, s);
        // The same all spell long, so standing still and watching doesn't average it out.
        float roll = new Random(ONLINE_SALT ^ ((s.day * 1000L + s.minute / ONLINE_SPELL) * 0x9E3779B97F4A7C15L)).nextFloat();
        if (roll < HIDDEN_CHANCE) return false;
        if (roll < HIDDEN_CHANCE + ONLINE_ANYWAY_CHANCE) return true;
        return nearby(p, s);
    }

    /** Whether she's near enough to you to show as online, for that phone. */
    private static boolean nearby(ServerPlayerEntity p, RunState s) {
        if (Stalker.closeness(p) > 0) return true;
        int her = Living.active(s) ? s.livingWith : s.freeYandere();
        return !p.getServerWorld().getEntitiesByType(com.kokuhaku.Kokuhaku.STUDENT, p.getBoundingBox().expand(ONLINE_WITHIN), e -> e.getSkin() == her).isEmpty();
    }

    private static String pick(List<String> lines) {
        return lines.get(RANDOM.nextInt(lines.size()));
    }

    private static String pick(List<String> lines, Random r) {
        return lines.get(r.nextInt(lines.size()));
    }

    /** As people have each other in their phones: first name and an initial. */
    private static String shortName(Roster.Student st) {
        String[] n = st.name().split(" ");
        return n.length > 1 ? n[0] + " " + n[n.length - 1].charAt(0) + "." : n[0];
    }

    /** A classmate who's still about, never the one named in the message. */
    private static String someone(RunState s, String notNamed) {
        List<Roster.Student> about = new ArrayList<>();
        for (int id : Roster.active(Math.max(1, s.party.size()))) {
            Roster.Student st = Roster.get(id);
            if (!s.dead.contains(id) && !s.caught.contains(id) && !st.firstName().equals(notNamed)) about.add(st);
        }
        return about.isEmpty() ? "" : shortName(about.get(RANDOM.nextInt(about.size())));
    }

    /** A post in the class chat from someone in particular. */
    static void classPost(MinecraftServer server, RunState s, String body, int sender) {
        receive(server, s, CHAT, body, shortName(Roster.get(sender)), false, -1);
    }

    private static void classChat(MinecraftServer server, RunState s, String body, String about) {
        receive(server, s, CHAT, body, someone(s, about), false, -1);
    }

    /** Wherever you are, if it's somewhere with a name. */
    private static String whereYouAre(ServerPlayerEntity p, Town town) {
        for (String[] place : PLACES) {
            BlockPos at = town.graph.pos(town.place(place[0]));
            if (p.getPos().squaredDistanceTo(Vec3d.ofCenter(at)) < NEAR_PLACE * NEAR_PLACE) return place[1];
        }
        return null;
    }

    /** The class has noticed who you're going out with. The same for any girlfriend, her too. */
    private static void goingOut(MinecraftServer server, RunState s) {
        if (s.dating < 0 || Living.active(s) || s.dead.contains(s.dating)) return;
        String n = Roster.get(s.dating).firstName();
        if (s.day == s.datingSince + 2) classChat(server, s, "saw " + n.toLowerCase() + " walking home with someone from 2-A. holding hands??", n);
        else if (s.day == s.datingSince + 5 && s.fondness[s.dating] <= 2) classChat(server, s, "are they still going out? " + n.toLowerCase() + " looked upset", n);
    }

    private static void chat(MinecraftServer server, RunState s) {
        if (!s.dead.isEmpty() && s.day >= DEAD_POST_FROM && RANDOM.nextInt(5) == 0) {
            List<Integer> dead = new ArrayList<>(s.dead);
            receive(server, s, CHAT, pick(FROM_THE_DEAD), shortName(Roster.get(dead.get(RANDOM.nextInt(dead.size())))), false, -1);
            return;
        }
        String dead = RunManager.lastDeadName(s);
        if (dead != null && RANDOM.nextBoolean()) classChat(server, s, String.format(pick(AFTER_A_DEATH), dead), dead);
        else classChat(server, s, pick(CHATTER), null);
    }

    private static void hers(MinecraftServer server, RunState s, ServerPlayerEntity p, Town town) {
        if (Living.active(s)) {
            fromHer(server, s, pick(HER_ROOM));
            return;
        }
        if (s.day < UNKNOWN_FROM || Romance.datingHer(s) || RANDOM.nextFloat() > UNKNOWN_CHANCE) return;
        String where = whereYouAre(p, town);
        String talked = Whispers.lastGirl();
        List<String> open = new ArrayList<>();
        if (where != null) open.add("Why are you at " + where + "?");
        if (talked != null) open.add("You talked to " + talked + " today. What did she want?");
        if (RunManager.isHomeNow(p, s)) open.addAll(AT_HOME);
        else open.add(pick(s.day > 6 ? LATE_DAYS : ANY_DAY));
        // Now and then, something true about her that she shouldn't have said.
        // Fewer and later the harder it is. On Hard and up, never her club or where she lives:
        // everyone tells you those, and the two together are nearly her name.
        int level = s.difficulty.ordinal();
        if (s.day >= CLUES_FROM[level] && slipped.size() < MOST_SLIPS[level] && RANDOM.nextFloat() < CLUE_CHANCE) {
            List<String> slips = new ArrayList<>();
            List<Integer> which = new ArrayList<>();
            for (int i = level >= Difficulty.HARD.ordinal() ? 2 : 0; i < SLIPS; i++) {
                String line = slipped.contains(i) ? null : slip(s, s.freeYandere(), i);
                if (line != null) {
                    slips.add(line);
                    which.add(i);
                }
            }
            if (!slips.isEmpty()) {
                int k = RANDOM.nextInt(slips.size());
                slipped.add(which.get(k));
                fromHer(server, s, slips.get(k));
                return;
            }
        }
        boolean ask = RANDOM.nextBoolean();
        fromHer(server, s, pick(open), () -> waiting = ask ? REPLIES : List.of());
    }

    private static boolean hasChat(String name) {
        for (Message m : feed) if (m.from.equals(name)) return true;
        return false;
    }

    /** Each phone photographs its owner from behind; a while later she sends it. */
    static void photo(MinecraftServer server, RunState s, List<ServerPlayerEntity> living, int delay) {
        int id = ++photos;
        for (ServerPlayerEntity p : living) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(id);
            ServerPlayNetworking.send(p, Net.PHOTO_TAKE, buf);
        }
        RunManager.later(delay, () -> type(server, s, pick(CAPTIONS), id, null));
    }

    /** Something you sent is gone. You didn't delete it. */
    private static void unsendOneOfYours() {
        List<Message> yours = new ArrayList<>();
        for (Message m : feed) if (m.mine && !m.deleted) yours.add(m);
        if (!yours.isEmpty()) yours.get(RANDOM.nextInt(yours.size())).deleted = true;
    }

    /** Checked each game minute. */
    static void minute(MinecraftServer server, RunState s, List<ServerPlayerEntity> living, Town town) {
        if (living.isEmpty() || s.day > Schedule.DAYS || s.devoted && s.minute != AFTER_TEXT) return;
        // In a party, her texts are about any one of you, not always the same one.
        ServerPlayerEntity p = living.size() == 1 ? living.get(0) : living.get(RANDOM.nextInt(living.size()));
        rename(s);
        if (s.minute >= 7 * 60 && s.minute < Math.min(23 * 60 + 30, RunManager.curfewOf(s)) && !s.devoted && !Living.active(s)
                && typingIn == null && !chatTyping(server) && RANDOM.nextInt(45) == 0) classTyping(server, s);
        for (ServerPlayerEntity q : living) {
            if (online(q, s)) {
                seenDay = s.day;
                seenMinute = s.minute;
                break;
            }
        }
        boolean stranger = !Living.active(s) && !Romance.datingHer(s);
        if (s.minute == Schedule.WAKE + 2) {
            if (Schedule.isSnowDay(s.day)) classChat(server, s, "SNOW DAY. school's shut, it's on the website", null);
            if (stranger && s.day >= CALLS_FROM && RANDOM.nextInt(4) == 0) callsInTheNight(s);
        }
        if (s.minute == CALL_TEXT && stranger && s.day >= CALLS_FROM && RANDOM.nextInt(4) == 0) call(server, s, living, true);
        if (s.minute == VOICE_TEXT && stranger && s.day >= VOICE_FROM && RANDOM.nextInt(4) == 0) voice(server, s);
        if (s.minute == LOCATION_TEXT && stranger && s.day >= LOCATION_FROM && RANDOM.nextInt(4) == 0) location(server, s, p, town);
        if (s.minute == Festival.INVITE_TEXT && s.day == Festival.DAY - 1) classChat(server, s, "festival tomorrow!! everyone's getting the 9am train from chinetsu station, come", null);
        if (s.minute == LUNCH_TEXT && Schedule.isSchoolDay(s.day) && !Living.active(s)) chat(server, s);
        if (s.minute == (Schedule.isSchoolDay(s.day) ? LUNCH_TEXT : 13 * 60)) goingOut(server, s);
        if (s.minute == AFTER_TEXT) hers(server, s, p, town);
        if (s.minute == PHOTO_TEXT && s.day >= PHOTOS_FROM && !Living.active(s) && RANDOM.nextInt(3) == 0) photo(server, s, living, PHOTO_DELAY);
        if (s.minute == UNSEND_AT && s.day >= UNSENDS_FROM && RANDOM.nextBoolean()) unsendOneOfYours();
        if (s.minute == SILENT_TEXT && s.day >= SILENT_FROM && hasChat(herName(s)) && RANDOM.nextInt(3) == 0) type(server, s, null, -1, null);
        if (s.minute == LATE_TEXT) {
            if (s.day == HARUKA_DAY && !Living.active(s)) {
                text(server, s, "Haruka M.", "is this still your number? don't go home the way you usually go");
            } else if (s.dating >= 0 && !Living.active(s) && !s.dead.contains(s.dating)) {
                // The same for any girlfriend, her too. A week in, she wants to know more.
                Random r = new Random(s.seed ^ s.day * 0x474EL);
                text(server, s, Roster.get(s.dating).firstName(), pick(s.day >= 8 ? GOODNIGHT_LATE : GOODNIGHT, r));
            } else if (s.day >= 4 && RANDOM.nextInt(3) == 0) {
                hers(server, s, p, town);
            }
        }
        if (!Living.active(s) && !s.devoted && !Kept.on(s) && s.day >= 2 && s.minute >= Schedule.EVENING && s.minute < Schedule.EVENING + 240) friends(server, s);
    }

    /**
     * Girls who like you text you of an evening, now and then: two a night at most.
     * Who texts goes by her fondness alone, the same for every girl, and never near a
     * minute the unknown number might be using.
     */
    private static void friends(MinecraftServer server, RunState s) {
        int[] avoid = {AFTER_TEXT, LATE_TEXT, CALL_TEXT, VOICE_TEXT, LOCATION_TEXT, PHOTO_TEXT, SILENT_TEXT, UNSEND_AT, Festival.INVITE_TEXT};
        List<int[]> due = new ArrayList<>();
        for (int g : Roster.active(Math.max(1, s.party.size()))) {
            if (!Roster.get(g).female() || s.fondness[g] < 3 || g == s.dating || g == s.livingWith
                    || s.dead.contains(g) || s.caught.contains(g) || s.accused.contains(g)) continue;
            Random r = friendRandom(s, g);
            if (r.nextInt(3) != 0) continue;
            int at = Schedule.EVENING + 5 + r.nextInt(230);
            for (int tries = 0; tries < 5; tries++) {
                boolean clash = false;
                for (int x : avoid) if (Math.abs(at - x) <= 3) clash = true;
                if (!clash) break;
                at += 7;
            }
            due.add(new int[]{at, g});
        }
        due.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        for (int i = 0; i < Math.min(2, due.size()); i++) {
            int at = due.get(i)[0], g = due.get(i)[1];
            if (s.minute != at) continue;
            // The same draws again, so the line is fixed for the day.
            Random r = friendRandom(s, g);
            r.nextInt(3);
            r.nextInt(230);
            Roster.Student st = Roster.get(g);
            String line = s.day >= 8 && r.nextInt(3) == 0 ? pick(FRIEND_LATE, r) : pick(FRIEND_TEXTS.get(st.personality()), r);
            text(server, s, st.firstName(), line);
        }
    }

    private static Random friendRandom(RunState s, int g) {
        return new Random(s.seed ^ s.day * 0x51ED27L ^ (g + 1) * 0x9E3779B97F4A7C15L);
    }

    /** Developer testing only: there's something to answer. */
    static void devReplies() {
        waiting = REPLIES;
    }

    /** Developer testing only: one of hers taken back, one of yours gone, and something from the dead. */
    static void devStrange(MinecraftServer server, RunState s) {
        for (int i = feed.size() - 1; i >= 0; i--) {
            if (!feed.get(i).mine && feed.get(i).from.equals(herName(s))) {
                feed.get(i).deleted = true;
                break;
            }
        }
        unsendOneOfYours();
        receive(server, s, CHAT, pick(FROM_THE_DEAD), "Aiko S.", false, -1);
        receive(server, s, herName(s), "Missed voice call", "", false, -1, CALL, 0);
        receive(server, s, herName(s), "Your house", "", false, -1, LOCATION, 0);
        receive(server, s, herName(s), "", "", false, -1, VOICE, 9);
        receive(server, s, herName(s), slip(s, s.freeYandere(), 1), "", false, -1);
        receive(server, s, CHAT, "behind you she is watching", "", true, -1);
    }

    /** The phone asks again every couple of seconds while it's open, so new messages turn up as they arrive. */
    public static void open(ServerPlayerEntity p, boolean refresh) {
        // Getting it out while it rings picks up. Not a ghost, not anyone watching, not mid-scene.
        if (!refresh && ringing && !answered && p.server.getTicks() < ringEnd && !RunManager.isGhost(p) && !RunManager.isOut(p) && !RunManager.inScene(p))
            answer(p, RunManager.state(p.server));
        // Only what this one of you hadn't seen: the others still have theirs to read.
        int fresh = unread(p);
        seenUpTo.put(p.getUuid(), received);
        send(p, fresh, refresh);
    }

    /**
     * Everything the screen shows. Each message's From is the conversation it's in, yours included,
     * and Sender who wrote it, in the class chat. Unread is how many of the newest weren't seen
     * before this opening. Devoted is No One Will Save You, where she has your phone and nothing gets
     * out; Watched is how close something is, 0..1. TypingIn is the chat she's typing in, for
     * TypingFor more milliseconds. Refresh only updates a phone that's already open.
     */
    private static void send(ServerPlayerEntity p, int fresh, boolean refresh) {
        RunState s = RunManager.state(p.server);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("Refresh", refresh);
        NbtList messages = new NbtList();
        for (Message m : feed) {
            NbtCompound e = new NbtCompound();
            e.putString("When", "Day " + m.day + ", " + Schedule.clock(m.minute));
            e.putInt("Day", m.day);
            e.putString("Clock", Schedule.clock(m.minute));
            e.putString("From", m.from);
            e.putString("Sender", m.sender);
            e.putString("Body", m.deleted ? "" : m.body);
            e.putBoolean("Mine", m.mine);
            e.putBoolean("Deleted", m.deleted);
            e.putBoolean("Dead", m.dead);
            e.putInt("Photo", m.deleted ? -1 : m.photo);
            e.putInt("Kind", m.deleted ? TEXT : m.kind);
            e.putInt("Length", m.length);
            messages.add(e);
        }
        nbt.put("Messages", messages);
        NbtList replies = new NbtList();
        for (Reply r : waiting) replies.add(NbtString.of(r.label()));
        nbt.put("Replies", replies);
        nbt.putString("ReplyIn", herName(s));
        nbt.putString("Her", herName(s));
        nbt.putString("HerStatus", online(p, s) ? "online" : seenDay < 0 ? "" : (seenDay == s.day ? "last seen today at " : seenDay == s.day - 1 ? "last seen yesterday at " : "last seen day " + seenDay + " at ") + Schedule.clock(seenMinute));
        nbt.putInt("Unread", fresh);
        nbt.putBoolean("Devoted", s.devoted);
        nbt.putFloat("Watched", Chase.secondsLeft(p) >= 0 ? 1f : Stalker.closeness(p));
        int left = typingUntil - p.server.getTicks();
        if (typingIn != null && left > 0) {
            nbt.putString("TypingIn", typingIn);
            nbt.putInt("TypingFor", left * 50);
            nbt.putLong("TypingSeed", typingSeed);
            nbt.putInt("TypingWhat", typingWhat);
        }
        int chatLeft = chatTypingUntil - p.server.getTicks();
        if (chatTyper != null && chatLeft > 0) {
            nbt.putString("ChatTypingWho", chatTyper);
            nbt.putBoolean("ChatTypingDead", chatTyperDead);
            nbt.putInt("ChatTypingFor", chatLeft * 50);
            nbt.putLong("ChatTypingSeed", chatTypingSeed);
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(nbt);
        ServerPlayNetworking.send(p, Net.PHONE, buf);
    }

    public static void reply(ServerPlayerEntity p, int option) {
        // Only someone still in it can answer her: not a ghost, and not anyone watching over a shoulder.
        if (option < 0 || option >= waiting.size() || RunManager.isGhost(p) || RunManager.isOut(p)) return;
        RunState s = RunManager.state(p.server);
        Reply r = waiting.get(option);
        waiting = List.of();
        feed.add(new Message(s.day, s.minute, herName(s), r.label(), true, "", false, -1));
        // An unknown number: it moves how she feels, not anything you can see on her.
        s.longed(r.fondness());
        Shadow.saw(s, "You answered her text. She read it forty times.");
        send(p, 0, true);
        RunManager.later(100, () -> fromHer(p.server, s, r.answer()));
    }

    /** A ghost's few words, posted to the class chat from nobody. Once every half a minute at most. */
    public static void ghostSays(ServerPlayerEntity p, int[] words) {
        if (!RunManager.isGhost(p) || words.length == 0 || words.length > GHOST_MOST) return;
        int now = p.server.getTicks();
        Integer last = ghostSaid.get(p.getUuid());
        if (last != null && now - last < GHOST_EVERY) return;
        StringBuilder said = new StringBuilder();
        for (int w : words) {
            if (w < 0 || w >= GHOST_WORDS.size()) return;
            if (!said.isEmpty()) said.append(' ');
            said.append(GHOST_WORDS.get(w));
        }
        ghostSaid.put(p.getUuid(), now);
        receive(p.server, RunManager.state(p.server), CHAT, said.toString(), "", true, -1);
        send(p, 0, true);
    }
}
