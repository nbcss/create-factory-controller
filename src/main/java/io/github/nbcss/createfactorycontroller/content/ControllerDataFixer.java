package io.github.nbcss.createfactorycontroller.content;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public abstract class ControllerDataFixer {
    public static final int LATEST_VERSION = 1;
    private static final List<ControllerDataFixer> FIXERS = new ArrayList<>();
    static {
        // 0 -> 1: connections moved from each component's per-component "TargetedBy" list onto a single central
        // "Connections" edge list at the controller root. In the old layout a wire was stored on its OWNER (the
        // consumer for an ingredient wire; the link for a gauge↔link wire) keyed by its source, so each old entry
        // becomes a central edge with To = that owner's position and a type derived from the owner's kind; From + the
        // arrow-bend + payload fields are carried over unchanged. (Component "Type" ids are matched as historical
        // literals so the fix is stable even if those constants are later renamed.)
        FIXERS.add(new ControllerDataFixer(1) {
            @Override
            public CompoundTag fix(CompoundTag tag) {
                System.out.println("Fix: ");
                System.out.println(tag);
                ListTag components = tag.getList("Components", Tag.TAG_COMPOUND);
                ListTag connections = new ListTag();
                for (int i = 0; i < components.size(); i++) {
                    CompoundTag comp = components.getCompound(i);
                    String connType = connectionTypeOf(comp.getString("Type"));
                    if (connType != null) {
                        CompoundTag ownerPos = comp.getCompound("Pos");
                        ListTag targetedBy = comp.getList("TargetedBy", Tag.TAG_COMPOUND);
                        for (int j = 0; j < targetedBy.size(); j++) {
                            CompoundTag edge = targetedBy.getCompound(j).copy();   // keeps From, ArrowBendMode, payload
                            edge.putString("Type", connType);
                            edge.put("To", ownerPos.copy());
                            connections.add(edge);
                        }
                    }
                    comp.remove("TargetedBy");
                    comp.remove("Targeting");
                }
                tag.put("Connections", connections);
                return tag;
            }
        });
    }

    /** The {@code Connection.Type} name for a component kind ({@code Component#getTypeId}), or null if it owns no wires. */
    @Nullable
    private static String connectionTypeOf(String componentTypeId) {
        return switch (componentTypeId) {
            case "createfactorycontroller:gauge" -> "logistics";
            case "createfactorycontroller:redstone_link" -> "redstone";
            default -> null;
        };
    }
    private final int version;
    private ControllerDataFixer(int version) {
        this.version = version;
    }

    public abstract CompoundTag fix(CompoundTag tag);

    public static CompoundTag fixControllerBE(CompoundTag tag) {
        int ver = tag.getInt("Ver");
        System.out.println(ver);
        System.out.println(tag);
        if (ver >= LATEST_VERSION)
            return tag;
        for (ControllerDataFixer fixer : FIXERS) {
            if (fixer.version > ver) {
                tag = fixer.fix(tag);
            }
        }
        tag.putInt("Ver", LATEST_VERSION);
        return tag;
    }
}
