package com.bosschongsheng.network;

import com.bosschongsheng.BossChongsheng;
import com.bosschongsheng.client.BossRespawnConfigClient;
import com.bosschongsheng.config.BossRespawnConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 客户端 -> 服务端：从磁盘重载规则，并把最新规则（带成功标记）发回客户端
 */
public record ReloadBossRulesPayload() implements CustomPacketPayload {
    public static final Type<ReloadBossRulesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BossChongsheng.MOD_ID, "reload_boss_rules"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReloadBossRulesPayload> STREAM_CODEC =
            StreamCodec.unit(new ReloadBossRulesPayload());

    public static void handle(ReloadBossRulesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
                return;
            }
            BossRespawnConfig cfg = BossRespawnConfig.getInstance();
            cfg.reloadConfig();
            List<BossRespawnConfigClient.RuleEntry> rules = cfg.getRules().stream()
                    .map(r -> new BossRespawnConfigClient.RuleEntry(
                            r.structureId(), r.triggerItemId(), r.entityTypeId()))
                    .toList();
            PacketDistributor.sendToPlayer(player, new BossRespawnListPayload(rules, true,
                    cfg.isSpawnProtectionEnabled(), cfg.getSpawnProtectionSeconds(),
                    cfg.getSpawnDistance()));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
