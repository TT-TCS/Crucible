package com.tt_tcs.crucible.util;

import com.tt_tcs.crucible.CrucibleMain;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {

    private static final String API_LATEST = "https://api.github.com/repos/TT-TCS/Crucible/releases/latest";
    private static final String RELEASES_PAGE = "https://github.com/TT-TCS/Crucible/releases";

    private volatile boolean checked = false;
    private volatile String latestTag = null;
    private volatile String latestUrl = null;

    public void checkAsync(JavaPlugin plugin) {
        if (checked) return;

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
                    checked = true;
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                String json = sb.toString();

                // Extremely small JSON parse: grab "tag_name" and "html_url"
                latestTag = match(json, "\"tag_name\"\s*:\s*\"([^\"]+)\"");
                latestUrl = match(json, "\"html_url\"\s*:\s*\"([^\"]+)\"");

                checked = true;
            } catch (Exception ignored) {
                checked = true;
            }
        });
    }

    private static String match(String src, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(src);
        return m.find() ? m.group(1) : null;
    }

    public boolean hasUpdate(JavaPlugin plugin) {
        if (!checked) return false;
        if (latestTag == null) return false;

        String current = plugin.getDescription().getVersion();
        // If versions match exactly, no update. Otherwise, notify admins.
        return !latestTag.equalsIgnoreCase(current) && !("v" + current).equalsIgnoreCase(latestTag);
    }

    public String getLatestTag() {
        return latestTag;
    }

    public String getUpdateUrl() {
        return latestUrl != null ? latestUrl : RELEASES_PAGE;
    }

    public String getReleasesPage() {
        return RELEASES_PAGE;
    }
}
