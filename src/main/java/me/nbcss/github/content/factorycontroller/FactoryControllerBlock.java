package me.nbcss.github.content.factorycontroller;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class FactoryControllerBlock extends HorizontalDirectionalBlock implements IBE<FactoryControllerBlockEntity> {

    public static final MapCodec<FactoryControllerBlock> CODEC = simpleCodec(FactoryControllerBlock::new);

    public FactoryControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            withBlockEntityDo(level, pos, be -> sp.openMenu(be, pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<FactoryControllerBlockEntity> getBlockEntityClass() {
        return FactoryControllerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FactoryControllerBlockEntity> getBlockEntityType() {
        return me.nbcss.github.CreateFactoryController.FACTORY_CONTROLLER_BE.get();
    }
}
