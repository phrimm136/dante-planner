package org.danteplanner.backend.support;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class PongRedis implements AutoCloseable {

    private final ServerSocket listener;
    private final List<Socket> clients = new CopyOnWriteArrayList<>();
    private final AtomicInteger accepted = new AtomicInteger();

    public PongRedis() throws IOException {
        this.listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::accept, "pong-redis-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public String host() {
        return listener.getInetAddress().getHostAddress();
    }

    public int port() {
        return listener.getLocalPort();
    }

    public int acceptedConnections() {
        return accepted.get();
    }

    public void dropClients() throws IOException {
        for (Socket client : clients) {
            client.close();
        }
        clients.clear();
    }

    @Override
    public void close() throws IOException {
        listener.close();
        dropClients();
    }

    private void accept() {
        try {
            while (!listener.isClosed()) {
                Socket client = listener.accept();
                accepted.incrementAndGet();
                clients.add(client);
                Thread handler = new Thread(() -> serve(client), "pong-redis-client");
                handler.setDaemon(true);
                handler.start();
            }
        } catch (IOException closed) {
            return;
        }
    }

    private static void serve(Socket client) {
        try (InputStream in = new BufferedInputStream(client.getInputStream());
                OutputStream out = client.getOutputStream()) {
            String command = readCommandName(in);
            while (command != null) {
                out.write(replyTo(command).getBytes(StandardCharsets.UTF_8));
                out.flush();
                command = readCommandName(in);
            }
        } catch (IOException closed) {
            return;
        }
    }

    private static String replyTo(String command) {
        return switch (command.toUpperCase(Locale.ROOT)) {
            case "HELLO" -> "-ERR unknown command 'HELLO'\r\n";
            case "PING" -> "+PONG\r\n";
            default -> "+OK\r\n";
        };
    }

    private static String readCommandName(InputStream in) throws IOException {
        String header = readLine(in);
        if (header == null) {
            return null;
        }
        int count = Integer.parseInt(header.substring(1));
        String name = null;
        for (int i = 0; i < count; i++) {
            int length = Integer.parseInt(readLine(in).substring(1));
            byte[] bytes = in.readNBytes(length + 2);
            if (i == 0) {
                name = new String(bytes, 0, length, StandardCharsets.UTF_8);
            }
        }
        return name;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        int next = in.read();
        while (next != -1) {
            if (previous == '\r' && next == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
            }
            line.write(next);
            previous = next;
            next = in.read();
        }
        return null;
    }
}
