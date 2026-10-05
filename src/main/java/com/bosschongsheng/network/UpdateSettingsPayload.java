package com.bosschongsheng.network;

import com.bosschongsheng.BossChongsheng;
import com.bosschongsheng.client.BossRespawnConfigClient;
import com.bosschongsheng.config.BossRespawnConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 客户端 -> 服务端：修改出生保护全局设置，立即写入 boss_respawn.json 并回传最新配置
 */
public record UpdateSettingsPayload(boolean spawnProtectionEnabled, int spawnProtectionSeconds,
                                    int spawnDistance)
        implements CustomPacketPayload {
    public static final Type<UpdateSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BossChongsheng.MOD_ID, "update_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateSettingsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBoolean(payload.spawnProtectionEnabled());
                        buf.writeInt(payload.spawnProtectionSeconds());
                        buf.writeInt(payload.spawnDistance());
                    },
                    buf -> new UpdateSettingsPayload(buf.readBoolean(), buf.readInt(), buf.readInt())
            );

    public static void handle(UpdateSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
                return;
            }
            BossRespawnConfig cfg = BossRespawnConfig.getInstance();
            cfg.updateSettings(payload.spawnProtectionEnabled(), payload.spawnProtectionSeconds(),
                    payload.spawnDistance());
            if (cfg.saveToFile()) {
                player.displayClientMessage(
                        Component.translatable("gui.bosschongsheng.config.saved"), true);
            } else {
                player.displayClientMessage(
                        Component.translatable("gui.bosschongsheng.config.save_failed"), true);
            }
            List<BossRespawnConfigClient.RuleEntry> rules = cfg.getRules().stream()
                    .map(r -> new BossRespawnConfigClient.RuleEntry(
                            r.structureId(), r.triggerItemId(), r.entityTypeId()))
                    .toList();
            PacketDistributor.sendToPlayer(player, new BossRespawnListPayload(
                    rules, cfg.isSpawnProtectionEnabled(), cfg.getSpawnProtectionSeconds(),
                    cfg.getSpawnDistance()));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
