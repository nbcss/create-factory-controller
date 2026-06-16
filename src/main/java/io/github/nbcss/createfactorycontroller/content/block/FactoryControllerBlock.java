package io.github.nbcss.createfactorycontroller.content.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FactoryControllerBlock extends HorizontalDirectionalBlock
        implements IBE<FactoryControllerBlockEntity> {

    public static final MapCodec<FactoryControllerBlock> CODEC = simpleCodec(FactoryControllerBlock::new);

    /** Render-only flag: true while the block receives a redstone signal, selecting the powered model. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

    public FactoryControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(POWERED, false));
    }

    @Override
    protected @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state,
                                        @NotNull BlockGetter level,
                                        @NotNull BlockPos pos,
                                        @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(POWERED, powered);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state,
                                                        Level level,
                                                        @NotNull BlockPos pos,
                                                        @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            withBlockEntityDo(level, pos, be ->
                sp.openMenu(be, buf -> FactoryControllerMenu.writeExtraData(be, buf)));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(@NotNull BlockState state, Level level, @NotNull BlockPos pos,
                                   @NotNull Block neighborBlock, @NotNull BlockPos neighborPos,
                                   boolean movedByPiston) {
        if (level.isClientSide()) return;
        // Keep the render-only POWERED state in sync (vanilla syncs the blockstate to clients and picks
        // the matching model); the BE handles the request-pausing logic separately.
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED))
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        withBlockEntityDo(level, pos, FactoryControllerBlockEntity::onNeighborChanged);
    }

    @Override
    protected void onRemove(@NotNull BlockState state,
                            @NotNull Level level,
                            @NotNull BlockPos pos,
                            @NotNull BlockState newState,
                            boolean movedByPiston) {
        if (!movedByPiston && !state.is(newState.getBlock()))
            withBlockEntityDo(level, pos, FactoryControllerBlockEntity::dropComponents);
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<FactoryControllerBlockEntity> getBlockEntityClass() {
        return FactoryControllerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FactoryControllerBlockEntity> getBlockEntityType() {
        return CreateFactoryController.FACTORY_CONTROLLER_BE.get();
    }
}
