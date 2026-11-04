package io.github.nbcss.createfactorycontroller.content.component.redstone;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.AbstractVirtualComponent;
import io.github.nbcss.createfactorycontroller.content.component.SyncCodecs;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionCapability;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionValue;
import io.github.nbcss.createfactorycontroller.content.component.connection.RedstoneConnection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ValidationResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.List;

public abstract class ManualRedstoneSourceBehaviour extends AbstractVirtualComponent {

    protected boolean powered = false;

    protected ManualRedstoneSourceBehaviour(FactoryControllerBlockEntity controller, VirtualComponentPosition position,
                                            Item item) {
        super(controller, position, item);
    }

    public boolean isPowered() { return powered; }

    /** A player pressed this component */
    public abstract void onPress(ServerPlayer player);

    /** A player released the mouse button after pressing this component */
    public void onRelease(ServerPlayer player) {}

    @Override
    public List<Component> infoTooltip() {
        return List.of(stateLine());
    }

    public Component stateLine() {
        Component state = Component.translatable(powered
                        ? "createfactorycontroller.connection.state.powered"
                        : "createfactorycontroller.connection.state.unpowered")
                .withStyle(ChatFormatting.WHITE);
        return Component.translatable("createfactorycontroller.connection.info.state", state)
                .withStyle(ChatFormatting.GRAY);
    }

    protected void setPowered(boolean value, boolean settleNow) {
        if (powered == value) return;
        powered = value;
        publish(RedstoneConnection.TYPE);
        if (controller != null) {
            if (settleNow) controller.settleConnections();
            controller.setChanged();
            controller.syncComponentState(position);
        }
    }

    // ── Connections: REDSTONE source only ───────────────────────────────────────

    @Override
    public List<ConnectionCapability> ports() {
        return List.of(new ConnectionCapability(RedstoneConnection.TYPE, ConnectionCapability.Role.SOURCE, 1.0));
    }

    @Override
    public ValidationResult validateAsSource(Connection.Type type, VirtualComponentBehaviour sink) {
        return ValidationResult.SUCCESS;
    }

    @Override
    public ConnectionValue outputValue(Connection.Type type) {
        if (!RedstoneConnection.TYPE.equals(type)) return null;
        return powered ? RedstoneConnection.State.POWERED : RedstoneConnection.State.UNPOWERED;
    }

    @Override
    public void tick() {}

    // ── Client sync ─────────────────────────────────────────────────────────────

    @Override
    public void writeClient(RegistryFriendlyByteBuf buf) {
        SyncCodecs.writePos(buf, position);
        buf.writeResourceLocation(getItemId());
        writeClientState(buf);
    }

    @Override
    public void writeClientState(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(powered);
    }

    @Override
    public void readClientState(RegistryFriendlyByteBuf buf) {
        powered = buf.readBoolean();
    }
}
