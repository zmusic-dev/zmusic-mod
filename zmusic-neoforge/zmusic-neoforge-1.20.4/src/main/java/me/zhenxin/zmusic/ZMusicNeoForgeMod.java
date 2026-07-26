package me.zhenxin.zmusic;

import me.zhenxin.zmusic.event.ClientEvent;
import me.zhenxin.zmusic.event.NeoForgeEvent;
import me.zhenxin.zmusic.manager.SoundManagerImpl;
import me.zhenxin.zmusic.network.ZMusicPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge Mod 主入口
 *
 * @author 真心
 * @since 2026-04-24 11:00
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
        ClientEvent.configure(payload -> {
            PacketDistributor.SERVER.noArg().send(new ZMusicPayload(payload));
            return true;
        }, "1.20.4", "neoforge");
        ZMusic.onEnable();
    }

    private void registerPayloads(RegisterPayloadHandlerEvent event) {
        event.registrar("zmusic")
                .optional()
                .play(ZMusicPayload.ID, ZMusicPayload::new, handler -> handler.client(this::handlePayload));
    }

    private void handlePayload(ZMusicPayload payload, PlayPayloadContext context) {
        context.workHandler().execute(() -> ClientEvent.onPacket(payload.data()));
    }
}
