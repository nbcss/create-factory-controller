package io.github.nbcss.logisticscontrol.content.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.render.RenderTypes;
import io.github.nbcss.logisticscontrol.CreateLogisticsControl;
import io.github.nbcss.logisticscontrol.content.block.FilterLinkBlock;
import io.github.nbcss.logisticscontrol.content.block.FilterLinkBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

public class FilterLinkRenderer extends SafeBlockEntityRenderer<FilterLinkBlockEntity> {
    private static final PartialModel BULB = PartialModel.of(ResourceLocation.fromNamespaceAndPath(
        CreateLogisticsControl.MODID, "block/filter_link/bulb"));
    private static final PartialModel GLOW = PartialModel.of(ResourceLocation.fromNamespaceAndPath(
        CreateLogisticsControl.MODID, "block/filter_link/glow"));

    public FilterLinkRenderer(BlockEntityRendererProvider.Context context) {}

    public static void registerPartialModels() {}

    @Override
    protected void renderSafe(FilterLinkBlockEntity blockEntity, float partialTicks, PoseStack poseStack,
                              MultiBufferSource bufferSource, int light, int overlay) {
        float glow = blockEntity.getGlow(partialTicks);
        if (glow < .125f) return;

        glow = Mth.clamp((float) (1 - 2 * Math.pow(glow - .75f, 2)), -1, 1);
        int intensity = (int) (200 * glow);
        BlockState state = blockEntity.getBlockState();

        float yRotation = switch (state.getValue(FilterLinkBlock.FACING)) {
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            case EAST -> 270;
            default -> 0;
        };
        var face = state.getValue(FilterLinkBlock.FACE);
        if (face == AttachFace.WALL)
            yRotation = (yRotation + 180) % 360;
        float xRotation = switch (face) {
            case FLOOR -> 0;
            case WALL -> 90;
            case CEILING -> 180;
        };

        poseStack.pushPose();
        PoseTransformStack transform = TransformStack.of(poseStack);
        transform.center();
        transform.rotateYDegrees(-yRotation);
        transform.rotateXDegrees(-xRotation);
        transform.uncenter();

        CachedBuffers.partial(BULB, state)
            .light(0xF000F0)
            .renderInto(poseStack, bufferSource.getBuffer(RenderType.translucent()));
        CachedBuffers.partial(GLOW, state)
            .light(0xF000F0)
            .color(intensity, intensity, intensity, 255)
            .disableDiffuse()
            .renderInto(poseStack, bufferSource.getBuffer(RenderTypes.additive()));
        poseStack.popPose();
    }
}
