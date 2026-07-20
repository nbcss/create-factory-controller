package io.github.nbcss.createfactorycontroller.content.gui.screen;

import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintStorage;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BlueprintSaveScreen extends BlueprintFormScreen {
    private final List<VirtualComponentPosition> selected;
    private final List<BlueprintStorage.Material> materials;
    private final List<UUID> networks;

    public BlueprintSaveScreen(FactoryControllerScreen controller, Set<VirtualComponentPosition> selection) {
        super(controller, Component.translatable("createfactorycontroller.gui.blueprint.save_title"));
        this.selected = selection.stream().filter(p -> menu.componentAt(p) != null).toList();
        this.materials = BlueprintStorage.materials(menu, this.selected);
        this.networks = new ArrayList<>(BlueprintStorage.networks(menu, this.selected));
    }

    @Override
    protected List<BlueprintStorage.Material> materials() {
        return materials;
    }

    @Override
    protected int networkCount() {
        return networks.size();
    }

    @Override
    protected String initialName() {
        return "";
    }

    @Override
    protected String initialNote() {
        return "";
    }

    @Override
    protected boolean networksDraggable() {
        return true;
    }

    @Override
    protected void moveNetwork(int from, int to) {
        networks.add(to, networks.remove(from));
    }

    @Override
    protected List<Component> networkTooltip(int slot) {
        return List.of(menu.networkName(networks.get(slot)),
                Component.translatable("createfactorycontroller.gui.blueprint.drag_to_reorder")
                        .withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected void renderNetworkSlotIcon(GuiGraphics gfx, int slot, int x, int y) {
        NetworkSettings settings = menu.networkSettings(networks.get(slot));
        if (!settings.hasCustomIcon()) {
            renderNetworkIcon(gfx, x, y, settings.backgroundColor(), settings.color());
            return;
        }
        renderNetworkSlotBackground(gfx, x, y, 0xFF8B8B8B);
        gfx.renderItem(settings.icon(), x + 1, y + 1);
    }

    @Override
    protected void confirm() {
        try {
            Path saved = BlueprintStorage.save(menu, selected, networks, blueprintName(),
                    blueprintNote(), Minecraft.getInstance().level.registryAccess());
            controller.showBlueprintSaved(saved.getFileName().toString());
            Minecraft.getInstance().setScreen(controller);
        } catch (IOException | RuntimeException exception) {
            showError(Component.translatable("createfactorycontroller.gui.blueprint.save_failed",
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
                    .withStyle(ChatFormatting.RED));
        }
    }
}
