package io.github.nbcss.createfactorycontroller.content.block;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerTerminalMenu.Target;
import io.github.nbcss.createfactorycontroller.content.item.FactoryControllerTerminalItem;
import io.github.nbcss.createfactorycontroller.content.item.terminal.TerminalLinks;
import io.github.nbcss.createfactorycontroller.registry.CFCItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public record TerminalMenuProvider(ItemStack terminal, TerminalLinks state, Target target) implements MenuProvider {

    public static void open(ServerPlayer player, ItemStack terminal) {
        TerminalLinks state = FactoryControllerTerminalItem.linksOf(terminal);
        Target target = Target.resolve(player.server, state);
        state = FactoryControllerTerminalMenu.rememberName(terminal, state, target);

        TerminalMenuProvider provider = new TerminalMenuProvider(terminal, state, target);
        player.openMenu(provider, buf -> FactoryControllerTerminalMenu.writeExtraData(provider, buf));
        if (target.be() != null) target.be().syncEverything();
    }

    @NotNull
    @Override
    public AbstractContainerMenu createMenu(int syncId, @NotNull Inventory inventory, @NotNull Player player) {
        return new FactoryControllerTerminalMenu(syncId, inventory, (ServerPlayer) player, this);
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return CFCItems.FACTORY_CONTROLLER_TERMINAL.get().getDescription();
    }
}
