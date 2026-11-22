package com.kokuhaku.client;

import com.kokuhaku.Kokuhaku;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** What the Settings screen keeps that Minecraft doesn't: how the window sits, the blood, the microphone. */
public final class GameSettings {
    public enum WindowMode { WINDOWED("Windowed"), BORDERLESS("Borderless"), FULLSCREEN("Fullscreen");

        public final String label;

        WindowMode(String label) {
            this.label = label;
        }
    }

    public enum Quality { LOW("Low", 6), MEDIUM("Medium", 8), HIGH("High", 12);

        public final String label;
        public final int viewDistance;

        Quality(String label, int viewDistance) {
            this.label = label;
            this.viewDistance = viewDistance;
        }
    }

    private static final int WINDOW_W = 1280, WINDOW_H = 720;

    public static WindowMode windowMode = WindowMode.FULLSCREEN;
    /** How loud the radio is, 0..1. */
    public static float radioLevel = 1f;
    public static Quality quality = Quality.MEDIUM;
    public static boolean noBlood = false;
    /** The name of the microphone, or empty for whatever Windows says. */
    public static String microphone = "";
    private static boolean loaded = false, applied = false;
    /** Leaving fullscreen only happens on the next frame, so the window's size waits for it. */
    private static boolean pendingGeometry = false;

    private GameSettings() {
    }

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("kokuhaku-settings.properties");
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        Properties p = new Properties();
        if (Files.exists(file())) {
            try (Reader in = Files.newBufferedReader(file())) {
                p.load(in);
            } catch (IOException e) {
                Kokuhaku.LOG.warn("Couldn't read settings: {}", e.getMessage());
            }
        }
        // Each on its own, so one bad line doesn't lose the rest.
        windowMode = parse(WindowMode.class, p.getProperty("windowMode"), windowMode);
        try {
            float level = Float.parseFloat(p.getProperty("radioLevel", "1"));
            radioLevel = Float.isNaN(level) ? 1f : MathHelper.clamp(level, 0f, 1f);
        } catch (IllegalArgumentException e) {
            Kokuhaku.LOG.warn("Ignoring a setting I don't recognise: {}", e.getMessage());
        }
        quality = parse(Quality.class, p.getProperty("quality"), quality);
        RadioPlayer.level = radioLevel;
        noBlood = Boolean.parseBoolean(p.getProperty("noBlood", "false"));
        microphone = p.getProperty("microphone", "");
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            Kokuhaku.LOG.warn("Ignoring a setting I don't recognise: {}", e.getMessage());
            return fallback;
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("windowMode", windowMode.name());
        p.setProperty("radioLevel", Float.toString(radioLevel));
        p.setProperty("quality", quality.name());
        p.setProperty("noBlood", Boolean.toString(noBlood));
        p.setProperty("microphone", microphone);
        try (Writer out = Files.newBufferedWriter(file())) {
            p.store(out, "Kept by the Settings screen");
        } catch (IOException e) {
            Kokuhaku.LOG.warn("Couldn't save settings: {}", e.getMessage());
        }
    }

    /** Called every tick: the saved window comes back once the game's up. */
    public static void tick(MinecraftClient client) {
        load();
        RadioPlayer.tick(client);
        // Once the game's really out of fullscreen, the window can take its size.
        if (pendingGeometry && client.getWindow() != null && GLFW.glfwGetWindowMonitor(client.getWindow().getHandle()) == 0L) {
            pendingGeometry = false;
            placeWindow(client.getWindow().getHandle());
        }
        if (applied || client.getWindow() == null) return;
        applied = true;
        // From the very start, not just once someone's opened Settings: the default's what runs out of memory.
        if (client.options.getViewDistance().getValue() != quality.viewDistance) {
            client.options.getViewDistance().setValue(quality.viewDistance);
            client.options.write();
        }
        // Developer testing only: test runs stay in a window.
        if (System.getenv("KOKUHAKU_AUTOPLAY") != null && !"1".equals(System.getenv("KOKUHAKU_FULLSCREEN"))) return;
        if (windowMode != WindowMode.WINDOWED) applyWindow(client);
    }

    public static void applyWindow(MinecraftClient client) {
        Window window = client.getWindow();
        boolean full = windowMode == WindowMode.FULLSCREEN;
        pendingGeometry = false;
        if (window.isFullscreen() != full) {
            client.options.getFullscreen().setValue(full);
            client.options.write();
            // The switch itself happens next frame; tick() sizes the window after it.
            if (!full) pendingGeometry = true;
            return;
        }
        if (full) return;
        if (GLFW.glfwGetWindowMonitor(window.getHandle()) != 0L) pendingGeometry = true;
        else placeWindow(window.getHandle());
    }

    private static void placeWindow(long handle) {
        boolean borderless = windowMode == WindowMode.BORDERLESS;
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, borderless ? GLFW.GLFW_FALSE : GLFW.GLFW_TRUE);
        GLFWVidMode screen = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        if (screen == null) return;
        if (borderless) {
            GLFW.glfwSetWindowPos(handle, 0, 0);
            GLFW.glfwSetWindowSize(handle, screen.width(), screen.height());
        } else {
            GLFW.glfwSetWindowSize(handle, WINDOW_W, WINDOW_H);
            GLFW.glfwSetWindowPos(handle, (screen.width() - WINDOW_W) / 2, (screen.height() - WINDOW_H) / 2);
        }
    }

    /** Through Iris's public API, which isn't there when compiling. False if there's no Iris at all. */
    public static boolean setShaders(boolean on) {
        try {
            Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
            Object config = api.getClass().getMethod("getConfig").invoke(api);
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
            type.getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, on);
            return true;
        } catch (ReflectiveOperationException e) {
            Kokuhaku.LOG.warn("Couldn't change shaders: {}", e.toString());
            return false;
        }
    }

    public static boolean shadersOn() {
        try {
            Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
            Object config = api.getClass().getMethod("getConfig").invoke(api);
            return (boolean) Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("areShadersEnabled").invoke(config);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Whether a shader pack's really running, as opposed to switched on with nothing to run. */
    public static boolean shaderPackRunning() {
        try {
            Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
            return (boolean) api.getClass().getMethod("isShaderPackInUse").invoke(api);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static void applyQuality(MinecraftClient client) {
        client.options.getViewDistance().setValue(quality.viewDistance);
        client.options.write();
        client.worldRenderer.scheduleTerrainUpdate();
    }
}
