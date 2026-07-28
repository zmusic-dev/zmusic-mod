package me.zhenxin.zmusic.common

/**
 * ZMusic 共享常量。
 *
 * @author 真心
 * @since 2026-04-25 00:13
 */
object ZMusicConstants {
    const val MOD_ID = "zmusic"
    const val MOD_VERSION = "5.0.0-dev"
    const val CHANNEL = "zmusic:packet"
    const val PROTOCOL_VERSION = 1
    const val MAX_PACKET_BYTES = 32760
    const val MAX_JSON_BYTES = MAX_PACKET_BYTES - 5
    const val MAX_LYRICS_BYTES = 262144
}
