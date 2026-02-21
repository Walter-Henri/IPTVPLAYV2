package com.m3u.core.extension;

import com.m3u.core.extension.IExtensionCallback;

interface IExtension {
    String resolve(String url);
    void extractLinksAsync(String jsonContent, IExtensionCallback callback);
    void syncChannels(IExtensionCallback callback);
}
