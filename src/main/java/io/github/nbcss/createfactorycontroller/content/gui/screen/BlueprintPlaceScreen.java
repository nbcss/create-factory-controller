package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import net.createmod.catnip.gui.element.ScreenElement;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintPlacement;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintStorage;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.gui.widget.ScrollListWindow;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import io.github.nbcss.createfactorycontroller.content.packet.BlueprintPlacePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlueprintPlaceScreen extends BlueprintFormScreen {
    private static final int NEW_PLUS_COLOR = 0xFF55FF55;

    private static final ResourceLocation PLACE_ICON = resource("icons/bp_place");

    private final BlueprintPlacement placement;
    private final VirtualComponentPosition anchor;
    /** Rebuilt each tick so the material preview tracks the inventory while the screen is open. */
    private Map<Item, Integer> inventoryCounts = Map.of();

    public BlueprintPlaceScreen(FactoryControllerScreen controller, BlueprintPlacement placement,
                                VirtualComponentPosition anchor) {
        super(controller, Component.translatable("createfactorycontroller.gui.blueprint.place_title"));
        this.placement = placement;
        this.anchor = anchor;
    }

    @Override
    protected void init() {
        inventoryCounts = BlueprintPlacement.inventoryCounts(Minecraft.getInstance().player);
        super.init();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        inventoryCounts = BlueprintPlacement.inventoryCounts(Minecraft.getInstance().player);
    }

    @Override
    protected int materialCountColor(BlueprintStorage.Material material) {
        return BlueprintPlacement.isMaterialSufficient(Minecraft.getInstance().player, inventoryCounts, material)
                ? BlueprintPlacement.MATERIAL_HELD_COLOR : BlueprintPlacement.MATERIAL_MISSING_COLOR;
    }

    @Override
    protected List<BlueprintStorage.Material> materials() {
        return placement.info().materials();
    }

    @Override
    protected int networkCount() {
        return placement.info().networkCount();
    }

    @Override
    protected String initialName() {
        return placement.name();
    }

    @Override
    protected String initialNote() {
        return placement.info().note();
    }

    @Override
    protected boolean editable() {
        return false;
    }

    @Override
    protected ScreenElement confirmIcon() {
        return (gfx, x, y) -> gfx.blitSprite(PLACE_ICON, x, y, 16, 16);
    }

    @Override
    protected Component confirmTooltip() {
        return Component.translatable("createfactorycontroller.gui.blueprint.place");
    }

    @Override
    protected ScreenElement discardIcon() {
        return AllIcons.I_MTD_CLOSE;
    }

    @Override
    protected Component discardTooltip() {
        return Component.translatable("createfactorycontroller.gui.blueprint.cancel");
    }

    private List<UUID> options() {
        return BlueprintPlacement.networkOptions(menu, Minecraft.getInstance().player);
    }

    private boolean isNewNetwork(UUID network) {
        return !menu.knownNetworks.contains(network);
    }

    // ── Network slots ─────────────────────────────────────────────────────────

    @Override
    protected boolean scrollNetworkSlot(int slot, double direction) {
        List<UUID> options = options();
        if (options.isEmpty()) return false;
        // Index 0 is the unassigned state, so the options themselves start at 1.
        UUID current = placement.assignment(slot);
        int index = current == null ? 0 : options.indexOf(current) + 1;
        int next = Mth.clamp(index - (int) Math.signum(direction), 0, options.size());
        if (next == index) return true;   // at a clamp boundary — still handled
        placement.assign(slot, next == 0 ? null : options.get(next - 1));
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(AllSoundEvents.SCROLL_VALUE.getMainEvent(), 1.5f));
        return true;
    }

    @Override
    protected void renderNetworkSlotIcon(GuiGraphics gfx, int slot, int x, int y) {
        UUID network = placement.assignment(slot);
        if (network == null) {
            super.renderNetworkSlotIcon(gfx, slot, x, y);   // the base's neutral "unassigned" icon
            return;
        }
        NetworkSettings settings = menu.networkSettings(network);
        if (settings.hasCustomIcon()) {
            renderNetworkSlotBackground(gfx, x, y, 0xFF8B8B8B);
            gfx.renderItem(settings.icon(), x + 1, y + 1);
        } else {
            renderNetworkIcon(gfx, x, y, settings.backgroundColor(), settings.color());
        }
    }

    @Override
    protected void renderNetworkSlotDecoration(GuiGraphics gfx, int slot, int x, int y) {
        UUID network = placement.assignment(slot);
        if (network == null || !isNewNetwork(network)) return;
        String text = "+";
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 200);
        Matrix4f matrix = gfx.pose().last().pose();
        font.drawInBatch8xOutline(Component.literal(text).getVisualOrderText(),
                x + 17 - font.width(text), y + 10, NEW_PLUS_COLOR, 0x000000,
                matrix, gfx.bufferSource(), LightTexture.FULL_BRIGHT);
        gfx.flush();
        gfx.pose().popPose();
    }

    @Override
    protected List<Component> networkTooltip(int slot) {
        List<UUID> options = options();
        UUID current = placement.assignment(slot);
        int state = current == null ? 0 : options.indexOf(current) + 1;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("createfactorycontroller.gui.blueprint.network_slot", slot + 1)
                .withColor(ScrollInput.HEADER_RGB.getRGB()));

        for (int i : ScrollListWindow.rows(options.size() + 1, state)) {
            if (i == ScrollListWindow.MARKER) {
                lines.add(Component.literal("> ...").withStyle(ChatFormatting.GRAY));
                continue;
            }
            boolean selected = i == state;
            Component name = i == 0
                    ? Component.translatable("createfactorycontroller.gui.blueprint.network_unassigned")
                    : menu.networkName(options.get(i - 1));
            ChatFormatting color = i > 0 && isNewNetwork(options.get(i - 1)) ? ChatFormatting.GREEN
                    : selected ? ChatFormatting.WHITE : ChatFormatting.GRAY;
            lines.add(Component.literal(selected ? "-> " : "> ").append(name).withStyle(color));
        }

        if (options.isEmpty())
            lines.add(Component.translatable("createfactorycontroller.gui.network_selector_no_network_tip")
                    .withStyle(ChatFormatting.RED));
        lines.add(Component.translatable("createfactorycontroller.gui.blueprint.network_scroll_tip")
                .withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
        return lines;
    }

    // ── Confirm ───────────────────────────────────────────────────────────────

    @Override
    protected boolean canConfirm() {
        return placement.allNetworksAssigned()
                && placement.hasCapacity(menu)
                && placement.hasMaterials(Minecraft.getInstance().player);
    }

    @Nullable
    @Override
    protected Component confirmBlockedReason() {
        if (!placement.hasCapacity(menu))
            return Component.translatable("createfactorycontroller.gui.blueprint.capacity_reached");
        if (!placement.hasMaterials(Minecraft.getInstance().player))
            return Component.translatable("createfactorycontroller.gui.blueprint.missing_materials");
        if (!placement.allNetworksAssigned())
            return Component.translatable("createfactorycontroller.gui.blueprint.assign_networks");
        return null;
    }

    @Override
    protected void confirm() {
        PacketDistributor.sendToServer(new BlueprintPlacePacket(menu.controllerPos, anchor,
                placement.payload(), placement.assignments()));
        controller.abortBlueprintPlacement();
        Minecraft.getInstance().setScreen(controller);
    }

    @Override
    protected void onDiscard() {
        controller.abortBlueprintPlacement();
    }
}
