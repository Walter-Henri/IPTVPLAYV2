package com.m3u.common;

import com.m3u.common.ExtractionData;

oneway interface IExtractionCallback {
    void onSuccess(in ExtractionData data);
    void onError(String message);
}
