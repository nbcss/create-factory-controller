package io.github.nbcss.createfactorycontroller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Some client-side state, store in independent file to prevent player to see those options in config GUI.
 */
public final class ClientSettings {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "createfactorycontroller-client-settings.json";
    private static final String VERSION_KEY = "version";
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String COMPONENT_SUPPLY_MODE = "componentSupplyMode";
    private static final String COMPONENT_SUPPLY_HOTBAR = "componentSupplyHotbar";

    @Nullable
    private static volatile JsonObject root;
    /** The newest file content not yet written; writers take it under {@link #WRITE_LOCK}, so stale content never wins. */
    private static final AtomicReference<String> PENDING = new AtomicReference<>();
    private static final Object WRITE_LOCK = new Object();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ClientSettings::writePending, "ClientSettings flush"));
    }

    private ClientSettings() {}

    /** Reads the file; called from client setup so the render thread never waits on it. */
    public static void load() {
        if (root == null) root = read();
    }

    /** Whether the Controller screen's inventory panel opens on the Component Supply tab. */
    public static boolean componentSupplyMode() {
        return get(COMPONENT_SUPPLY_MODE) instanceof JsonPrimitive primitive && primitive.isBoolean()
                && primitive.getAsBoolean();
    }

    public static void setComponentSupplyMode(boolean enabled) {
        set(COMPONENT_SUPPLY_MODE, new JsonPrimitive(enabled));
    }

    /** The Component Supply hotbar's item ids ({@code ""} for an empty slot), or {@code null} until first edited. */
    @Nullable
    public static List<String> componentSupplyHotbar() {
        if (!(get(COMPONENT_SUPPLY_HOTBAR) instanceof JsonArray array)) return null;
        List<String> ids = new ArrayList<>(array.size());
        for (JsonElement value : array) ids.add(value.isJsonPrimitive() ? value.getAsString() : "");
        return ids;
    }

    public static void setComponentSupplyHotbar(List<String> ids) {
        JsonArray array = new JsonArray();
        ids.forEach(array::add);
        set(COMPONENT_SUPPLY_HOTBAR, array);
    }

    @Nullable
    private static JsonElement get(String key) {
        return root().get(key);
    }

    private static void set(String key, JsonElement value) {
        JsonObject root = root();
        root.add(key, value);
        save(root);
    }

    private static JsonObject root() {
        JsonObject loaded = root;
        if (loaded != null) return loaded;
        LOGGER.warn("Client settings used before client setup loaded them; reading now");
        load();
        return root;
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static JsonObject read() {
        Path path = path();
        if (!Files.isRegularFile(path)) return new JsonObject();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonObject()) return migrate(parsed.getAsJsonObject());
            LOGGER.warn("Ignoring {}: not a JSON object", path);
        } catch (Exception e) {
            LOGGER.warn("Ignoring unreadable {}", path, e);
        }
        return new JsonObject();
    }

    private static JsonObject migrate(JsonObject root) {
        // No format changes yet; a future VERSION bump upgrades older files here.
        root.remove(VERSION_KEY);
        return root;
    }

    /** Serialises now (the tree is only touched on the render thread) and writes on an IO worker. */
    private static void save(JsonObject root) {
        JsonObject out = new JsonObject();
        out.addProperty(VERSION_KEY, VERSION);
        root.entrySet().forEach(entry -> out.add(entry.getKey(), entry.getValue()));
        // Only the first change since the last write schedules one; later changes just replace the content.
        if (PENDING.getAndSet(GSON.toJson(out)) == null) {
            Util.ioPool().execute(ClientSettings::writePending);
        }
    }

    private static void writePending() {
        synchronized (WRITE_LOCK) {
            String content = PENDING.getAndSet(null);
            if (content != null) write(content);
        }
    }

    private static void write(String content) {
        Path target = path();
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".saving-", ".json.tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
        } catch (IOException e) {
            LOGGER.warn("Failed to save {}", target, e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
