package io.github.nbcss.createfactorycontroller.content.packet;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers every custom packet payload. */
public final class NetworkHandler {

    private NetworkHandler() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(AttachComponentPacket.TYPE, AttachComponentPacket.STREAM_CODEC, AttachComponentPacket::handle);
        registrar.playToServer(RemoveComponentPacket.TYPE, RemoveComponentPacket.STREAM_CODEC, RemoveComponentPacket::handle);
        registrar.playToServer(MoveComponentPacket.TYPE, MoveComponentPacket.STREAM_CODEC, MoveComponentPacket::handle);
        registrar.playToServer(BatchMoveComponentPacket.TYPE, BatchMoveComponentPacket.STREAM_CODEC, BatchMoveComponentPacket::handle);
        registrar.playToServer(GaugeSetItemPacket.TYPE, GaugeSetItemPacket.STREAM_CODEC, GaugeSetItemPacket::handle);
        registrar.playToServer(ConfigureRecipePacket.TYPE, ConfigureRecipePacket.STREAM_CODEC, ConfigureRecipePacket::handle);
        registrar.playToServer(AddConnectionPacket.TYPE, AddConnectionPacket.STREAM_CODEC, AddConnectionPacket::handle);
        registrar.playToServer(RemoveConnectionPacket.TYPE, RemoveConnectionPacket.STREAM_CODEC, RemoveConnectionPacket::handle);
        registrar.playToServer(DisconnectIngredientPacket.TYPE, DisconnectIngredientPacket.STREAM_CODEC, DisconnectIngredientPacket::handle);
        registrar.playToServer(DisconnectLinksPacket.TYPE, DisconnectLinksPacket.STREAM_CODEC, DisconnectLinksPacket::handle);
        registrar.playToServer(CycleArrowModePacket.TYPE, CycleArrowModePacket.STREAM_CODEC, CycleArrowModePacket::handle);
        registrar.playToServer(CycleConnectionArrowModePacket.TYPE, CycleConnectionArrowModePacket.STREAM_CODEC, CycleConnectionArrowModePacket::handle);
        registrar.playToServer(CycleOperationModePacket.TYPE, CycleOperationModePacket.STREAM_CODEC, CycleOperationModePacket::handle);
        registrar.playToServer(ReverseConnectionPacket.TYPE, ReverseConnectionPacket.STREAM_CODEC, ReverseConnectionPacket::handle);
        registrar.playToServer(ConfigureLogicalTubePacket.TYPE, ConfigureLogicalTubePacket.STREAM_CODEC, ConfigureLogicalTubePacket::handle);
        registrar.playToServer(ConfigureRedstoneLinkPacket.TYPE, ConfigureRedstoneLinkPacket.STREAM_CODEC, ConfigureRedstoneLinkPacket::handle);
        registrar.playToServer(RetuneCarriedPacket.TYPE, RetuneCarriedPacket.STREAM_CODEC, RetuneCarriedPacket::handle);
        registrar.playToServer(RenameControllerPacket.TYPE, RenameControllerPacket.STREAM_CODEC, RenameControllerPacket::handle);
        registrar.playToServer(SetNetworkSettingsPacket.TYPE, SetNetworkSettingsPacket.STREAM_CODEC, SetNetworkSettingsPacket::handle);
        registrar.playToServer(RequestProductionOrdersPacket.TYPE, RequestProductionOrdersPacket.STREAM_CODEC, RequestProductionOrdersPacket::handle);
        registrar.playToServer(RemoveProductionOrderPacket.TYPE, RemoveProductionOrderPacket.STREAM_CODEC, RemoveProductionOrderPacket::handle);
        registrar.playToServer(RequestIngredientCheckPacket.TYPE, RequestIngredientCheckPacket.STREAM_CODEC, RequestIngredientCheckPacket::handle);
        registrar.playToServer(RequestGaugePromiseInfoPacket.TYPE, RequestGaugePromiseInfoPacket.STREAM_CODEC, RequestGaugePromiseInfoPacket::handle);
        registrar.playToServer(RegisterOrderNotificationPacket.TYPE, RegisterOrderNotificationPacket.STREAM_CODEC, RegisterOrderNotificationPacket::handle);
        registrar.playToClient(SyncPanelStatePacket.TYPE, SyncPanelStatePacket.STREAM_CODEC, SyncPanelStatePacket::handle);
        registrar.playToClient(SyncProductionOrdersPacket.TYPE, SyncProductionOrdersPacket.STREAM_CODEC, SyncProductionOrdersPacket::handle);
        registrar.playToClient(IngredientCheckResultPacket.TYPE, IngredientCheckResultPacket.STREAM_CODEC, IngredientCheckResultPacket::handle);
        registrar.playToClient(GaugePromiseInfoPacket.TYPE, GaugePromiseInfoPacket.STREAM_CODEC, GaugePromiseInfoPacket::handle);
        registrar.playToClient(OrderNotificationPacket.TYPE, OrderNotificationPacket.STREAM_CODEC, OrderNotificationPacket::handle);
    }
}
