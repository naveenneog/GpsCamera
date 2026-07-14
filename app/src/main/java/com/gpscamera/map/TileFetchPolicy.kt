package com.gpscamera.map

internal object TileFetchPolicy {
    const val CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1_000L
    const val MAX_ATTEMPTS = 3
    const val INITIAL_RETRY_DELAY_MS = 350L

    fun isFresh(lastModifiedMs: Long, nowMs: Long): Boolean =
        lastModifiedMs > 0L && nowMs - lastModifiedMs < CACHE_TTL_MS

    fun shouldRetryHttpStatus(status: Int): Boolean =
        status == 408 || status == 425 || status == 429 || status in 500..599

    fun retryDelayMs(failedAttemptIndex: Int): Long =
        INITIAL_RETRY_DELAY_MS * (1L shl failedAttemptIndex.coerceIn(0, 3))

    fun cacheFileName(zoom: Int, x: Int, y: Int): String = "${zoom}_${x}_${y}.png"
}
