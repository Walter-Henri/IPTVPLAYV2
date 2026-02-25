package com.m3u.core.plugin.model

import kotlinx.serialization.Serializable

@Serializable
data class Result(
    val success: Boolean,
    val message: String? = null
)
