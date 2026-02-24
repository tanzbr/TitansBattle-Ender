package me.roinujnosde.titansbattle.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.bukkit.plugin.Plugin;

public class GameLogger {

    private File logFile;
    private PrintWriter writer;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public GameLogger(String gameName, Plugin plugin) {
        try {
            File logsFolder = new File(plugin.getDataFolder(), "logs");
            if (!logsFolder.exists()) {
                logsFolder.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String fileName = gameName.replaceAll("[^a-zA-Z0-9_-]", "") + "_" + timestamp + ".log";

            this.logFile = new File(logsFolder, fileName);
            this.writer = new PrintWriter(new FileWriter(logFile, true));
            logLine("==========================================");
            logLine(" EVENT LOG STARTED: " + gameName.toUpperCase());
            logLine(" TIME: " + timestamp);
            logLine("==========================================");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to initialize GameLogger for " + gameName + ": " + e.getMessage());
        }
    }

    public void logLine(String message) {
        if (writer != null) {
            String time = timeFormat.format(new Date());
            writer.println("[" + time + "] " + message);
            writer.flush();
        }
    }

    public void close() {
        if (writer != null) {
            logLine("==========================================");
            logLine(" EVENT LOG ENDED ");
            logLine("==========================================");
            writer.close();
            writer = null;
        }
    }

    public File getLogFile() {
        return logFile;
    }
}
