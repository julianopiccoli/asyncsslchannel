package jpiccoli.io.ssl;

import java.nio.channels.CompletionHandler;

public class IOHandlerBuilder {

    private IOHandlerBuilder() {
        //
    }

    public static <R, A> CompletionHandler<R, A> buildCompletionHandler(final SuccessHandler<R, A> successHandler) {
        return new CompletionHandler<R, A>() {
            @Override
            public void completed(R result, A attachment) {
                successHandler.complete(result, attachment);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                exc.printStackTrace();
            }
        };
    }

    public static <R, A> CompletionHandler<R, A> buildCompletionHandler(final SuccessHandler<R, A> successHandler, final ErrorHandler<A> errorHandler) {
        return new CompletionHandler<R, A>() {
            @Override
            public void completed(R result, A attachment) {
                successHandler.complete(result, attachment);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                errorHandler.failed(exc, attachment);
            }
        };
    }

    @FunctionalInterface
    public interface SuccessHandler<R, A> {
        void complete(R result, A attachment);
    }

    @FunctionalInterface
    public interface ErrorHandler<A> {
        void failed(Throwable exc, A attachment);
    }

}
