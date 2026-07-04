package io.github.nbcss.createfactorycontroller.content.gui.widget;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.net.URL;

/**
 * A general help button appearing in the top-right corner of a window.
 * Opens a URL in browser to a documentation page.
 */
public class HelpButton extends GraphicButton {

    public static final int WIDTH = 9;
    public static final int HEIGHT = 9;

    public enum ColorPalette {
        GENERAL(0x555555, 0xFFFFFF, "factory_controller/tiny_button/base_general"),
        ANDESITE(0x494848, 0xC6C6C6, "factory_controller/tiny_button/base_andesite"),
        BRASS(0x5E3201, 0xFFEB8C, "factory_controller/tiny_button/base_brass"),
        LOGISTICS(0x44485A, 0xA8C4DF, "factory_controller/tiny_button/base_logistics"),
        ROSE(0x741A41, 0xF6D4C2, "factory_controller/tiny_button/base_rose"),
        ;

        private final int darkColor, lightColor;
        private final ResourceLocation baseSprite;

        ColorPalette(int darkColor, int lightColor, String path) {
            this.darkColor = darkColor;
            this.lightColor = lightColor;
            this.baseSprite = ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, path);
        }
    }

    private static final ResourceLocation iconSprite = ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID,
            "factory_controller/tiny_button/help");

    public HelpButton(int x, int y, ColorPalette colorPalette, String url) {
        super(x, y, WIDTH, HEIGHT, () -> {
            Util.getPlatform().openUri(url);
            return true;
        });
        addGraphic(DISPLAY_BOTH, colorPalette.baseSprite);
        addGraphic(DISPLAY_HOVER, 0x44FFFFFF, 1, 1, 7, 7);
        addGraphic(DISPLAY_NORMAL, iconSprite, colorPalette.darkColor, 2, 2, 5, 5);
        addGraphic(DISPLAY_HOVER, iconSprite, colorPalette.lightColor, 2, 2, 5, 5);
        //withTooltip(Component.translatable("createfactorycontroller.gui.help_button"));
    }
}
