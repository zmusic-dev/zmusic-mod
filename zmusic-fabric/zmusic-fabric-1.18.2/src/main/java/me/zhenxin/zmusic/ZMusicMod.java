package me.zhenxin.zmusic;

import me.zhenxin.zmusic.event.ClientEvent;
import me.zhenxin.zmusic.manager.SoundManagerImpl;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;


/**
 * Mod 主入口
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/28 13:01
 */
@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public class ZMusicMod implements ModInitializer {

    @Override
    public void onInitialize() {
        ZMusic.setSoundManager(new SoundManagerImpl());
        Identifier identifier = new Identifier("zmusic", "packet");
        ClientPlayNetworking.registerGlobalReceiver(identifier, (client, handler, buf, responseSender) -> {
            byte[] buffer = new byte[buf.readableBytes()];
            buf.readBytes(buffer);
            client.execute(() -> ClientEvent.onPacket(buffer));
        });
        ClientEvent.configure(payload -> {
            PacketByteBuf packet = PacketByteBufs.create();
            packet.writeBytes(payload);
            ClientPlayNetworking.send(identifier, packet);
            return true;
        }, "1.18.2", "fabric");
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientEvent.onConnected());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEvent.onDisconnect());
        ZMusic.onEnable();
    }
}
