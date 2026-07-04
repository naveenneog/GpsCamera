package com.gpscamera.camera

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhotoSaverNamingTest {

    @Test
    fun fileName_followsTimestampPattern() {
        val name = PhotoSaver.fileName(1_783_184_442_000L)
        assertThat(name).matches("GPS_\\d{8}_\\d{6}\\.jpg")
    }

    @Test
    fun fileName_isUniquePerSecond() {
        val a = PhotoSaver.fileName(1_783_184_442_000L)
        val b = PhotoSaver.fileName(1_783_184_443_000L)
        assertThat(a).isNotEqualTo(b)
    }
}
