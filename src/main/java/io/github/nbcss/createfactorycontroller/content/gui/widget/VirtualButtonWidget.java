package io.github.nbcss.createfactorycontroller.content.gui.widget;

import io.github.nbcss.createfactorycontroller.content.component.redstone.ButtonBehaviour;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** A button on the canvas: powered while held. Drawn pressed while this client holds it, ahead of the server state. */
@OnlyIn(Dist.CLIENT)
public class VirtualButtonWidget extends VirtualManualSourceWidget<ButtonBehaviour> {

    public VirtualButtonWidget(ButtonBehaviour behaviour) {
        super(behaviour);
    }

    @Override
    protected boolean renderPowered() {
        return behaviour.isPowered()
                || Minecraft.getInstance().screen instanceof FactoryControllerScreen screen
                   && position().equals(screen.heldPress());
    }

    @Override
    protected Component actionHint() {
        return Component.translatable("createfactorycontroller.gui.action_hold_press");
    }

    @Override
    public void onPressed(FactoryControllerScreen screen) {
        screen.beginHeldPress(position());
        playClick(SoundEvents.STONE_BUTTON_CLICK_ON);
    }

    @Override
    public void onReleased(FactoryControllerScreen screen) {
        playClick(SoundEvents.STONE_BUTTON_CLICK_OFF);
    }

    /** Full forUI volume: sounds.json already scales the button clicks to 0.3; the 0.25 default would mute them. */
    private static void playClick(SoundEvent sound) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1f, 1f));
    }
}
