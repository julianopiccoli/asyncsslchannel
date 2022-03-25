package jpiccoli.io.ssl;

public class RunnableComposer {

    private RunnableComposer() {
        //
    }

    public static Runnable compose(final Runnable... runnables) {
        return () -> {
            for (Runnable runnable : runnables) {
                runnable.run();
            }
        };
    }

}
