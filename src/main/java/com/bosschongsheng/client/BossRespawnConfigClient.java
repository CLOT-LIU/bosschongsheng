package com.bosschongsheng.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端持有的配置数据缓存（由服务端同步）
 */
public final class BossRespawnConfigClient {
    private static final List<String> STRUCTURE_IDS = new ArrayList<>();
    private static final List<RuleEntry> RULE_LIST = new ArrayList<>();
    private static boolean spawnProtectionEnabled = true;
    private static int spawnProtectionSeconds = 5;
    private static int spawnDistance = 6;
    private static String lastError;

    private BossRespawnConfigClient() {
    }

    public static void setStructureIds(List<String> ids) {
        STRUCTURE_IDS.clear();
        if (ids != null) {
            STRUCTURE_IDS.addAll(ids);
        }
    }

    public static List<String> getStructureIds() {
        return new ArrayList<>(STRUCTURE_IDS);
    }

    public static void setRuleList(List<RuleEntry> list) {
        RULE_LIST.clear();
        if (list != null) {
            RULE_LIST.addAll(list);
        }
    }

    public static List<RuleEntry> getRuleList() {
        return new ArrayList<>(RULE_LIST);
    }

    public static void setSettings(boolean enabled, int seconds, int distance) {
        spawnProtectionEnabled = enabled;
        spawnProtectionSeconds = seconds;
        spawnDistance = distance;
    }

    public static boolean isSpawnProtectionEnabled() {
        return spawnProtectionEnabled;
    }

    public static int getSpawnProtectionSeconds() {
        return spawnProtectionSeconds;
    }

    public static int getSpawnDistance() {
        return spawnDistance;
    }

    public static void setLastError(String err) {
        lastError = err;
    }

    public static String getLastError() {
        return lastError;
    }

    public static void clearError() {
        lastError = null;
    }

    public record RuleEntry(String structureId, String triggerItemId, String entityTypeId) {
    }
}
