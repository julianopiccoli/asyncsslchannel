package jpiccoli.io.ssl;

import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;

class CompletableFutureWrapper<V> implements CompletionHandler<V, Void> {

    private final CompletableFuture<V> completableFuture;

    CompletableFutureWrapper(final CompletableFuture<V> completableFuture) {
        this.completableFuture = completableFuture;
    }

    @Override
    public void completed(V result, Void attachment) {
        completableFuture.complete(result);
    }

    @Override
    public void failed(Throwable exc, Void attachment) {
        completableFuture.completeExceptionally(exc);
    }

}
