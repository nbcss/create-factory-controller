package io.github.nbcss.createfactorycontroller.content.production;

import net.minecraft.server.level.ServerPlayer;

/**
 * Carries the player who triggered an order across the synchronous server-side dispatch, so
 * {@link ProductionOrderManager#interceptProductionOrder} can subscribe them for completion toasts.
 *
 * <p>Create's own Stock Keeper subscribes via a client-sent {@code RegisterOrderNotificationPacket}; third-party
 * portable keepers (createphantom, Create: Mobile Packages) send no such packet, so a thin {@code @WrapMethod} around
 * each mod's order-send handler sets the player here for the duration of the dispatch and clears it in a {@code finally}.
 * Set on the server thread, read on the same thread later in the same call, so a plain {@link ThreadLocal} suffices.</p>
 */
public final class OrderNotificationCapture {

    private static final ThreadLocal<ServerPlayer> ORDERER = new ThreadLocal<>();

    private OrderNotificationCapture() {}

    public static void set(ServerPlayer player) { ORDERER.set(player); }

    public static void clear() { ORDERER.remove(); }

    /** The player currently placing an order on this thread, or null when the dispatch has no captured player. */
    public static ServerPlayer get() { return ORDERER.get(); }
}
