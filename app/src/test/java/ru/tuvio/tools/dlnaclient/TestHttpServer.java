package ru.tuvio.tools.dlnaclient;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/** Minimal loopback HTTP fixture, compatible with Android's Java test compilation. */
final class TestHttpServer implements AutoCloseable {
    private final ServerSocket server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
    private final Thread worker;
    private volatile Throwable failure;
    TestHttpServer(Function<String, String> response) throws IOException {
        worker = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket client = server.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                    String line; int length = 0;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))
                            length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                    }
                    char[] body = new char[length]; int offset = 0;
                    while (offset < length) {
                        int n = reader.read(body, offset, length - offset);
                        if (n < 0) throw new EOFException(); offset += n;
                    }
                    byte[] bytes = response.apply(new String(body)).getBytes(StandardCharsets.UTF_8);
                    OutputStream out = client.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/xml; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.write(bytes); out.flush();
                } catch (Throwable e) { if (!server.isClosed()) failure = e; }
            }
        }, "test-dlna-http");
        worker.setDaemon(true); worker.start();
    }
    int port() { return server.getLocalPort(); }
    @Override public void close() throws Exception {
        server.close(); worker.join(2000);
        if (failure != null) throw new AssertionError("Fixture failed", failure);
    }
}
