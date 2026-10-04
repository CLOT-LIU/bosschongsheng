package com.bosschongsheng.network;

import com.bosschongsheng.BossChongsheng;
import com.bosschongsheng.config.BossRespawnConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：将当前规则保存到 boss_respawn.json 并立即重载
 */
public record SaveBossRulesPayload() implements CustomPacketPayload {
    public static final Type<SaveBossRulesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BossChongsheng.MOD_ID, "save_boss_rules"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveBossRulesPayload> STREAM_CODEC =
            StreamCodec.unit(new SaveBossRulesPayload());

    public static void handle(SaveBossRulesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
                return;
            }
            BossRespawnConfig cfg = BossRespawnConfig.getInstance();
            if (cfg.saveToFile()) {
                cfg.reloadConfig();
                player.displayClientMessage(
                        Component.translatable("gui.bosschongsheng.config.saved"), false);
            } else {
                player.displayClientMessage(
                        Component.translatable("gui.bosschongsheng.config.save_failed"), false);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
