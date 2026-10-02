package io.github.nbcss.createfactorycontroller.content.item;

import com.simibubi.create.AllSoundEvents;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlock;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.TerminalMenuProvider;
import io.github.nbcss.createfactorycontroller.content.item.terminal.RemoteControllerLink;
import io.github.nbcss.createfactorycontroller.content.item.terminal.TerminalLinks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FactoryControllerTerminalItem extends Item {

    public FactoryControllerTerminalItem(Properties properties) {
        super(properties);
    }

    public static TerminalLinks linksOf(ItemStack stack) {
        return stack.getOrDefault(CreateFactoryController.TERMINAL_LINKS.get(), TerminalLinks.EMPTY);
    }

    public static Component controllerDisplayName(String cachedName) {
        return cachedName == null || cachedName.isBlank()
            ? Component.translatable("block.createfactorycontroller.factory_controller")
            : Component.literal(cachedName);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean isController = level.getBlockState(pos).getBlock() instanceof FactoryControllerBlock;

        if (player != null && player.isSecondaryUseActive()) {
            // Shift-click: link/unlink the targeted controller. Elsewhere, PASS falls through to use() (opens the GUI).
            if (!isController) return InteractionResult.PASS;
            if (!level.isClientSide() && player instanceof ServerPlayer sp && level instanceof ServerLevel sl)
                toggleLink(sp, sl, pos, context.getItemInHand());
            return InteractionResult.SUCCESS;
        }

        // Plain right-click on any other block: open the terminal GUI. (A plain right-click on a controller never
        // gets here — the block's own useWithoutItem runs first and opens that controller directly.)
        if (!level.isClientSide() && player instanceof ServerPlayer sp)
            TerminalMenuProvider.open(sp, context.getItemInHand());
        return InteractionResult.SUCCESS;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer sp)
            TerminalMenuProvider.open(sp, stack);
        return InteractionResultHolder.success(stack);
    }

    /** Adds or removes the (dimension, pos) controller from the terminal's links. Success feedback is the hover
     *  tip's action line (it sees the synced link change); only a refused link (terminal full) messages here. */
    private void toggleLink(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
        ResourceKey<Level> dim = level.dimension();
        TerminalLinks state = linksOf(stack);
        int idx = state.indexOf(dim, pos);

        if (idx >= 0) {
            stack.set(CreateFactoryController.TERMINAL_LINKS.get(), state.withoutIndex(idx));
            playLinkSound(level, pos, false);
            return;
        }

        if (state.isFull()) {
            player.displayClientMessage(
                Component.translatable("createfactorycontroller.terminal.full", TerminalLinks.MAX_LINKS)
                    .withStyle(ChatFormatting.RED), true);
            AllSoundEvents.DENY.playOnServer(level, pos);
            return;
        }

        String cached = level.getBlockEntity(pos) instanceof FactoryControllerBlockEntity be ? be.customName : "";
        stack.set(CreateFactoryController.TERMINAL_LINKS.get(), state.withLink(new RemoteControllerLink(dim, pos, cached)));
        playLinkSound(level, pos, true);
    }

    private static void playLinkSound(ServerLevel level, BlockPos pos, boolean link) {
        if (link) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.6f, 0.75f);
        } else {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("createfactorycontroller.terminal.tooltip.count",
                linksOf(stack).links().size(), TerminalLinks.MAX_LINKS).withStyle(ChatFormatting.GOLD));
    }
}
