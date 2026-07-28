package me.zhenxin.zmusic;

import me.zhenxin.zmusic.event.ClientEvent;
import me.zhenxin.zmusic.event.NeoForgeEvent;
import me.zhenxin.zmusic.manager.SoundManagerImpl;
import me.zhenxin.zmusic.network.ZMusicPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * NeoForge Mod 主入口
 *
 * @author 真心
 * @since 2026-04-24 18:00
 */
@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
@Mod("zmusic")
public class ZMusicNeoForgeMod {

    public ZMusicNeoForgeMod(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.register(new NeoForgeEvent());
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        ZMusic.setSoundManager(new SoundManagerImpl());
        ZMusic.onEnable();
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .optional()
                .playToClient(ZMusicPayload.TYPE, ZMusicPayload.STREAM_CODEC, this::handlePayload);
    }

    private void handlePayload(ZMusicPayload payload, IPayloadContext context) {
        ClientEvent.onPacket(payload.message());
    }
}
