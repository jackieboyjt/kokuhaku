package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Somewhere to eat that isn't the school canteen. Each place keeps its own hours, as places
 * do, charges what places charge, and sitting down to eat takes a while.
 */
final class Eateries {
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
            Talk.say(p, place.name(), "The shutter's down. A sign says: Open " + hours(place) + ".", Talk.leave("Okay."));
            return;
        }
        if (Schedule.isSnowDay(s.day) && place.key().equals("beach")) {
            Talk.say(p, place.name(), "Shut. There's snow on the tables.", Talk.leave("Okay."));
            return;
        }
        List<Talk.Option> options = new ArrayList<>();
        for (Dish d : place.menu()) {
            String label = d.name() + (d.price() == 0 ? "" : " (¥" + d.price() + ")");
            options.add(Talk.option(label, q -> order(q, s, place, d)));
        }
        options.add(Talk.leave("Not today."));
        int food = Body.food(s, p), water = Body.water(s, p);
        String state = food < Body.LOW ? " You're starving." : water < Body.LOW ? " You're parched." : "";
        Talk.say(p, place.name(), place.greeting() + " You've got ¥" + Math.max(0, s.yen) + "." + state, options.toArray(new Talk.Option[0]));
    }

    private static void order(ServerPlayerEntity p, RunState s, Place place, Dish d) {
        if (s.yen < d.price()) {
            Talk.say(p, place.name(), "That's ¥" + d.price() + ". You count what you've got twice. It's ¥" + Math.max(0, s.yen) + ".", Talk.leave("Sorry."));
            return;
        }
        s.yen -= d.price();
        Body.eat(p, s, d.food(), d.water());
        // A glass of water's quick. A meal isn't.
        if (d.food() > 0) RunManager.skipMinutes(p.server, s, place.minutes());
        Whispers.bought(d.name());
        s.markDirty();
        Kokuhaku.LOG.info("Ate at {}: {}", place.name(), d.name());
        Talk.say(p, place.name(), d.served(), Talk.leave("Thanks."));
    }
}
