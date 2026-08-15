package io.github.nbcss.logisticscontrol.content.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.funnel.AbstractDirectionalFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.PonderHilo;
import io.github.nbcss.logisticscontrol.CreateLogisticsControl;
import io.github.nbcss.logisticscontrol.content.helper.PackageFilter;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FilterLinkScenes {
    private FilterLinkScenes() {}

    public static void usingFilterLink(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("filter_link", "Using the Filter Link");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(.85f);
        scene.setSceneOffsetY(-.25f);

        BlockPos sawPos = util.grid().at(2, 1, 2);
        BlockPos inputBelt = util.grid().at(4, 1, 2);
        BlockPos vaultPos = inputBelt.above();
        BlockPos beltFunnelPos = inputBelt.west().above();
        BlockPos packagerPos = vaultPos.above();
        BlockPos centerCog = util.grid().at(2, 1, 3);
        BlockPos westCog = centerCog.west();
        BlockPos eastCog = centerCog.east();
        BlockPos funnelPos = util.grid().at(3, 2, 4);
        BlockPos targetVaultPos = funnelPos.below();
        BlockPos basinPos = util.grid().at(1, 2, 4);
        BlockPos burnerPos = basinPos.below();
        BlockPos linkPos = packagerPos.north();
        BlockPos tickerPos = sawPos.north();
        BlockPos boundsMarker = util.grid().at(6, 4, 6);

        Selection sawLine = util.select().fromTo(0, 1, 2, 4, 1, 2);
        Selection cogwheels = util.select().fromTo(westCog, eastCog);
        Selection drive = util.select().fromTo(2, 1, 4, 2, 1, 5);
        Selection sawSelection = util.select().position(sawPos);
        Selection vaultSelection = util.select().position(vaultPos);
        Selection beltFunnelSelection = util.select().position(beltFunnelPos);
        Selection targetVaultSelection = util.select().position(targetVaultPos);
        Selection burnerSelection = util.select().position(burnerPos);
        Selection funnelSelection = util.select().position(funnelPos);
        Selection basinSelection = util.select().position(basinPos);
        Selection packagerSelection = util.select().position(packagerPos);
        Selection linkSelection = util.select().position(linkPos);
        Selection tickerSelection = util.select().position(tickerPos);

        Vec3 sawFilter = util.vector().of(2.5, 1 + 13 / 16f, 2 + 5 / 16f);
        Vec3 funnelFilter = util.vector().topOf(funnelPos).add(0, .2, 0);
        Vec3 basinFilter = util.vector().topOf(basinPos).add(0, .2, 0);
        Vec3 linkCenter = util.vector().centerOf(linkPos);
        Vec3 packagerFace = util.vector().blockSurface(packagerPos, Direction.NORTH);

        ItemStack filterLink = CreateLogisticsControl.FILTER_LINK_ITEM.get().getDefaultInstance();
        ItemStack input = new ItemStack(Items.OAK_PLANKS);
        ItemStack output = new ItemStack(Items.OAK_FENCE);
        ItemStack box = PackageStyles.getDefaultBox().copy();
        box.set(CreateLogisticsControl.PACKAGE_FILTER.get(), PackageFilter.of(output));

        // The template marker expands PonderLevel's actual block-derived bounds; remove it before revealing the scene.
        scene.world().setBlock(boundsMarker, Blocks.AIR.defaultBlockState(), false);
        BlockState cogwheel = AllBlocks.COGWHEEL.getDefaultState()
            .setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        scene.world().setBlock(centerCog, cogwheel, false);
        scene.world().setBlock(westCog, cogwheel, false);
        scene.world().setBlock(eastCog, cogwheel, false);
        scene.world().setBlock(targetVaultPos, AllBlocks.ITEM_VAULT.getDefaultState()
            .setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X), false);
        scene.world().setBlock(funnelPos, AllBlocks.BRASS_FUNNEL.getDefaultState()
            .setValue(AbstractDirectionalFunnelBlock.FACING, Direction.UP), false);
        scene.world().setBlock(burnerPos, AllBlocks.BLAZE_BURNER.getDefaultState()
                .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED), false);
        scene.world().setBlock(basinPos, AllBlocks.BASIN.getDefaultState(), false);
        scene.world().setBlock(basinPos.above(2), AllBlocks.MECHANICAL_MIXER.getDefaultState(), false);
        scene.world().setBlock(linkPos, CreateLogisticsControl.FILTER_LINK.get().defaultBlockState()
            .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
            .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, Direction.NORTH), false);
        scene.world().setBlock(tickerPos, AllBlocks.STOCK_TICKER.getDefaultState()
            .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), false);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(8);
        scene.world().showSection(drive, Direction.NORTH);
        scene.world().showSection(cogwheels, Direction.NORTH);
        scene.idle(8);
        scene.world().showSection(sawLine, Direction.DOWN);
        scene.world().showSection(vaultSelection, Direction.DOWN);
        scene.world().showSection(beltFunnelSelection, Direction.DOWN);
        scene.world().showSection(packagerSelection, Direction.DOWN);
        scene.idle(18);

        scene.world().flapFunnel(beltFunnelPos, true);
        scene.world().createItemOnBelt(beltFunnelPos.below(), Direction.UP, input.copy());
        scene.idle(12);
        scene.world().flapFunnel(beltFunnelPos, true);
        scene.world().createItemOnBelt(beltFunnelPos.below(), Direction.UP, input.copy());
        scene.overlay().showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .text("Some recipes may not process correctly without a filter configured")
            .pointAt(sawFilter)
            .placeNearTarget();
        scene.overlay().showFilterSlotInput(sawFilter, Direction.UP, 90);
        scene.idle(110);

        scene.world().showSection(linkSelection, Direction.SOUTH);
        scene.overlay().showText(70)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text("A Filter Link can be used to solve this issue")
            .pointAt(linkCenter)
            .placeNearTarget();
        scene.idle(70);
        scene.world().hideSection(linkSelection, Direction.NORTH);
        scene.idle(20);

        scene.overlay().showControls(sawFilter, Pointing.DOWN, 24)
            .rightClick()
            .withItem(filterLink);
        scene.idle(8);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, sawPos, new AABB(sawPos), 246);
        scene.overlay().showText(75)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text("Right-click block with Filter Link to select as target")
            .pointAt(sawFilter)
            .placeNearTarget();
        scene.idle(85);

        scene.world().showSection(targetVaultSelection, Direction.DOWN);
        scene.world().showSection(funnelSelection, Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(burnerSelection, Direction.DOWN);
        scene.world().showSection(basinSelection, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showControls(funnelFilter, Pointing.DOWN, 24)
            .rightClick()
            .withItem(filterLink);
        scene.idle(8);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, funnelPos, new AABB(funnelPos), 130);

        scene.overlay().showControls(basinFilter, Pointing.DOWN, 24)
            .rightClick()
            .withItem(filterLink);
        scene.idle(8);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, basinPos, new AABB(basinPos), 122);
        scene.overlay().showText(100)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text("Any block with filter capability is valid, and multiple target can be selected")
            .pointAt(util.vector().centerOf(basinPos).add(0, .5, 0))
            .placeNearTarget();
        scene.idle(110);

        scene.overlay().showControls(packagerFace, Pointing.RIGHT, 30)
            .rightClick()
            .withItem(filterLink);
        scene.idle(12);
        scene.world().showSection(linkSelection, Direction.SOUTH);
        scene.overlay().showLine(PonderPalette.GREEN, linkCenter, util.vector().centerOf(sawPos), 85);
        scene.overlay().showLine(PonderPalette.GREEN, linkCenter, util.vector().centerOf(funnelPos), 85);
        scene.overlay().showLine(PonderPalette.GREEN, linkCenter, util.vector().centerOf(basinPos), 85);
        scene.overlay().showText(90)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text("Then place the Filter Link on a Packager to configure it with the selected targets")
            .pointAt(linkCenter)
            .placeNearTarget();
        scene.idle(100);

        PonderHilo.packagerUnpack(scene, packagerPos, box);
        scene.overlay().showControls(util.vector().topOf(packagerPos).add(-.3, .2, 0), Pointing.DOWN, 45)
            .withItem(output);
        scene.overlay().showText(90)
            .attachKeyFrame()
            .text("Whenever a package carrying filter metadata is unpacked by the Packager...")
            .pointAt(util.vector().centerOf(packagerPos))
            .placeNearTarget();
        scene.idle(100);

        scene.world().setFilterData(sawSelection, SawBlockEntity.class, output);
        scene.effects().indicateSuccess(sawPos);
        scene.world().setFilterData(funnelSelection, FunnelBlockEntity.class, output);
        scene.effects().indicateSuccess(funnelPos);
        scene.world().setFilterData(basinSelection, BasinBlockEntity.class, output);
        scene.effects().indicateSuccess(basinPos);
        scene.overlay().showText(100)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text("The Filter Link automatically applies the filter to every configured target")
            .pointAt(util.vector().centerOf(sawPos).add(0, .7, 0))
            .placeNearTarget();

        scene.world().flapFunnel(beltFunnelPos, true);
        scene.world().createItemOnBelt(beltFunnelPos.below(), Direction.UP, input.copy());
        scene.idle(12);
        scene.world().flapFunnel(beltFunnelPos, true);
        scene.world().createItemOnBelt(beltFunnelPos.below(), Direction.UP, input.copy());
        scene.idle(48);
        scene.world().createItemOnBeltLike(sawPos.east(), Direction.WEST, output);
        scene.idle(65);

        scene.world().showSection(tickerSelection, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(tickerPos).add(0, .15, 0), Pointing.DOWN, 70)
            .withItem(box);
        scene.overlay().showText(120)
            .attachKeyFrame()
            .text("Packages requested by Factory Gauges or from a Stock Keeper recipe in JEI carry the expected output as filter metadata")
            .pointAt(util.vector().topOf(tickerPos))
            .placeNearTarget();
        scene.idle(120);
        scene.markAsFinished();
    }
}
