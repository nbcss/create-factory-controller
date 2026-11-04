package io.github.nbcss.createfactorycontroller.content.component.redstone;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.SyncCodecs;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.RedstoneConnection;
import io.github.nbcss.createfactorycontroller.content.helper.ControllerAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ButtonBehaviour extends ManualRedstoneSourceBehaviour {

    public static final int MIN_PRESS_TICKS = 2;

    public static final Type TYPE = new Type() {
        @Override public String id() { return "BUTTON"; }
        @Override public List<ResourceLocation> items() {
            return List.of(BuiltInRegistries.ITEM.getKey(Items.STONE_BUTTON));
        }
        @Override public int color() { return 0x8F8F8F; }
        @Override public boolean isRequireNetwork() { return false; }

        @Override
        public VirtualComponentBehaviour create(FactoryControllerBlockEntity controller, VirtualComponentPosition pos,
                                                Item item, UUID networkId) {
            return new ButtonBehaviour(controller, pos, item);
        }

        @Override
        public VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller, CompoundTag tag,
                                                 HolderLookup.Provider registries) {
            return ButtonBehaviour.fromNBT(controller, tag);
        }

        @Override
        public VirtualComponentBehaviour fromClient(RegistryFriendlyByteBuf buf) {
            VirtualComponentPosition pos = SyncCodecs.readPos(buf);
            Item item = BuiltInRegistries.ITEM.get(buf.readResourceLocation());
            ButtonBehaviour button = new ButtonBehaviour(null, pos, item);
            button.readClientState(buf);
            return button;
        }
    };

    /** Players holding the button. */
    private final Set<UUID> holders = new HashSet<>();
    /** Game time of the press that powered the button. */
    private long pressedAt;
    /** Saved edges may still carry a pre-unload POWERED; republish the released state on the first tick. */
    private boolean republish = false;

    public ButtonBehaviour(FactoryControllerBlockEntity controller, VirtualComponentPosition position, Item item) {
        super(controller, position, item);
    }

    @Override public String typeId() { return TYPE.id(); }

    @Override
    public ResourceLocation getTexture() {
        return ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID,
                "factory_controller/" + getItemId().getPath());
    }

    @Override public int getColor() { return TYPE.color(); }

    @Override
    public Component getName() {
        return Component.translatable("createfactorycontroller.component.button");
    }

    @Override
    public void onPress(ServerPlayer player) {
        holders.add(player.getUUID());
        if (!powered) {
            pressedAt = gameTime();
            setPowered(true, true);
        }
    }

    @Override
    public void onRelease(ServerPlayer player) {
        holders.remove(player.getUUID());
    }

    @Override
    public void preTick() {
        if (republish) {
            republish = false;
            publish(RedstoneConnection.TYPE);
        }
        if (!holders.isEmpty()) dropStaleHolders();
        if (powered && holders.isEmpty() && gameTime() >= pressedAt + MIN_PRESS_TICKS)
            setPowered(false, false);
    }

    /** Lease check */
    private void dropStaleHolders() {
        MinecraftServer server = controller == null || controller.getLevel() == null
                ? null : controller.getLevel().getServer();
        if (server == null) { holders.clear(); return; }
        holders.removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player == null || !(player.containerMenu instanceof FactoryControllerMenu menu)
                    || ControllerAccessor.getBlockEntity(player, menu.activeControllerPos()) != controller;
        });
    }

    private long gameTime() {
        return controller != null && controller.getLevel() != null ? controller.getLevel().getGameTime() : 0;
    }

    @Override
    public void onRemoved() {
        holders.clear();
    }

    @Override
    public void onUnload() {
        holders.clear();
    }

    // ── NBT ─────────────────────────────────────────────────────────────────────

    @Override
    public CompoundTag toNBT(HolderLookup.Provider registries, NbtProfile profile) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", TYPE.id());
        tag.put("Pos", position.toNBT());
        tag.putString("Item", getItemId().toString());
        return tag;
    }

    public static ButtonBehaviour fromNBT(FactoryControllerBlockEntity controller, CompoundTag tag) {
        VirtualComponentPosition pos = VirtualComponentPosition.fromNBT(tag.getCompound("Pos"));
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("Item")));
        ButtonBehaviour button = new ButtonBehaviour(controller, pos, item);
        button.republish = true;
        return button;
    }
}
