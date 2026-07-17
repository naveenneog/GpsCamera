package com.gpscamera

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

/**
 * Registers Coil's [VideoFrameDecoder] so the in-app gallery can render a frame of each
 * recorded video as its thumbnail (Coil only decodes images out of the box).
 */
class GpsCameraApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
