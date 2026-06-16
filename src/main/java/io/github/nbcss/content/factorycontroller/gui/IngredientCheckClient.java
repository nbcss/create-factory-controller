package io.github.nbcss.content.factorycontroller.gui;

import com.simibubi.create.content.logistics.BigItemStack;
import io.github.nbcss.ClientConfig;
import io.github.nbcss.ServerConfig;
import io.github.nbcss.content.factorycontroller.item.ProductionPatternItem;
import io.github.nbcss.content.factorycontroller.item.ProductionTarget;
import io.github.nbcss.content.factorycontroller.packet.RequestIngredientCheckPacket;
import io.github.nbcss.content.factorycontroller.production.IngredientDemandResolver.PatternDemand;
import io.github.nbcss.content.factorycontroller.production.IngredientDemandResolver.Reserved;
import io.github.nbcss.content.factorycontroller.production.IngredientDemandResolver.Shortfall;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side of the Send-button ingredient pre-check: gathers the staged Production Patterns, throttles a request
 * to the server (≤ once per second while hovering), caches the latest result, and renders the tooltip. Driven from
 * {@code StockKeeperRequestScreenMixin}.
 */
public final class IngredientCheckClient {

    private static final long REQUEST_INTERVAL_MS = 1000;

    private static volatile boolean patternMissing = false;
    private static volatile List<Shortfall> shortfalls = List.of();

    private static long lastRequestMs = 0;
    private static String lastRequestKey = "";

    private IngredientCheckClient() {}

    /** Called by the S2C result packet. */
    public static void update(boolean missing, List<Shortfall> result) {
        patternMissing = missing;
        shortfalls = result;
    }

    /** Whether the feature is on (requires both the client and the (synced) server config enabled). */
    public static boolean enabled() {
        return ClientConfig.checkIngredientsOnSend() && ServerConfig.checkIngredientsOnSend();
    }

    /**
     * While the player hovers the keeper's Send button, (re)request the pre-check at most once a second (immediately
     * if the staged patterns changed) and draw the resulting tooltip. No staged patterns → nothing (item 3).
     */
    public static void onSendHover(BlockPos keeperPos, List<BigItemStack> itemsToOrder,
                                   GuiGraphics gfx, int mouseX, int mouseY) {
        if (!enabled()) return;

        List<PatternDemand> patterns = new ArrayList<>();
        List<Reserved> reserved = new ArrayList<>();   // real items in the same order — they ship out, so subtract
        StringBuilder key = new StringBuilder();
        for (BigItemStack b : itemsToOrder) {
            if (b.count <= 0) continue;
            if (ProductionPatternItem.isPattern(b.stack)) {
                ProductionTarget t = ProductionPatternItem.getTarget(b.stack);
                if (t == null) continue;
                patterns.add(new PatternDemand(t.patternId(), b.count));
                key.append(t.patternId()).append('x').append(b.count).append(';');
            } else {
                reserved.add(new Reserved(b.stack.copy(), b.count));
                key.append('r').append(b.stack.getItem()).append('x').append(b.count).append(';');
            }
        }
        if (patterns.isEmpty()) return;

        long nowMs = System.currentTimeMillis();
        String sig = key.toString();
        if (!sig.equals(lastRequestKey) || nowMs - lastRequestMs >= REQUEST_INTERVAL_MS) {
            lastRequestKey = sig;
            lastRequestMs = nowMs;
            PacketDistributor.sendToServer(new RequestIngredientCheckPacket(keeperPos, patterns, reserved));
        }

        gfx.renderComponentTooltip(Minecraft.getInstance().font, buildTooltip(), mouseX, mouseY);
    }

    private static List<Component> buildTooltip() {
        List<Component> lines = new ArrayList<>();
        if (patternMissing) {
            lines.add(Component.translatable("createfactorycontroller.gui.ingredients_pattern_missing")
                .withStyle(ChatFormatting.RED));
            return lines;
        }
        if (shortfalls.isEmpty()) {
            lines.add(Component.translatable("createfactorycontroller.gui.ingredients_sufficient")
                .withStyle(ChatFormatting.GREEN));
            return lines;
        }
        lines.add(Component.translatable("createfactorycontroller.gui.ingredients_warning")
            .withStyle(ChatFormatting.GOLD));
        for (Shortfall s : shortfalls)
            lines.add(Component.empty()
                .append(s.item().getHoverName().copy().withColor(0xFBDC7D))
                .append(Component.literal(": " + s.inStock() + "/" + s.required()).withStyle(ChatFormatting.GRAY)));
        return lines;
    }
}
