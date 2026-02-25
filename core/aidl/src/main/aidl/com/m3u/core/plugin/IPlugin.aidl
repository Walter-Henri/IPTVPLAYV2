package com.m3u.core.plugin;

import com.m3u.core.plugin.IPluginCallback;

interface IPlugin {
    String resolve(String url);
    void extractLinksAsync(String jsonContent, IPluginCallback callback);
    void syncChannels(IPluginCallback callback);
}
