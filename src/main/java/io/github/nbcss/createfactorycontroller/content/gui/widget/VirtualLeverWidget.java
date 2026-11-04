package io.github.nbcss.createfactorycontroller.content.gui.widget;

import io.github.nbcss.createfactorycontroller.content.component.redstone.LeverBehaviour;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** A lever on the canvas: a click toggles it. The sprite follows the server state (no prediction). */
@OnlyIn(Dist.CLIENT)
public class VirtualLeverWidget extends VirtualManualSourceWidget<LeverBehaviour> {

    public VirtualLeverWidget(LeverBehaviour behaviour) {
        super(behaviour);
    }

    @Override
    protected Component actionHint() {
        return Component.translatable("createfactorycontroller.gui.action_toggle");
    }

    @Override
    public void onPressed(FactoryControllerScreen screen) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.LEVER_CLICK, behaviour.isPowered() ? 0.5f : 0.6f));
    }
}
