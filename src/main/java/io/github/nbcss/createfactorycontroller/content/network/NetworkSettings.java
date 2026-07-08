package io.github.nbcss.createfactorycontroller.content.network;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Shared, per-network customization: the network's UUID plus an optional display {@code name} and
 * {@code icon} item. Self-describing, so it derives its own {@link #color()} and {@link #displayName()} —
 * the client always holds one of these per known network (see {@link #defaultFor}), with the customized
 * fields possibly empty. A value where both name and icon are blank {@link #isDefault() is default} and is
 * never stored (its store entry is dropped, see {@link NetworkSettingsStore#set}).
 *
 * <p>Note: {@link ItemStack} has no content-aware {@code equals}, so use {@link #matches} rather than
 * {@code equals} to compare two settings.</p>
 */
public class NetworkSettings {
    public static final int DARK_FILL = 0xFF3A3A3A;
    public static final int LIGHT_FILL = 0xFFE8DFD3;
    public final UUID network;
    public final String name;
    public final ItemStack icon;
    private int color;

    public NetworkSettings(UUID network, String name, ItemStack icon) {
        this.network = network;
        this.name = name == null ? "" : name;
        this.icon = icon == null ? ItemStack.EMPTY : icon;
        try {
            color = Integer.parseInt(network.toString().substring(0, 6), 16);
        } catch (NumberFormatException e) {
            color = 0xFFFFFF;
        }
    }

    /** The uncustomized settings for {@code network} (blank name + empty icon). */
    public static NetworkSettings defaultFor(UUID network) {
        return new NetworkSettings(network, "", ItemStack.EMPTY);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkSettings> STREAM_CODEC =
        StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, NetworkSettings::network,
            ByteBufCodecs.STRING_UTF8, NetworkSettings::name,
            ItemStack.OPTIONAL_STREAM_CODEC, NetworkSettings::icon,
            NetworkSettings::new
        );

    // ── Derivation ──────────────────────────────────────────────────────────────

    public UUID network() { return network;}
    public String name() { return name; }
    public ItemStack icon() { return icon; }
    public int color() { return color; }

    /** True when nothing is customized. */
    public boolean isDefault()     { return name.isBlank() && icon.isEmpty(); }
    public boolean hasCustomName() { return !name.isBlank(); }
    public boolean hasCustomIcon() { return !icon.isEmpty(); }

    /** Display name: the custom name, or a default "Network #XXXX" from the first 6 UUID chars. */
    public Component displayName() {
        return hasCustomName()
            ? Component.literal(name)
            : Component.translatable("createfactorycontroller.network.default",
                                     network.toString().substring(0, 6).toUpperCase());
    }

    public int backgroundColor() {
        int color = color();
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return luminance > 0.5 ? DARK_FILL : LIGHT_FILL;
    }

    /** Content-aware comparison (the record's generated {@code equals} can't compare {@link ItemStack}s). */
    public boolean matches(NetworkSettings other) {
        return other != null && network.equals(other.network)
            && name.equals(other.name) && ItemStack.matches(icon, other.icon);
    }

    // ── NBT (name + icon; the UUID is the store's map key) ───────────────────────

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!name.isBlank()) tag.putString("Name", name);
        if (!icon.isEmpty()) tag.put("Icon", icon.save(registries));
        return tag;
    }

    public static NetworkSettings load(UUID network, CompoundTag tag, HolderLookup.Provider registries) {
        String name = tag.getString("Name");   // "" when absent
        ItemStack icon = tag.contains("Icon")
            ? ItemStack.parseOptional(registries, tag.getCompound("Icon"))
            : ItemStack.EMPTY;
        return new NetworkSettings(network, name, icon);
    }
}
