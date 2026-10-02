package io.github.nbcss.createfactorycontroller.content.helper;

import io.github.nbcss.createfactorycontroller.ServerConfig;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerTerminalMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public final class ControllerAccessor {

    private ControllerAccessor() {}

    @Nullable
    public static FactoryControllerBlockEntity getBlockEntity(Player player, BlockPos pos) {
        if (player.containerMenu instanceof FactoryControllerTerminalMenu terminal) {
            if (!pos.equals(terminal.activeControllerPos())) return null;
            return getLiveBlockEntity(terminal.activeControllerLevel(), pos);
        }
        return player.level().getBlockEntity(pos) instanceof FactoryControllerBlockEntity be ? be : null;
    }

    @Nullable
    public static FactoryControllerBlockEntity getLiveBlockEntity(@Nullable ServerLevel level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof FactoryControllerBlockEntity be &&
                !be.isRemoved() && be.isTicking() ? be : null;
    }

    /** Whether items may move between {@code player} and the board (placing consumes one, removing refunds one).
     *  Always true at the block; through a terminal only for creative players or when the server config allows. */
    public static boolean canTransferItems(Player player) {
        return !(player.containerMenu instanceof FactoryControllerTerminalMenu) || player.isCreative()
            || ServerConfig.allowComponentPlacementInTerminal();
    }
}
