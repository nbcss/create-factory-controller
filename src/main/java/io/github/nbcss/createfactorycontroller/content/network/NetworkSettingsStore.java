package io.github.nbcss.createfactorycontroller.content.network;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-global store of {@link NetworkSettings} keyed by logistics-network UUID (one {@link SavedData} on
 * the overworld, since networks are server-wide and their name/icon are <b>shared across every
 * controller</b>). An entry only exists for a network the player has customized; resetting a network to
 * default removes its entry (see {@link #set}). There is no automatic GC — settings persist until
 * explicitly cleared, so a network torn down and rebuilt keeps its name/icon.
 */
public class NetworkSettingsStore extends SavedData {

    private static final String FILE_ID = "createfactorycontroller_network_settings";

    private final Map<UUID, NetworkSettings> settings = new HashMap<>();

    public NetworkSettingsStore() {}

    public static SavedData.Factory<NetworkSettingsStore> factory() {
        return new SavedData.Factory<>(NetworkSettingsStore::new, NetworkSettingsStore::load, null);
    }

    public static NetworkSettingsStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    /** Convenience for code holding a server {@link Level}; returns null on the client. */
    public static NetworkSettingsStore get(Level level) {
        return level instanceof ServerLevel sl ? get(sl.getServer()) : null;
    }

    // ── Access ────────────────────────────────────────────────────────────────

    public NetworkSettings get(UUID network) {
        return settings.getOrDefault(network, NetworkSettings.defaultFor(network));
    }

    /** Stores {@code value} for {@code network}; a default value removes the entry (the explicit-clear path). */
    public void set(UUID network, NetworkSettings value) {
        if (value.isDefault()) settings.remove(network);
        else settings.put(network, value);
        setDirty();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, NetworkSettings> e : settings.entrySet()) {
            CompoundTag entry = e.getValue().save(registries);
            entry.putUUID("Id", e.getKey());
            list.add(entry);
        }
        tag.put("Networks", list);
        return tag;
    }

    public static NetworkSettingsStore load(CompoundTag tag, HolderLookup.Provider registries) {
        NetworkSettingsStore store = new NetworkSettingsStore();
        ListTag list = tag.getList("Networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID id = entry.getUUID("Id");
            NetworkSettings value = NetworkSettings.load(id, entry, registries);
            if (!value.isDefault()) store.settings.put(id, value);
        }
        return store;
    }
}
