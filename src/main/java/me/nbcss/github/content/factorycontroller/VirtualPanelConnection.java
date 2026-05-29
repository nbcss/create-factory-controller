package me.nbcss.github.content.factorycontroller;

import net.minecraft.nbt.CompoundTag;

public class VirtualPanelConnection {

    public VirtualPanelPosition from;
    public int amount;
    public int arrowBendMode; // -1 = auto, 0-3 = fixed bend direction
    public boolean success;

    public VirtualPanelConnection(VirtualPanelPosition from, int amount) {
        this.from = from;
        this.amount = amount;
        this.arrowBendMode = -1;
        this.success = false;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("From", from.toNBT());
        tag.putInt("Amount", amount);
        tag.putInt("ArrowBendMode", arrowBendMode);
        tag.putBoolean("Success", success);
        return tag;
    }

    public static VirtualPanelConnection fromNBT(CompoundTag tag) {
        VirtualPanelPosition pos = VirtualPanelPosition.fromNBT(tag.getCompound("From"));
        VirtualPanelConnection conn = new VirtualPanelConnection(pos, tag.getInt("Amount"));
        conn.arrowBendMode = tag.getInt("ArrowBendMode");
        conn.success = tag.getBoolean("Success");
        return conn;
    }
}
