package io.github.nbcss.logisticscontrol.content.compat.jei;

import io.github.nbcss.logisticscontrol.CreateLogisticsControl;
import io.github.nbcss.logisticscontrol.ServerConfig;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * JEI integration. Loaded only when JEI is present (JEI scans for {@code @JeiPlugin}) and references only
 * JEI API types (a {@code compileOnly} dependency), so a JEI-less client never classloads it.
 *
 * <p>Removes the Filter Link from JEI's ingredient list when the feature is disabled, matching its absence
 * from the creative menu and the (condition-gated) recipe. Evaluated at runtime-available, i.e. after the
 * server config has synced, so it reflects the server's setting on join.</p>
 */
@JeiPlugin
public class LogisticsControlJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath(CreateLogisticsControl.MODID, "jei");

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime jeiRuntime) {
        if (!ServerConfig.filterLinkEnabled())
            jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(
                VanillaTypes.ITEM_STACK,
                List.of(new ItemStack(CreateLogisticsControl.FILTER_LINK_ITEM.get())));
    }
}
