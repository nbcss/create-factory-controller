package io.github.nbcss.logisticscontrol.content.packet;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkHandler {

    private NetworkHandler() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(FilterLinkConfigurePacket.TYPE, FilterLinkConfigurePacket.STREAM_CODEC,
            FilterLinkConfigurePacket::handle);
        registrar.playToClient(FilterLinkPlacedPacket.TYPE, FilterLinkPlacedPacket.STREAM_CODEC,
            FilterLinkPlacedPacket::handle);
    }
}
