package carpet.script;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import org.apache.commons.io.IOUtils;

import carpet.script.argument.FileArgument;
import net.minecraft.nbt.Tag;

public record Module(String name, String code, boolean library)
{
    public Module
    {
        Objects.requireNonNull(name);
        Objects.requireNonNull(code);
    }

    public static Module fromPath(Path path)
    {
        boolean library = path.getFileName().toString().endsWith(".scl");
        try
        {
            String name = path.getFileName().toString().replaceFirst("\\.scl?", "").toLowerCase(Locale.ROOT);
            String code = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            return new Module(name, code, library);
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("Failed to load scarpet module", e);
        }
    }

    public static Module carpetNative(String scriptName, boolean isLibrary)
    {
        return fromJarPath("assets/carpet/scripts/", scriptName, isLibrary);
    }

    public static Module fromJarPath(String path, String scriptName, boolean isLibrary)
    {
        return fromJarPathWithCustomName(path + scriptName + (isLibrary ? ".scl" : ".sc"), scriptName, isLibrary);
    }

    public static Module fromJarPathWithCustomName(String fullPath, String customName, boolean isLibrary)
    {
        try
        {
            String name = customName.toLowerCase(Locale.ROOT);
            String code = IOUtils.toString(
                    Module.class.getClassLoader().getResourceAsStream(fullPath),
                    StandardCharsets.UTF_8
            );
            return new Module(name, code, isLibrary);
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("Failed to load bundled module", e);
        }
    }

    public static Tag getData(Module module, ScriptServer scriptServer)
    {
        Path dataFile = resolveResource(module, scriptServer);
        if (dataFile == null || !Files.exists(dataFile) || !(Files.isRegularFile(dataFile)))
        {
            return null;
        }
        synchronized (FileArgument.writeIOSync)
        {
            return FileArgument.readTag(dataFile);
        }
    }

    public static void saveData(Module module, Tag globalState, ScriptServer scriptServer)
    {
        Path dataFile = resolveResource(module, scriptServer);
        if (dataFile == null)
        {
            return;
        }
        if (!Files.exists(dataFile.getParent()))
        {
            try
            {
                Files.createDirectories(dataFile.getParent());
            }
            catch (IOException e)
            {
                throw new IllegalStateException(e);
            }
        }
        synchronized (FileArgument.writeIOSync)
        {
            FileArgument.writeTagDisk(globalState, dataFile, false);
        }
    }

    private static Path resolveResource(Module module, ScriptServer scriptServer)
    {
        return module == null ? null : scriptServer.resolveResource(module.name() + ".data.nbt");
    }
}
