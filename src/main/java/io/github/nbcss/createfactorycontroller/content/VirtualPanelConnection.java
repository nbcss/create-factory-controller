package io.github.nbcss.createfactorycontroller.content;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

public class VirtualPanelConnection {

    public VirtualPanelPosition from;
    /**
     * Per-slot input amounts. One entry per grid slot this connection occupies (always ≥1 entry).
     * A connection "repeated" across several slots holds multiple entries — all share this single
     * link, and the {@link #totalAmount() sum} of the entries is the required input for a request.
     */
    public List<Integer> amounts;
    public int arrowBendMode; // -1 = auto, 0-3 = fixed bend direction
    public boolean success;

    public VirtualPanelConnection(VirtualPanelPosition from, int amount) {
        this.from = from;
        this.amounts = new ArrayList<>();
        this.amounts.add(Math.max(1, amount));
        this.arrowBendMode = -1;
        this.success = false;
    }

    /** Total required input across every repeated slot (≥1). */
    public int totalAmount() {
        int sum = 0;
        for (int a : amounts) sum += Math.max(1, a);
        return Math.max(1, sum);
    }

    /** Number of grid slots this connection occupies (≥1). */
    public int repeatCount() {
        return Math.max(1, amounts.size());
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("From", from.toNBT());
        int[] arr = new int[amounts.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = Math.max(1, amounts.get(i));
        tag.putIntArray("Amounts", arr);
        tag.putInt("ArrowBendMode", arrowBendMode);
        tag.putBoolean("Success", success);
        return tag;
    }

    public static VirtualPanelConnection fromNBT(CompoundTag tag) {
        VirtualPanelPosition pos = VirtualPanelPosition.fromNBT(tag.getCompound("From"));
        VirtualPanelConnection conn = new VirtualPanelConnection(pos, 1);
        conn.amounts = new ArrayList<>();
        if (tag.contains("Amounts")) {
            for (int a : tag.getIntArray("Amounts")) conn.amounts.add(Math.max(1, a));
        } else if (tag.contains("Amount")) {
            conn.amounts.add(Math.max(1, tag.getInt("Amount")));   // legacy single-amount data
        }
        if (conn.amounts.isEmpty()) conn.amounts.add(1);
        conn.arrowBendMode = tag.getInt("ArrowBendMode");
        conn.success = tag.getBoolean("Success");
        return conn;
    }
}
