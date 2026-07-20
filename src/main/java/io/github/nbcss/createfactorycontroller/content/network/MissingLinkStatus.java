package io.github.nbcss.createfactorycontroller.content.network;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Runtime-only diagnostics for the unloaded logistics links of one known network. */
public record MissingLinkStatus(UUID network, List<GlobalPos> links) {

    public MissingLinkStatus {
        links = List.copyOf(links);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, MissingLinkStatus> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public MissingLinkStatus decode(RegistryFriendlyByteBuf buf) {
                UUID network = UUIDUtil.STREAM_CODEC.decode(buf);
                int size = buf.readVarInt();
                List<GlobalPos> links = new ArrayList<>(size);
                for (int i = 0; i < size; i++)
                    links.add(GlobalPos.STREAM_CODEC.decode(buf));
                return new MissingLinkStatus(network, links);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, MissingLinkStatus status) {
                UUIDUtil.STREAM_CODEC.encode(buf, status.network());
                buf.writeVarInt(status.links().size());
                for (GlobalPos link : status.links())
                    GlobalPos.STREAM_CODEC.encode(buf, link);
            }
        };

    public static List<MissingLinkStatus> readList(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<MissingLinkStatus> statuses = new ArrayList<>(size);
        for (int i = 0; i < size; i++) statuses.add(STREAM_CODEC.decode(buf));
        return List.copyOf(statuses);
    }

    public static void writeList(RegistryFriendlyByteBuf buf, List<MissingLinkStatus> statuses) {
        buf.writeVarInt(statuses.size());
        for (MissingLinkStatus status : statuses) STREAM_CODEC.encode(buf, status);
    }
}
