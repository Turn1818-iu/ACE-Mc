package com.anticheatexpert;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiCheatExpertMod implements ModInitializer, DedicatedServerModInitializer {
    private static final String MOD_NAME = "AntiCheatExpert";
    private static final String LOG_FILE = "AntiCheatExpert-detections.log";
    private static final String DATA_FILE = "AntiCheatExpert-data.json";
    private static final String VERSION = "1.5.0";
    private static final String[] SUSPICIOUS_CLASS_NAMES = {
            // Wurst clients
            "net.wurstclient.Wurst",
            "net.wurstclient.WurstClient",
            "net.wurstclient.WurstScreen",
            // Vape
            "vape.Main",
            "vape.utils.Wrapper",
            "dev.l3gacy.vape.Vape",
            "com.vape.wurst.Wurst",
            "me.obsidianbreaker.vape.Vape",
            "com.vape.api.VapeAPI",
            "vape.module.ModuleManager",
            // Cortex
            "me.cortex.client.Cortex",
            "cortex.client.main.Main",
            // LiquidBounce
            "net.liquidbounce.LiquidBounce",
            "net.liquidbounce.client.Client",
            "net.liquidbounce.features.module.Module",
            // Zephyr
            "me.znzr.zephyr.Zephyr",
            "zephyr.core.events.EventBus",
            // Meteor
            "meteordevelopment.meteorclient.MeteorClient",
            "meteordevelopment.meteorclient.systems.modules.Module",
            // HORION
            "Horion.Client",
            "Horion.Logger",
            // Future
            "org.future.Client",
            "net.future.client.Future",
            // Ares Client
            "com.ares.client.AresClient",
            "ares.module.ModuleManager",
            // Alpine Client
            "alpine.client.Alpine",
            "alpine.module.Module",
            // Onix Client
            "onix.client.Onix",
            "onix.features.modules.Module"
    };
    
    private static final Set<String> reported = new HashSet<>();
    private static final Map<String, PlayerData> playerData = new ConcurrentHashMap<>();
    private static final Set<String> whitelist = new HashSet<>();
    private static final Set<String> admins = new HashSet<>();
    private static final Map<String, BanData> bannedPlayers = new ConcurrentHashMap<>();
    private static long detectionInterval = 10;
    private static final long MIN_INTERVAL = 5;
    private static final long MAX_INTERVAL = 500;
    private static Object server;

    @Override
    public void onInitialize() {
        // Client-side initialization
    }

    @Override
    public void onInitializeServer() {
        loadData();
        startBackgroundWatcher();
        log("AntiCheatExpert Server Mod " + VERSION + " initialized for Minecraft 1.16+ Fabric");
    }

    private void startBackgroundWatcher() {
        Thread watcher = new Thread(() -> {
            while (true) {
                try {
                    long startTime = System.currentTimeMillis();
                    detectKnownClients();
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    
                    if (elapsedTime > detectionInterval * 0.8) {
                        detectionInterval = Math.min(detectionInterval + 5, MAX_INTERVAL);
                        log("Server performance reduced. Increasing detection interval to " + detectionInterval + "ms");
                    } else if (elapsedTime < detectionInterval * 0.2 && detectionInterval > MIN_INTERVAL) {
                        detectionInterval = Math.max(detectionInterval - 2, MIN_INTERVAL);
                    }
                    
                    Thread.sleep(Math.max(0, detectionInterval - elapsedTime));
                } catch (Throwable t) {
                    log("Watcher exception: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }, "AntiCheatExpert-Watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void detectKnownClients() {
        for (String className : SUSPICIOUS_CLASS_NAMES) {
            if (isClassLoaded(className)) {
                report("Detected suspicious client class: " + className);
                banPlayer("unknown", "Detected cheat client after login", 1354312);
            }
        }
    }

    private boolean isClassLoaded(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void banPlayer(String playerName, String reason, long years) {
        if (whitelist.contains(playerName)) {
            log("Skipping ban for whitelisted player: " + playerName);
            return;
        }

        Date until = calculateBanUntil(years);
        bannedPlayers.put(playerName, new BanData(reason, until));
        log("Banned " + playerName + " for " + years + " years until " + until + " because: " + reason);
        saveData();
    }

    private void banPlayerWithTime(String playerName, String reason, String timeStr) {
        if (whitelist.contains(playerName)) {
            log("Skipping ban for whitelisted player: " + playerName);
            return;
        }

        long seconds = parseTimeToSeconds(timeStr);
        Date until = new Date(System.currentTimeMillis() + seconds * 1000);
        bannedPlayers.put(playerName, new BanData(reason, until));
        log("Banned " + playerName + " for " + timeStr + " (" + seconds + "s) until " + until + " because: " + reason);
        saveData();
    }

    private long parseTimeToSeconds(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return 3600;
        }

        String numStr = timeStr.replaceAll("[^0-9]", "");
        String unit = timeStr.replaceAll("[0-9]", "").toLowerCase();

        try {
            long num = Long.parseLong(numStr);
            switch (unit) {
                case "s": return num;
                case "m": return num * 60;
                case "h": return num * 3600;
                case "d": return num * 86400;
                case "w": return num * 604800;
                case "y": return num * 31536000;
                default: return 3600;
            }
        } catch (NumberFormatException e) {
            log("Invalid time format: " + timeStr);
            return 3600;
        }
    }

    private void unbanPlayer(String playerName) {
        if (bannedPlayers.remove(playerName) != null) {
            log("Unbanned " + playerName + " via .unban command");
            saveData();
        } else {
            log("Attempted to unban " + playerName + ", but player was not banned");
        }
    }

    private Date calculateBanUntil(long years) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, (int) Math.min(years, Integer.MAX_VALUE));
        return cal.getTime();
    }

    private boolean isBanned(String playerName) {
        BanData banData = bannedPlayers.get(playerName);
        if (banData == null) {
            return false;
        }
        return banData.until.after(new Date());
    }

    private void addToWhitelist(String playerName) {
        whitelist.add(playerName);
        saveData();
        log("Added " + playerName + " to whitelist");
    }

    private void removeFromWhitelist(String playerName) {
        whitelist.remove(playerName);
        saveData();
        log("Removed " + playerName + " from whitelist");
    }

    private void addAdmin(String playerName) {
        admins.add(playerName);
        saveData();
        log("Added " + playerName + " as admin");
    }

    private void removeAdmin(String playerName) {
        admins.remove(playerName);
        saveData();
        log("Removed " + playerName + " from admins");
    }

    private void registerPlayer(String playerName, String pass1, String pass2, Object source) {
        if (!pass1.equals(pass2)) {
            log("Registration failed for " + playerName + ": passwords do not match");
            return;
        }
        PlayerData data = new PlayerData();
        data.password = pass1;
        data.isLoggedIn = true;
        playerData.put(playerName, data);
        log("Registered player: " + playerName);
        saveData();
    }

    private void loginPlayer(String playerName, String password, Object source) {
        PlayerData data = playerData.get(playerName);
        if (data != null && data.password.equals(password)) {
            data.isLoggedIn = true;
            log("Player logged in: " + playerName);
            saveData();
        } else {
            log("Login failed for " + playerName + ": invalid password");
        }
    }

    private void loadData() {
        try {
            File file = new File(DATA_FILE);
            if (file.exists()) {
                log("Data loaded from " + DATA_FILE);
            }
        } catch (Exception e) {
            log("Failed to load data: " + e.getMessage());
        }
    }

    private void saveData() {
        try {
            File file = new File(DATA_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("{\"whitelist\":" + whitelist + ",\"admins\":" + admins + ",\"players\":" + playerData + ",\"bans\":" + bannedPlayers + "}\n");
            }
            log("Data saved to " + DATA_FILE);
        } catch (Exception e) {
            log("Failed to save data: " + e.getMessage());
        }
    }

    private synchronized void report(String message) {
        if (!reported.add(message)) {
            return;
        }
        log(message);
    }

    private synchronized void log(String message) {
        try {
            File file = new File(LOG_FILE);
            if (!file.exists()) {
                file.createNewFile();
            }
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + " " + message + "\n");
            }
        } catch (IOException ignored) {
        }
    }

    private static class PlayerData {
        String password;
        boolean isLoggedIn = false;
    }

    private static class BanData {
        String reason;
        Date until;

        BanData(String reason, Date until) {
            this.reason = reason;
            this.until = until;
        }

        @Override
        public String toString() {
            return "BanData{reason='" + reason + "', until=" + until + "}";
        }
    }
}
