package com.kokuhaku.client;

import com.kokuhaku.KokuhakuClient;
import com.kokuhaku.game.Outfit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Out-of-school clothes, made from the uniform skins: everything below the head is dyed in
 * the outfit's colours, keeping the shading of the uniform's folds, and the hands left as
 * they are. Worked out once per skin and outfit and kept.
 */
public final class OutfitSkins {
    /** What each player is wearing, as the server last said. */
    private static final Map<UUID, Integer> wearing = new HashMap<>();
    private static final Map<String, Identifier> made = new HashMap<>();

    private OutfitSkins() {
    }

    public static void set(UUID who, int outfit) {
        wearing.put(who, outfit);
    }

    public static void clear() {
        wearing.clear();
    }

    /** The skin to draw for this player: the base one, or the base dyed for what they're wearing. */
    public static Identifier dress(UUID who, Identifier base) {
        Outfit o = Outfit.of(wearing.getOrDefault(who, 0));
        if (o == Outfit.UNIFORM || !ClientRun.active) return base;
        String key = base + "#" + o.name();
        Identifier id = made.get(key);
        if (id != null) return id;
        id = make(base, o);
        made.put(key, id == null ? base : id);
        return id == null ? base : id;
    }

    private static Identifier make(Identifier base, Outfit o) {
        MinecraftClient client = MinecraftClient.getInstance();
        try (InputStream in = client.getResourceManager().getResource(base).orElseThrow().getInputStream()) {
            NativeImage img = NativeImage.read(in);
            for (int y = 16; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int abgr = img.getColor(x, y);
                    int a = abgr >>> 24 & 255;
                    if (a == 0) continue;
                    int r = abgr & 255, g = abgr >> 8 & 255, b = abgr >> 16 & 255;
                    if (isSkin(r, g, b)) continue;
                    int rgb = leg(x, y) ? shoe(x, y) ? o.shoes : o.bottom : o.top;
                    // The uniform's light and shade, in the new colour.
                    float light = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
                    float k = 0.62f + light * 0.75f;
                    int nr = Math.min(255, (int) ((rgb >> 16 & 255) * k)), ng = Math.min(255, (int) ((rgb >> 8 & 255) * k)), nb = Math.min(255, (int) ((rgb & 255) * k));
                    img.setColor(x, y, a << 24 | nb << 16 | ng << 8 | nr);
                }
            }
            Identifier id = new Identifier(KokuhakuClient.MOD_ID, "outfit/" + base.getPath().replaceAll("[^a-z0-9_]", "_") + "_" + o.name().toLowerCase());
            client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    /** Warm and light: a hand or a neck, not cloth. */
    private static boolean isSkin(int r, int g, int b) {
        return r > 150 && r >= g && g >= b && r - b > 35 && g > 95;
    }

    /** Both legs and their overlays, in the 64 by 64 skin layout. */
    private static boolean leg(int x, int y) {
        if (x < 16 && y >= 16 && y < 48) return true;
        return y >= 48 && x < 32;
    }

    /** The bottom few rows of a leg's sides are its shoe. */
    private static boolean shoe(int x, int y) {
        return y % 16 >= 12;
    }
}
