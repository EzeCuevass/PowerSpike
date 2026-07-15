package com.cuevas.powerspike.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class LcuLockfile {

    private int port;
    private String token;
    private boolean connected;
    private long lastCheck;

    @PostConstruct
    public void init() {
        check();
    }

    public void check() {
        if (System.currentTimeMillis() - lastCheck < 2000) return;
        lastCheck = System.currentTimeMillis();

        try {
            Process process = Runtime.getRuntime().exec(
                "wmic process where name='LeagueClientUx.exe' get commandline"
            );
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            String line;
            boolean found = false;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("CommandLine")) continue;
                if (line.contains("--app-port=")) {
                    port = Integer.parseInt(extractValue(line, "--app-port="));
                    token = extractValue(line, "--remoting-auth-token=");
                    found = true;
                    break;
                }
            }
            reader.close();
            process.waitFor();

            if (found) {
                if (!connected) {
                    System.out.println(">>> LCU detectado en puerto " + port);
                }
                connected = true;
            } else {
                connected = false;
            }

        } catch (Exception e) {
            connected = false;
        }
    }

    private String extractValue(String cmd, String param) {
        int start = cmd.indexOf(param) + param.length();
        int end = cmd.indexOf(' ', start);
        if (end == -1) end = cmd.length();
        String value = cmd.substring(start, end);
        if (value.startsWith("\"")) value = value.substring(1);
        if (value.endsWith("\"")) value = value.substring(0, value.length() - 1);
        return value;
    }

    public boolean isConnected() { return connected; }
    public int getPort() { return port; }
    public String getToken() { return token; }

    public String getAuth() {
        if (!connected || token == null) return "";
        String creds = "riot:" + token;
        return "Basic " + java.util.Base64.getEncoder().encodeToString(creds.getBytes());
    }
}
