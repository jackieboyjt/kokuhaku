package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.ClientRun;
import com.kokuhaku.client.KeyBindings;
import com.kokuhaku.game.Clothes;
import com.kokuhaku.game.Eateries;
import com.kokuhaku.game.Schedule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Your diary: the week's lessons, with today's column and the current period highlighted. */
public class TimetableScreen extends Screen {
    /** The back page: the town's hours instead of the school day. Stays where you left it. */
    private static boolean townPage = false;

    public TimetableScreen() {
        super(KokuhakuClient.menuText("Diary"));
    }

    @Override
    protected void init() {
        int colW = Math.min(100, (width - 90) / 5);
        int left = (width - (60 + colW * 5)) / 2;
        addDrawableChild(new MenuButton(left + 60 + colW * 5 - 70, 12, 70, 14, KokuhakuClient.menuText(townPage ? "Lessons" : "In town"), b -> {
            townPage = !townPage;
            clearAndInit();
        }));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyBindings.TIMETABLE.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xD0000000);
        int colW = Math.min(100, (width - 90) / 5);
        int left = (width - (60 + colW * 5)) / 2;
        int top = 46;
        int rowH = 16;

        Icons.draw(context, Icons.CLOCK, left, 12, 1);
        context.drawTextWithShadow(textRenderer, title, left + 22, 17, 0xFFE8E2E4);

        int today = Schedule.weekday(ClientRun.day);
        int period = currentPeriod();

        for (int d = 0; d < 5; d++) {
            int x = left + 60 + d * colW;
            if (d == today) context.fill(x, top - 14, x + colW - 2, top + rowH * 6, 0x30E0283C);
            context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText(Schedule.WEEKDAYS[d].substring(0, 3)),
                    x + 4, top - 11, d == today ? 0xFFE0283C : 0xFF9A9AA2);
        }
        // Days the school was shut, up to today. A snow day you haven't had yet isn't in the diary.
        boolean[] closed = new boolean[5];
        for (int d = 0; d < 5; d++) {
            int date = (ClientRun.day - 1) / 7 * 7 + d + 1;
            int x = left + 60 + d * colW;
            if (date <= ClientRun.day && Schedule.isSnowDay(date)) {
                closed[d] = true;
                for (int p = 0; p < 6; p++) context.fill(x, top + p * rowH, x + colW - 2, top + p * rowH + rowH - 2, 0x90000000);
                Text shut = KokuhakuClient.menuText("Closed");
                context.drawTextWithShadow(textRenderer, shut, x + (colW - 2 - textRenderer.getWidth(shut)) / 2, top + 2 * rowH + 4, 0xFF9A9AA2);
            } else if (date < ClientRun.day) {
                for (int p = 0; p < 6; p++) context.fill(x, top + p * rowH, x + colW - 2, top + p * rowH + rowH - 2, 0x40000000);
            }
        }
        for (int p = 0; p < 6; p++) {
            int y = top + p * rowH;
            context.drawTextWithShadow(textRenderer, Text.literal(Schedule.clock(Schedule.PERIOD_START[p])), left, y + 4, 0xFF6C6C74);
            for (int d = 0; d < 5; d++) {
                if (closed[d]) continue;
                List<String> subjects = Schedule.subjects(ClientRun.week, d);
                int x = left + 60 + d * colW;
                boolean now = d == today && p == period;
                if (now) context.fill(x, y, x + colW - 2, y + rowH - 2, 0x80E0283C);
                String s = subjects.get(p);
                int number = Schedule.roomNumber(s);
                int date = (ClientRun.day - 1) / 7 * 7 + d + 1;
                String room = number == 0 ? (Schedule.swimming(date) ? "Pool" : "Court") : Schedule.practical(s, date) ? (s.equals("Science") ? "Lab" : "Kit") : Schedule.roomKey(number);
                int roomW = textRenderer.getWidth(room);
                // Too long for the box: written smaller, down to three quarters, before anything's cut off.
                int room4 = colW - 12 - roomW, full = textRenderer.getWidth(s);
                float k = full <= room4 ? 1f : Math.max(0.75f, room4 / (float) full);
                String shown = textRenderer.trimToWidth(s, (int) (room4 / k));
                context.getMatrices().push();
                context.getMatrices().translate(x + 4, y + 4 + (1 - k) * 4, 0);
                context.getMatrices().scale(k, k, 1f);
                context.drawTextWithShadow(textRenderer, Text.literal(shown), 0, 0, now ? 0xFFFFFFFF : 0xFFD8D0D4);
                context.getMatrices().pop();
                context.drawTextWithShadow(textRenderer, Text.literal(room), x + colW - 5 - roomW, y + 4, now ? 0xFFFFFFFF : 0xFF8A8A92);
            }
        }

        int y = top + rowH * 6 + 14;
        List<String[]> rest = new ArrayList<>();
        if (townPage) {
            rest.addAll(Eateries.diary());
            rest.add(Clothes.diary());
            String[] shifts = {"After school", "Shifts: Konbini, Station Kiosk, Library Aide."};
            rest.add(shifts);
            rest.add(new String[]{"Mondays", "Pocket money on the kitchen table: " + Schedule.yen(2000) + "."});
            // A short window loses the shifts first. The locker line and the weekend/snow line come after the list.
            int trailing = 12 + (Schedule.isSchoolDay(ClientRun.day) ? 0 : 14);
            if (y + rest.size() * 11 + trailing > height - 8) rest.remove(shifts);
        } else {
            rest.add(new String[]{Schedule.clock(Schedule.WAKE), "Wake up"});
            rest.add(new String[]{Schedule.clock(Schedule.HOMEROOM), "Homeroom, Room " + Schedule.roomKey(Schedule.HOMEROOM_2A)});
            rest.add(new String[]{Schedule.clock(Schedule.LUNCH), "Lunch"});
            rest.add(new String[]{Schedule.clock(Schedule.AFTER_SCHOOL), "After school: clubs, visiting houses. You can sleep now."});
            rest.add(new String[]{Schedule.clock(ClientRun.difficulty.curfew), "Curfew. Be inside your home. Don't be late."});
        }
        for (String[] r : rest) {
            context.drawTextWithShadow(textRenderer, Text.literal(r[0]), left, y, 0xFF6C6C74);
            context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(r[1], colW * 5)), left + 60, y, 0xFFB0A8AC);
            y += 11;
        }
        context.drawTextWithShadow(textRenderer, Text.literal("Shoe locker number " + ClientRun.locker + ". Lock your front door: key in slot 2."), left, y + 2, 0xFF9A9AA2);
        y += 12;
        if (!Schedule.isSchoolDay(ClientRun.day)) {
            // A snow day shuts the school on a weekday too.
            String why = Schedule.isSnowDay(ClientRun.day)
                    ? "It snowed in the night. The school's shut today."
                    : "It's the weekend. No school today.";
            context.drawTextWithShadow(textRenderer, KokuhakuClient.menuText(why), left, y + 6, 0xFFE0283C);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private int currentPeriod() {
        if (!Schedule.isSchoolDay(ClientRun.day)) return -1;
        for (int p = 0; p < Schedule.PERIOD_START.length; p++) {
            int start = Schedule.PERIOD_START[p];
            if (ClientRun.minute >= start && ClientRun.minute < start + Schedule.PERIOD_LENGTH) return p;
        }
        return -1;
    }
}
