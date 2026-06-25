package io.github.nbcss.createfactorycontroller.content;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public abstract class ControllerDataFixer {
    private static final List<ControllerDataFixer> FIXERS = new ArrayList<>();
    static {
        // Mod version 0.2.1 -> 1.0, Data version 0 -> 1
        // Connections moved from each component's per-component "TargetedBy" list onto a single central
        // "Connections" edge list at the controller root. In the old layout a wire was stored on its OWNER (the
        // consumer for an ingredient wire; the link for a gauge↔link wire) keyed by its source, so each old entry
        // becomes a central edge with To = that owner's position and a type derived from the owner's kind; From + the
        // arrow-bend + payload fields are carried over unchanged. (Component "Type" ids are matched as historical
        // literals so the fix is stable even if those constants are later renamed.)
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
                        case "createfactorycontroller:gauge" -> Connection.Type.LOGISTICS.name();
                        case "createfactorycontroller:redstone_link" -> Connection.Type.REDSTONE.name();
                        default -> null;
                    };
                    if (connType != null) {
                        CompoundTag ownerPos = comp.getCompound("Pos");
                        ListTag targetedBy = comp.getList("TargetedBy", Tag.TAG_COMPOUND);
                        for (int j = 0; j < targetedBy.size(); j++) {
                            // keeps From, ArrowBendMode, payload
                            CompoundTag edge = targetedBy.getCompound(j).copy();
                            edge.putString("Type", connType);
                            edge.put("To", ownerPos.copy());
                            connections.add(edge);
                        }
                    }
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
        if (tag.isEmpty())
            return tag;
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
