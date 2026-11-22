package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.ClientRun;
import com.kokuhaku.game.Mystery;
import com.kokuhaku.game.Schedule;
import com.kokuhaku.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Your bag: the clues you've found, and the board of suspects where you mark them for yourself. */
public class BagScreen extends Screen {
    private static boolean suspectsTab = false;
    private static int selected = -1;
    /** The girl the Clues tab is showing, or -1 for everything. */
    private static int about = -1;

    /** A new run starts with nobody picked. */
    public static void forget() {
        selected = -1;
        about = -1;
    }

    private static final String[] MARKS = {"Her?", "Not sure", "Not her"};

    /** Whether you keep a board: spectators and watchers don't. */
    private final boolean canMark;
    /** Whether "Ask about her" would do anything: not for ghosts, the devoted, or while living with her. */
    private final boolean canAsk;
    /** The line to yourself, and whose it is. */
    private TextFieldWidget memo;
    private int memoGirl = -1;

    private final NbtList clues;
    private final NbtList girls;
    private final int guesses;
    /** How many girls your clues still leave, or -1 if the difficulty won't tell you; and whether the rest are greyed out. */
    private final int fits;
    private final boolean pruned;
    /** False leads left to plant, or -1 if that isn't you. */
    private final int leads;
    /** Friends you could call back, and what it costs. */
    private final List<String> callable;
    private final int ghostCost;
    private double scroll = 0;
    private final List<String> signs;

    public BagScreen(NbtCompound data) {
        super(KokuhakuClient.menuText("Your Bag"));
        this.clues = data.getList("Clues", NbtElement.COMPOUND_TYPE);
        this.girls = data.getList("Girls", NbtElement.COMPOUND_TYPE);
        // A bigger party's run can leave her index past the end of this class.
        if (selected >= girls.size()) selected = -1;
        this.guesses = data.getInt("Guesses");
        this.fits = data.getInt("Fits");
        this.pruned = data.getBoolean("Pruned");
        this.leads = data.getInt("Leads");
        this.signs = data.getList("Signs", NbtElement.STRING_TYPE).stream().map(NbtElement::asString).toList();
        this.callable = data.getList("Callable", NbtElement.STRING_TYPE).stream().map(NbtElement::asString).toList();
        this.ghostCost = data.getInt("GhostCost");
        this.canMark = data.getBoolean("CanMark");
        this.canAsk = data.getBoolean("CanAsk");
        if (about >= girls.size()) about = -1;
    }

    @Override
    protected void init() {
        // Whatever was typed goes to the server before the field is rebuilt.
        saveMemo();
        memo = null;
        memoGirl = -1;
        int x = Math.max(16, width / 2 - 220);
        addDrawableChild(new MenuButton(x + 110, 14, 50, 14, tabLabel("Clues", !suspectsTab), b -> switchTab(false)));
        addDrawableChild(new MenuButton(x + 165, 14, 70, 14, tabLabel("Suspects", suspectsTab), b -> switchTab(true)));
        addDrawableChild(new MenuButton(x + 240, 14, 50, 14, tabLabel("Diary", false), b -> client.setScreen(new TimetableScreen())));
        int low = height - 22;
        addDrawableChild(new MenuButton(x, low, 50, 14, KokuhakuClient.menuText("Phone"), b -> {
            close();
            ClientPlayNetworking.send(Net.OPEN_PHONE, PacketByteBufs.empty());
        }));

        for (int i = 0; i < callable.size(); i++) {
            String name = callable.get(i);
            addDrawableChild(new MenuButton(x, low - 16 * (i + 1), 300, 14, KokuhakuClient.menuText("Call " + name + " back as a ghost (" + Schedule.yen(ghostCost) + ")"), b -> {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeString(name);
                ClientPlayNetworking.send(Net.CALL_GHOST, buf);
                close();
            }));
        }
        if (ClientRun.party > 1) addDrawableChild(new MenuButton(x + 58, low, 120, 14, KokuhakuClient.menuText("Leave a note"), b -> client.setScreen(new NoteScreen(this, signs))));
        if (leads > 0) addDrawableChild(new MenuButton(x + 186, low, 150, 14, KokuhakuClient.menuText("Plant a false lead (" + leads + " left)"), b -> {
            close();
            ClientPlayNetworking.send(Net.PLANT, PacketByteBufs.empty());
        }));
        if (suspectsTab) {
            initSuspects();
        } else if (about >= 0) {
            int right = Math.min(width - 16, width / 2 + 220);
            addDrawableChild(new MenuButton(right - 50, (fits >= 0 ? 54 : 40) - 3, 50, 14, KokuhakuClient.menuText("Show all"), b -> {
                about = -1;
                scroll = 0;
                clearAndInit();
            }));
        }
    }

    private Text tabLabel(String s, boolean active) {
        return KokuhakuClient.menuText(s).formatted(active ? Formatting.UNDERLINE : Formatting.RESET);
    }

    /** Developer testing only: the Suspects tab, as if clicked. */
    public void devSuspects() {
        switchTab(true);
    }

    private void switchTab(boolean suspects) {
        suspectsTab = suspects;

        clearAndInit();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Typing an e into your line shouldn't shut the bag. Escape still does.
        if (memo != null && memo.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(keyCode, scanCode, modifiers);
        if (client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = Math.max(0, scroll - amount * 14);
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xD0000000);
        int x = Math.max(16, width / 2 - 220);
        Icons.draw(context, Icons.BAG, x, 10, 1);
        context.drawTextWithShadow(textRenderer, title, x + 22, 15, 0xFFE8E2E4);

        // Guesses left, as crosshairs, top right.
        int gx = Math.min(width - 16, width / 2 + 220);
        for (int i = 0; i < guesses; i++) {
            gx -= 18;
            Icons.draw(context, Icons.ACCUSE, gx, 10, 1);
        }
        Text label = KokuhakuClient.menuText("Guesses");
        // Only if there's room for it beside the tabs.
        if (gx - textRenderer.getWidth(label) - 6 > x + 296) context.drawTextWithShadow(textRenderer, label, gx - textRenderer.getWidth(label) - 6, 15, 0xFF9A9AA2);

        if (suspectsTab) {
            renderSuspects(context, mouseX, mouseY);
        } else {
            renderClues(context);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    // ---------------------------------------------------------------- clues

    private void renderClues(DrawContext context) {
        int left = Math.max(16, width / 2 - 220), right = Math.min(width - 16, width / 2 + 220);
        int top = fits >= 0 ? 54 : 40, bottom = height - 28;
        if (fits >= 0) context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText(fits + (fits == 1 ? " girl still fits" : " girls still fit") + " what you know."), left, 38, fits <= 3 ? 0xFFE0283C : 0xFFD8D0D4);
        // Newest first; only what names her, if you're looking at one girl.
        List<NbtCompound> shown = new ArrayList<>();
        for (int i = clues.size() - 1; i >= 0; i--) shown.add(clues.getCompound(i));
        if (about >= 0 && about < girls.size()) {
            String name = girls.getCompound(about).getString("Name");
            String first = name.split(" ")[0];
            Pattern named = Pattern.compile("\\b" + Pattern.quote(first) + "\\b");
            shown.removeIf(c -> !named.matcher(c.getString("Text")).find() && !c.getString("From").equals(name));
            int n = shown.size();
            context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText("About " + first), left, top, 0xFFE8E2E4);
            String count = n == 0 ? "Nothing you've written down names her." : n == 1 ? "1 thing mentions her." : n + " things mention her.";
            context.drawTextWithShadow(textRenderer, Text.literal(count), left, top + 12, 0xFF9A9AA2);
            top += 26;
        }
        context.enableScissor(left, top, right, bottom);
        int y = top - (int) scroll;
        for (NbtCompound c : shown) {
            Mystery.Kind kind = Mystery.Kind.values()[c.getInt("Kind")];
            int icon = switch (kind) {
                case NOTE -> Icons.NOTE;
                case TRAIT -> Icons.CLUE;
                case HERRING, ALIBI -> Icons.TALK;
                case HINT -> Icons.EYE;
            };
            Icons.draw(context, icon, left, y, 1);
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(c.getString("Text")), right - left - 26);
            int ly = y + 2;
            for (OrderedText l : lines) {
                context.drawTextWithShadow(textRenderer, l, left + 24, ly, 0xFFE8E2E4);
                ly += 10;
            }
            String from = c.getString("From");
            if (c.getInt("Day") > 0) {
                String meta = "Day " + c.getInt("Day") + (from.isEmpty() ? "" : "  ·  from " + from);
                context.drawTextWithShadow(textRenderer, Text.literal(meta), left + 24, ly, 0xFF6C6C74);
                ly += 10;
            }
            y = ly + 6;
        }
        context.disableScissor();
        // Don't let it scroll past the last clue.
        int contentHeight = y + (int) scroll - top;
        scroll = Math.min(scroll, Math.max(0, contentHeight - (bottom - top)));
    }

    // ------------------------------------------------------------- suspects

    private int gridLeft() {
        return Math.max(16, width / 2 - 220);
    }

    private int gridWidth() {
        return Math.min(440, width - 32) - 170;
    }

    private int cardW() {
        return (gridWidth() - 3 * 4) / 4;
    }

    /** Where the controls under her details start, clear of the hint above them. */
    private static final int CARD_H = 18, GRID_TOP = 44, CTRL = GRID_TOP + 126;

    /** The controls' top: CTRL, or higher if the ghost calls and the bottom row would sit on them. */
    private int ctrlTop() {
        return Math.max(GRID_TOP + 16, Math.min(CTRL, height - 22 - 16 * callable.size() - 16 - 52));
    }

    private void initSuspects() {
        int px = gridLeft() + gridWidth() + 14;
        if (selected < 0) return;
        final int CTRL = ctrlTop();
        int girl = selected;
        NbtCompound g = girls.getCompound(girl);
        if (canMark) {
            int current = g.getInt("Mark");
            for (int m = 1; m <= MARKS.length; m++) {
                int mark = m;
                addDrawableChild(new MenuButton(px + 51 * (m - 1), CTRL, 48, 14, tabLabel(MARKS[m - 1], current == m), b -> {
                    int now = g.getInt("Mark") == mark ? 0 : mark;
                    g.putInt("Mark", now);
                    String text = memo != null && memoGirl == girl ? memo.getText() : g.getString("Memo");
                    g.putString("Memo", text);
                    sendMark(girl, now, text);
                    clearAndInit();
                }));
            }
            memo = new TextFieldWidget(textRenderer, px, CTRL + 18, 150, 16, Text.literal("A line to yourself"));
            memo.setMaxLength(60);
            memo.setText(g.getString("Memo"));
            memo.setPlaceholder(Text.literal("A line to yourself…").formatted(Formatting.GRAY));
            memoGirl = girl;
            addDrawableChild(memo);
        }
        addDrawableChild(new MenuButton(px, CTRL + 38, 73, 14, KokuhakuClient.menuText("Her clues"), b -> {
            about = girl;
            suspectsTab = false;
            scroll = 0;
            clearAndInit();
        }));
        if (canAsk && !g.getBoolean("Dead") && !g.getBoolean("Caught")) {
            addDrawableChild(new MenuButton(px + 77, CTRL + 38, 73, 14, KokuhakuClient.menuText("Ask about her"), b -> {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(girl);
                ClientPlayNetworking.send(Net.ASK_ABOUT, buf);
                close();
            }));
        }
    }

    /** Your line about her, if it's changed since the server last had it. */
    private void saveMemo() {
        if (memo == null || memoGirl < 0 || memoGirl >= girls.size()) return;
        NbtCompound g = girls.getCompound(memoGirl);
        String text = memo.getText();
        if (text.equals(g.getString("Memo"))) return;
        g.putString("Memo", text);
        sendMark(memoGirl, g.getInt("Mark"), text);
    }

    private void sendMark(int girl, int mark, String text) {
        if (client == null || client.getNetworkHandler() == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(girl);
        buf.writeVarInt(mark);
        buf.writeString(text, 64);
        ClientPlayNetworking.send(Net.MARK, buf);
    }

    @Override
    public void removed() {
        saveMemo();
        super.removed();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (suspectsTab) {
            for (int i = 0; i < girls.size(); i++) {
                int cx = gridLeft() + (i % 4) * (cardW() + 4), cy = GRID_TOP + (i / 4) * (CARD_H + 4);
                if (mouseX >= cx && mouseX < cx + cardW() && mouseY >= cy && mouseY < cy + CARD_H) {
                    selected = i;
            
                    clearAndInit();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderSuspects(DrawContext context, int mouseX, int mouseY) {
        for (int i = 0; i < girls.size(); i++) {
            NbtCompound g = girls.getCompound(i);
            int cx = gridLeft() + (i % 4) * (cardW() + 4), cy = GRID_TOP + (i / 4) * (CARD_H + 4);
            boolean hover = mouseX >= cx && mouseX < cx + cardW() && mouseY >= cy && mouseY < cy + CARD_H;
            int mark = g.getInt("Mark");
            boolean crossed = mark == 3;
            boolean accused = g.getBoolean("Accused") || g.getBoolean("Dead");
            context.fill(cx, cy, cx + cardW(), cy + CARD_H, i == selected ? 0x60E0283C : hover ? 0x30FFFFFF : 0x18FFFFFF);
            String first = g.getString("Name").split(" ")[0];
            Text name = KokuhakuClient.menuText(first);
            // The menu font has no dot, so the mark goes on in the plain one.
            if (!accused && (mark == 1 || mark == 2)) name = Text.empty().append(name).append(Text.literal(mark == 1 ? " •" : " ?"));
            if (crossed || accused) name = name.copy().formatted(Formatting.STRIKETHROUGH);
            boolean ruledOut = pruned && !g.getBoolean("Fits");
            int color = accused ? 0xFF8A1A26 : crossed || ruledOut ? 0xFF55505A : mark == 1 ? 0xFFE0283C : mark == 2 ? 0xFFD8A040 : 0xFFE8E2E4;
            context.drawTextWithShadow(textRenderer, name, cx + 5, cy + 5, color);
        }

        int px = gridLeft() + gridWidth() + 14;
        if (selected < 0) {
            context.drawTextWrapped(textRenderer, Text.literal("Pick a girl to see what you know about her."), px, GRID_TOP, 150, 0xFF9A9AA2);
            return;
        }
        NbtCompound g = girls.getCompound(selected);
        boolean known = g.getBoolean("Known");
        context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText(g.getString("Name")), px, GRID_TOP, 0xFFE0283C);
        int y = GRID_TOP + 16;
        y = row(context, px, y, "Hair", g.getString("Hair") + ", " + g.getString("Length").toLowerCase());
        y = row(context, px, y, "Glasses", g.getString("Glasses").equals("Glasses") ? "Yes" : "No");
        y = row(context, px, y, "Class", known ? g.getString("Class") : "???");
        y = row(context, px, y, "Club", known ? g.getString("Club") : "???");
        y = row(context, px, y, "Lives", known ? g.getString("District") : "???");
        int fond = g.getInt("Fondness");
        y = row(context, px, y, "Likes you", g.getBoolean("Dating") ? "You're dating" : fond >= 2 ? "Yes" : fond <= -2 ? "No" : "Not sure");
        int off = g.getInt("DaysOff"), late = g.getInt("Late");
        y = row(context, px, y, "Days off", late > 0 ? off + ", late " + late : Integer.toString(off));
        if (!known) {
            context.drawTextWrapped(textRenderer, Text.literal("Talk to her, or follow her home."), px, y + 2, 150, 0xFF6C6C74);
        }
        // Under the controls, if there's room above the buttons along the bottom.
        int top = ctrlTop();
        if (top + 66 > height - 26 - 16 * callable.size()) return;
        int sy = top + 56;
        if (g.getBoolean("Dead")) {
            context.drawTextWithShadow(textRenderer, Text.literal("Dead."), px, sy, 0xFF8A1A26);
        } else if (g.getBoolean("Caught")) {
            context.drawTextWithShadow(textRenderer, Text.literal("She was one of them."), px, sy, 0xFFE0283C);
        } else if (g.getBoolean("Accused")) {
            context.drawTextWithShadow(textRenderer, Text.literal("Accused. It wasn't her."), px, sy, 0xFF8A1A26);
        } else if (guesses > 0) {
            context.drawTextWrapped(textRenderer, Text.literal("If you think it's her, go and say it to her face."), px, sy, 150, 0xFF8A1A26);
        }
    }

    private int row(DrawContext context, int x, int y, String label, String value) {
        context.drawTextWithShadow(textRenderer, Text.literal(label), x, y, 0xFF6C6C74);
        context.drawTextWithShadow(textRenderer, Text.literal(value), x + 50, y, 0xFFD8D0D4);
        return y + 12;
    }
}
