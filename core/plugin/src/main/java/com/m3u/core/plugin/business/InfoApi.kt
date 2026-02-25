package com.m3u.core.plugin.business

interface InfoApi {
    suspend fun getAppInfo(): com.m3u.core.plugin.model.GetAppInfoResponse
    suspend fun getModules(): com.m3u.core.plugin.model.GetModulesResponse
    suspend fun getMethods(req: com.m3u.core.plugin.model.GetMethodsRequest): com.m3u.core.plugin.model.GetMethodsResponse
}
