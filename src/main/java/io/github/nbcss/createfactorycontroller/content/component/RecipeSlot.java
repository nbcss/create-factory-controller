package io.github.nbcss.createfactorycontroller.content.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

/**
 * One cell of a CUSTOM-arrangement gauge's 3×3 ingredient grid: the connected {@code source} it draws from
 * plus a {@code count}, or {@link #EMPTY} for a gap. The displayed/dispatched item is the source gauge's
 * filter, resolved live (so a source whose wire is removed makes the slot empty).
 */
public record RecipeSlot(@Nullable VirtualComponentPosition source, int count) {

    public static final RecipeSlot EMPTY = new RecipeSlot(null, 0);

    public boolean isEmpty() { return source == null || count <= 0; }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        if (!isEmpty()) {
            tag.put("Source", source.toNBT());
            tag.putInt("Count", count);
        }
        return tag;
    }

    public static RecipeSlot fromNBT(CompoundTag tag) {
        if (!tag.contains("Source")) return EMPTY;
        return new RecipeSlot(VirtualComponentPosition.fromNBT(tag.getCompound("Source")),
                Math.max(1, tag.getInt("Count")));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(!isEmpty());
        if (!isEmpty()) {
            SyncCodecs.writePos(buf, source);
            buf.writeVarInt(count);
        }
    }

    public static RecipeSlot read(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return EMPTY;
        return new RecipeSlot(SyncCodecs.readPos(buf), Math.max(1, buf.readVarInt()));
    }
}
