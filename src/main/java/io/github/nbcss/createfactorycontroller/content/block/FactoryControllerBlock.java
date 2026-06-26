package io.github.nbcss.createfactorycontroller.content.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.ServerConfig;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FactoryControllerBlock extends HorizontalDirectionalBlock
        implements IBE<FactoryControllerBlockEntity>, IWrenchable {

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
        //if (ServerConfig.preserveControllerData()) be.abortAllTasks();
        //else be.dropComponents();
        if (!movedByPiston && !state.is(newState.getBlock()))
            withBlockEntityDo(level, pos, FactoryControllerBlockEntity::abortAllTasks);
        IBE.onRemove(state, level, pos, newState);
    }

    /** Preserve mode: the controller drops a single item carrying the board setup instead of the gauges. */
    @Override
    public @NotNull List<ItemStack> getDrops(@NotNull BlockState state, LootParams.@NotNull Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof FactoryControllerBlockEntity be) {
            if (ServerConfig.preserveControllerData()) {
                ItemStack stack = new ItemStack(this);
                be.writeSetupToItem(stack);
                return List.of(stack);
            } else {
                List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
                for (VirtualComponentBehaviour b : be.components.values()) {
                    drops.add(new ItemStack(b.getItem()));
                }
                return drops;
            }
        }
        return super.getDrops(state, params);
    }

    /** Creative break skips loot drops, so spawn the setup-bearing item here so the board is still preserved. */
    @Override
    public @NotNull BlockState playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos,
                                                 @NotNull BlockState state, @NotNull Player player) {
        if (!level.isClientSide() && player.isCreative() && ServerConfig.preserveControllerData()
            && level.getBlockEntity(pos) instanceof FactoryControllerBlockEntity be && be.hasSetup()) {
            ItemStack stack = new ItemStack(this);
            be.writeSetupToItem(stack);
            Block.popResource(level, pos, stack);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Restore a preserved board when the controller item is placed. */
    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;
        CompoundTag setup = stack.get(CreateFactoryController.CONTROLLER_SETUP.get());
        if (setup != null) {
            withBlockEntityDo(level, pos, be -> {
                be.applySetup(setup, level.registryAccess());
                // Vanilla copies item data components into the placed block entity before setPlacedBy.
                // This component is only a placement payload; keeping it on the BE duplicates setup data on save/drop.
                be.setComponents(be.components().filter(type -> !type.equals(CreateFactoryController.CONTROLLER_SETUP.get())));
            });
        }
    }

    /** Plain wrench does nothing (no rotation). Overridden to a no-op so the IWrenchable default — which assumes a
     *  kinetic block entity — never runs on this non-kinetic controller. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.SUCCESS;
    }

    /** Sneak-wrench pickup: hand the player the (setup-bearing) drops in every game mode — Create's default skips
     *  the hand-off for creative players — then remove the block without a ground drop. */
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!(level instanceof ServerLevel serverLevel))
            return InteractionResult.SUCCESS;
        if (player != null) {
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled())
                return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FactoryControllerBlockEntity controller) {
            boolean hasSetup = ServerConfig.preserveControllerData() && controller.hasSetup();
            for (ItemStack drop : Block.getDrops(state, serverLevel, pos, be, player, context.getItemInHand())) {
                if (player == null)
                    Block.popResource(level, pos, drop);
                else if (!player.isCreative() || hasSetup)
                    player.getInventory().placeItemBackInInventory(drop);
            }
        }

        level.destroyBlock(pos, false);
        IWrenchable.playRemoveSound(level, pos);
        return InteractionResult.SUCCESS;
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
