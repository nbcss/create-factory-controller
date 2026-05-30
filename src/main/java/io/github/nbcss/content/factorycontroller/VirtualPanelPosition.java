package io.github.nbcss.content.factorycontroller;

import net.minecraft.nbt.CompoundTag;

public record VirtualPanelPosition(int col, int row) {

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Col", col);
        tag.putInt("Row", row);
        return tag;
    }

    public static VirtualPanelPosition fromNBT(CompoundTag tag) {
        return new VirtualPanelPosition(tag.getInt("Col"), tag.getInt("Row"));
    }
}
