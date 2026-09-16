package carpet.script.utils;

import carpet.script.CarpetScriptHost;
import carpet.script.CarpetScriptServer;
import carpet.script.Expression;
import carpet.script.exception.InternalExpressionException;
import carpet.script.external.Carpet;
import carpet.script.external.Vanilla;
import carpet.script.value.MapValue;
import carpet.script.value.StringValue;
import carpet.script.value.Value;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import org.jspecify.annotations.Nullable;

public class AppStoreManager
{

    private static StoreNode APP_STORE_ROOT = StoreNode.folder(null, "");
    private static long storeErrorTime = 0;

    private static String scarpetRepoLink = "https://api.github.com/repos/gnembon/scarpet/contents/programs/";

    public static void setScarpetRepoLink(@Nullable String link)
    {
        APP_STORE_ROOT = AppStoreManager.StoreNode.folder(null, "");
        scarpetRepoLink = link;
    }

    public static boolean enabled()
    {
        return scarpetRepoLink != null;
    }

    private record AppInfo(String name, String url, StoreNode source)
    {
    }

    public static class StoreNode
    {
        public String name;
        @Nullable
        public StoreNode parent;
        public Map<String, StoreNode> children;
        public boolean sealed;
        public String value;

        public static StoreNode folder(@Nullable StoreNode parent, String name)
        {
            StoreNode node = new StoreNode(parent, name);
            node.children = new HashMap<>();
            node.value = null;
            node.sealed = false;
            return node;
        }

        public static StoreNode scriptFile(StoreNode parent, String name, String value)
        {
            StoreNode node = new StoreNode(parent, name);
            node.children = null;
            node.value = value;
            node.sealed = true;
            return node;
        }

        public boolean isLeaf()
        {
            return value != null;
        }

        public String pathElement()
        {
            return name + (isLeaf() ? "" : "/");
        }

        public String getPath()
        {
            return createPrePath().toString();
        }

        private StringBuilder createPrePath()
        {
            return this == APP_STORE_ROOT ? new StringBuilder() : parent.createPrePath().append(pathElement());
        }

        private StoreNode(@Nullable StoreNode parent, String name)
        {
            this.parent = parent;
            this.name = name;
            this.sealed = false;
        }

        public synchronized void fillChildren(@Nullable CommandSourceStack source) throws IOException
        {
            if (sealed)
            {
                return;
            }
            if (!enabled())
            {
                throw new IOException("Accessing scarpet app repo is disabled");
            }
            if (System.currentTimeMillis() - storeErrorTime < 30000)
            {
                if (source != null)
                {
                    Carpet.Messenger_message(source, "di App store is not available yet");
                }
                return;
            }

            String queryPath = scarpetRepoLink + getPath();
            String response;
            try
            {
                response = IOUtils.toString(new URL(queryPath), StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                if (source != null)
                {
                    Carpet.Messenger_message(source, "r Scarpet app store is not available at the moment, try in a minute");
                }
                storeErrorTime = System.currentTimeMillis();

                throw new IOException("Problems fetching " + queryPath, e);
            }
            JsonArray files = JsonParser.parseString(response).getAsJsonArray();
            for (JsonElement je : files)
            {
                JsonObject jo = je.getAsJsonObject();
                String elementName = jo.get("name").getAsString();
                if (jo.get("type").getAsString().equals("dir"))
                {
                    children.put(elementName, folder(this, elementName));
                }
                else
                {
                    String url = jo.get("download_url").getAsString();
                    children.put(elementName, scriptFile(this, elementName, url));
                }
            }
            sealed = true;
        }

        public boolean cannotContinueFor(String pathElement, CommandSourceStack source) throws IOException
        {
            if (isLeaf())
            {
                return true;
            }
            fillChildren(source);
            return !children.containsKey(pathElement);
        }

        public List<String> createPathSuggestions(CommandSourceStack source) throws IOException
        {
            if (isLeaf())
            {
                return name.endsWith(".sc") ? Collections.singletonList(getPath()) : Collections.emptyList();
            }
            fillChildren(source);
            String prefix = getPath();
            return children.values().stream().
                    filter(n -> (!n.isLeaf() || n.name.endsWith(".sc"))).
                    map(s -> prefix + s.pathElement().replaceAll("/$", "")).toList();
        }

        public StoreNode drillDown(String pathElement, CommandSourceStack source) throws IOException
        {
            if (isLeaf())
            {
                throw new IOException(pathElement + " is not a folder");
            }
            fillChildren(source);
            if (!children.containsKey(pathElement))
            {
                throw new IOException("Folder " + pathElement + " is not present");
            }
            return children.get(pathElement);
        }

        public String getValue(String file, CommandSourceStack source) throws IOException
        {
            StoreNode leaf = drillDown(file, source);
            if (!leaf.isLeaf())
            {
                throw new IOException(file + " is not a file");
            }
            return leaf.value;
        }
    }

    public static List<String> suggestionsFromPath(String currentPath, CommandSourceStack source) throws IOException
    {
        String[] path = currentPath.split("/");
        StoreNode appKiosk = APP_STORE_ROOT;
        for (String pathElement : path)
        {
            if (appKiosk.cannotContinueFor(pathElement, source))
            {
                break;
            }
            appKiosk = appKiosk.children.get(pathElement);
        }
        List<String> filteredSuggestions = appKiosk.createPathSuggestions(source).stream().filter(s -> s.startsWith(currentPath)).toList();
        if (filteredSuggestions.size() == 1 && !appKiosk.isLeaf())
        {
            return suggestionsFromPath(filteredSuggestions.get(0), source);
        }
        return filteredSuggestions;
    }

    public static int downloadScript(CommandSourceStack source, String path)
    {
        AppInfo nodeInfo = getFileNode(path, source);
        return downloadScript(source, path, nodeInfo, false);
    }

    private static int downloadScript(CommandSourceStack source, String path, AppInfo nodeInfo, boolean useTrash)
    {
        String code;
        try
        {
            code = IOUtils.toString(new URL(nodeInfo.url()), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new CommandRuntimeException(Carpet.Messenger_compose("rb Failed to obtain app file content: " + e.getMessage()));
        }
        if (!saveScriptToFile(source, path, nodeInfo.name(), code, useTrash))
        {
            return 0;
        }
        return Vanilla.MinecraftServer_getScriptServer(source.getServer()).addScriptHost(source, nodeInfo.name().replaceFirst("\\.sc$", ""), null, true, false, false, nodeInfo.source(), Expression.LoadOverride.DEFAULT);
    }

    public static AppInfo getFileNode(String appPath, CommandSourceStack source)
    {
        return getFileNodeFrom(APP_STORE_ROOT, appPath, source);
    }

    public static AppInfo getFileNodeFrom(StoreNode start, String appPath, CommandSourceStack source)
    {
        String[] path = appPath.split("/");
        StoreNode appKiosk = start;
        try
        {
            for (String pathElement : Arrays.copyOfRange(path, 0, path.length - 1))
            {
                appKiosk = appKiosk.drillDown(pathElement, source);
            }
            String appName = path[path.length - 1];
            appKiosk.getValue(appName, source);
            return new AppInfo(appName, appKiosk.getValue(appName, source), appKiosk);
        }
        catch (IOException e)
        {
            throw new CommandRuntimeException(Carpet.Messenger_compose("rb '" + appPath + "' is not a valid path to a scarpet app: " + e.getMessage()));
        }
    }

    public static boolean saveScriptToFile(CommandSourceStack source, String path, String appFileName, String code, boolean useTrash)
    {
        Path scriptLocation = source.getServer().getWorldPath(LevelResource.ROOT).resolve("scripts").toAbsolutePath().resolve(appFileName);
        try
        {
            Files.createDirectories(scriptLocation.getParent());
            if (Files.exists(scriptLocation))
            {
                if (useTrash)
                {
                    Files.createDirectories(scriptLocation.getParent().resolve("trash"));
                    Path trashPath = scriptLocation.getParent().resolve("trash").resolve(path);
                    int i = 0;
                    while (Files.exists(trashPath))
                    {
                        String[] nameAndExtension = appFileName.split("\\.");
                        String newFileName = String.format(nameAndExtension[0] + "%02d." + nameAndExtension[1], i);
                        trashPath = trashPath.getParent().resolve(newFileName);
                        i++;
                    }
                    Files.move(scriptLocation, trashPath);
                }
                Carpet.Messenger_message(source, String.format("gi Note: replaced existing app '%s'" + (useTrash ? " (old moved to /trash folder)" : ""), appFileName));
            }
            BufferedWriter writer = Files.newBufferedWriter(scriptLocation);
            writer.write(code);
            writer.close();
        }
        catch (IOException e)
        {
            Carpet.Messenger_message(source, "r Error while downloading app: " + e);
            CarpetScriptServer.LOG.warn("Error while downloading app", e);
            return false;
        }
        return true;
    }

    public static void writeUrlToFile(String url, Path destination) throws IOException
    {
        try (InputStream in = new URL(url).openStream())
        {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String getFullContentUrl(String original, StoreNode storeSource, CommandSourceStack source)
    {
        if (original.matches("^https?://.*$"))
        {
            return original;
        }
        if (original.charAt(0) == '/')
        {
            return getFileNode(original.substring(1), source).url();
        }
        return getFileNodeFrom(storeSource, original, source).url();
    }

    public static void addResource(CarpetScriptHost carpetScriptHost, StoreNode storeSource, Value resource)
    {
        if (!(resource instanceof final MapValue map))
        {
            throw new InternalExpressionException("This is not a valid resource map: " + resource.getString());
        }
        Map<String, Value> resourceMap = map.getMap().entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getString(), Map.Entry::getValue));
        if (!resourceMap.containsKey("source"))
        {
            throw new InternalExpressionException("Missing 'source' field in resource descriptor: " + resource.getString());
        }
        String source = resourceMap.get("source").getString();
        String contentUrl = getFullContentUrl(source, storeSource, carpetScriptHost.responsibleSource);
        String target = resourceMap.computeIfAbsent("target", k -> new StringValue(contentUrl.substring(contentUrl.lastIndexOf('/') + 1))).getString();
        boolean shared = resourceMap.getOrDefault("shared", Value.FALSE).getBoolean();

        if (!carpetScriptHost.applyActionForResource(target, shared, p -> {
            try
            {
                writeUrlToFile(contentUrl, p);
            }
            catch (IOException e)
            {
                throw new InternalExpressionException("Unable to write resource " + target + ": " + e);
            }
        }))
        {
            throw new InternalExpressionException("Unable to write resource " + target);
        }
        CarpetScriptServer.LOG.info("Downloaded resource " + target + " from " + contentUrl);
    }

    @Nullable
    private static StoreNode getNewStoreNode(CommandSourceStack commandSource, StoreNode originalSource, String sourceString, String contentUrl)
    {
        StoreNode next = originalSource;
        if (sourceString == contentUrl)
        {
            return null;
        }
        if (sourceString.charAt(0) == '/')
        {
            next = APP_STORE_ROOT;
            sourceString = sourceString.substring(1);
        }
        String[] dirs = sourceString.split("/");
        try
        {
            for (int i = 0; i < dirs.length - 1; i++)
            {
                next = next.drillDown(dirs[i], commandSource);
            }
        }
        catch (IOException e)
        {
            return null;
        }
        return next;
    }

    public static void addLibrary(CarpetScriptHost carpetScriptHost, StoreNode storeSource, Value library)
    {
        if (!(library instanceof final MapValue map))
        {
            throw new InternalExpressionException("This is not a valid library map: " + library.getString());
        }
        Map<String, String> libraryMap = map.getMap().entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getString(), e -> e.getValue().getString()));
        String source = libraryMap.get("source");
        String contentUrl = getFullContentUrl(source, storeSource, carpetScriptHost.responsibleSource);
        String target = libraryMap.computeIfAbsent("target", k -> contentUrl.substring(contentUrl.lastIndexOf('/') + 1));
        if (!(contentUrl.endsWith(".sc") || contentUrl.endsWith(".scl")))
        {
            throw new InternalExpressionException("App resource type must download a scarpet app or library.");
        }
        if (target.indexOf('/') != -1)
        {
            throw new InternalExpressionException("App resource tried to leave script reserved space");
        }
        try
        {
            downloadScript(carpetScriptHost.responsibleSource, target, new AppInfo(target, contentUrl, getNewStoreNode(carpetScriptHost.responsibleSource, storeSource, source, contentUrl)), true);
        }
        catch (CommandRuntimeException e)
        {
            throw new InternalExpressionException("Error when installing app dependencies: " + e);
        }
        CarpetScriptServer.LOG.info("Downloaded app " + target + " from " + contentUrl);
    }

    public static class CommandRuntimeException extends RuntimeException {
        private final Component message;

        public CommandRuntimeException(Component message) {
            super(message.getString(), null, false, false);
            this.message = message;
        }

        public Component getComponent() {
            return message;
        }
    }
}
