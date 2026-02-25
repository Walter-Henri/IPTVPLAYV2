package com.m3u.core.plugin;

import com.m3u.core.plugin.IRemoteCallback;

interface IRemoteService {
    void call(String module, String method, in byte[] param, IRemoteCallback callback);
}
