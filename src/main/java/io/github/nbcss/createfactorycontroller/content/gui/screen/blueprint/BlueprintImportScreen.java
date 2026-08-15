package io.github.nbcss.createfactorycontroller.content.gui.screen.blueprint;

import com.simibubi.create.foundation.gui.AllIcons;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintStorage;
import io.github.nbcss.createfactorycontroller.content.blueprint.SchematicImport;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Save screen for a blueprint imported from a Schematic-and-Quill world selection. */
public class BlueprintImportScreen extends BlueprintFormScreen {
    private final SchematicImport.ImportedBoard board;
    private final List<UUID> networks;

    public BlueprintImportScreen(FactoryControllerScreen controller, SchematicImport.ImportedBoard board) {
        super(controller, Component.translatable("createfactorycontroller.gui.blueprint.import_title"));
        this.board = board;
        this.networks = new ArrayList<>(board.networks);
    }

    @Override
    protected List<BlueprintStorage.Material> materials() {
        return board.materials;
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
    protected ScreenElement discardIcon() {
        return AllIcons.I_MTD_CLOSE;
    }

    @Override
    protected Component discardTooltip() {
        return Component.translatable("createfactorycontroller.gui.blueprint.cancel");
    }

    /** Cancel returns to the library it was opened from, not the controller board. */
    @Override
    protected Screen previousScreen() {
        return new BlueprintLibraryScreen(controller);
    }

    @Override
    protected void confirm() {
        try {
            Path saved = BlueprintStorage.save(board, board.positions, networks, blueprintName(),
                    blueprintNote(), Minecraft.getInstance().level.registryAccess());
            controller.showBlueprintSaved(saved.getFileName().toString());
            Minecraft.getInstance().setScreen(controller);
        } catch (IOException | RuntimeException exception) {
            showError(BlueprintErrors.describe("createfactorycontroller.gui.blueprint.save_failed", exception));
        }
    }
}
