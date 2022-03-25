package jpiccoli.io.ssl;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import javax.net.ssl.SSLEngineResult;

class IOOperation<A> {

    private final ByteBuffer buffer;
    private final A attachment;
    private final CompletionHandler<Integer, ? super A> completionHandler;
    private SSLEngineResult lastEngineResult;
    private int byteCount;

    IOOperation(final ByteBuffer buffer, final A attachment, final CompletionHandler<Integer, ? super A> completionHandler) {
        this.buffer = buffer;
        this.attachment = attachment;
        this.completionHandler = completionHandler;
    }

    ByteBuffer getBuffer() {
        return buffer;
    }

    void setLastEngineResult(SSLEngineResult lastEngineResult) {
        this.lastEngineResult = lastEngineResult;
    }

    SSLEngineResult getLastEngineResult() {
        return lastEngineResult;
    }

    void incrementByteCount(final int increment) {
        this.byteCount += increment;
    }

    void setByteCount(final int byteCount) {
        this.byteCount = byteCount;
    }

    int getByteCount() {
        return byteCount;
    }

    void fireCompletion() {
        completionHandler.completed(byteCount, attachment);
    }

    void fireException(final Throwable exc) {
        completionHandler.failed(exc, attachment);
    }

}
