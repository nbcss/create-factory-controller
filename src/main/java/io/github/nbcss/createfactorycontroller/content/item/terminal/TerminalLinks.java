package io.github.nbcss.createfactorycontroller.content.item.terminal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A Factory Controller Terminal's saved state: the linked controllers and which one is currently selected.
 */
public record TerminalLinks(List<RemoteControllerLink> links, int activeIndex) {

    public static final int MAX_LINKS = 3;

    public static final TerminalLinks EMPTY = new TerminalLinks(List.of(), 0);

    public static final Codec<TerminalLinks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        RemoteControllerLink.CODEC.listOf().fieldOf("Links").forGetter(TerminalLinks::links),
        Codec.INT.optionalFieldOf("Active", 0).forGetter(TerminalLinks::activeIndex)
    ).apply(instance, TerminalLinks::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalLinks> STREAM_CODEC = StreamCodec.composite(
        RemoteControllerLink.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINKS)), TerminalLinks::links,
        ByteBufCodecs.VAR_INT, TerminalLinks::activeIndex,
        TerminalLinks::new
    );

    /** Canonicalises on construction: at most {@link #MAX_LINKS} links and a clamped active index. */
    public TerminalLinks {
        links = List.copyOf(links.size() > MAX_LINKS ? links.subList(0, MAX_LINKS) : links);
        activeIndex = links.isEmpty() ? 0 : Math.floorMod(activeIndex, links.size());
    }

    public boolean isEmpty() {
        return links.isEmpty();
    }

    public boolean isFull() {
        return links.size() >= MAX_LINKS;
    }

    /** Index of the link for {@code (dim, pos)}, or -1 when not linked. */
    public int indexOf(ResourceKey<Level> dim, BlockPos pos) {
        for (int i = 0; i < links.size(); i++)
            if (links.get(i).isAt(dim, pos)) return i;
        return -1;
    }

    public boolean contains(ResourceKey<Level> dim, BlockPos pos) {
        return indexOf(dim, pos) >= 0;
    }

    /** The selected link, or {@code null} when the terminal has no links. */
    public RemoteControllerLink active() {
        return links.isEmpty() ? null : links.get(activeIndex);
    }

    /** Adds a link (no-op when already present or full). */
    public TerminalLinks withLink(RemoteControllerLink link) {
        if (isFull() || contains(link.dimension(), link.pos())) return this;
        List<RemoteControllerLink> next = new ArrayList<>(links);
        next.add(link);
        return new TerminalLinks(next, next.size() - 1);
    }

    /** Removes the link at {@code index}, keeping the previously-selected controller selected where possible. */
    public TerminalLinks withoutIndex(int index) {
        if (index < 0 || index >= links.size()) return this;
        List<RemoteControllerLink> next = new ArrayList<>(links);
        next.remove(index);
        int nextActive = activeIndex;
        if (index < activeIndex)
            nextActive--;
        else if (index == activeIndex)
            nextActive = Math.min(activeIndex, next.size() - 1);
        return new TerminalLinks(next, Math.max(nextActive, 0));
    }

    /** Refreshes the active link's cached controller name (used after a successful resolve). */
    public TerminalLinks withActiveName(String name) {
        RemoteControllerLink current = active();
        if (current == null || name.equals(current.cachedName())) return this;
        List<RemoteControllerLink> next = new ArrayList<>(links);
        next.set(activeIndex, current.withName(name));
        return new TerminalLinks(next, activeIndex);
    }

    public TerminalLinks withActiveIndex(int index) {
        if (links.isEmpty()) return this;
        return new TerminalLinks(links, Math.floorMod(index, links.size()));
    }
}
