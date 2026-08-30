package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Edits an Arithmetic Tube's input operands from the settings GUI. {@code op}: {@link #ADD_CONSTANT},
 * {@link #SET_CONSTANT}, {@link #REMOVE}, {@link #PREPARE_WIRE}. {@code primary} picks the slot group (primary /
 * secondary); {@code index} is the primary-list index (ignored for secondary); {@code value} the constant value.
 */
public record ConfigureArithmeticInputPacket(BlockPos pos, VirtualComponentPosition tube,
                                             int op, boolean primary, int index, double value)
    implements CustomPacketPayload {

    public static final int ADD_CONSTANT = 0, SET_CONSTANT = 1, REMOVE = 2, PREPARE_WIRE = 3, SWAP = 4, LOOP = 5;

    public static final Type<ConfigureArithmeticInputPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "configure_arithmetic_input"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualComponentPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualComponentPosition::x,
            ByteBufCodecs.INT, VirtualComponentPosition::y,
            VirtualComponentPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureArithmeticInputPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureArithmeticInputPacket::pos,
            POS_CODEC, ConfigureArithmeticInputPacket::tube,
            ByteBufCodecs.VAR_INT, ConfigureArithmeticInputPacket::op,
            ByteBufCodecs.BOOL, ConfigureArithmeticInputPacket::primary,
            ByteBufCodecs.VAR_INT, ConfigureArithmeticInputPacket::index,
            ByteBufCodecs.DOUBLE, ConfigureArithmeticInputPacket::value,
            ConfigureArithmeticInputPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigureArithmeticInputPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.configureArithmeticInput(packet.tube(), packet.op(), packet.primary(), packet.index(), packet.value());
        });
    }
}
