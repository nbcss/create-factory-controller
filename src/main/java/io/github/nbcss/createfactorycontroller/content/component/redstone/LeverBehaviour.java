package io.github.nbcss.createfactorycontroller.content.component.redstone;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.SyncCodecs;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

public class LeverBehaviour extends ManualRedstoneSourceBehaviour {

    public static final Type TYPE = new Type() {
        @Override public String id() { return "LEVER"; }
        @Override public List<ResourceLocation> items() { return List.of(BuiltInRegistries.ITEM.getKey(Items.LEVER)); }
        @Override public int color() { return 0x8F8F8F; }
        @Override public boolean isRequireNetwork() { return false; }

        @Override
        public VirtualComponentBehaviour create(FactoryControllerBlockEntity controller, VirtualComponentPosition pos,
                                                Item item, UUID networkId) {
            return new LeverBehaviour(controller, pos, item);
        }

        @Override
        public VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller, CompoundTag tag,
                                                 HolderLookup.Provider registries) {
            return LeverBehaviour.fromNBT(controller, tag);
        }

        @Override
        public VirtualComponentBehaviour fromClient(RegistryFriendlyByteBuf buf) {
            VirtualComponentPosition pos = SyncCodecs.readPos(buf);
            Item item = BuiltInRegistries.ITEM.get(buf.readResourceLocation());
            LeverBehaviour lever = new LeverBehaviour(null, pos, item);
            lever.readClientState(buf);
            return lever;
        }
    };

    public static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "factory_controller/lever");

    public LeverBehaviour(FactoryControllerBlockEntity controller, VirtualComponentPosition position, Item item) {
        super(controller, position, item);
    }

    @Override public String typeId() { return TYPE.id(); }
    @Override public ResourceLocation getTexture() { return TEXTURE; }
    @Override public int getColor() { return TYPE.color(); }

    @Override
    public Component getName() {
        return Component.translatable("createfactorycontroller.component.lever");
    }

    @Override
    public void onPress(ServerPlayer player) {
        setPowered(!powered, true);
    }

    // ── NBT ─────────────────────────────────────────────────────────────────────

    @Override
    public CompoundTag toNBT(HolderLookup.Provider registries, NbtProfile profile) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", TYPE.id());
        tag.put("Pos", position.toNBT());
        tag.putString("Item", getItemId().toString());
        tag.putBoolean("Powered", powered);
        return tag;
    }

    public static LeverBehaviour fromNBT(FactoryControllerBlockEntity controller, CompoundTag tag) {
        VirtualComponentPosition pos = VirtualComponentPosition.fromNBT(tag.getCompound("Pos"));
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("Item")));
        LeverBehaviour lever = new LeverBehaviour(controller, pos, item);
        lever.powered = tag.getBoolean("Powered");
        return lever;
    }
}
