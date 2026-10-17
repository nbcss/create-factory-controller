package io.github.nbcss.createfactorycontroller.content.gui.screen.controller;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerTerminalMenu;
import io.github.nbcss.createfactorycontroller.content.gui.screen.PanelSyncListener;
import io.github.nbcss.createfactorycontroller.content.packet.TerminalStatePacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** To handle any client side packet for terminal */
@OnlyIn(Dist.CLIENT)
public final class TerminalStateClient {

    private TerminalStateClient() {}

    public static void apply(TerminalStatePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.player.containerMenu instanceof FactoryControllerTerminalMenu menu)
                || menu.containerId != packet.containerId()) return;
        FactoryControllerScreen board = mc.screen instanceof PanelSyncListener listener ?
                listener.boardScreen() : null;
        if (board == null || board.getMenu() != menu) {
            menu.applyState(packet);
            return;
        }
        board.applyTerminalState(() -> menu.applyState(packet), packet.retarget());
        if (!packet.signal() && mc.screen != board) mc.setScreen(board);
    }
}
