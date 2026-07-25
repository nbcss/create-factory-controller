package io.github.nbcss.createfactorycontroller.content.gui.screen.blueprint;

import com.simibubi.create.foundation.gui.AllIcons;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintStorage;
import io.github.nbcss.createfactorycontroller.content.gui.screen.FactoryControllerScreen;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class BlueprintEditScreen extends BlueprintFormScreen {
    private final Path file;
    private final String originalName;
    private final BlueprintStorage.Info info;

    public BlueprintEditScreen(FactoryControllerScreen controller, String blueprintName, BlueprintStorage.Info info) {
        super(controller, Component.translatable("createfactorycontroller.gui.blueprint.edit_title"));
        this.originalName = blueprintName;
        this.file = BlueprintStorage.blueprintPath(blueprintName);
        this.info = info;
    }

    @Override
    protected List<BlueprintStorage.Material> materials() {
        return info.materials();
    }

    @Override
    protected int networkCount() {
        return info.networkCount();
    }

    @Override
    protected String initialName() {
        return originalName;
    }

    @Override
    protected String initialNote() {
        return info.note();
    }

    @Override
    protected ScreenElement discardIcon() {
        return AllIcons.I_MTD_CLOSE;
    }

    @Override
    protected Component discardTooltip() {
        return Component.translatable("createfactorycontroller.gui.blueprint.cancel");
    }

    @Override
    protected boolean isOwnName(String name) {
        return BlueprintStorage.isValidBlueprintName(name)
                && BlueprintStorage.sameBlueprintFile(BlueprintStorage.blueprintPath(name), file);
    }

    @Override
    protected Screen previousScreen() {
        return new BlueprintLibraryScreen(controller);
    }

    @Override
    protected void confirm() {
        try {
            BlueprintStorage.edit(file, blueprintName(), blueprintNote());
            Minecraft.getInstance().setScreen(previousScreen());
        } catch (IOException | RuntimeException exception) {
            showError(BlueprintErrors.describe("createfactorycontroller.gui.blueprint.save_failed", exception));
        }
    }
}
