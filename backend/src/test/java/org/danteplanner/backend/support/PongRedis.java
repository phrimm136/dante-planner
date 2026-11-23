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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class PongRedis implements AutoCloseable {

    private static final String PING = "PING";

    private final ServerSocket listener;
    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final AtomicInteger accepted = new AtomicInteger();
    private final AtomicInteger answeredPings = new AtomicInteger();
    private final AtomicInteger closedByClients = new AtomicInteger();
    private final AtomicInteger blackholedClosedByClients = new AtomicInteger();
    private volatile CompletableFuture<Void> newConnectionsServed = CompletableFuture.completedFuture(null);
    private final AtomicBoolean nextPingReplyDelayed = new AtomicBoolean();
    private volatile CompletableFuture<Void> delayedPingReplySent = CompletableFuture.completedFuture(null);
    private final AtomicBoolean nextPingRefused = new AtomicBoolean();

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

    public int answeredPings() {
        return answeredPings.get();
    }

    public int connectionsClosedByClients() {
        return closedByClients.get();
    }

    public int blackholedConnectionsClosedByClients() {
        return blackholedClosedByClients.get();
    }

    public void holdNewConnections() {
        newConnectionsServed = new CompletableFuture<>();
    }

    public void releaseNewConnections() {
        newConnectionsServed.complete(null);
    }

    public void delayNextPingReply() {
        delayedPingReplySent = new CompletableFuture<>();
        nextPingReplyDelayed.set(true);
    }

    public void refuseNextPing() {
        nextPingRefused.set(true);
    }

    public void releaseDelayedPingReply() {
        delayedPingReplySent.complete(null);
    }

    public void blackholeEstablished() {
        for (Client client : clients) {
            client.blackholed = true;
        }
    }

    public void dropClients() throws IOException {
        for (Client client : clients) {
            client.droppedByServer = true;
            client.socket.close();
        }
        clients.clear();
    }

    @Override
    public void close() throws IOException {
        listener.close();
        releaseNewConnections();
        releaseDelayedPingReply();
        dropClients();
    }

    private void accept() {
        try {
            while (!listener.isClosed()) {
                recordThenServe(new Client(listener.accept()));
            }
        } catch (IOException closed) {
            return;
        }
    }

    private void recordThenServe(Client client) {
        accepted.incrementAndGet();
        clients.add(client);
        CompletableFuture<Void> served = newConnectionsServed;
        Thread handler = new Thread(() -> serve(client, served), "pong-redis-client");
        handler.setDaemon(true);
        handler.start();
    }

    private void serve(Client client, CompletableFuture<Void> served) {
        served.join();
        exchange(client);
        if (!client.droppedByServer) {
            closedByClients.incrementAndGet();
            if (client.blackholed) {
                blackholedClosedByClients.incrementAndGet();
            }
        }
    }

    private void exchange(Client client) {
        try (InputStream in = new BufferedInputStream(client.socket.getInputStream());
                OutputStream out = client.socket.getOutputStream()) {
            String command = readCommandName(in);
            while (command != null) {
                awaitDelayedReply(command);
                if (!client.blackholed) {
                    recordThenReply(out, command);
                }
                command = readCommandName(in);
            }
        } catch (IOException closed) {
            return;
        }
    }

    private void awaitDelayedReply(String command) {
        if (PING.equalsIgnoreCase(command) && nextPingReplyDelayed.compareAndSet(true, false)) {
            delayedPingReplySent.join();
        }
    }

    private void recordThenReply(OutputStream out, String command) throws IOException {
        if (PING.equalsIgnoreCase(command)) {
            answeredPings.incrementAndGet();
        }
        boolean refused = PING.equalsIgnoreCase(command) && nextPingRefused.compareAndSet(true, false);
        out.write((refused ? "-ERR ping refused\r\n" : replyTo(command)).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String replyTo(String command) {
        return switch (command.toUpperCase(Locale.ROOT)) {
            case "HELLO" -> "-ERR unknown command 'HELLO'\r\n";
            case PING -> "+PONG\r\n";
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

    private static final class Client {

        private final Socket socket;
        private volatile boolean blackholed;
        private volatile boolean droppedByServer;

        private Client(Socket socket) {
            this.socket = socket;
        }
    }
}
