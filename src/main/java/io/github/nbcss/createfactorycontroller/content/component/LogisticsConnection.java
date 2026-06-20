package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.content.VirtualPanelConnection;
import io.github.nbcss.createfactorycontroller.content.VirtualPanelPosition;
import net.minecraft.nbt.CompoundTag;

/**
 * A gauge ingredient connection (gauge ← source gauge). Carries the single required input {@link #amount} and the
 * last-request {@link #success} flag (drives the connection-line flash). The UI splits the amount across grid slots
 * on demand, so a single total is all the model needs.
 */
public class LogisticsConnection extends VirtualPanelConnection {

    public int amount;
    public boolean success;

    public LogisticsConnection(VirtualPanelPosition from, int amount) {
        super(from);
        this.amount = Math.max(1, amount);
        this.success = false;
    }

    public int amount() {
        return Math.max(1, amount);
    }

    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("From", from.toNBT());
        tag.putInt("ArrowBendMode", arrowBendMode);
        tag.putInt("Amount", Math.max(1, amount));
        tag.putBoolean("Success", success);
        return tag;
    }

    public static LogisticsConnection fromNBT(CompoundTag tag) {
        VirtualPanelPosition pos = VirtualPanelPosition.fromNBT(tag.getCompound("From"));
        LogisticsConnection conn = new LogisticsConnection(pos, 1);
        conn.arrowBendMode = tag.getInt("ArrowBendMode");
        if (tag.contains("Amount")) {
            conn.amount = Math.max(1, tag.getInt("Amount"));
        } else if (tag.contains("Amounts")) {            // legacy per-slot list → sum into the single total
            int sum = 0;
            for (int a : tag.getIntArray("Amounts")) sum += Math.max(1, a);
            conn.amount = Math.max(1, sum);
        }
        conn.success = tag.getBoolean("Success");
        return conn;
    }
}
