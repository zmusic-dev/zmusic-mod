package me.zhenxin.zmusic.event;

import lombok.extern.log4j.Log4j2;

/**
 * 客户端事件
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/29 22:52
 */
@Log4j2
public class ClientEvent {

    @SuppressWarnings("AlibabaUndefineMagicConstant")
    public static void onPacket(String message) {
        if (message == null) {
            log.warn("Received null ZMusic packet message");
            return;
        }
        log.info("Received ZMusic packet message: {}", message);
        if (message.startsWith("[Play]")) {
            String data = message.replace("[Play]", "");
            log.info("Parsed ZMusic play command: {}", data);
            PacketEvent.onPlay(data);
        } else if ("[Stop]".equals(message)) {
            log.info("Parsed ZMusic stop command");
            PacketEvent.onStop();
        } else {
            log.warn("Ignored unknown ZMusic packet message: {}", message);
        }
    }

    public static void onDisconnect() {
        log.info("ZMusic client disconnected, stopping native player");
        PacketEvent.onStop();
    }
}
