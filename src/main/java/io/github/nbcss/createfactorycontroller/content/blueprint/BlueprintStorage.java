package io.github.nbcss.createfactorycontroller.content.blueprint;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Client-local persistence for reusable Factory Controller component blueprints. */
public final class BlueprintStorage {
    public static final int FORMAT_VERSION = 1;
    public static final String EXTENSION = ".nbt";
    public static final int MAX_NAME_LENGTH = 50;
    public static final int MAX_NOTE_LENGTH = 500;

    private BlueprintStorage() {}

    public record Material(ResourceLocation item, int count) {}

    /** One component of a stored blueprint, positioned in blueprint-local coordinates. */
    public record Placement(ResourceLocation item, VirtualComponentPosition pos) {}

    /** Everything the blueprint screens display about a stored blueprint. */
    public record Info(String note, List<Material> materials, int networkCount, int width, int height,
                       List<Placement> placements) {
        public static final Info EMPTY = new Info("", List.of(), 0, 0, 0, List.of());
    }

    /** Aggregates the component items in stable first-appearance order for the save-screen preview. */
    public static List<Material> materials(FactoryControllerMenu menu,
                                           Iterable<VirtualComponentPosition> positions) {
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        for (VirtualComponentPosition pos : positions) {
            VirtualComponentBehaviour component = menu.componentAt(pos);
            if (component != null) counts.merge(component.getItemId(), 1, Integer::sum);
        }
        return counts.entrySet().stream().map(e -> new Material(e.getKey(), e.getValue())).toList();
    }

    /** Reads everything the blueprint screens need: note, materials, placeholder count, size and layout. */
    public static Info read(Path blueprint) throws IOException {
        CompoundTag root = NbtIo.readCompressed(blueprint, NbtAccounter.unlimitedHeap());
        ListTag components = root.getList("Components", Tag.TAG_COMPOUND);
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        Set<Integer> networks = new LinkedHashSet<>();
        List<Placement> placements = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            CompoundTag component = components.getCompound(i);
            if (component.contains("Network", Tag.TAG_INT)) networks.add(component.getInt("Network"));
            if (!component.contains("Item", Tag.TAG_STRING)) continue;
            ResourceLocation item = ResourceLocation.tryParse(component.getString("Item"));
            if (item == null) continue;
            counts.merge(item, 1, Integer::sum);
            placements.add(new Placement(item, VirtualComponentPosition.fromNBT(component.getCompound("Pos"))));
        }
        List<Material> materials = counts.entrySet().stream()
                .map(e -> new Material(e.getKey(), e.getValue())).toList();
        return new Info(root.getString("Note"), materials, networks.size(),
                root.getInt("Width"), root.getInt("Height"), List.copyOf(placements));
    }

    /** The stored file verbatim — already gzipped NBT, shipped to the server to place the blueprint. */
    public static byte[] payload(Path blueprint) throws IOException {
        return Files.readAllBytes(blueprint);
    }

    /** Returns distinct logistics networks in stable component-selection order. */
    public static List<UUID> networks(FactoryControllerMenu menu,
                                      Iterable<VirtualComponentPosition> positions) {
        Set<UUID> result = new LinkedHashSet<>();
        for (VirtualComponentPosition pos : positions) {
            if (menu.componentAt(pos) instanceof VirtualGaugeBehaviour gauge)
                result.add(gauge.networkId);
        }
        return new ArrayList<>(result);
    }

    /**
     * Writes one compressed, versioned Minecraft NBT blueprint.
     */
    public static Path save(FactoryControllerMenu menu,
                            Iterable<VirtualComponentPosition> selectedPositions,
                            List<UUID> orderedNetworks,
                            String name,
                            String note,
                            HolderLookup.Provider registries) throws IOException {
        List<VirtualComponentPosition> positions = new ArrayList<>();
        for (VirtualComponentPosition pos : selectedPositions)
            if (menu.componentAt(pos) != null) positions.add(pos);
        if (positions.isEmpty()) throw new IOException("No components selected");

        if (!isValidBlueprintName(name)) throw new IOException("Invalid blueprint file name");

        return writeThroughTemporary(build(menu, positions, orderedNetworks, note, registries),
                blueprintPath(name));
    }

    /**
     * Rewrites a stored blueprint's note, renaming its file when the name changed. Everything else
     * the blueprint holds - components, connections and network placeholders - is carried over as-is.
     */
    public static Path edit(Path source, String name, String note) throws IOException {
        if (!isValidBlueprintName(name)) throw new IOException("Invalid blueprint file name");
        CompoundTag root = NbtIo.readCompressed(source, NbtAccounter.unlimitedHeap());
        root.putString("Note", truncateNote(note));

        Path target = blueprintPath(name);
        boolean renamed = !sameBlueprintFile(source, target);
        writeThroughTemporary(root, target);
        if (renamed) Files.deleteIfExists(source);
        return target;
    }

    /** Writes to a sibling temporary file first so a failed write cannot truncate the target. */
    private static Path writeThroughTemporary(CompoundTag root, Path target) throws IOException {
        Path directory = blueprintDirectory();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".saving-", EXTENSION + ".tmp");
        boolean moved = false;
        try {
            NbtIo.writeCompressed(root, temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            return target;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    /** True when both paths lead to one file, including names differing only in case on Windows. */
    public static boolean sameBlueprintFile(Path a, Path b) {
        if (a.equals(b)) return true;
        try {
            return Files.isRegularFile(a) && Files.isRegularFile(b) && Files.isSameFile(a, b);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String truncateNote(String note) {
        return note.length() > MAX_NOTE_LENGTH ? note.substring(0, MAX_NOTE_LENGTH) : note;
    }

    static CompoundTag build(FactoryControllerMenu menu,
                             List<VirtualComponentPosition> positions,
                             List<UUID> orderedNetworks,
                             String note,
                             HolderLookup.Provider registries) {
        int minX = positions.stream().mapToInt(VirtualComponentPosition::x).min().orElse(0);
        int minY = positions.stream().mapToInt(VirtualComponentPosition::y).min().orElse(0);
        int maxX = positions.stream().mapToInt(VirtualComponentPosition::x).max().orElse(minX);
        int maxY = positions.stream().mapToInt(VirtualComponentPosition::y).max().orElse(minY);
        Set<VirtualComponentPosition> selected = new LinkedHashSet<>(positions);

        Set<UUID> usedNetworks = new LinkedHashSet<>();
        for (VirtualComponentPosition pos : positions)
            if (menu.componentAt(pos) instanceof VirtualGaugeBehaviour gauge) usedNetworks.add(gauge.networkId);
        Map<UUID, Integer> placeholders = new LinkedHashMap<>();
        for (UUID network : orderedNetworks)
            if (usedNetworks.contains(network) && !placeholders.containsKey(network))
                placeholders.put(network, placeholders.size() + 1);
        for (UUID network : usedNetworks)
            if (!placeholders.containsKey(network)) placeholders.put(network, placeholders.size() + 1);

        CompoundTag root = new CompoundTag();
        root.putString("Format", CreateFactoryController.MODID + ":blueprint");
        root.putInt("Version", FORMAT_VERSION);
        root.putString("Note", truncateNote(note));
        root.putInt("Width", maxX - minX + 1);
        root.putInt("Height", maxY - minY + 1);

        ListTag components = new ListTag();
        for (VirtualComponentPosition pos : positions) {
            VirtualComponentBehaviour component = menu.componentAt(pos);
            if (component == null) continue;
            CompoundTag tag = component.toNBT(registries, VirtualComponentBehaviour.NbtProfile.EXPORT);
            offsetPosition(tag.getCompound("Pos"), minX, minY);
            if (component instanceof VirtualGaugeBehaviour gauge) {
                tag.remove("Network");
                Integer placeholder = placeholders.get(gauge.networkId);
                if (placeholder == null) throw new IllegalStateException("Missing network placeholder");
                tag.putInt("Network", placeholder);
                normalizeRecipeSources(tag, selected, minX, minY);
            }
            components.add(tag);
        }
        root.put("Components", components);

        ListTag connections = new ListTag();
        Set<String> seenEdges = new LinkedHashSet<>();
        for (VirtualComponentPosition pos : positions) {
            VirtualComponentBehaviour component = menu.componentAt(pos);
            if (component == null) continue;
            for (Connection connection : component.outgoingConnections()) {
                if (!selected.contains(connection.from) || !selected.contains(connection.to)) continue;
                String edgeKey = connection.type.name() + ":" + connection.from + ":" + connection.to;
                if (!seenEdges.add(edgeKey)) continue;
                CompoundTag tag = connection.toExportNBT();
                offsetPosition(tag.getCompound("From"), minX, minY);
                offsetPosition(tag.getCompound("To"), minX, minY);
                connections.add(tag);
            }
        }
        root.put("Connections", connections);
        return root;
    }

    private static void normalizeRecipeSources(CompoundTag component, Set<VirtualComponentPosition> selected,
                                               int minX, int minY) {
        ListTag slots = component.getList("RecipeSlots", Tag.TAG_COMPOUND);
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag slot = slots.getCompound(i);
            if (!slot.contains("Source", Tag.TAG_COMPOUND)) continue;
            CompoundTag source = slot.getCompound("Source");
            VirtualComponentPosition sourcePos = VirtualComponentPosition.fromNBT(source);
            if (selected.contains(sourcePos)) offsetPosition(source, minX, minY);
            else slots.set(i, new CompoundTag());
        }
    }

    private static void offsetPosition(CompoundTag position, int minX, int minY) {
        position.putInt("X", position.getInt("X") - minX);
        position.putInt("Y", position.getInt("Y") - minY);
    }

    public static Path blueprintDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("controller_blueprints");
    }

    public static Path blueprintPath(String name) {
        String fileName = name.toLowerCase(Locale.ROOT).endsWith(EXTENSION) ? name : name + EXTENSION;
        return blueprintDirectory().resolve(fileName);
    }

    public static boolean blueprintExists(String name) {
        return isValidBlueprintName(name) && Files.isRegularFile(blueprintPath(name));
    }

    private static final Pattern NAME_INVALID_REGEX = Pattern.compile("[/\\p{Cntrl}]|^[\\s.]|[\\s.]$");
    private static final Pattern NAME_INVALID_REGEX_WIN = Pattern.compile("(?i)[<>:\"/\\\\|?*]|^(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\.|$)");

    /** Client-local filename validation:
     * - No control characters or path separator
     * - Not start or end with whitespace or dot
     * - Valid name on Windows
     **/
    public static boolean isValidBlueprintName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (NAME_INVALID_REGEX.matcher(name).find()) return false;
        return Util.getPlatform() != Util.OS.WINDOWS || !NAME_INVALID_REGEX_WIN.matcher(name).find();
    }
}
