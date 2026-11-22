package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Somewhere to eat that isn't the school canteen. Each place keeps its own hours, as places
 * do, charges what places charge, and sitting down to eat takes a while.
 */
public final class Eateries {
    /** What's on the menu: what it costs, how much food and water it is, and what you're told. */
    private record Dish(String name, int price, int food, int water, String served) {
    }

    private record Place(String key, String name, int opens, int closes, int minutes, String greeting, List<Dish> menu) {
    }

    private static final List<Place> PLACES = List.of(
            new Place("ramen", "Ramen Kazu", 11 * 60, 22 * 60, 25, "\"Sit anywhere. Counter's quickest.\"", List.of(
                    new Dish("Shoyu ramen", 780, 600, 150, "Thin noodles, a soft egg, too much spring onion. You drink the broth when he isn't looking."),
                    new Dish("Tonkotsu ramen", 900, 700, 100, "Thick and white and far too salty. You'd come back tomorrow."),
                    new Dish("Gyoza, six", 380, 280, 0, "Crisp on the bottom. You burn your mouth on the first one, as you always do."),
                    new Dish("Just a glass of water", 0, 0, 300, "He fills it without a word and leaves the jug."))),
            new Place("soba", "Station Soba", 6 * 60, 21 * 60, 10, "\"Ticket from the machine. Standing only.\"", List.of(
                    new Dish("Kake soba", 380, 420, 120, "Eaten standing up between two men in suits. Nobody looks at anybody."),
                    new Dish("Onigiri, salmon", 160, 220, 0, "Still warm. The seaweed's gone soft. It's perfect."),
                    new Dish("Hot barley tea", 120, 0, 320, "It warms your hands more than anything."))),
            new Place("beach", "Umi no Ie", 10 * 60, 18 * 60, 20, "\"We're nearly out of ice. Quick.\"", List.of(
                    new Dish("Yakisoba", 600, 520, 0, "Out of a paper tray, with sand in it somehow. The sea's very loud."),
                    new Dish("Shaved ice, strawberry", 400, 90, 260, "Your teeth hurt. Your tongue's gone red."),
                    new Dish("Ramune", 200, 30, 350, "The marble rattles every time you tip it. You drink it too fast."))));

    private Eateries() {
    }

    /** For the diary: each place's hours and last orders, one row each. */
    public static List<String[]> diary() {
        List<String[]> rows = new ArrayList<>();
        for (Place p : PLACES)
            rows.add(new String[]{Schedule.clock(p.opens()) + "-" + Schedule.clock(p.closes()),
                    p.name() + ". Last orders " + Schedule.clock(p.closes() - p.minutes()) + "." + (p.key().equals("beach") ? " Shut on snow days." : "")});
        return rows;
    }

    private static Place place(String key) {
        for (Place p : PLACES) if (p.key().equals(key)) return p;
        return null;
    }

    private static String hours(Place p) {
        return Schedule.clock(p.opens()) + " to " + Schedule.clock(p.closes());
    }

    /** Right-clicking the menu board outside. */
    static void open(ServerPlayerEntity p, RunState s, String key) {
        Place place = place(key);
        if (place == null) return;
        if (s.minute < place.opens() || s.minute >= place.closes() - place.minutes()) {
            Talk.say(p, place.name(), "The shutter's down. A sign says: Open " + hours(place) + ", last orders " + Schedule.clock(place.closes() - place.minutes()) + ".", Talk.leave("Okay."));
            return;
        }
        if (Schedule.isSnowDay(s.day) && place.key().equals("beach")) {
            Talk.say(p, place.name(), "Shut. There's snow on the tables.", Talk.leave("Okay."));
            return;
        }
        // Come in often enough and they know what you have.
        int k = slot(s, p) * 3 + PLACES.indexOf(place);
        Dish usual = s.eatVisits[k] >= 3 && s.lastDish[k] > 0 && s.lastDish[k] <= place.menu().size() ? place.menu().get(s.lastDish[k] - 1) : null;
        String greeting = place.greeting();
        List<Talk.Option> options = new ArrayList<>();
        if (usual != null) {
            greeting = switch (place.key()) {
                case "ramen" -> "\"The usual? " + usual.name() + (usual.name().endsWith("ramen") ? ", too much spring onion.\"" : "?\"");
                case "soba" -> "He's already reaching for the " + usual.name().toLowerCase() + " ticket.";
                case "beach" -> "\"You again. Brave.\"";
                default -> greeting;
            };
            options.add(Talk.option(usual.price() == 0 ? "The usual" : "The usual (" + Schedule.yen(usual.price()) + ")", q -> order(q, s, place, usual)));
        }
        for (Dish d : place.menu()) {
            if (d == usual) continue;
            String label = d.name() + (d.price() == 0 ? "" : " (" + Schedule.yen(d.price()) + ")");
            options.add(Talk.option(label, q -> order(q, s, place, d)));
        }
        options.add(Talk.leave("Not today."));
        int food = Body.food(s, p), water = Body.water(s, p);
        String state = food < Body.LOW ? " You're starving." : water < Body.LOW ? " You're parched." : "";
        Talk.say(p, place.name(), greeting + " You've got " + Schedule.yen(Math.max(0, s.yen)) + "." + state, options.toArray(new Talk.Option[0]));
    }

    private static void order(ServerPlayerEntity p, RunState s, Place place, Dish d) {
        // The menu may have been left open a while. Check the hours again before anyone pays.
        if (s.minute < place.opens() || s.minute >= place.closes() - place.minutes()
                || (Schedule.isSnowDay(s.day) && place.key().equals("beach"))) {
            Talk.say(p, place.name(), "The shutter's coming down. \"Kitchen's closed. Sorry.\"", Talk.leave("Okay."));
            return;
        }
        // The clock's shared in a party, so nobody else loses the time you spent eating.
        int minutes = d.food() > 0 && s.party.size() == 1 ? place.minutes() : 0;
        if (minutes > 0 && Schedule.isSchoolDay(s.day) && s.minute < Schedule.AFTER_SCHOOL && s.minute + minutes >= Schedule.HOMEROOM) {
            Talk.say(p, place.name(), "There isn't time to sit down. You'd never make it back for the bell.", Talk.leave("Okay."));
            return;
        }
        if (d.price() > 0 && s.yen < d.price()) {
            Talk.say(p, place.name(), "That's " + Schedule.yen(d.price()) + ". You count what you've got twice. It's " + Schedule.yen(Math.max(0, s.yen)) + ".", Talk.leave("Sorry."));
            return;
        }
        s.yen -= d.price();
        if (d.price() > 0) Devotion.spent(p, s);
        Body.eat(p, s, d.food(), d.water());
        int index = PLACES.indexOf(place), k = slot(s, p) * 3 + index;
        s.eatVisits[k]++;
        s.lastDish[k] = place.menu().indexOf(d) + 1;
        // A glass of water's quick. A meal isn't.
        if (minutes > 0) RunManager.skipMinutes(p.server, s, minutes, p);
        Whispers.bought(d.name());
        s.markDirty();
        Kokuhaku.LOG.info("Ate at {}: {}", place.name(), d.name());
        Talk.say(p, place.name(), d.served() + (d.food() > 0 ? overheard(s, index) : ""), Talk.leave("Thanks."));
    }

    private static final String[] OVERHEARD = {
            "\"%s was in at lunch. Sat where you're sitting.\"",
            "\"One of your lot, %s, asked if you come here much. I said I wouldn't know.\"",
            "\"%s and her friends were in. Loud. Left a good tip.\"",
            "\"%s had what you're having. Didn't finish it.\""};

    /**
     * Now and then whoever's serving mentions a girl from your class who was in. Any girl still
     * about, picked by the day and the place, so it's the same all day there. It means nothing.
     */
    private static String overheard(RunState s, int index) {
        Random r = new Random(s.seed ^ s.day * 0x1EA7L ^ index);
        if (r.nextInt(4) != 0) return "";
        List<Integer> girls = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(Math.max(1, s.party.size())); g++)
            if (!s.dead.contains(g) && !s.caught.contains(g)) girls.add(g);
        if (girls.isEmpty()) return "";
        String first = Roster.get(girls.get(r.nextInt(girls.size()))).firstName();
        String line = OVERHEARD[r.nextInt(OVERHEARD.length)];
        // Lunch hasn't happened yet over a breakfast at the station.
        if (s.minute < 13 * 60) line = line.replace("in at lunch", "in earlier");
        return " " + String.format(line, first);
    }

    /** Which player this is, for what each place remembers. */
    private static int slot(RunState s, ServerPlayerEntity p) {
        return Math.min(Roster.MAX_PARTY - 1, Math.max(0, s.party.indexOf(p.getUuid())));
    }
}
