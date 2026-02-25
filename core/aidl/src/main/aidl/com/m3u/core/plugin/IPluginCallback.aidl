package com.m3u.core.plugin;

interface IPluginCallback {
    oneway void onProgress(int current, int total, String name);
    oneway void onResult(String jsonResult);
    oneway void onError(String message);
}
