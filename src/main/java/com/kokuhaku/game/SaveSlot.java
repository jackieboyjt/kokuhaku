package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The one saved run. It's written every morning, before the day starts, so
 * Continue puts you back in bed that morning. Nothing can be saved over it: a
 * new run can't start until this one's finished, one way or the other.
 * It lives beside the game, not inside a world, because every launch builds the
 * town fresh.
 */
public final class SaveSlot {
    private static final String FOLDER = "kokuhaku-save", RUN = "run.dat", SUMMARY = "summary.txt";

    /** What the menu shows about it. */
    public record Summary(int day, String difficulty, String players, boolean devoted) {
    }

    private SaveSlot() {
    }

    private static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve(FOLDER);
    }

    public static boolean exists() {
        return Files.isRegularFile(dir().resolve(RUN));
    }

    public static Summary summary() {
        try {
            int day = 1;
            String difficulty = "", players = "";
            boolean devoted = false;
            for (String line : Files.readAllLines(dir().resolve(SUMMARY), StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq), value = line.substring(eq + 1);
                switch (key) {
                    case "day" -> day = Integer.parseInt(value.trim());
                    case "difficulty" -> difficulty = value.trim();
                    case "players" -> players = value.trim();
                    case "devoted" -> devoted = Boolean.parseBoolean(value.trim());
                    default -> {
                    }
                }
            }
            return new Summary(day, difficulty, players, devoted);
        } catch (Exception e) {
            return new Summary(1, "", "", false);
        }
    }

    /**
     * The run as it stands. morning: the night's over and the day hasn't begun, so
     * loading it starts that next day; otherwise it's the very first morning.
     */
    static void write(RunState s, boolean morning, Collection<UUID> gone, Collection<UUID> ghosts, UUID accomplice, List<ServerPlayerEntity> players) {
        if (System.getenv("KOKUHAKU_NOSAVE") != null) return;
        NbtCompound save = new NbtCompound();
        save.put("State", s.writeNbt(new NbtCompound()));
        save.putBoolean("Morning", morning);
        NbtList out = new NbtList();
        for (UUID id : gone) out.add(NbtString.of(id.toString()));
        save.put("Gone", out);
        // Of those, who'd been called back. Older saves don't have it.
        NbtList called = new NbtList();
        for (UUID id : ghosts) called.add(NbtString.of(id.toString()));
        save.put("Ghosts", called);
        if (accomplice != null) {
            save.putString("Accomplice", accomplice.toString());
            // Older saves have only who: the name's filled in when they're back, and no leads were used.
            Accomplice.write(save);
        }
        NbtCompound bags = new NbtCompound();
        StringBuilder names = new StringBuilder();
        for (ServerPlayerEntity p : players) {
            if (!s.party.contains(p.getUuid())) continue;
            bags.put(p.getUuid().toString(), p.getInventory().writeNbt(new NbtList()));
            if (names.length() > 0) names.append(", ");
            names.append(p.getGameProfile().getName());
        }
        save.put("Bags", bags);
        // Older saves have neither: the phone starts empty and the plan from nothing.
        save.put("Phone", Phone.write(s));
        save.put("Kept", Kept.write());
        // The day you'll wake up on.
        int day = morning ? s.day + 1 : s.day;
        try {
            Files.createDirectories(dir());
            Path temp = dir().resolve(RUN + ".new");
            NbtIo.writeCompressed(save, temp.toFile());
            Files.move(temp, dir().resolve(RUN), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.writeString(dir().resolve(SUMMARY), "day=" + day + "\ndifficulty=" + s.difficulty.name() + "\nplayers=" + names + "\ndevoted=" + s.devoted + "\n", StandardCharsets.UTF_8);
            Kokuhaku.LOG.info("Saved: day {}, {}", day, names);
        } catch (IOException e) {
            Kokuhaku.LOG.warn("Couldn't save the run", e);
        }
    }

    /** The saved run, or null if it can't be read: then it's put aside, so the slot's free again. */
    static NbtCompound read() {
        try {
            return NbtIo.readCompressed(dir().resolve(RUN).toFile());
        } catch (Exception e) {
            Kokuhaku.LOG.warn("Couldn't read the saved run", e);
            quarantine();
            return null;
        }
    }

    /** A save that can't be loaded. It's kept beside the slot, not deleted, so it can be looked at. */
    static void quarantine() {
        try {
            if (Files.exists(dir().resolve(RUN))) Files.move(dir().resolve(RUN), dir().resolve(RUN + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(dir().resolve(SUMMARY));
            Kokuhaku.LOG.warn("The saved run was put aside as {}.broken", RUN);
        } catch (IOException e) {
            Kokuhaku.LOG.warn("Couldn't put the saved run aside", e);
        }
    }

    /** It's over: the slot's free. */
    static void delete() {
        try {
            Files.deleteIfExists(dir().resolve(RUN));
            Files.deleteIfExists(dir().resolve(SUMMARY));
            Kokuhaku.LOG.info("Run finished: the save slot is free");
        } catch (IOException e) {
            Kokuhaku.LOG.warn("Couldn't clear the saved run", e);
        }
    }
}
