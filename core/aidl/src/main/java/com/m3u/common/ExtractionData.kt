package com.m3u.common

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Parcelable data class that carries extraction results across IPC.
 *
 * [headersJson] is a JSON-serialized Map<String, String> because
 * generic Map is not stable via Binder. Deserialize on the host
 * side with Gson/kotlinx.serialization.
 */
@Parcelize
data class ExtractionData(
    val m3u8Url: String,
    val userAgent: String,
    val cookies: String,
    val headersJson: String = "{}"
) : Parcelable
