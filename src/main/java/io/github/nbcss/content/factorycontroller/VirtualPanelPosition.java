package io.github.nbcss.content.factorycontroller;

import net.minecraft.nbt.CompoundTag;

public record VirtualPanelPosition(int x, int y) {

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", x);
        tag.putInt("Y", y);
        return tag;
    }

    public static VirtualPanelPosition fromNBT(CompoundTag tag) {
        return new VirtualPanelPosition(tag.getInt("X"), tag.getInt("Y"));
    }
}
