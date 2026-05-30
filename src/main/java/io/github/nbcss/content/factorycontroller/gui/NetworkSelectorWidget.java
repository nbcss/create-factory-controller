package io.github.nbcss.content.factorycontroller.gui;

import com.simibubi.create.AllBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class NetworkSelectorWidget extends AbstractWidget {

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP  = 2;
    private static final int PADDING   = 2;
    public static final int WIDGET_W   = PADDING + SLOT_SIZE + PADDING;

    private final List<UUID> networks;
    private int selectedIndex = 0;

    public NetworkSelectorWidget(int x, int y, List<UUID> networks) {
        super(x, y, WIDGET_W, computeHeight(networks.size()), Component.empty());
        this.networks = networks;
    }

    private static int computeHeight(int n) {
        return n == 0 ? 0 : PADDING + n * SLOT_SIZE + (n - 1) * SLOT_GAP + PADDING;
    }

    public void onNetworksUpdated() {
        this.height = computeHeight(networks.size());
        if (!networks.isEmpty())
            selectedIndex = Math.min(selectedIndex, networks.size() - 1);
        else
            selectedIndex = 0;
    }

    public UUID getSelectedNetwork() {
        if (networks.isEmpty()) return null;
        return networks.get(Math.min(selectedIndex, networks.size() - 1));
    }

    public void setSelectedNetwork(UUID network) {
        int idx = networks.indexOf(network);
        if (idx >= 0) selectedIndex = idx;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || networks.isEmpty()) return false;
        selectedIndex = Math.floorMod(selectedIndex - (int) Math.signum(scrollY), networks.size());
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (networks.isEmpty()) return;

        ItemStack icon = new ItemStack(AllBlocks.STOCK_LINK.get());

        for (int i = 0; i < networks.size(); i++) {
            int slotX = getX() + PADDING;
            int slotY = getY() + PADDING + i * (SLOT_SIZE + SLOT_GAP);

            if (i == selectedIndex) {
                gfx.fill(slotX - 1, slotY - 1, slotX + SLOT_SIZE + 1, slotY + SLOT_SIZE + 1, 0xFF66CCFF);
            }

            gfx.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xFF404040);
            gfx.renderItem(icon, slotX + 1, slotY + 1);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
