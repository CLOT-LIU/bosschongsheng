package com.bosschongsheng.network;

import com.bosschongsheng.BossChongsheng;
import com.bosschongsheng.config.BossRespawnConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：按结构 ID 删除一条规则
 */
public record RemoveBossRulePayload(String structureId) implements CustomPacketPayload {
    public static final Type<RemoveBossRulePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BossChongsheng.MOD_ID, "remove_boss_rule"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveBossRulePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUtf(payload.structureId()),
                    buf -> new RemoveBossRulePayload(buf.readUtf())
            );

    public static void handle(RemoveBossRulePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
                return;
            }
            BossRespawnConfig.getInstance().removeRule(payload.structureId());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
