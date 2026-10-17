package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.TerminalStateClient;
import io.github.nbcss.createfactorycontroller.content.item.terminal.RemoteControllerLink;
import io.github.nbcss.createfactorycontroller.content.item.terminal.TerminalLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public record TerminalStatePacket(int containerId, List<RemoteControllerLink> links, int activeIndex, BlockPos pos,
                                  Optional<ResourceKey<Level>> dimension, boolean signal, String name,
                                  boolean retarget) implements CustomPacketPayload {

    public static final Type<TerminalStatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "terminal_state"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<RemoteControllerLink>> LINKS_CODEC =
        RemoteControllerLink.STREAM_CODEC.apply(ByteBufCodecs.list(TerminalLinks.MAX_LINKS));
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<ResourceKey<Level>>> DIMENSION_CODEC =
        ByteBufCodecs.optional(ResourceKey.streamCodec(Registries.DIMENSION)).cast();

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalStatePacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeVarInt(p.containerId);
            LINKS_CODEC.encode(buf, p.links);
            buf.writeVarInt(p.activeIndex);
            buf.writeBlockPos(p.pos);
            DIMENSION_CODEC.encode(buf, p.dimension);
            buf.writeBoolean(p.signal);
            buf.writeUtf(p.name);
            buf.writeBoolean(p.retarget);
        },
        buf -> new TerminalStatePacket(buf.readVarInt(), LINKS_CODEC.decode(buf), buf.readVarInt(),
            buf.readBlockPos(), DIMENSION_CODEC.decode(buf), buf.readBoolean(), buf.readUtf(), buf.readBoolean())
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    // only runs on the client
    public static void handle(TerminalStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> TerminalStateClient.apply(packet));
    }
}
