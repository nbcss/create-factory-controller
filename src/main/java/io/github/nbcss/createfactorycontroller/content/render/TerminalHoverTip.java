package io.github.nbcss.createfactorycontroller.content.render;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlock;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.item.FactoryControllerTerminalItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Hover tip of Factory Controller Terminal when looking at a controller
 */
public final class TerminalHoverTip implements LayeredDraw.Layer {

    public static final TerminalHoverTip INSTANCE = new TerminalHoverTip();

    private static final int TITLE_COLOR = 0xFBDC7D;
    private static final int TEXT_COLOR = 0xFFFFFF;

    @Nullable private List<Component> lastTip;
    private int hoverTicks;
    private int hoverWarmup;

    /** The controller currently hovered */
    @Nullable private String hoveredKey;
    private boolean hoveredLinked;
    @Nullable private Boolean lastAction;

    private TerminalHoverTip() {}

    public void tick() {
        if (hoverWarmup > 0) hoverWarmup--;
        if (hoverTicks > 0) hoverTicks--;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        ItemStack terminal = player == null ? null
                : player.getMainHandItem().getItem() instanceof FactoryControllerTerminalItem ? player.getMainHandItem()
                : player.getOffhandItem().getItem() instanceof FactoryControllerTerminalItem ? player.getOffhandItem()
                : null;
        if (terminal == null || mc.level == null || player.isSpectator() || !player.getAbilities().mayBuild
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !(mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof FactoryControllerBlock)) {
            hoveredKey = null;
            return;
        }
        BlockPos pos = hit.getBlockPos();

        boolean linked = FactoryControllerTerminalItem.linksOf(terminal).contains(mc.level.dimension(), pos);

        String key = mc.level.dimension().location() + "@" + pos.asLong();
        if (!key.equals(hoveredKey)) {
            hoveredKey = key;
            lastAction = null;
        } else if (linked != hoveredLinked) {
            lastAction = linked;
        }
        hoveredLinked = linked;

        // Warm-up / fade timing
        if (mc.screen != null) return;
        if (hoverWarmup < 6) {
            hoverWarmup += 2;
            return;
        }
        hoverWarmup++;
        hoverTicks = hoverTicks == 0 ? 11 : Math.max(hoverTicks, 6);

        String name = mc.level.getBlockEntity(pos) instanceof FactoryControllerBlockEntity be ? be.customName : "";
        Component displayName = FactoryControllerTerminalItem.controllerDisplayName(name);
        List<Component> tip = new ArrayList<>(3);
        tip.add(linked ? Component.translatable("createfactorycontroller.terminal.hud.linked", displayName)
                : displayName);
        tip.add(Component.translatable(linked
                ? "createfactorycontroller.terminal.hud.unlink"
                : "createfactorycontroller.terminal.hud.link").withStyle(ChatFormatting.GRAY));
        if (lastAction != null)
            tip.add(Component.translatable(lastAction
                    ? "createfactorycontroller.terminal.hud.linked_action"
                    : "createfactorycontroller.terminal.hud.unlinked_action").withStyle(ChatFormatting.GREEN));
        lastTip = tip;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || hoverTicks == 0 || lastTip == null) return;

        float alpha = hoverTicks > 5 ? (11 - hoverTicks) / 5f : Math.min(1, hoverTicks / 5f);
        int a = (int) (alpha * 255);
        if (a < 5) return;   // Font draws a near-zero alpha fully opaque

        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 75 - lastTip.size() * 12;
        for (int i = 0; i < lastTip.size(); i++) {
            Component line = lastTip.get(i);
            graphics.drawString(mc.font, line, x - mc.font.width(line) / 2, y,
                    (a << 24) | (i == 0 ? TITLE_COLOR : TEXT_COLOR));
            y += 12;
        }
    }
}
