package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.simibubi.create.AllSoundEvents;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Shared client-side UI feedback sounds for screens and widgets. */
@OnlyIn(Dist.CLIENT)
public final class ScreenSounds {

    private ScreenSounds() {}

    /** Create's soft GUI button blip — for value-box / slot clicks. */
    public static void click() { play(SoundEvents.UI_BUTTON_CLICK.value(), 1f); }

    /** Create's value-scroll blip (SCROLL_VALUE, pitch 1.5) — matches its ScrollInput widget. */
    public static void scroll() { play(AllSoundEvents.SCROLL_VALUE.getMainEvent(), 1.5f); }

    /** Factory Controller board screen opened. */
    public static void controllerOpen() { play(CreateFactoryController.CONTROLLER_UI_OPEN.get(), 1f); }

    /** Factory Controller board screen closed. */
    public static void controllerClose() { play(CreateFactoryController.CONTROLLER_UI_CLOSE.get(), 1f); }

    /** Component / gauge settings overlay opened. */
    public static void panelOpen() { play(CreateFactoryController.GAUGE_UI_OPEN.get(), 1f); }

    /** Component / gauge settings overlay closed. */
    public static void panelClose() { play(CreateFactoryController.GAUGE_UI_CLOSE.get(), 1f); }

    /** Create's rejection buzz, for actions the client rejects before sending. */
    public static void deny() { play(AllSoundEvents.DENY.getMainEvent(), 1f); }

    /** Create's wrench-rotate click, for board edits done with the wrench keys. */
    public static void wrench() { play(AllSoundEvents.WRENCH_ROTATE.getMainEvent(), 1f); }

    /** UI sound at {@code forUI}'s default volume (0.25). */
    public static void play(SoundEvent sound, float pitch) {
        play(sound, pitch, 0.25f);
    }

    public static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
