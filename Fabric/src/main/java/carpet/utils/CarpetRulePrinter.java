package carpet.utils;

import carpet.CarpetServer;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.System;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CarpetRulePrinter implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {

        String[] args = Arrays.stream(FabricLoader.getInstance().getLaunchArguments(true)).filter(opt -> !opt.equals("--")).toArray(String[]::new);

        OptionParser parser = new OptionParser();
        OptionSpec<Void> shouldDump = parser.accepts("carpetDumpRules");
        OptionSpec<Path> pathSpec = parser.accepts("dumpPath").withRequiredArg().withValuesConvertedBy(new PathConverter());
        OptionSpec<String> filterSpec = parser.accepts("dumpFilter").withRequiredArg();
        parser.allowsUnrecognizedOptions();
        OptionSet options = parser.parse(args);

        if (!options.has(shouldDump)) return;

        Logger logger = LoggerFactory.getLogger("Carpet Rule Printer");
        logger.info("Starting in rule dump mode...");

        PrintStream outputStream;
        try {
            Path path = options.valueOf(pathSpec).toAbsolutePath();
            logger.info("Printing rules to: " + path);
            Files.createDirectories(path.getParent());
            outputStream = new PrintStream(Files.newOutputStream(path));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Translations.updateLanguage();
        String filter = options.valueOf(filterSpec);
        if (filter != null) logger.info("Applying category filter: " + filter);
        CarpetServer.settingsManager.dumpAllRulesToStream(outputStream, filter);
        outputStream.close();
        logger.info("Rules have been printed");
        System.exit(0);
    }
}
