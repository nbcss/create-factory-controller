package io.github.nbcss.logisticscontrol.content.helper;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInput;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import io.github.nbcss.logisticscontrol.CreateLogisticsControl;
import io.github.nbcss.logisticscontrol.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Lets the Filter Link pin which recipe a Mechanical Crafter resolves. Every crafter carries an <b>inert</b>
 * {@link FilteringBehaviour}: it is never rendered and never captures a right-click (so a Deployer aimed at the crafter
 * still inserts items, and players can't hand-set it) — only the Filter Link writes it, via {@link #applyOnUnwrap}. The
 * crafter reads it when it resolves its recipe (see {@code MechanicalCrafterFilterMixin}).
 */
public final class CrafterRecipeFilter {
    private CrafterRecipeFilter() {}

    /** Attach the inert recipe filter to every Mechanical Crafter as it is built/loaded. */
    public static void onAttachBehaviours(BlockEntityBehaviourEvent event) {
        event.forType(AllBlockEntityTypes.MECHANICAL_CRAFTER.get(), crafter ->
            event.attach(new FilteringBehaviour(crafter, new HeadlessSlot()).onlyActiveWhen(() -> false)));
    }

    /** When a package is unwrapped into a crafter, pin its carried filter onto the chain's resolving crafter — the one
     *  that actually looks the recipe up. Absent filter clears the pin (matches the Filter Link's clear-on-absent). */
    public static void applyOnUnwrap(PackagerBlockEntity packager, ItemStack box) {
        if (!ServerConfig.filterLinkEnabled()) return;
        Level level = packager.getLevel();
        if (level == null || level.isClientSide) return;
        Direction facing = packager.getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(Direction.UP);
        BlockPos targetPos = packager.getBlockPos().relative(facing.getOpposite());
        if (!(level.getBlockEntity(targetPos) instanceof MechanicalCrafterBlockEntity entry)) return;
        PackageFilter pf = box.get(CreateLogisticsControl.PACKAGE_FILTER.get());
        ItemStack toApply = pf == null || pf.stack().isEmpty() ? ItemStack.EMPTY : pf.stack().copyWithCount(1);
        List<MechanicalCrafterBlockEntity> chain = RecipeGridHandler.getAllCraftersOfChain(entry);
        if (chain == null) chain = List.of(entry);
        for (MechanicalCrafterBlockEntity c : chain) {
            FilteringBehaviour beh = BlockEntityBehaviour.get(level, c.getBlockPos(), FilteringBehaviour.TYPE);
            if (beh != null) beh.setFilter(toApply);
        }
    }

    /**
     * Among the recipes the current grid actually satisfies, return the one whose output matches {@code want}, or
     * {@code null} if none — the caller then falls back to Create's default first-match, so an unusable filter is simply
     * ignored and the crafter never fabricates an output the grid can't legitimately craft. Mirrors the regular-then-
     * mechanical precedence of {@link RecipeGridHandler#tryToApplyRecipe}.
     */
    public static ItemStack resolveFiltered(Level level, RecipeGridHandler.GroupedItems items, ItemStack want) {
        items.calcStats();
        CraftingInput input = MechanicalCraftingInput.of(items);
        RegistryAccess registries = level.registryAccess();
        RecipeManager recipes = level.getRecipeManager();
        if (AllConfigs.server().recipes.allowRegularCraftingInCrafter.get()) {
            for (RecipeHolder<CraftingRecipe> holder : recipes.getRecipesFor(RecipeType.CRAFTING, input, level)) {
                if (!RecipeGridHandler.isRecipeAllowed(holder, input)) continue;
                ItemStack out = holder.value().assemble(input, registries);
                if (!out.isEmpty() && ItemStack.isSameItem(out, want)) return out;
            }
        }
        RecipeType<MechanicalCraftingRecipe> mechanicalType = AllRecipeTypes.MECHANICAL_CRAFTING.getType();
        for (RecipeHolder<MechanicalCraftingRecipe> holder : recipes.getRecipesFor(mechanicalType, input, level)) {
            ItemStack out = holder.value().assemble(input, registries);
            if (!out.isEmpty() && ItemStack.isSameItem(out, want)) return out;
        }
        return null;
    }

    /** A value-box slot that positions nowhere: the filter behaviour is inert, so this is never consulted, but pairing
     *  it with a null offset keeps the filter invisible and unclickable even if the behaviour is ever made active. */
    private static final class HeadlessSlot extends ValueBoxTransform {
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return null;
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {}
    }
}
