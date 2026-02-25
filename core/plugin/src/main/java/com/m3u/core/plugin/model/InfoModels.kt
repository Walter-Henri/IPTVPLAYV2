package com.m3u.core.plugin.model

import kotlinx.serialization.Serializable

@Serializable
data class GetAppInfoResponse(
    val app_id: String,
    val app_version: String,
    val app_name: String,
    val app_description: String,
    val app_package_name: String
)

@Serializable
data class GetModulesResponse(
    val modules: List<String>
)

@Serializable
data class GetMethodsRequest(
    val module: String
)

@Serializable
data class GetMethodsResponse(
    val methods: List<String>
)
