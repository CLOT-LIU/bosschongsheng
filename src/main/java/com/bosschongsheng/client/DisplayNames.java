package com.bosschongsheng.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 统一的注册表条目显示名处理。
 *
 * <p>结构在原版中只有少量（村庄、海底神殿等）带有 {@code structure.<ns>.<path>} 翻译键，
 * 大量模组结构（路径常含 "/"，如 {@code ctov:medium/village_desert_oasis}）没有翻译键，
 * 直接 translatable 会显示成键本身。这里先查语言表，缺失时把路径片段转换成可读名称。</p>
 */
public final class DisplayNames {
    private DisplayNames() {
    }

    /**
     * 结构显示名：有翻译键用翻译（如"沙漠村庄"），否则把路径人工化
     * （如 ctov:medium/village_desert_oasis → "Medium / Village Desert Oasis"）。
     */
    public static String structure(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return id;
        }
        String key = "structure." + rl.getNamespace() + "." + rl.getPath();
        String translated = Component.translatable(key).getString();
        // 语言表中没有该键时，translatable 会原样返回键本身
        if (!translated.equals(key)) {
            return translated;
        }
        return humanizePath(rl.getPath());
    }

    private static String humanizePath(String path) {
        String[] segments = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(" / ");
            }
            sb.append(humanize(segment));
        }
        return sb.isEmpty() ? path : sb.toString();
    }

    private static String humanize(String raw) {
        String[] words = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
