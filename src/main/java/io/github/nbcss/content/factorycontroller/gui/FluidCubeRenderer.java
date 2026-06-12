package io.github.nbcss.content.factorycontroller.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Renders a fluid as a 3D block (a 1×1×1 cube) in a GUI. catnip's {@code GuiGameElement.of(Fluid)} only draws a
 * flat liquid-block sprite and Create's {@code renderFluidStream} is a capless tube (no top face), so we emit the
 * six cube faces ourselves from the fluid's still sprite + tint. A no-cull translucent layer keeps it
 * winding-agnostic, and real per-face normals under {@link Lighting#setupFor3DItems()} give the standard
 * block-item shading (no class-level dependency on CreateFluidLogistic — this is pure NeoForge fluid rendering).
 */
@OnlyIn(Dist.CLIENT)
public final class FluidCubeRenderer {

    private FluidCubeRenderer() {}

    /** A cube face: its four corners (unit-cube coords) and outward normal (for diffuse shading). */
    private record CubeFace(float[][] corners, float nx, float ny, float nz) {}

    private static final CubeFace[] FACES = {
        new CubeFace(new float[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}, 0, 1, 0),    // UP
        new CubeFace(new float[][]{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}}, 0, -1, 0),   // DOWN
        new CubeFace(new float[][]{{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}}, 0, 0, -1),   // NORTH
        new CubeFace(new float[][]{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}}, 0, 0, 1),    // SOUTH
        new CubeFace(new float[][]{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}}, -1, 0, 0),   // WEST
        new CubeFace(new float[][]{{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}, 1, 0, 0),    // EAST
    };

    /**
     * Draws {@code fluid} as a {@code px}-pixel 3D cube centred on the slot at {@code (x, y)} (top-left of a 16px
     * slot region), in an isometric block-item orientation.
     */
    public static void render(GuiGraphics gfx, FluidStack fluid, int x, int y, float px) {
        if (fluid.isEmpty()) return;
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ext.getStillTexture(fluid));
        int tint = ext.getTintColor(fluid);
        float r = (tint >> 16 & 0xFF) / 255f, g = (tint >> 8 & 0xFF) / 255f, b = (tint & 0xFF) / 255f;
        float a = (tint >>> 24) == 0 ? 1f : (tint >>> 24) / 255f;
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        float[] uv = {u0, v0, u0, v1, u1, v1, u1, v0};               // one sprite tile across each face

        PoseStack ps = gfx.pose();
        ps.pushPose();
        ps.translate(x + px / 2f, y + px / 2f, 250);                 // centre the cube on the slot
        ps.scale(px, -px, px);                                       // GUI px → model units (flip Y for GUIs)
        ps.mulPose(Axis.XP.rotationDegrees(30));                     // vanilla block-in-inventory GUI orientation
        ps.mulPose(Axis.YP.rotationDegrees(225));                    // (matches how the gauge block beside it is lit)
        ps.translate(-0.5f, -0.5f, -0.5f);                          // origin → cube centre
        PoseStack.Pose pose = ps.last();

        Lighting.setupFor3DItems();                                  // directional shading, like a 3D block item
        MultiBufferSource.BufferSource buffer = gfx.bufferSource();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        for (CubeFace face : FACES)
            for (int i = 0; i < 4; i++) {
                float[] c = face.corners[i];
                // Pose-aware calls so BOTH the position and the normal are transformed — passing a raw normal
                // leaves it in model space and the diffuse lighting only catches whichever face happens to align.
                vc.addVertex(pose, c[0], c[1], c[2]).setColor(r, g, b, a).setUv(uv[i * 2], uv[i * 2 + 1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(pose, face.nx, face.ny, face.nz);
            }
        buffer.endBatch();
        ps.popPose();
        Lighting.setupForFlatItems();                                // restore the GUI's flat lighting
    }
}
