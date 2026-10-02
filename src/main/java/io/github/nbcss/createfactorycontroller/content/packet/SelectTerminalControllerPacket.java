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

public record SelectTerminalControllerPacket(int index) implements CustomPacketPayload {

    public static final Type<SelectTerminalControllerPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "select_terminal_controller"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectTerminalControllerPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectTerminalControllerPacket::index,
            SelectTerminalControllerPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SelectTerminalControllerPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof FactoryControllerTerminalMenu menu)) return;
            ItemStack terminal = menu.heldTerminal(player);
            if (terminal == null) return;
            TerminalLinks state = FactoryControllerTerminalItem.linksOf(terminal);
            if (packet.index() < 0 || packet.index() >= state.links().size()) return;
            terminal.set(CreateFactoryController.TERMINAL_LINKS.get(), state.withActiveIndex(packet.index()));
            menu.updateControllerPage();
        });
    }
}
