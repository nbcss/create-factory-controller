package io.github.nbcss.createfactorycontroller.content.gui.widget;

import com.ibm.icu.util.LocaleMatcher;
import com.ibm.icu.util.ULocale;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * A general help button appearing in the top-right corner of a window.
 * Opens a URL in browser to a documentation page.
 */
public class HelpButton extends GraphicButton {

    public static final int WIDTH = 9;
    public static final int HEIGHT = 9;

    /**
     * Suggested tooltip width.
     */
    public static final int TOOLTIP_WIDTH = 200;

    public enum ColorPalette {
        GENERAL(0x555555, 0xFFFFFF, "factory_controller/tiny_button/base_general"),
        ANDESITE(0x494848, 0xC6C6C6, "factory_controller/tiny_button/base_andesite"),
        BRASS(0x5E3201, 0xFFEB8C, "factory_controller/tiny_button/base_brass"),
        LOGISTICS(0x44485A, 0xA8C4DF, "factory_controller/tiny_button/base_logistics"),
        ROSE(0x741A41, 0xF6D4C2, "factory_controller/tiny_button/base_rose"),
        STOCK_KEEPER(0xB59370, 0xF8F8EC, "factory_controller/tiny_button/base_stock_keeper"),
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

    public HelpButton(int x, int y, ColorPalette colorPalette, String docPath) {
        super(x, y, WIDTH, HEIGHT, () -> {
            Util.getPlatform().openUri(getDocUrl(docPath));
            return true;
        });
        addGraphic(DISPLAY_BOTH, colorPalette.baseSprite);
        addGraphic(DISPLAY_HOVER, 0x44FFFFFF, 1, 1, 7, 7);
        addGraphic(DISPLAY_NORMAL, iconSprite, colorPalette.darkColor, 2, 2, 5, 5);
        addGraphic(DISPLAY_HOVER, iconSprite, colorPalette.lightColor, 2, 2, 5, 5);

        addTooltip(Component.translatable("createfactorycontroller.gui.help.open_wiki"));
        addTooltip(Component.literal(getDocUrl(docPath)).withStyle(ChatFormatting.BLUE));
    }

    public void renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (isMouseOver(mouseX, mouseY))
            graphics.renderTooltip(font, getTooltipText(font, TOOLTIP_WIDTH), mouseX, mouseY);
    }

    private static final String DOC_URL_BASE = "https://nbcss.github.io/create-factory-controller/manual/";
    private static final List<String> DOC_LANGUAGES = List.of("en", "zh-hans"); // manually change as supported manual languages changes

    private static final LocaleMatcher DOC_LOCALE_MATCHER = LocaleMatcher.builder()
            .setSupportedULocales(DOC_LANGUAGES.stream().map(ULocale::new).toList()).build();

    private static String getDocUrl(String path) {
        String gameLanguage = Minecraft.getInstance().getLanguageManager().getSelected();
        String docLanguage = DOC_LOCALE_MATCHER.getBestMatch(new ULocale(gameLanguage))
                .toLanguageTag().toLowerCase(Locale.ROOT);
        return DOC_URL_BASE + docLanguage + '/' + path;
    }
}
