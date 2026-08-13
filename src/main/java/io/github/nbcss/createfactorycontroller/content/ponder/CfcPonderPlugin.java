package io.github.nbcss.createfactorycontroller.content.ponder;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class CfcPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return CreateFactoryController.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(CreateFactoryController.FILTER_LINK.getId())
            .addStoryBoard("filter_link/using_filter_link", FilterLinkScenes::usingFilterLink);
    }
}
