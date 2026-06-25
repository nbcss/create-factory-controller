package io.github.nbcss.createfactorycontroller.content.component;

import net.minecraft.nbt.CompoundTag;

public record VirtualComponentPosition(int x, int y) {

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", x);
        tag.putInt("Y", y);
        return tag;
    }

    public static VirtualComponentPosition fromNBT(CompoundTag tag) {
        return new VirtualComponentPosition(tag.getInt("X"), tag.getInt("Y"));
    }
}
