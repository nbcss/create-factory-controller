package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/**
 * A gauge ingredient connection (gauge ← source gauge). Carries the single required input {@link #amount} and the
 * last-request {@link #success} flag (drives the connection-line flash). The UI splits the amount across grid slots
 * on demand, so a single total is all the model needs.
 */
public class LogisticsConnection extends Connection {
    public int amount;
    public boolean success;

    public LogisticsConnection(VirtualComponentPosition from, VirtualComponentPosition to, int amount) {
        super(Type.LOGISTICS, from, to);
        this.amount = Math.max(1, amount);
        this.success = false;
    }

    public int amount() {
        return Math.max(1, amount);
    }

    @Override
    public int getConnectionColor(ComponentHolder holder) {
        if (holder.componentAt(to) instanceof VirtualGaugeBehaviour behaviour) {
            return behaviour.getConnectionColor();
        }
        return super.getConnectionColor(holder);
    }

    @Override
    public long getAnimationTick(ComponentHolder holder) {
        if (!(holder.componentAt(to) instanceof VirtualGaugeBehaviour behaviour))
            return -1;
        if (behaviour.isMissingAddress() || behaviour.waitingForNetwork)
            return -1;
        if (behaviour.satisfied || behaviour.redstonePowered)
            return -1;
        if (Minecraft.getInstance().level == null)
            return -1;
        return Minecraft.getInstance().level.getGameTime() - behaviour.lastRequestTick;
    }

    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putInt("Amount", Math.max(1, amount));
        tag.putBoolean("Success", success);
        return tag;
    }

    private LogisticsConnection(CompoundTag tag) {
        super(tag);
        if (tag.contains("Amount")) {
            this.amount = Math.max(1, tag.getInt("Amount"));
        } else if (tag.contains("Amounts")) {            // legacy per-slot list → sum into the single total
            int sum = 0;
            for (int a : tag.getIntArray("Amounts")) sum += Math.max(1, a);
            this.amount = Math.max(1, sum);
        } else {
            this.amount = 1;
        }
        this.success = tag.getBoolean("Success");
    }

    public static LogisticsConnection fromNBT(CompoundTag tag) {
        return new LogisticsConnection(tag);
    }
}
