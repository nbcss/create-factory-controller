package io.github.nbcss.createfactorycontroller.content.gui;

import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import net.liukrast.deployer.lib.logistics.packager.screen.TabsWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.jetbrains.annotations.Nullable;

/**
 * All access to Deployer's keeper tab strip ({@code TabsWidget}), kept out of {@link ProductionOrdersScreen} so that
 * screen carries no direct Deployer reference and can be class-loaded without Deployer installed. Every method here is
 * only ever called from a branch guarded by {@link io.github.nbcss.createfactorycontroller.content.compat.DeployerCompat#isLoaded()},
 * so this class is itself only loaded when Deployer is present.
 */
final class ProductionOrdersStrip {

    /** Result of a click handled by the strip. */
    static final int NOT_HANDLED = 0;   // click wasn't on the strip
    static final int STAY = 1;          // strip handled it, our tab is still selected
    static final int GO_BACK = 2;       // strip handled it, selection moved off our tab → return to the keeper

    private ProductionOrdersStrip() {}

    /** The host's tab strip (same instance as the host's field), or null if absent. */
    @Nullable
    private static TabsWidget<?> of(StockKeeperRequestScreen host) {
        for (GuiEventListener c : host.children())
            if (c instanceof TabsWidget<?> t) return t;
        return null;
    }

    static void render(StockKeeperRequestScreen host, GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        TabsWidget<?> strip = of(host);
        if (strip != null) strip.render(gfx, mouseX, mouseY, partialTicks);
    }

    static int mouseClicked(StockKeeperRequestScreen host, double mouseX, double mouseY, int button) {
        TabsWidget<?> strip = of(host);
        if (strip == null || !strip.mouseClicked(mouseX, mouseY, button)) return NOT_HANDLED;
        return strip.getSelected() instanceof ProductionOrdersTab ? STAY : GO_BACK;
    }
}
