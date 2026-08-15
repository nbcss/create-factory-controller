package io.github.nbcss.createfactorycontroller.content.component.connection;

import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A gauge ingredient connection (gauge ← source gauge). Carries the single required input {@link #amount} and the
 * last-request {@link #success} flag (drives the connection-line flash). The UI splits the amount across grid slots
 * on demand, so a single total is all the model needs.
 */
public class LogisticsConnection extends Connection {
    public static final Type TYPE = new Connection.Type("LOGISTICS", 0x409DF7) {
        @Override
        public Connection create(VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {
            int amount = source instanceof VirtualGaugeBehaviour g && FluidCompat.isFluidFilter(g.filter) ? 1000 : 1;
            return new LogisticsConnection(source.position(), sink.position(), amount);
        }

        @Override
        public Connection fromNBT(CompoundTag tag) {
            return LogisticsConnection.fromNBT(tag);
        }

        @Override
        public Connection fromClient(net.minecraft.network.RegistryFriendlyByteBuf buf,
                                     VirtualComponentPosition from, VirtualComponentPosition to, int arrowBendMode) {
            LogisticsConnection c = new LogisticsConnection(from, to, buf.readVarInt());
            c.arrowBendMode = arrowBendMode;
            c.excludeFromRequestMultiplier = buf.readBoolean();
            c.success = buf.readBoolean();
            return c;
        }

        /** Ingredient wires are not reversible: direction encodes producer→consumer (and carries an ingredient
         *  {@code amount}), so a flip would silently swap the recipe's roles — redraw instead. */
        @Override
        public boolean reversible() { return false; }

        /** Ingredient flow: names the two gauges' filters (Create's own "panels connected" prompt). */
        @Override
        public Component successMessage(VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {
            if (!(source instanceof VirtualGaugeBehaviour sourceBehaviour))
                return super.successMessage(source, sink);
            if (!(sink instanceof VirtualGaugeBehaviour sinkBehaviour))
                return super.successMessage(source, sink);
            return CreateLang.translate("factory_panel.panels_connected", sourceBehaviour.getFilterName(),
                    sinkBehaviour.getFilterName()).style(ChatFormatting.GREEN).component();
        }
    };

    public int amount;
    /** When true this ingredient is sent once per request instead of being scaled by the request multiplier. */
    public boolean excludeFromRequestMultiplier;
    public boolean success;

    public LogisticsConnection(VirtualComponentPosition from, VirtualComponentPosition to, int amount) {
        super(TYPE, from, to);
        this.amount = Math.max(1, amount);
        this.excludeFromRequestMultiplier = false;
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
    public List<Component> getInfoTooltip(ComponentHolder holder) {
        if (!(holder.componentAt(from) instanceof VirtualGaugeBehaviour source))
            return List.of();
        Component itemName = source.getFilterName().copy().withStyle(ChatFormatting.WHITE);
        return List.of(
                Component.translatable("createfactorycontroller.connection.info.item", itemName)
                        .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putInt("Amount", Math.max(1, amount));
        if (excludeFromRequestMultiplier) tag.putBoolean("ExcludeFromRequestMultiplier", true);
        tag.putBoolean("Success", success);
        return tag;
    }

    @Override
    public CompoundTag toExportNBT() {
        CompoundTag tag = super.toNBT();
        tag.putInt("Amount", Math.max(1, amount));
        if (excludeFromRequestMultiplier) tag.putBoolean("ExcludeFromRequestMultiplier", true);
        return tag;
    }

    @Override
    protected void writeClientExtra(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(Math.max(1, amount));
        buf.writeBoolean(excludeFromRequestMultiplier);
        buf.writeBoolean(success);
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
        this.excludeFromRequestMultiplier = tag.getBoolean("ExcludeFromRequestMultiplier");
        this.success = tag.getBoolean("Success");
    }

    public static LogisticsConnection fromNBT(CompoundTag tag) {
        return new LogisticsConnection(tag);
    }
}
