package com.jhopanstore.vpnhospot;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class TrafficCounter {
    private final AtomicLong uploadBytes = new AtomicLong();
    private final AtomicLong downloadBytes = new AtomicLong();
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    void setEnabled(boolean value) {
        enabled.set(value);
    }

    boolean isEnabled() {
        return enabled.get();
    }

    void addUpload(long bytes) {
        if (enabled.get() && bytes > 0) uploadBytes.addAndGet(bytes);
    }

    void addDownload(long bytes) {
        if (enabled.get() && bytes > 0) downloadBytes.addAndGet(bytes);
    }

    long uploadBytes() {
        return uploadBytes.get();
    }

    long downloadBytes() {
        return downloadBytes.get();
    }

    void reset() {
        uploadBytes.set(0);
        downloadBytes.set(0);
    }
}
