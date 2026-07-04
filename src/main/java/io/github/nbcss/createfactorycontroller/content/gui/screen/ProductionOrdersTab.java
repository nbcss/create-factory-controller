package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.liukrast.deployer.lib.logistics.packager.screen.KeeperSourceContext;
import net.liukrast.deployer.lib.logistics.packager.screen.KeeperTabScreen;
import net.liukrast.deployer.lib.logistics.packager.screen.TabsWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

/**
 * The Stock-Keeper tab BUTTON for Promise Orders (registered via Deployer's keeper-tab API).
 */
public class ProductionOrdersTab extends KeeperTabScreen {

    public ProductionOrdersTab(KeeperSourceContext context, StockKeeperRequestMenu menu) {
        super(context, menu, Component.translatable("createfactorycontroller.gui.production_orders"),
              CreateFactoryController.PRODUCTION_PATTERN.get());
    }

    @Override
    public void containerTick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof StockKeeperRequestScreen host)) return;   // already swapped away (or not hosted)
        if (selectedOn(host) == this)
            mc.setScreen(new ProductionOrdersScreen(host, menu));
    }

    /** The tab currently selected in the host's Deployer tab strip, or null. */
    private static Object selectedOn(StockKeeperRequestScreen host) {
        for (GuiEventListener c : host.children())
            if (c instanceof TabsWidget<?> t) return t.getSelected();
        return null;
    }
}
