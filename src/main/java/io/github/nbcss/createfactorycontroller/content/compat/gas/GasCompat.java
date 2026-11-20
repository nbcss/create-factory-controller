package io.github.nbcss.createfactorycontroller.content.compat.gas;

import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/**
 * The seam to Create: Crafted Beginning's gas logistics. A gas is keyed by CCB's virtual gas item, which rides
 * Create's item logistics in gas units (GU). Recognition is id-only and the one CCB call is a method handle, so
 * this class compiles and loads without CCB.
 */
public final class GasCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String MODID = "createcraftedbeginning";
    private static final ResourceLocation TOKEN_ITEM_ID = ResourceLocation.fromNamespaceAndPath(MODID, "gas_virtual_item");
    private static final ResourceLocation TOKEN_TYPE_ID = ResourceLocation.fromNamespaceAndPath(MODID, "gas_virtual_item_type");
    private static final String VIRTUAL_GAS_ITEMS = "net.ty.createcraftedbeginning.content.airtights.gasfilter.VirtualGasItems";
    private static final String GAS_STACK_CLASS = "net.ty.createcraftedbeginning.api.gas.GasStack";

    private static volatile boolean resolved = false;
    @Nullable private static Item tokenItem;
    @Nullable private static DataComponentType<?> tokenType;
    @Nullable private static MethodHandle createVirtualItems;
    /** {@code VirtualGasItems.createVirtualItem(GasStack)}, typed {@code (Object) -> ItemStack}. */
    @Nullable private static MethodHandle createVirtualItem;

    private GasCompat() {}

    private static void resolve() {
        if (resolved) return;
        ModList list = ModList.get();
        if (list == null) return;
        if (list.isLoaded(MODID) && BuiltInRegistries.ITEM.containsKey(TOKEN_ITEM_ID)) {
            tokenItem = BuiltInRegistries.ITEM.get(TOKEN_ITEM_ID);
            tokenType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(TOKEN_TYPE_ID);
            try {
                Class<?> virtualGasItems = Class.forName(VIRTUAL_GAS_ITEMS);
                createVirtualItems = MethodHandles.publicLookup().findStatic(virtualGasItems,
                    "createVirtualItems", MethodType.methodType(List.class, ItemStack.class));
                createVirtualItem = MethodHandles.publicLookup().findStatic(virtualGasItems,
                    "createVirtualItem", MethodType.methodType(ItemStack.class, Class.forName(GAS_STACK_CLASS)))
                    .asType(MethodType.methodType(ItemStack.class, Object.class));
            } catch (ReflectiveOperationException | LinkageError e) {
                LOGGER.warn("Create: Crafted Beginning is installed but its gas API changed; gas can't be set from held items or JEI.", e);
            }
        }
        resolved = true;
    }

    /** Whether CCB's gas logistics is present. */
    public static boolean isLoaded() {
        resolve();
        return tokenItem != null;
    }

    /** True when {@code stack} is a CCB gas key (the virtual gas item carrying a gas type). */
    public static boolean isGasFilter(ItemStack stack) {
        if (stack.isEmpty() || !isLoaded()) return false;
        return stack.is(tokenItem) && (tokenType == null || stack.has(tokenType));
    }

    /**
     * The gas key for a held item (gas key, balloon or single-gas canister), else EMPTY. Built by CCB itself so its
     * components match the keys gas packagers report to the network.
     */
    @SuppressWarnings("unchecked")
    public static ItemStack gasFromCarried(ItemStack carried) {
        if (carried.isEmpty() || !isLoaded()) return ItemStack.EMPTY;
        if (isGasFilter(carried)) return carried.copyWithCount(1);
        if (createVirtualItems == null) return ItemStack.EMPTY;
        try {
            List<ItemStack> keys = (List<ItemStack>) createVirtualItems.invokeExact(carried);
            return keys.size() == 1 ? keys.getFirst().copyWithCount(1) : ItemStack.EMPTY;
        } catch (Throwable e) {
            return ItemStack.EMPTY;
        }
    }

    /** The gas key for a CCB {@code GasStack} (e.g. a JEI gas ingredient), else EMPTY (also for any other object). */
    public static ItemStack gasFromStack(Object gasStack) {
        if (!isLoaded() || createVirtualItem == null || !GAS_STACK_CLASS.equals(gasStack.getClass().getName()))
            return ItemStack.EMPTY;
        try {
            return (ItemStack) createVirtualItem.invokeExact(gasStack);
        } catch (Throwable e) {
            return ItemStack.EMPTY;
        }
    }
}
