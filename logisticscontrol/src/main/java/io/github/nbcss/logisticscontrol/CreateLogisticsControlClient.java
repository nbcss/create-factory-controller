package io.github.nbcss.logisticscontrol;

import io.github.nbcss.logisticscontrol.content.ponder.LogisticsControlPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = CreateLogisticsControl.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CreateLogisticsControl.MODID, value = Dist.CLIENT)
public class CreateLogisticsControlClient {

    public CreateLogisticsControlClient() {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PonderIndex.addPlugin(new LogisticsControlPonderPlugin()));
    }
}
