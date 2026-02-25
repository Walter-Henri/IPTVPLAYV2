package com.m3u.common;

import com.m3u.common.ExtractionData;
import com.m3u.common.IExtractionCallback;

interface IExtractorService {
    /**
     * Extracts the stream from a YouTube live asynchronously.
     * Result delivered via IExtractionCallback.
     */
    void extractStream(
        in String youtubeUrl,
        in IExtractionCallback callback
    );

    /** Returns the contract version for future compatibility. */
    int getVersion();
}
