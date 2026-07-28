package me.zhenxin.zmusic.client

/**
 * 一条按时间排序的 LRC 歌词时间线。
 *
 * @author 真心
 * @since 2026-07-28 00:00
 */
internal class LrcTimeline private constructor(private val lines: List<Line>) {
    data class Match(val index: Int, val text: String)
    private data class Line(val timestampMillis: Long, val text: String)

    fun lineAt(positionMillis: Long): Match {
        var low = 0
        var high = lines.lastIndex
        var match = -1
        while (low <= high) {
            val middle = (low + high).ushr(1)
            if (lines[middle].timestampMillis <= positionMillis) {
                match = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return if (match < 0) Match(-1, "") else Match(match, lines[match].text)
    }

    companion object {
        private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

        fun parse(content: String): LrcTimeline {
            val lines = ArrayList<Line>()
            content.lineSequence().forEach { rawLine ->
                val matches = timestamp.findAll(rawLine).toList()
                if (matches.isEmpty()) return@forEach
                val text = rawLine.substring(matches.last().range.last + 1).trim()
                matches.forEach { match ->
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toLong()
                    val fraction = match.groupValues[3]
                    val millis = when (fraction.length) {
                        1 -> fraction.toLong() * 100
                        2 -> fraction.toLong() * 10
                        3 -> fraction.toLong()
                        else -> 0
                    }
                    lines += Line((minutes * 60 + seconds) * 1000 + millis, text)
                }
            }
            return LrcTimeline(lines.sortedBy(Line::timestampMillis))
        }

        fun empty() = LrcTimeline(emptyList())
    }
}
