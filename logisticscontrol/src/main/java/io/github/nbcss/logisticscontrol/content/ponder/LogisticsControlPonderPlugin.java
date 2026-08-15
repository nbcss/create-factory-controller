package io.github.nbcss.logisticscontrol.content.ponder;

import io.github.nbcss.logisticscontrol.CreateLogisticsControl;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class LogisticsControlPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return CreateLogisticsControl.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(CreateLogisticsControl.FILTER_LINK.getId())
            .addStoryBoard("filter_link/using_filter_link", FilterLinkScenes::usingFilterLink);
    }
}
