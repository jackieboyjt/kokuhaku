package com.kokuhaku.game;

/**
 * What you can wear out of school. You go to school in the uniform, as everyone does: what
 * you've bought goes on after school and at weekends. Colours are for the client, which
 * dyes the uniform skin in them.
 */
public enum Outfit {
    UNIFORM("School uniform", 0, 0, 0, 0, false, "You've got three of them. They're all the same one."),
    SHIRT("Shirt and chinos", 3000, 0xE9E6DC, 0xB09872, 0x5A3B24, true, "Plain. It looks like you made an effort, without trying too hard."),
    HOODIE("Grey hoodie and jeans", 3800, 0x86898E, 0x3A567F, 0xE2E2E2, false, "Soft and warm, and the hood goes up. Harder to recognise after dark."),
    TRACKSUIT("Tracksuit", 4200, 0x2D6A3E, 0x2D6A3E, 0xE2E2E2, false, "Comfortable, and you can run in it. Nobody's ever been asked out in one."),
    DENIM("Denim jacket and black jeans", 6500, 0x4D6C98, 0x2A2A2F, 0x6A4A2E, true, "It looks good on you. The man at the till says so, and he means it."),
    COAT("Long black coat", 9800, 0x1B1B1F, 0x1F1F24, 0x121214, false, "Dark as the street at night. Hard to pick out after the lights go on. Not really one for a date.");

    public final String label;
    public final int price;
    /** Top, bottoms and shoes, as RGB. */
    public final int top, bottom, shoes;
    /** Whether it looks like you've made an effort. */
    public final boolean smart;
    public final String description;

    Outfit(String label, int price, int top, int bottom, int shoes, boolean smart, String description) {
        this.label = label;
        this.price = price;
        this.top = top;
        this.bottom = bottom;
        this.shoes = shoes;
        this.smart = smart;
        this.description = description;
    }

    public static Outfit of(int id) {
        return id < 0 || id >= values().length ? UNIFORM : values()[id];
    }
}
