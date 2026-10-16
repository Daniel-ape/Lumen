package com.yourname.lumen.core.util

import android.content.Context
import coil.request.ImageRequest

/**
 * One way to ask for each kind of picture, so the same picture always has the same size and the
 * same cache entry. The hero, the details page and the background prefetch therefore share one
 * decoded copy instead of loading it again, and nothing is decoded larger than it is shown.
 */
fun backdropRequest(context: Context, url: String): ImageRequest =
    ImageRequest.Builder(context).data(url).size(1280, 720).build()

fun posterRequest(context: Context, url: String): ImageRequest =
    ImageRequest.Builder(context).data(url).size(300, 450).crossfade(160).build()

fun logoRequest(context: Context, url: String): ImageRequest =
    ImageRequest.Builder(context).data(url).size(96, 96).build()
