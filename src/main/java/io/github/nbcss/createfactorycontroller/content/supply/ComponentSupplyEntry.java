package io.github.nbcss.createfactorycontroller.content.supply;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** One Component Supply slot: a component item and the component type it places. */
public record ComponentSupplyEntry(Item item, VirtualComponentBehaviour.Type type) {

    public boolean networked() {
        return type.isRequireNetwork();
    }

    public ResourceLocation id() {
        return BuiltInRegistries.ITEM.getKey(item);
    }
}
