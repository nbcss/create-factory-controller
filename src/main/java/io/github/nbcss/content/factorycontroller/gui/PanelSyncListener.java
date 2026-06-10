package io.github.nbcss.content.factorycontroller.gui;

/**
 * A client screen that shows the controller's panel and therefore must refresh its cached view when a
 * {@link io.github.nbcss.content.factorycontroller.packet.SyncPanelStatePacket} arrives.
 *
 * <p>The packet always updates the shared {@code FactoryControllerMenu} (so live lookups stay current),
 * but {@link FactoryControllerScreen} also keeps a position-indexed cache of {@code VirtualGaugeWidget}s
 * that wrap the (now replaced) behaviour instances and is only rebuilt on sync. A sub-screen
 * ({@link SetItemScreen}, {@link ConfigureRecipeScreen}) renders that same board as its background via
 * {@code controller.renderBoard(...)}, so when one is open the parent's widget cache must still be
 * rebuilt — otherwise the gauge count overlay freezes. Implementing this lets the sync handler refresh
 * whichever of these screens is active without knowing the concrete type.</p>
 */
public interface PanelSyncListener {

    /** Re-index any cached panel view against the freshly synced menu state. */
    void onPanelSync();
}
