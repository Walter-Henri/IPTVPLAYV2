package com.m3u.core.plugin;

interface IRemoteCallback {
    void onSuccess(String module, String method, in byte[] result);
    void onError(String module, String method, int errorCode, String errorMessage);
}
