package io.github.nbcss.createfactorycontroller.content.blueprint;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.content.schematics.client.SchematicAndQuillHandler;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.GaugeWorkMode;
import io.github.nbcss.createfactorycontroller.content.ThresholdUnit;
import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.compat.RepackagedCompat;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualRedstoneLinkBehaviour;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionGraph;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.component.connection.RedstoneConnection;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side reader that turns the gauge blocks into blueprint.
 */
public final class SchematicImport {
    private SchematicImport() {}

    private static final int BOARD_SPAN = 2 * FactoryControllerBlockEntity.BOARD_LIMIT + 1;
    private static final long MAX_SCAN_VOLUME = 262_144;
    private static final String REPACKAGED_FLUID = "repackaged:fluid";

    /** A finished import: a {@link ComponentHolder} over the mapped components, ready for {@code BlueprintStorage}. */
    public static final class ImportedBoard implements ComponentHolder {
        private final Map<VirtualComponentPosition, VirtualComponentBehaviour> components;
        public final List<VirtualComponentPosition> positions;
        public final List<BlueprintStorage.Material> materials;
        public final List<UUID> networks;
        /** The source selection box — carried to the paste's place packet for server-side network authorization. */
        public final BlockPos boxMin, boxMax;

        private ImportedBoard(Map<VirtualComponentPosition, VirtualComponentBehaviour> components,
                              BlockPos boxMin, BlockPos boxMax) {
            this.components = components;
            this.positions = List.copyOf(components.keySet());
            this.materials = BlueprintStorage.materials(this, positions);
            this.networks = new ArrayList<>(BlueprintStorage.networks(this, positions));
            this.boxMin = boxMin;
            this.boxMax = boxMax;
        }

        @Override
        public VirtualComponentBehaviour componentAt(VirtualComponentPosition position) {
            return components.get(position);
        }

        public int size() { return components.size(); }
    }

    /** Per-requirement state, in the order shown in the import button's checklist tooltip. */
    public record Requirements(boolean twoPositions, boolean planar, boolean withinSize,
                               boolean hasComponent, boolean uniformFacing) {
        static final Requirements NONE = new Requirements(false, false, false, false, false);

        public boolean allMet() {
            return twoPositions && planar && withinSize && hasComponent && uniformFacing;
        }
    }

    /** Either a ready board or, when not, which requirements are unmet — drives the button and its tooltip. */
    public record Scan(@Nullable ImportedBoard board, Requirements requirements) {
        public boolean ready() { return board != null; }

        private static Scan notReady(Requirements requirements) { return new Scan(null, requirements); }
    }

    public static Scan scan(Level level) {
        SchematicAndQuillHandler handler = CreateClient.SCHEMATIC_AND_QUILL_HANDLER;
        BlockPos corner1 = handler.firstPos, corner2 = handler.secondPos;
        if (corner1 == null || corner2 == null) return Scan.notReady(Requirements.NONE);

        BoundingBox box = BoundingBox.fromCorners(corner1, corner2);
        boolean planar = box.getXSpan() == 1 || box.getYSpan() == 1 || box.getZSpan() == 1;
        long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
        boolean withinSize = planar && volume <= MAX_SCAN_VOLUME && fitsBoard(box);

        // Components can only be read once the selection is planar, bounded, and fully loaded.
        if (!withinSize || !allLoaded(level, box))
            return Scan.notReady(new Requirements(true, planar, withinSize, false, false));

        // Pass 1 — a gauge that faces a thin (span-1) axis is a candidate for the board plane, and every candidate
        // must share one FACE+FACING. Otherwise the selection mixes boards — e.g. when both X and Y are thin, wall
        // gauges facing X and floor gauges facing Y are all candidates and must not be silently split. A gauge
        // facing a non-thin axis (a wall merely crossing a flat slab) can't lie in the plane, so it is ignored.
        EnumSet<Axis> thin = thinAxes(box);
        BlockState gaugeRef = null;
        boolean facingConflict = false;
        boolean anyComponent = false;
        for (BlockPos pos : iterate(box)) {
            BlockState state = level.getBlockState(pos);
            BlockEntity be = level.getBlockEntity(pos);
            if (isGauge(state, be)) {
                if (!thin.contains(gaugeAxis(state))) continue;   // faces a non-thin axis → off-plane, ignore
                if (((FactoryPanelBlockEntity) be).activePanels() == 0) continue;
                if (gaugeRef == null) gaugeRef = state;
                else if (!sameFacing(gaugeRef, state)) facingConflict = true;
                anyComponent = true;
            } else if (isLink(state)) {
                anyComponent = true;
            }
        }
        if (!anyComponent || facingConflict)
            return Scan.notReady(new Requirements(true, true, true, anyComponent, !facingConflict));

        Axis normalAxis = gaugeRef != null ? gaugeAxis(gaugeRef) : thin.iterator().next();
        Basis basis = gaugeRef != null ? Basis.forGauge(gaugeRef) : Basis.forPlane(normalAxis);

        // build components and assign board cells.
        Map<VirtualComponentPosition, VirtualComponentBehaviour> components = new LinkedHashMap<>();
        Set<VirtualComponentPosition> occupied = new HashSet<>();
        Map<FactoryPanelPosition, VirtualComponentPosition> gaugeCell = new HashMap<>();
        Map<BlockPos, VirtualRedstoneLinkBehaviour> linkAt = new HashMap<>();
        Map<BlockPos, VirtualComponentPosition> linkCell = new HashMap<>();
        List<FactoryPanelBehaviour> panels = new ArrayList<>();

        for (BlockPos pos : iterate(box)) {
            BlockState state = level.getBlockState(pos);
            BlockEntity be = level.getBlockEntity(pos);
            if (isGauge(state, be)) {
                if (!facesPlane(state, normalAxis)) continue;   // off-plane gauge (e.g. on a wall) → ignore
                FactoryPanelBlockEntity fp = (FactoryPanelBlockEntity) be;
                Item item = state.getBlock().asItem();
                // Deployer/Repackaged compat
                CompoundTag beTag = fp.saveWithoutMetadata(level.registryAccess());
                CompoundTag customPanels = beTag.getCompound("CustomPanels");
                for (FactoryPanelBehaviour panel : fp.panels.values()) {
                    if (!panel.active) continue;
                    VirtualComponentPosition cell = basis.cell(pos, 1 - panel.slot.xOffset, 1 - panel.slot.yOffset);
                    if (!occupied.add(cell)) continue;
                    VirtualGaugeBehaviour gauge = buildGauge(cell, item, panel,
                            customPanels.getString(slotKey(panel)), beTag);
                    if (gauge == null) { occupied.remove(cell); continue; }   // unregistered/unsupported → ignore
                    components.put(cell, gauge);
                    gaugeCell.put(panel.getPanelPosition(), cell);
                    panels.add(panel);
                }
            } else if (isLink(state) && be instanceof RedstoneLinkBlockEntity rl) {
                Item item = state.getBlock().asItem();
                VirtualComponentPosition cell = basis.cell(pos, 0, 0);   // links land top-left of their block
                if (!occupied.add(cell)) continue;
                if (!(ComponentRegistry.createFromItem(null, cell, item, null)
                        instanceof VirtualRedstoneLinkBehaviour link)) {
                    occupied.remove(cell);
                    continue;
                }
                applyLink(link, rl, state);
                components.put(cell, link);
                linkAt.put(pos.immutable(), link);
                linkCell.put(pos.immutable(), cell);
            }
        }
        // A registered gauge can still be unsupported (buildGauge → null), so re-check something was actually built.
        if (components.isEmpty())
            return Scan.notReady(new Requirements(true, true, true, false, true));
        if (components.size() > FactoryControllerBlockEntity.maxComponents())
            return Scan.notReady(new Requirements(true, true, false, true, true));

        // Wire connections into a transient graph, dropping any whose endpoint wasn't imported.
        ConnectionGraph graph = new ConnectionGraph();
        ImportedBoard board = new ImportedBoard(components,
                new BlockPos(box.minX(), box.minY(), box.minZ()),
                new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
        for (VirtualComponentBehaviour component : components.values()) {
            component.setGraph(graph);
            component.setHolder(board);
        }
        for (FactoryPanelBehaviour panel : panels) {
            VirtualComponentPosition to = gaugeCell.get(panel.getPanelPosition());
            panel.targetedBy.forEach((source, connection) -> {
                VirtualComponentPosition from = gaugeCell.get(source);
                if (from == null || from.equals(to)) return;
                LogisticsConnection wire = new LogisticsConnection(from, to, connection.amount);
                wire.arrowBendMode = connection.arrowBendMode;
                graph.add(wire);
            });
            panel.targetedByLinks.forEach((linkPos, connection) -> {
                VirtualRedstoneLinkBehaviour link = linkAt.get(linkPos);
                VirtualComponentPosition cell = linkCell.get(linkPos);
                if (link == null || cell == null) return;
                // RECEIVE link drives the gauge (link → gauge); SEND link is driven by it (gauge → link).
                RedstoneConnection wire = link.receive
                        ? new RedstoneConnection(cell, to) : new RedstoneConnection(to, cell);
                wire.arrowBendMode = connection.arrowBendMode;
                graph.add(wire);
            });
        }
        return new Scan(board, new Requirements(true, true, true, true, true));
    }

    /** Server-side authorization for a Schematic paste */
    public static Set<UUID> selectionNetworks(Level level, BlockPos min, BlockPos max) {
        Set<UUID> networks = new HashSet<>();
        BoundingBox box = BoundingBox.fromCorners(min, max);
        if ((long) box.getXSpan() * box.getYSpan() * box.getZSpan() > MAX_SCAN_VOLUME) return networks;
        for (BlockPos pos : iterate(box)) {
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof FactoryPanelBlockEntity fp)
                for (FactoryPanelBehaviour panel : fp.panels.values())
                    if (panel.active) networks.add(panel.network);
        }
        return networks;
    }

    private static void applyGauge(VirtualGaugeBehaviour gauge, FactoryPanelBehaviour panel) {
        gauge.filter = panel.getFilter().copy();
        gauge.unit = FluidCompat.isFluidFilter(gauge.filter)
                ? (panel.upTo ? ThresholdUnit.FLUID_MB : ThresholdUnit.FLUID_BUCKET)
                : panel.upTo ? ThresholdUnit.ITEMS : ThresholdUnit.STACKS;
        gauge.count = importedCount(panel.count, gauge);
        gauge.recipeAddress = panel.recipeAddress == null ? "" : panel.recipeAddress;
        gauge.recipeOutput = Math.max(1, panel.recipeOutput);
        gauge.promiseClearingInterval = panel.promiseClearingInterval;
        List<ItemStack> arrangement = new ArrayList<>(panel.activeCraftingArrangement.size());
        for (ItemStack stack : panel.activeCraftingArrangement) arrangement.add(stack.copy());
        gauge.activeCraftingArrangement = arrangement;
        gauge.mode = arrangement.isEmpty() ? GaugeWorkMode.REGULAR : GaugeWorkMode.CRAFTING;
    }

    private static int importedCount(int rawCount, VirtualGaugeBehaviour gauge) {
        return FluidCompat.isFluidFilter(gauge.filter)
                ? rawCount / gauge.unit.toCountMultiplier(gauge.filter)
                : rawCount;
    }

    @Nullable
    private static VirtualGaugeBehaviour buildGauge(VirtualComponentPosition cell, Item blockItem,
                                                    FactoryPanelBehaviour panel, String stockType, CompoundTag beTag) {
        if (REPACKAGED_FLUID.equals(stockType)) {
            if (!RepackagedCompat.isLoaded() || !ComponentRegistry.contains(RepackagedCompat.FLUID_GAUGE)) return null;
            if (!(ComponentRegistry.createFromItem(null, cell,
                    BuiltInRegistries.ITEM.get(RepackagedCompat.FLUID_GAUGE), panel.network)
                    instanceof VirtualGaugeBehaviour gauge)) return null;
            applyFluidGauge(gauge, panel, beTag.getCompound(slotKey(panel)));
            return gauge;
        }
        if (!stockType.isEmpty()) return null;   // an unknown generic stock type
        if (!(ComponentRegistry.createFromItem(null, cell, blockItem, panel.network)
                instanceof VirtualGaugeBehaviour gauge)) return null;
        applyGauge(gauge, panel);
        return gauge;
    }

    /** Applies a Repackaged fluid gauge */
    private static void applyFluidGauge(VirtualGaugeBehaviour gauge, FactoryPanelBehaviour panel, CompoundTag panelTag) {
        gauge.filter = FluidCompat.makeFluidGaugeFilter(readFluid(panelTag.getCompound("Stack")));
        int scale = panelTag.getInt("Scale");
        gauge.unit = scale >= 1 ? ThresholdUnit.FLUID_BUCKET : ThresholdUnit.FLUID_MB;
        long targetMb = (long) panel.count * pow1000(scale);
        gauge.count = (int) Math.min(Integer.MAX_VALUE, targetMb / gauge.unit.toCountMultiplier(gauge.filter));
        gauge.recipeAddress = panel.recipeAddress == null ? "" : panel.recipeAddress;
        long outputMb = (long) Math.max(1, panel.recipeOutput) * pow1000(scale);
        gauge.recipeOutput = (int) Math.min(VirtualGaugeBehaviour.FLUID_OUTPUT_CAP_MB, outputMb);
        gauge.promiseClearingInterval = panel.promiseClearingInterval;
        gauge.mode = GaugeWorkMode.REGULAR;
    }

    private static long pow1000(int scale) {
        long value = 1;
        for (int i = 0; i < scale && i < 4; i++) value *= 1000;
        return value;
    }

    private static FluidStack readFluid(CompoundTag stackTag) {
        ResourceLocation id = ResourceLocation.tryParse(stackTag.getString("id"));
        if (id == null) return FluidStack.EMPTY;
        Fluid fluid = BuiltInRegistries.FLUID.get(id);
        if (fluid == Fluids.EMPTY) return FluidStack.EMPTY;
        return new FluidStack(BuiltInRegistries.FLUID.wrapAsHolder(fluid), Math.max(1, stackTag.getInt("amount")));
    }

    private static String slotKey(FactoryPanelBehaviour panel) {
        return CreateLang.asId(panel.slot.name());
    }

    private static void applyLink(VirtualRedstoneLinkBehaviour link, RedstoneLinkBlockEntity be, BlockState state) {
        link.receive = state.getValue(RedstoneLinkBlock.RECEIVER);
        LinkBehaviour behaviour = be.getBehaviour(LinkBehaviour.TYPE);
        if (behaviour == null) return;
        Couple<Frequency> key = behaviour.getNetworkKey();
        link.redFreq = key.getFirst().getStack().copy();
        link.blueFreq = key.getSecond().getStack().copy();
    }

    /** Whether the selection's largest span maps within the board — 2 cells/block, ≤ {@link #BOARD_SPAN} cells.
     *  Since the components sit inside the box, this bounds their board footprint without scanning them. */
    private static boolean fitsBoard(BoundingBox box) {
        int largest = Math.max(box.getXSpan(), Math.max(box.getYSpan(), box.getZSpan()));
        return largest * 2 <= BOARD_SPAN;
    }

    private static boolean allLoaded(Level level, BoundingBox box) {
        for (BlockPos pos : iterate(box))
            if (!level.isLoaded(pos)) return false;
        return true;
    }

    private static boolean isGauge(BlockState state, BlockEntity be) {
        return be instanceof FactoryPanelBlockEntity
                && ComponentRegistry.contains(BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()));
    }

    private static boolean isLink(BlockState state) { return AllBlocks.REDSTONE_LINK.has(state); }

    private static boolean sameFacing(BlockState a, BlockState b) {
        return a.getOptionalValue(FactoryPanelBlock.FACING).orElse(Direction.SOUTH)
                == b.getOptionalValue(FactoryPanelBlock.FACING).orElse(Direction.SOUTH)
                && a.getOptionalValue(FactoryPanelBlock.FACE).orElse(AttachFace.FLOOR)
                == b.getOptionalValue(FactoryPanelBlock.FACE).orElse(AttachFace.FLOOR);
    }

    /** The axes the selection is thin (span 1) along — the candidate plane normals. */
    private static EnumSet<Axis> thinAxes(BoundingBox box) {
        EnumSet<Axis> thin = EnumSet.noneOf(Axis.class);
        if (box.getXSpan() == 1) thin.add(Axis.X);
        if (box.getYSpan() == 1) thin.add(Axis.Y);
        if (box.getZSpan() == 1) thin.add(Axis.Z);
        return thin;
    }

    /** The axis a gauge's panels face along (perpendicular to the panel plane). */
    private static Axis gaugeAxis(BlockState state) {
        return FactoryPanelBlock.connectedDirection(state).getAxis();
    }

    /** A gauge belongs to the board only if its panels lie in the plane — i.e. it faces along the plane normal. */
    private static boolean facesPlane(BlockState state, Axis normalAxis) {
        return gaugeAxis(state) == normalAxis;
    }

    private static Iterable<BlockPos> iterate(BoundingBox box) {
        return BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    /**
     * World to board projection
     */
    private record Basis(Vec3 right, Vec3 down) {
        static Basis forGauge(BlockState state) {
            float xRotDeg = (float) Math.toDegrees(FactoryPanelBlock.getXRot(state)) + 90f;
            float yRotDeg = (float) Math.toDegrees(FactoryPanelBlock.getYRot(state));
            return new Basis(rotate(new Vec3(-1, 0, 0), xRotDeg, yRotDeg),
                    rotate(new Vec3(0, 0, -1), xRotDeg, yRotDeg));
        }

        /** Links-only fallback: no gauge facing to read, so pick canonical in-plane axes from the plane normal. */
        static Basis forPlane(Axis normal) {
            return switch (normal) {
                case Y -> new Basis(new Vec3(1, 0, 0), new Vec3(0, 0, 1));
                case Z -> new Basis(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                case X -> new Basis(new Vec3(0, 0, 1), new Vec3(0, -1, 0));
            };
        }

        VirtualComponentPosition cell(BlockPos block, int subCol, int subRow) {
            Vec3 corner = Vec3.atLowerCornerOf(block);
            int col = (int) Math.round(corner.dot(right));
            int row = (int) Math.round(corner.dot(down));
            return new VirtualComponentPosition(2 * col + subCol, 2 * row + subRow);
        }

        private static Vec3 rotate(Vec3 v, float xRotDeg, float yRotDeg) {
            v = VecHelper.rotate(v, 180, Axis.Y);
            v = VecHelper.rotate(v, xRotDeg, Axis.X);
            v = VecHelper.rotate(v, yRotDeg, Axis.Y);
            return v;
        }
    }
}
