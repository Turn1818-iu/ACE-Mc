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
    private static final String VERSION = "2.0.0";
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
    
    // 新增检测数据
    private static final Map<String, PlayerMovementData> movementData = new ConcurrentHashMap<>();
    private static final Map<String, PlayerInputData> inputData = new ConcurrentHashMap<>();
    private static final Set<String> badWords = new HashSet<>(Arrays.asList(
        "fuck", "shit", "damn", "bitch", "asshole", "bastard", "crap", "piss", "dick", "cock", "pussy", "cunt"
    ));

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
                    
                    // 新增检测功能
                    for (String playerName : playerData.keySet()) {
                        PlayerData data = playerData.get(playerName);
                        if (data.isLoggedIn) {
                            detectAimbot(playerName);
                            detectAutoAFK(playerName);
                            detectTeleport(playerName);
                        }
                    }
                    
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
                // 移除立即封禁，改为在登录后检测
            }
        }
    }

    private boolean isPasswordValid(String password) {
        if (password.length() < 8) {
            return false;
        }
        String lowerPassword = password.toLowerCase();
        for (String badWord : badWords) {
            if (lowerPassword.contains(badWord)) {
                return false;
            }
        }
        return true;
    }

    private void startPasswordCheckTimer(String playerName) {
        Thread timer = new Thread(() -> {
            try {
                Thread.sleep(60000); // 1分钟
                PlayerData data = playerData.get(playerName);
                if (data != null && !data.passwordChecked) {
                    if (!isPasswordValid(data.password)) {
                        banPlayerWithTime(playerName, "Password contains inappropriate content", "10d");
                    }
                    data.passwordChecked = true;
                    saveData();
                }
            } catch (InterruptedException ignored) {}
        }, "PasswordCheck-" + playerName);
        timer.setDaemon(true);
        timer.start();
    }

    private void detectAimbot(String playerName) {
        // 自瞄检测逻辑 - 简化实现
        PlayerInputData input = inputData.get(playerName);
        if (input != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - input.lastMouseMoveTime < 50 && input.mouseMoveCount > 10) {
                banPlayerWithTime(playerName, "Detected aimbot", "30d");
            }
        }
    }

    private void detectAutoAFK(String playerName) {
        PlayerInputData input = inputData.get(playerName);
        if (input != null) {
            long currentTime = System.currentTimeMillis();
            if (input.isAfk && (currentTime - input.afkStartTime) > 300000) { // 5分钟AFK
                if (input.keyPressCount > 0 || input.mouseMoveCount > 0) {
                    banPlayerWithTime(playerName, "Detected AutoAFK", "1w");
                }
            }
        }
    }

    private void detectTeleport(String playerName) {
        PlayerMovementData movement = movementData.get(playerName);
        if (movement != null) {
            // 简化的TP检测 - 检查位置跳跃
            if (!movement.positionHistory.isEmpty()) {
                double lastPos = movement.positionHistory.get(movement.positionHistory.size() - 1);
                double currentPos = movement.lastX + movement.lastY + movement.lastZ;
                if (Math.abs(currentPos - lastPos) > 10) { // 假设瞬移距离阈值
                    banPlayerWithTime(playerName, "Detected ClickTP/TP Aura", "1y");
                }
            }
        }
    }

    private void updateMovementData(String playerName, double x, double y, double z) {
        PlayerMovementData data = movementData.computeIfAbsent(playerName, k -> new PlayerMovementData());
        data.lastX = x;
        data.lastY = y;
        data.lastZ = z;
        data.lastUpdateTime = System.currentTimeMillis();
        data.positionHistory.add(x + y + z);
        if (data.positionHistory.size() > 10) {
            data.positionHistory.remove(0);
        }
    }

    private void updateInputData(String playerName, boolean isMouseMove, boolean isKeyPress) {
        PlayerInputData data = inputData.computeIfAbsent(playerName, k -> new PlayerInputData());
        long currentTime = System.currentTimeMillis();
        
        if (isMouseMove) {
            data.lastMouseMoveTime = currentTime;
            data.mouseMoveCount++;
        }
        if (isKeyPress) {
            data.lastKeyPressTime = currentTime;
            data.keyPressCount++;
        }
        
        // AFK检测
        if (currentTime - Math.max(data.lastMouseMoveTime, data.lastKeyPressTime) > 60000) { // 1分钟无输入
            if (!data.isAfk) {
                data.afkStartTime = currentTime;
                data.isAfk = true;
            }
        } else {
            data.isAfk = false;
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
        
        // 密码合规性检查
        if (!isPasswordValid(pass1)) {
            log("Registration failed for " + playerName + ": password does not meet requirements");
            banPlayerWithTime(playerName, "Invalid password format", "1m");
            return;
        }
        
        PlayerData data = new PlayerData();
        data.password = pass1;
        data.isLoggedIn = true;
        data.registrationTime = System.currentTimeMillis();
        playerData.put(playerName, data);
        log("Registered player: " + playerName);
        saveData();
        
        // 启动密码检查定时器
        startPasswordCheckTimer(playerName);
    }

    private void loginPlayer(String playerName, String password, Object source) {
        PlayerData data = playerData.get(playerName);
        if (data != null && data.password.equals(password)) {
            data.isLoggedIn = true;
            log("Player logged in: " + playerName);
            saveData();
            
            // 登录后检测作弊客户端
            for (String className : SUSPICIOUS_CLASS_NAMES) {
                if (isClassLoaded(className)) {
                    banPlayer(playerName, "Detected cheat client after login", 1354312);
                    break;
                }
            }
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
        long registrationTime = 0;
        boolean passwordChecked = false;
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

    private static class PlayerMovementData {
        double lastX, lastY, lastZ;
        long lastUpdateTime;
        List<Double> speedHistory = new ArrayList<>();
        List<Double> positionHistory = new ArrayList<>();
    }

    private static class PlayerInputData {
        long lastMouseMoveTime;
        long lastKeyPressTime;
        int mouseMoveCount;
        int keyPressCount;
        long afkStartTime;
        boolean isAfk = false;
    }
}
