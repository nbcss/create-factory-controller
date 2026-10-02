package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerTerminalMenu;
import io.github.nbcss.createfactorycontroller.content.item.FactoryControllerTerminalItem;
import io.github.nbcss.createfactorycontroller.content.item.terminal.TerminalLinks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server: unlink a controller from the terminal.
 */
public record UnlinkTerminalControllerPacket(int index) implements CustomPacketPayload {

    public static final Type<UnlinkTerminalControllerPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "unlink_terminal_controller"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnlinkTerminalControllerPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UnlinkTerminalControllerPacket::index,
            UnlinkTerminalControllerPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(UnlinkTerminalControllerPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof FactoryControllerTerminalMenu menu)) return;
            ItemStack terminal = menu.heldTerminal(player);
            if (terminal == null) return;
            TerminalLinks state = FactoryControllerTerminalItem.linksOf(terminal);
            if (packet.index() < 0 || packet.index() >= state.links().size()) return;
            terminal.set(CreateFactoryController.TERMINAL_LINKS.get(), state.withoutIndex(packet.index()));
            menu.updateControllerPage();
        });
    }
}
