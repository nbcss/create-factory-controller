package io.github.nbcss.createfactorycontroller.content.client;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FilterLinkBlockEntity;
import io.github.nbcss.createfactorycontroller.content.helper.FilterApplication;
import io.github.nbcss.createfactorycontroller.content.packet.FilterLinkConfigurePacket;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CreateFactoryController.MODID, value = Dist.CLIENT)
public final class FilterLinkSelectionHandler {
    private static final List<BlockPos> currentSelection = new ArrayList<>();
    private static ItemStack currentItem = ItemStack.EMPTY;
    private static final int OUTLINE_COLOR = 0xDDC166;

    private FilterLinkSelectionHandler() {}

    private static boolean holdingLink(Player player) {
        return player != null && player.getMainHandItem().is(CreateFactoryController.FILTER_LINK_ITEM.get());
    }

    @SubscribeEvent
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!level.isClientSide) return;
        Player player = event.getEntity();
        if (player == null || player.isSpectator() || !holdingLink(player)) return;
        BlockPos pos = event.getPos();

        if (level.getBlockEntity(pos) instanceof PackagerBlockEntity) return;
        if (!FilterApplication.hasFilter(level, pos)) return;
        toggle(pos);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide || currentItem.isEmpty()) return;
        if (currentSelection.remove(event.getPos().immutable())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) { reset(); return; }
        if (holdingLink(player)) {
            ItemStack held = player.getMainHandItem();
            if (held != currentItem) {
                currentSelection.clear();
                currentItem = held;
            }
            drawOutlines(mc.level);
        } else {
            reset();
            highlightWrenchedLink(mc, player);
        }
    }

    private static void highlightWrenchedLink(Minecraft mc, Player player) {
        if (mc.level == null || !AllItems.WRENCH.isIn(player.getMainHandItem())) return;
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;
        if (!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof FilterLinkBlockEntity link)) return;
        for (BlockPos target : link.getTargets()) {
            BlockState state = mc.level.getBlockState(target);
            VoxelShape shape = state.getShape(mc.level, target);
            if (shape.isEmpty()) continue;
            Outliner.getInstance().showAABB("cfc_filterlink_wrench:" + target.asLong(), shape.bounds().move(target))
                .colored(OUTLINE_COLOR).lineWidth(0.0625F);
        }
    }

    public static void flush(BlockPos linkPos) {
        currentSelection.removeIf(p -> !p.closerThan(linkPos, FilterApplication.range()));
        PacketDistributor.sendToServer(new FilterLinkConfigurePacket(linkPos, new ArrayList<>(currentSelection)));
        reset();
    }

    private static void toggle(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (!currentSelection.remove(immutable)) currentSelection.add(immutable);
    }

    private static void reset() {
        currentSelection.clear();
        currentItem = ItemStack.EMPTY;
    }

    private static void drawOutlines(Level level) {
        if (level == null) return;
        for (BlockPos pos : currentSelection) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getShape(level, pos);
            if (shape.isEmpty()) continue;
            Outliner.getInstance().showAABB(pos, shape.bounds().move(pos))
                .colored(OUTLINE_COLOR).lineWidth(0.0625F);
        }
    }
}
