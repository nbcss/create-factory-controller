package io.github.nbcss.createfactorycontroller.content;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.component.connection.RedstoneConnection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * For controller data migration
 */
public abstract class ControllerDataFixer {
    private static final List<ControllerDataFixer> FIXERS = new ArrayList<>();
    static {
        // Mod version 0.2.1 -> 1.0, Data version 0 -> 1
        FIXERS.add(new ControllerDataFixer(1) {
            @Override
            public CompoundTag fix(CompoundTag tag) {
                ListTag components = tag.getList("Components", Tag.TAG_COMPOUND);
                ListTag connections = new ListTag();
                for (int i = 0; i < components.size(); i++) {
                    CompoundTag comp = components.getCompound(i);
                    String oldType = comp.getString("Type");
                    // Only support gauge & redstone link in 0.2.1.
                    String connType = switch (oldType) {
                        case "createfactorycontroller:gauge" -> LogisticsConnection.TYPE.name();
                        case "createfactorycontroller:redstone_link" -> RedstoneConnection.TYPE.name();
                        default -> null;
                    };
                    if (connType != null) {
                        CompoundTag ownerPos = comp.getCompound("Pos");
                        ListTag targetedBy = comp.getList("TargetedBy", Tag.TAG_COMPOUND);
                        for (int j = 0; j < targetedBy.size(); j++) {
                            // keeps From, ArrowBendMode, payload
                            CompoundTag edge = targetedBy.getCompound(j).copy();
                            edge.putString("Type", connType);
                            if (connType.equals(RedstoneConnection.TYPE.name()) && comp.getBoolean("Receive")) {
                                CompoundTag gaugePos = edge.getCompound("From").copy();
                                edge.put("From", ownerPos.copy());
                                edge.put("To", gaugePos);
                            } else {
                                edge.put("To", ownerPos.copy());
                            }
                            connections.add(edge);
                        }
                    }
                    if (comp.contains("GaugeItem")) {
                        comp.putString("Item", comp.getString("GaugeItem"));
                        comp.remove("GaugeItem");
                    }
                    if (comp.contains("LinkItem")) {
                        comp.putString("Item", comp.getString("LinkItem"));
                        comp.remove("LinkItem");
                    }
                    // we only have gauge & redstone link before v0.2.1 (no fluid gauge)
                    comp.putString("Type", switch (oldType) {
                        case "createfactorycontroller:gauge" -> "GAUGE";
                        case "createfactorycontroller:redstone_link" -> "REDSTONE_LINK";
                        default -> oldType;
                    });
                    comp.remove("TargetedBy");
                    comp.remove("Targeting");
                }
                tag.put("Connections", connections);
                return tag;
            }
        });
    }

    private final int version;
    private ControllerDataFixer(int version) {
        this.version = version;
    }

    public abstract CompoundTag fix(CompoundTag tag);

    public static CompoundTag fixControllerBE(CompoundTag tag) {
        if (tag.isEmpty()) return tag;
        int ver = tag.getInt("Ver");
        if (ver >= FactoryControllerBlockEntity.DATA_VERSION)
            return tag;
        for (ControllerDataFixer fixer : FIXERS)
            if (fixer.version > ver)
                tag = fixer.fix(tag);
        tag.putInt("Ver", FactoryControllerBlockEntity.DATA_VERSION);
        return tag;
    }
}
