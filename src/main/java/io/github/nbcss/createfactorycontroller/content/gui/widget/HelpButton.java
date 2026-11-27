package io.github.nbcss.createfactorycontroller.content.gui.widget;

import com.ibm.icu.util.LocaleMatcher;
import com.ibm.icu.util.ULocale;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final ResourceLocation baseSpritePrefix = ResourceLocation.fromNamespaceAndPath(
            CreateFactoryController.MODID, "tiny_button/base_");
    private static final ResourceLocation iconSprite = ResourceLocation.fromNamespaceAndPath(
            CreateFactoryController.MODID, "tiny_button/help");
    private static final ResourceLocation colorPaletteDir = ResourceLocation.fromNamespaceAndPath(
            CreateFactoryController.MODID, "textures/tiny_button/palettes");

    /**
     * Color palettes loaded from textures by {@link reloadClientResources}.
     */
    private static Map<String, ColorPalette> colorPalettes = Map.of();
    private record ColorPalette(int fgColor, int fgHoverColor) {
        static int toArgb(int abgr) {
            return abgr & 0xFF000000 | abgr << 16 & 0xFF0000 | abgr & 0xFF00 | abgr >> 16 & 0xFF;
        }
        ColorPalette(NativeImage image) {
            this(
                    toArgb(image.getPixelRGBA(3, 0)),
                    toArgb(image.getPixelRGBA(4, 0)));
        }
        static ColorPalette DEFAULT = new ColorPalette(0xFF000000, 0xFFFFFFFF);
    }

    /**
     * Load color palettes from {@code createfactorycontroller:tiny_button/palettes/{name}.png}.
     * Registered in {@link RegisterClientReloadListenersEvent}.
     */
    public static CompletableFuture<Void> reloadClientResources(
            PreparableReloadListener.PreparationBarrier preparationBarrier,
            ResourceManager resourceManager,
            ProfilerFiller preparationsProfiler,
            ProfilerFiller reloadProfiler,
            Executor backgroundExecutor,
            Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() ->
                    resourceManager.listResources(colorPaletteDir.getPath(), id ->
                                id.getNamespace().equals(colorPaletteDir.getNamespace()) && id.getPath().endsWith(".png"))
                        .entrySet().stream()
                        .flatMap(resource -> {
                            String path = resource.getKey().getPath();
                            String name = path.substring(colorPaletteDir.getPath().length() + 1, path.length() - 4);
                            if (name.equals("key")) return Stream.empty();
                            ColorPalette content;
                            try (InputStream inputStream = resource.getValue().open()) {
                                try (var image = NativeImage.read(inputStream)) {
                                    content = new ColorPalette(image);
                                }
                            } catch (IOException ex) {
                                LogUtils.getLogger().warn("Failed to read {}", resource.getKey(), ex);
                                return Stream.empty();
                            }
                            return Stream.of(Map.entry(name, content));
                        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), backgroundExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(data -> colorPalettes = data, gameExecutor);
    }


    public HelpButton(int x, int y, String colorPaletteName, String docPath) {
        super(x, y, WIDTH, HEIGHT, () -> {
            Util.getPlatform().openUri(getDocUrl(docPath));
            return true;
        });

        ColorPalette colorPalette = colorPalettes.getOrDefault(colorPaletteName, ColorPalette.DEFAULT);

        addGraphic(DISPLAY_BOTH, baseSpritePrefix.withSuffix(colorPaletteName));
        addGraphic(DISPLAY_HOVER, 0x44FFFFFF, 1, 1, 7, 7);
        addGraphic(DISPLAY_NORMAL, iconSprite, colorPalette.fgColor, 2, 2, 5, 5);
        addGraphic(DISPLAY_HOVER, iconSprite, colorPalette.fgHoverColor, 2, 2, 5, 5);

        addTooltip(Component.translatable("createfactorycontroller.gui.help.open_wiki"));
        addTooltip(Component.literal(getDocUrl(docPath)).withStyle(ChatFormatting.BLUE));
    }

    @Override
    public GraphicButton addTooltip(@Nullable Component line) {
        tooltip.wrapped(line, TOOLTIP_WIDTH);
        return this;
    }

    public void renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (isMouseOver(mouseX, mouseY))
            graphics.renderTooltip(font, getTooltipText(), mouseX, mouseY);
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
