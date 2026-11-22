package com.kokuhaku.screen;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.client.ArmView;
import com.kokuhaku.client.Cutscene;
import com.kokuhaku.client.StudentSkins;
import com.kokuhaku.game.Scenarios;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Invisible screen held open during a cutscene: it swallows every key and
 * click (Esc included) and draws the fades and any news on the black.
 */
public class CutsceneScreen extends Screen {
    private static final float ARM_LENGTH = 0.95f, ARM_ANGLE = -14f, HER_ANGLE = 62f;
    private final Random random = new Random();

    public CutsceneScreen() {
        super(Text.empty());
    }

    /** Your own arm, up in front of you and shaking; on the worse days, her hand closed round it. */
    private void renderArm(DrawContext context) {
        float raised = Cutscene.armRaised();
        if (raised <= 0f || client.player == null) return;
        int strike = Cutscene.strikes;
        int shake = 2 + strike * 2;
        float dx = random.nextInt(shake * 2 + 1) - shake, dy = random.nextInt(shake * 2 + 1) - shake;
        float size = height * ARM_LENGTH;
        float tipX = width * 0.56f + dx, tipY = height * 0.34f + (1f - raised) * height * 0.8f + dy;
        ArmView.draw(context, client.player.getSkinTexture(), ArmView.thin(client.player), tipX, tipY, ARM_ANGLE, size, 1f);
        if (strike >= 2) {
            // Her arm comes in from the side and takes you by the wrist. Hers isn't shaking.
            float wristX = width * 0.56f + size * 0.05f, wristY = tipY + size * 0.3f;
            ArmView.draw(context, StudentSkins.texture(Cutscene.herSkin), true, wristX - dx, wristY - dy, HER_ANGLE, size, 1f);
        }
    }

    /** Lunch, held out in front of you and rocking a little with each step. */
    protected void renderTray(DrawContext context) {
        var tray = com.kokuhaku.client.ClientRun.tray;
        if (tray == null || Cutscene.type() != Cutscene.Type.WALK) return;
        int half = width / 6, top = height - height / 5 + (int) (Math.sin(System.currentTimeMillis() / 170.0) * 3);
        context.fill(width / 2 - half, top, width / 2 + half, height, 0xFF8F8B82);
        context.fill(width / 2 - half + 6, top + 5, width / 2 + half - 6, height, 0xFFB9B4A8);
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f - 24f, top - 14f, 0f);
        context.getMatrices().scale(3f, 3f, 1f);
        context.drawItem(tray, 0, 0);
        context.getMatrices().pop();
    }

    /** Your eyes won't stay open: the dark comes in soft from the top and the bottom. */
    private void renderLids(DrawContext context, float open) {
        int lid = (int) (height * 0.5f * (1f - open));
        int soft = (int) (height * 0.2f);
        context.fill(0, 0, width, lid, 0xFF000000);
        context.fill(0, height - lid, width, height, 0xFF000000);
        context.fillGradient(0, lid, width, lid + soft, 0xFF000000, 0);
        context.fillGradient(0, height - lid - soft, width, height - lid, 0, 0xFF000000);
    }

    protected boolean swallowsInput() {
        return true;
    }

    @Override
    protected void init() {
        // Not Mouse.lockCursor(): that closes the current screen.
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    @Override
    public void removed() {
        // Give the pointer back, or a dialogue opened over us has none.
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return swallowsInput() || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        if (!Cutscene.active()) close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int hurt = (int) (Cutscene.hurt() * 255);
        if (hurt > 0) context.fill(0, 0, width, height, hurt << 24 | 0x500008);
        int white = (int) (Cutscene.flash() * 255);
        if (white > 0) context.fill(0, 0, width, height, white << 24 | 0xFFFFFF);
        int black = (int) (Cutscene.blackness() * 255);
        if (black > 0) context.fill(0, 0, width, height, black << 24);
        renderArm(context);
        renderTray(context);
        if (Cutscene.type() == Cutscene.Type.DRAG) renderLids(context, Cutscene.dragEyes());
        if (Cutscene.type() == Cutscene.Type.BLACK && !Cutscene.text().isEmpty()) {
            renderTyped(context);
            return;
        }
        // Wrapped, so long news still fits on a narrow screen. Blank lines stay as pauses.
        List<OrderedText> parts = new ArrayList<>();
        for (String line : Cutscene.text().split("\n")) {
            if (line.isEmpty()) parts.add(null);
            else parts.addAll(textRenderer.wrapLines(KokuhakuClient.menuText(line), Math.max(40, width - 40)));
        }
        int y = Cutscene.type() == Cutscene.Type.JEALOUS ? height - 40 - (parts.size() - 1) * 14 : height / 2 - parts.size() * 7;
        for (OrderedText part : parts) {
            if (part != null) context.drawCenteredTextWithShadow(textRenderer, part, width / 2, y, 0xFFD01C30);
            y += 14;
        }
    }

    private static final long BLANK_PAUSE_MS = 400, UNSAID_HOLD_MS = 700;
    private static final double DELETE_CPS = 60;
    private long lastElapsed = 0;
    private int clicksTyped = 0, clicksDeleted = 0;

    /** The night, typed out a letter at a time. A line that starts as UNSAID gets taken back. */
    private void renderTyped(DrawContext context) {
        String[] lines = Cutscene.text().split("\n");
        int total = 0;
        for (String line : lines) total += visible(line).length();
        double cps = Math.max(40, total / (0.6 * Cutscene.durationMs() / 1000.0));
        long elapsed = Cutscene.elapsedMs();
        if (elapsed < lastElapsed) {
            // A new scene.
            clicksTyped = 0;
            clicksDeleted = 0;
        }
        lastElapsed = elapsed;
        double clock = Math.max(0, elapsed);
        int typed = 0, deleted = 0;
        List<String> shown = new ArrayList<>();
        for (String raw : lines) {
            if (clock <= 0) break;
            if (raw.isEmpty()) {
                shown.add("");
                clock -= BLANK_PAUSE_MS;
                continue;
            }
            boolean unsaid = raw.startsWith(Scenarios.UNSAID);
            String line = visible(raw);
            double typing = line.length() * 1000.0 / cps;
            if (clock < typing) {
                int n = Math.min(line.length(), (int) Math.floor(clock * cps / 1000.0));
                shown.add(line.substring(0, n));
                typed += n;
                break;
            }
            typed += line.length();
            clock -= typing;
            if (!unsaid) {
                shown.add(line);
                continue;
            }
            if (clock < UNSAID_HOLD_MS) {
                shown.add(line);
                break;
            }
            clock -= UNSAID_HOLD_MS;
            double deleting = line.length() * 1000.0 / DELETE_CPS;
            if (clock < deleting) {
                int gone = Math.min(line.length(), (int) Math.floor(clock * DELETE_CPS / 1000.0));
                shown.add(line.substring(0, line.length() - gone));
                deleted += gone;
                break;
            }
            deleted += line.length();
            clock -= deleting;
            shown.add("");
        }
        // Keys under someone's fingers, somewhere.
        if (typed / 3 > clicksTyped) {
            clicksTyped = typed / 3;
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.6f + random.nextFloat() * 0.3f, 0.06f));
        }
        if (deleted / 3 > clicksDeleted) {
            clicksDeleted = deleted / 3;
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.2f, 0.06f));
        }
        // Laid out as it will end, so nothing jumps about while it's typed.
        int wrap = Math.max(40, width - 40), rows = 0;
        for (String line : lines) {
            rows += line.isEmpty() || line.startsWith(Scenarios.UNSAID) ? 1 : textRenderer.wrapLines(KokuhakuClient.menuText(line), wrap).size();
        }
        int y = height / 2 - rows * 7;
        for (String line : shown) {
            if (line.isEmpty()) {
                y += 14;
                continue;
            }
            for (OrderedText part : textRenderer.wrapLines(KokuhakuClient.menuText(line), wrap)) {
                context.drawCenteredTextWithShadow(textRenderer, part, width / 2, y, 0xFFD01C30);
                y += 14;
            }
        }
    }

    private static String visible(String line) {
        return line.startsWith(Scenarios.UNSAID) ? line.substring(Scenarios.UNSAID.length()) : line;
    }
}
