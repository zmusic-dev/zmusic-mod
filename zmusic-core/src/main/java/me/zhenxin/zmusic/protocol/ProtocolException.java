package me.zhenxin.zmusic.protocol;

/**
 * 表示可安全拒绝的 ZMusic 通信帧错误。
 *
 * @author 真心
 * @since 3.8.0
 */
public class ProtocolException extends IllegalArgumentException {
    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
