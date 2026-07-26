package me.zhenxin.zmusic.protocol;

/**
 * 由各 Minecraft 加载器实现的客户端插件消息发送出口。
 *
 * @author 真心
 * @since 3.8.0
 */
@FunctionalInterface
public interface ClientTransport {
    /**
     * 向当前服务器发送完整 ZMusic 协议帧。
     *
     * @param payload 原始协议帧
     * @return 当前连接可发送时为 true
     */
    boolean send(byte[] payload);
}
