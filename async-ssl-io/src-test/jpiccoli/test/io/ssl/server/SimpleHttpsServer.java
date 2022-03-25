package jpiccoli.test.io.ssl.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import jpiccoli.io.ssl.AsynchronousSSLChannel;

public class SimpleHttpsServer {
	
	private static final String KEYSTORE_PATH = "keystore";
	private static final String KEYSTORE_PASS = "pass123";
	
	private static final Logger LOGGER = Logger.getLogger(SimpleHttpsServer.class.getName());

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException, GeneralSecurityException {

    	LOGGER.info("Initializing.");
        final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        final SSLContext sslContext = createSSLContext();

        LOGGER.info("SSL context creation complete. Opening server socket.");
        try (AsynchronousServerSocketChannel serverSocketChannel = AsynchronousServerSocketChannel.open()) {
            serverSocketChannel.bind(new InetSocketAddress(443));
            LOGGER.info("Socket bound to port 443. Awaiting connections.");
            while(!Thread.interrupted()) {
                AsynchronousSocketChannel socket = serverSocketChannel.accept().get();
                SSLEngine engine = sslContext.createSSLEngine();
                engine.setUseClientMode(false);
                engine.setNeedClientAuth(false);
                engine.setWantClientAuth(false);
                AsynchronousSSLChannel sslSocket = new AsynchronousSSLChannel(socket, engine, executorService);
                SimpleHttpRequestHandler requestHandler = new SimpleHttpRequestHandler(sslSocket);
                requestHandler.startHandling();
            }
        } finally {
        	executorService.shutdownNow();
        }
        LOGGER.info("Closing server.");

    }

    private static SSLContext createSSLContext() throws IOException, GeneralSecurityException {
        final char[] password = KEYSTORE_PASS.toCharArray();
        KeyStore keyStore = KeyStore.getInstance("JKS");
        LOGGER.info("Loading keystore file from \"" + KEYSTORE_PATH + "\".");
        try (FileInputStream stream = new FileInputStream(KEYSTORE_PATH)) {
            keyStore.load(stream, password);
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        return sslContext;
    }

}
