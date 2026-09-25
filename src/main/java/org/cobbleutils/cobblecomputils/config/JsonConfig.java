package org.cobbleutils.cobblecomputils.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import org.cobbleutils.cobblecomputils.Cobblecomputils;

/** Loads a JSON config from {@code config/cobblecomputils/<name>}, writing defaults on first start. */
public final class JsonConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonConfig() {
    }

    /** Returns the loaded config, or {@code previous} if the file can't be read or parsed. */
    public static <T> T load(String fileName, Class<T> type, Supplier<T> defaults, T previous) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(Cobblecomputils.MOD_ID).resolve(fileName);
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path)) {
                    GSON.toJson(defaults.get(), writer);
                }
            }
            try (Reader reader = Files.newBufferedReader(path)) {
                T loaded = GSON.fromJson(reader, type);
                if (loaded == null) {
                    throw new JsonParseException("empty file");
                }
                return loaded;
            }
        } catch (IOException | JsonParseException e) {
            Cobblecomputils.LOGGER.error("Could not read {}, keeping previous settings", path, e);
            return previous;
        }
    }
}
