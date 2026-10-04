package com.bosschongsheng.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 服务端网络包在客户端的入口（仅在物理客户端被类加载）
 */
public final class ClientPackets {
    private ClientPackets() {
    }

    public static void openConfigScreen() {
        Minecraft.getInstance().setScreen(new BossRespawnConfigScreen());
    }

    public static void showReloadSuccessMessage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable("gui.bosschongsheng.config.reloaded"), true);
        }
    }
}
