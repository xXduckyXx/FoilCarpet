package carpet.folia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlatformCompat {

    private static JavaPlugin plugin;
    private static final Map<String, String> MOD_VERSIONS = new LinkedHashMap<>();

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        MOD_VERSIONS.put("carpet", "1.4.194");
        MOD_VERSIONS.put("minecraft", "1.21.11");
        MOD_VERSIONS.put("folia", Bukkit.getBukkitVersion());
    }

    public static boolean isDevelopmentEnvironment() {
        return plugin != null && plugin.getConfig().getBoolean("debug", false);
    }

    public static boolean isServer() {
        return true;
    }

    public static Path getConfigDir() {
        if (plugin == null) return Path.of("config");
        Path configDir = plugin.getDataFolder().toPath().getParent().resolve("carpet");
        try {
            Files.createDirectories(configDir);
        } catch (IOException ignored) {
        }
        return configDir;
    }

    public static String getCarpetVersion() {
        return MOD_VERSIONS.getOrDefault("carpet", "1.4.194");
    }

    public static int[] getMinecraftVersionComponents() {
        return new int[]{1, 21, 11};
    }

    public static String getMinecraftVersionString() {
        return "1.21.11";
    }

    public static boolean isModLoaded(String modId) {
        return MOD_VERSIONS.containsKey(modId);
    }

    public static String getModVersion(String modId) {
        return MOD_VERSIONS.get(modId);
    }

    public static String[] getLaunchArguments() {
        return new String[0];
    }

    public static int versionCompare(String v1, String v2) {
        String[] a1 = v1.split("\\.");
        String[] a2 = v2.split("\\.");
        int len = Math.max(a1.length, a2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < a1.length ? parseVersionPart(a1[i]) : 0;
            int n2 = i < a2.length ? parseVersionPart(a2[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }

    private static int parseVersionPart(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) sb.append(c);
            else break;
        }
        return sb.isEmpty() ? 0 : Integer.parseInt(sb.toString());
    }

    public static boolean meetsVersionPredicate(String actualVersion, String predicate) {
        if (predicate.isEmpty()) return true;
        predicate = predicate.replace(" ", "");
        if (predicate.startsWith(">=")) return versionCompare(actualVersion, predicate.substring(2)) >= 0;
        if (predicate.startsWith(">")) return versionCompare(actualVersion, predicate.substring(1)) > 0;
        if (predicate.startsWith("<=")) return versionCompare(actualVersion, predicate.substring(2)) <= 0;
        if (predicate.startsWith("<")) return versionCompare(actualVersion, predicate.substring(1)) < 0;
        if (predicate.startsWith("==")) return versionCompare(actualVersion, predicate.substring(2)) == 0;
        return versionCompare(actualVersion, predicate) == 0;
    }
}
