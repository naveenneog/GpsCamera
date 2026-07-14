package com.gpscamera.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TileFetchPolicyTest {

    @Test
    fun cacheIsFreshForSevenDays() {
        val now = 10L * TileFetchPolicy.CACHE_TTL_MS

        assertThat(
            TileFetchPolicy.isFresh(now - TileFetchPolicy.CACHE_TTL_MS + 1L, now)
        ).isTrue()
        assertThat(
            TileFetchPolicy.isFresh(now - TileFetchPolicy.CACHE_TTL_MS, now)
        ).isFalse()
    }

    @Test
    fun onlyTransientHttpFailuresAreRetried() {
        assertThat(TileFetchPolicy.shouldRetryHttpStatus(408)).isTrue()
        assertThat(TileFetchPolicy.shouldRetryHttpStatus(429)).isTrue()
        assertThat(TileFetchPolicy.shouldRetryHttpStatus(503)).isTrue()
        assertThat(TileFetchPolicy.shouldRetryHttpStatus(403)).isFalse()
        assertThat(TileFetchPolicy.shouldRetryHttpStatus(404)).isFalse()
    }

    @Test
    fun retryDelayUsesSmallExponentialBackoff() {
        assertThat(TileFetchPolicy.retryDelayMs(0)).isEqualTo(350L)
        assertThat(TileFetchPolicy.retryDelayMs(1)).isEqualTo(700L)
        assertThat(TileFetchPolicy.retryDelayMs(2)).isEqualTo(1_400L)
    }
}
