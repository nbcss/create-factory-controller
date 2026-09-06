package io.github.nbcss.createfactorycontroller.content.gui.widget.indicator;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.network.MissingLinkStatus;
import io.github.nbcss.createfactorycontroller.content.helper.TooltipBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Warns that one controller-known logistics network contains unloaded links. */
public class LinkMissingIndicator implements ControllerIndicator {
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(
            CreateFactoryController.MODID, "factory_controller/indicators/missing_links");

    private final FactoryControllerMenu menu;
    private final MissingLinkStatus status;

    public LinkMissingIndicator(FactoryControllerMenu menu, MissingLinkStatus status) {
        this.menu = menu;
        this.status = status;
    }

    @Override
    public ResourceLocation icon() {
        return ICON;
    }

    @Override
    public List<FormattedCharSequence> tooltip() {
        TooltipBuilder tooltip = TooltipBuilder.of(Minecraft.getInstance().font)
                .line(Component.translatable("createfactorycontroller.gui.indicator.missing_links",
                        menu.networkName(status.network())).withStyle(ChatFormatting.RED));
        for (GlobalPos link : status.links()) {
            ResourceLocation dimensionId = link.dimension().location();
            Component dimensionName = Component.translatableWithFallback(
                    dimensionId.toLanguageKey("dimension"), dimensionId.toString());
            tooltip.line(Component.translatable("createfactorycontroller.gui.indicator.missing_link_entry",
                    link.pos().toShortString(), dimensionName).withStyle(ChatFormatting.GRAY));
        }
        return tooltip.build();
    }
}
