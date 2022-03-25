package jpiccoli.test.io.ssl.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import jpiccoli.io.ssl.AsynchronousSSLChannel;
import jpiccoli.io.ssl.IOHandlerBuilder;

public class SimpleHttpsClient {

	private static final Logger LOGGER = Logger.getLogger(SimpleHttpsClient.class.getName());
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private static final String LINE_ENDING = "\r\n";
    private static final String RESPONSE_HEADER_ENDING = LINE_ENDING + LINE_ENDING;

    private final String server;

    private CompletableFuture<String> future;
    private AsynchronousByteChannel channel;
    private SSLEngine engine;
    private ByteBuffer requestBuffer;
    private ByteBuffer responseBuffer;
    private StringBuilder responseStringBuilder;

    public SimpleHttpsClient(final String server) {
        this.server = server;
    }

    public Future<String> start() throws IOException {
    	LOGGER.info("Creating socket.");
        AsynchronousSocketChannel socket = AsynchronousSocketChannel.open();
        channel = socket;
        future = new CompletableFuture<>();
        LOGGER.info("Connecting to \"" + server + "\".");
        socket.connect(new InetSocketAddress(server, 443), null, IOHandlerBuilder.buildCompletionHandler(this::connected, this::ioFailed));
        return future;
    }

    private void createSSLEngine() throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(null, new TrustManager[] {new DummyTrustManager()}, null);
        engine = context.createSSLEngine();
        engine.setUseClientMode(true);
    }

    private void connected(Void ignored, Void ignored2) {
        try {
        	LOGGER.info("Connected. Creating TLS layer.");
            createSSLEngine();
            channel = new AsynchronousSSLChannel(channel, engine, EXECUTOR_SERVICE);
            LOGGER.info("Sending request.");
            sendRequest();
        } catch (GeneralSecurityException e) {
            LOGGER.log(Level.SEVERE, "Error creating TLS layer.", e);
        }
    }

    private void sendRequest() {
        StringBuilder requestString = new StringBuilder();
        requestString.append("GET /").append(" HTTP/1.1").append(SimpleHttpsClient.LINE_ENDING);
        requestString.append("Host: ").append(server).append(SimpleHttpsClient.LINE_ENDING);
        requestString.append("Connection: close").append(SimpleHttpsClient.LINE_ENDING);
        requestString.append(SimpleHttpsClient.LINE_ENDING);
        requestBuffer = ByteBuffer.wrap(requestString.toString().getBytes());
        channel.write(requestBuffer, null, IOHandlerBuilder.buildCompletionHandler(this::writeCompleted, this::ioFailed));
    }

    private void readCompleted(Integer result, Void dummy) {
        if (result > 0) {
            responseBuffer.flip();
            responseStringBuilder.append(new String(responseBuffer.array(), responseBuffer.arrayOffset(), responseBuffer.limit()));
            responseBuffer.clear();
            channel.read(responseBuffer, null, IOHandlerBuilder.buildCompletionHandler(this::readCompleted, this::ioFailed));
        } else {
            int responseHeaderEndingIndex = responseStringBuilder.indexOf(RESPONSE_HEADER_ENDING);
            if (responseHeaderEndingIndex >= 0) {
            	LOGGER.info("Finished reading response.");
                String responseContent = responseStringBuilder.substring(responseHeaderEndingIndex + RESPONSE_HEADER_ENDING.length());
                future.complete(responseContent);
            }
            try {
                channel.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error closing socket.", e);
            }
        }
    }

    private void writeCompleted(Integer result, Void ignored) {
        if (requestBuffer.hasRemaining()) {
            channel.write(requestBuffer, null, IOHandlerBuilder.buildCompletionHandler(this::writeCompleted, this::ioFailed));
        } else {
        	LOGGER.info("Finished sending request. Reading response.");
            responseBuffer = ByteBuffer.allocate(1024 * 100);
            responseStringBuilder = new StringBuilder();
            channel.read(responseBuffer, null, IOHandlerBuilder.buildCompletionHandler(this::readCompleted, this::ioFailed));
        }
    }

    private void ioFailed(Throwable exc, Void ignored) {
        LOGGER.log(Level.SEVERE, "Error performing IO operation.", exc);
        try {
            channel.close();
        } catch (IOException e) {
        	LOGGER.log(Level.SEVERE, "Error closing socket.", e);
        }
        future.completeExceptionally(exc);
    }

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {

        String server = "www.google.com";
        SimpleHttpsClient simpleHttpsClient = new SimpleHttpsClient(server);
        System.out.println("--- Sending request to: " + server);
        Future<String> future = simpleHttpsClient.start();
        String content = future.get();
        System.out.println("--- Got response: ");
        System.out.println("------------------");
        System.out.println(content);
        EXECUTOR_SERVICE.shutdownNow();

    }

}
