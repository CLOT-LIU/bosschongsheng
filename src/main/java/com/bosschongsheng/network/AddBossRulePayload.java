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
 * 客户端 -> 服务端：新增或覆盖一条 Boss 重生规则
 */
public record AddBossRulePayload(String structureId, String triggerItemId, String entityTypeId)
        implements CustomPacketPayload {
    public static final Type<AddBossRulePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BossChongsheng.MOD_ID, "add_boss_rule"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddBossRulePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.structureId());
                        buf.writeUtf(payload.triggerItemId());
                        buf.writeUtf(payload.entityTypeId());
                    },
                    buf -> new AddBossRulePayload(buf.readUtf(), buf.readUtf(), buf.readUtf())
            );

    public static void handle(AddBossRulePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
                return;
            }
            BossRespawnConfig.getInstance().addRule(new BossRespawnConfig.BossRespawnRule(
                    payload.structureId(), payload.triggerItemId(), payload.entityTypeId()));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
