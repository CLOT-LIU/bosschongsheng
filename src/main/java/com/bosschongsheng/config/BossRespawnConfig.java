package com.bosschongsheng.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Boss 重生规则配置（单例）
 *
 * <p>配置文件位置：config/bosschongsheng/boss_respawn.json</p>
 */
public final class BossRespawnConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CONFIG_FILE_NAME = "boss_respawn.json";
    private static BossRespawnConfig instance;

    private final Map<String, BossRespawnRule> rules = new HashMap<>();

    /** 召唤生物是否开启出生保护（短暂无敌且不能攻击玩家） */
    private boolean spawnProtectionEnabled = true;
    /** 出生保护时长（秒），仅在开启时生效 */
    private int spawnProtectionSeconds = 5;
    /** 生成距离（格）：在玩家正前方该距离内找出生点 */
    private int spawnDistance = 6;

    public static BossRespawnConfig getInstance() {
        if (instance == null) {
            instance = new BossRespawnConfig();
        }
        return instance;
    }

    private BossRespawnConfig() {
        loadConfig();
    }

    private Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("bosschongsheng").resolve(CONFIG_FILE_NAME);
    }

    public void reloadConfig() {
        rules.clear();
        loadConfig();
    }

    public List<BossRespawnRule> getRules() {
        return new ArrayList<>(rules.values());
    }

    public void addRule(BossRespawnRule rule) {
        rules.put(rule.structureId(), rule);
    }

    public void removeRule(String structureId) {
        rules.remove(structureId);
    }

    public BossRespawnRule getRule(String structureId) {
        return rules.get(structureId);
    }

    public boolean isSpawnProtectionEnabled() {
        return spawnProtectionEnabled;
    }

    public int getSpawnProtectionSeconds() {
        return spawnProtectionSeconds;
    }

    public int getSpawnDistance() {
        return spawnDistance;
    }

    /**
     * 更新全局设置；seconds 收敛到 1～600 秒，distance 收敛到 2～16 格。
     * 调用方负责后续 {@link #saveToFile()} 持久化。
     */
    public void updateSettings(boolean enabled, int seconds, int distance) {
        this.spawnProtectionEnabled = enabled;
        this.spawnProtectionSeconds = Math.max(1, Math.min(600, seconds));
        this.spawnDistance = Math.max(2, Math.min(16, distance));
    }

    public boolean saveToFile() {
        Path path = getConfigPath();
        try {
            JsonObject root = new JsonObject();
            root.add("spawnProtection", new JsonPrimitive(spawnProtectionEnabled));
            root.add("spawnProtectionSeconds", new JsonPrimitive(spawnProtectionSeconds));
            root.add("spawnDistance", new JsonPrimitive(spawnDistance));
            JsonArray arr = new JsonArray();
            for (BossRespawnRule r : rules.values()) {
                JsonObject o = new JsonObject();
                o.add("structure", new JsonPrimitive(r.structureId()));
                o.add("triggerItem", new JsonPrimitive(r.triggerItemId()));
                o.add("entity", new JsonPrimitive(r.entityTypeId()));
                arr.add(o);
            }
            root.add("rules", arr);
            Files.writeString(path, root.toString());
            LOGGER.info("saved {} boss respawn rules to {}", rules.size(), path);
            return true;
        } catch (IOException e) {
            LOGGER.error("save boss_respawn.json failed", e);
            return false;
        }
    }

    private void loadConfig() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path.getParent());
            } catch (IOException e) {
                LOGGER.warn("create config directory failed", e);
            }
            return;
        }
        try {
            String jsonContent = Files.readString(path);
            JsonObject root = JsonParser.parseString(jsonContent).getAsJsonObject();
            if (root.has("spawnProtection")) {
                spawnProtectionEnabled = root.get("spawnProtection").getAsBoolean();
            }
            if (root.has("spawnProtectionSeconds")) {
                spawnProtectionSeconds = Math.max(1, Math.min(600,
                        root.get("spawnProtectionSeconds").getAsInt()));
            }
            if (root.has("spawnDistance")) {
                spawnDistance = Math.max(2, Math.min(16,
                        root.get("spawnDistance").getAsInt()));
            }
            if (root.has("rules")) {
                JsonArray arr = root.getAsJsonArray("rules");
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("structure") || !o.has("triggerItem") || !o.has("entity")) {
                        continue;
                    }
                    String structureId = o.get("structure").getAsString();
                    String triggerItemId = o.get("triggerItem").getAsString();
                    String entityTypeId = o.get("entity").getAsString();
                    rules.put(structureId, new BossRespawnRule(structureId, triggerItemId, entityTypeId));
                }
            }
            LOGGER.info("loaded {} boss respawn rules", rules.size());
        } catch (IOException e) {
            LOGGER.error("read boss_respawn.json failed", e);
        } catch (Exception e) {
            LOGGER.error("parse boss_respawn.json failed", e);
        }
    }

    /**
     * 一条 Boss 重生规则：在指定结构内使用触发物品，召唤指定实体
     */
    public record BossRespawnRule(String structureId, String triggerItemId, String entityTypeId) {
    }
}
