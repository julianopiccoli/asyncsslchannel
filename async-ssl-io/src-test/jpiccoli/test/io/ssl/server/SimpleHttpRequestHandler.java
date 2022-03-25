package jpiccoli.test.io.ssl.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import jpiccoli.io.ssl.IOHandlerBuilder;

class SimpleHttpRequestHandler {

	private static final Logger LOGGER = Logger.getLogger(SimpleHttpRequestHandler.class.getName());
	
    private static final File SERVER_ROOT_DIRECTORY = new File("server_root");
    private static final String LINE_ENDING = "\r\n";
    private static final String REQUEST_ENDING = "\r\n\r\n";

    private final AsynchronousByteChannel channel;
    private final ByteBuffer incomingBuffer;
    private final StringBuilder requestStringBuffer;

    private ByteBuffer responseHeaderBuffer;
    private ByteBuffer outgoingBuffer;
    private File requestedFile;
    private FileChannel fileChannel;

    SimpleHttpRequestHandler(final AsynchronousByteChannel channel) {
        this.channel = channel;
        incomingBuffer = ByteBuffer.allocate(1024 * 100);
        requestStringBuffer = new StringBuilder();
    }

    void startHandling() {
    	LOGGER.info("Reading client request.");
        channel.read(incomingBuffer, null, IOHandlerBuilder.buildCompletionHandler(this::readCompleted, this::ioFailed));
    }

    private void setResponse(final int responseCode, final File file) {
        StringBuilder responseStringBuffer = new StringBuilder();
        responseStringBuffer.append("HTTP/1.1 ").append(responseCode).append(" ").append(getResponseCodeDescription(responseCode)).append(LINE_ENDING);
        if (file != null && file.exists()) {
            this.requestedFile = file;
            String filename = file.getName().toLowerCase();
            if (filename.endsWith(".html") || filename.endsWith(".htm")) {
                responseStringBuffer.append("Content-Type: text/html").append(LINE_ENDING);
            } else {
                responseStringBuffer.append("Content-Type: application/octet-stream").append(LINE_ENDING);
            }
            responseStringBuffer.append("Content-Length: ").append(file.length()).append(LINE_ENDING);
        } else {
            responseStringBuffer.append("Content-Length: 0").append(LINE_ENDING);
        }
        responseStringBuffer.append("Connection: close").append(LINE_ENDING);
        responseStringBuffer.append(LINE_ENDING);
        responseHeaderBuffer = ByteBuffer.wrap(responseStringBuffer.toString().getBytes());
        LOGGER.info("Writing response header.");
        channel.write(responseHeaderBuffer, null, IOHandlerBuilder.buildCompletionHandler(this::headerWriteCompleted, this::ioFailed));
    }

    private void startTransfer() throws IOException {
    	if (requestedFile.exists()) {
    		LOGGER.info("Writing requested file contents.");
            fileChannel = new FileInputStream(requestedFile).getChannel();
            outgoingBuffer = ByteBuffer.allocateDirect(1024 * 100);
            transfer();    		
    	}
    }

    private void transfer() throws IOException {
        outgoingBuffer.clear();
        int result = fileChannel.read(outgoingBuffer);
        if (result > 0) {
            outgoingBuffer.flip();
            channel.write(outgoingBuffer, null, IOHandlerBuilder.buildCompletionHandler(this::contentWriteCompleted, this::ioFailed));
        } else {
        	LOGGER.info("Finished transferring file contents.");
            fileChannel.close();
        }
    }

    private String getResponseCodeDescription(final int responseCode) {
        switch (responseCode) {
            case 200:
                return "OK";
            case 404:
                return "Not Found";
            case 405:
                return "Method Not Allowed";
            default:
                return "Unknown";
        }
    }

    private void readCompleted(Integer result, Void attachment) {
        if (result > 0) {
            incomingBuffer.flip();
            requestStringBuffer.append(new String(incomingBuffer.array(), incomingBuffer.position(), incomingBuffer.limit()));
            if (requestStringBuffer.indexOf(REQUEST_ENDING) > -1) {
                String firstLine = requestStringBuffer.substring(0, requestStringBuffer.indexOf(LINE_ENDING));
                String upperCaseFirstLine = firstLine.toUpperCase();
                if (upperCaseFirstLine.startsWith("GET ")) {
                    String requestedFileName = firstLine.substring(firstLine.indexOf(' ') + 1, firstLine.lastIndexOf(' '));
                    if (requestedFileName.startsWith("/")) {
                        requestedFileName = requestedFileName.substring(1);
                    }
                    requestedFile = new File(SERVER_ROOT_DIRECTORY, requestedFileName);
                    if (requestedFile.exists()) {
                    	LOGGER.info("Sending requested file contents: \"" + requestedFileName + "\".");
                        setResponse(200, requestedFile);
                    } else {
                    	LOGGER.info("Sending not found for requested file: \"" + requestedFileName + "\".");
                        setResponse(404, null);
                    }
                } else {
                	LOGGER.info("Invalid method: " + firstLine);
                    setResponse(405, null);
                }
            } else {
                incomingBuffer.clear();
                channel.read(incomingBuffer, null, IOHandlerBuilder.buildCompletionHandler(this::readCompleted, this::ioFailed));
            }
        }
    }

    private void headerWriteCompleted(Integer result, Void ignored) {
        if (responseHeaderBuffer.hasRemaining()) {
            channel.write(responseHeaderBuffer, null, IOHandlerBuilder.buildCompletionHandler(this::headerWriteCompleted, this::ioFailed));
        } else if (requestedFile != null) {
            try {
                startTransfer();
            } catch (IOException e) {
            	LOGGER.log(Level.SEVERE, "Error starting file contents transference", e);
            }
        }
    }

    private void contentWriteCompleted(Integer result, Void ignored) {
        if (outgoingBuffer.hasRemaining()) {
            channel.write(outgoingBuffer, null, IOHandlerBuilder.buildCompletionHandler(this::contentWriteCompleted, this::ioFailed));
        } else {
            try {
                transfer();
            } catch (IOException e) {
            	LOGGER.log(Level.SEVERE, "Error transferring file contents", e);
            }
        }
    }

    private void ioFailed(Throwable exc, Void ignored) {
    	LOGGER.log(Level.SEVERE, "Error performing IO operation", exc);
        try {
            channel.close();
        } catch (IOException e) {
        	LOGGER.log(Level.SEVERE, "Error closing socket", e);
        }
        if (fileChannel != null) {
            try {
                fileChannel.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error closing file channel", e);
            }
        }
    }

}
