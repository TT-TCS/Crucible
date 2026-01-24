package com.tt_tcs.crucible.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {

    private static final String API_LATEST = "https://api.github.com/repos/TT-TCS/Crucible/releases/latest";
    private static final String RELEASES_PAGE = "https://github.com/TT-TCS/Crucible/releases";

    private final AtomicBoolean checked = new AtomicBoolean(false);
    private final AtomicBoolean notified = new AtomicBoolean(false);

    private volatile String latestTag = null;
    private volatile String latestUrl = null;

    public void checkAsync(JavaPlugin plugin) {
        if (checked.get()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(API_LATEST).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "Crucible/" + plugin.getDescription().getVersion());

                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    checked.set(true);
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }

                String json = sb.toString();
                latestTag = match(json, "\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
                latestUrl = match(json, "\"html_url\"\\s*:\\s*\"([^\"]+)\"");
            } catch (Exception ignored) {
                // silent fail – never spam console or chat
            } finally {
                checked.set(true);
            }
        });
    }

    public boolean hasUpdate(JavaPlugin plugin) {
        int[] current = parseSemver(normalize(plugin.getDescription().getVersion()));
        int[] latest = parseSemver(normalize(latestTag));

        if (current == null || latest == null) return false;

        if (latest[0] != current[0]) return latest[0] > current[0];
        if (latest[1] != current[1]) return latest[1] > current[1];
        return latest[2] > current[2];
    }

    private static String match(String src, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(src);
        return m.find() ? m.group(1) : null;
    }

    private static String normalize(String v) {
        if (v == null) return null;
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        return v;
    }

    private static int[] parseSemver(String v) {
        if (v == null) return null;
        String[] parts = v.split("\\.");
        if (parts.length < 3) return null;

        try {
            return new int[] {
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getLatestTag() {
        return latestTag;
    }

    public String getUpdateUrl() {
        return latestUrl != null ? latestUrl : RELEASES_PAGE;
    }
}
