package com.bosschongsheng.network;

import com.bosschongsheng.BossChongsheng;
import com.bosschongsheng.client.BossRespawnConfigClient;
import com.bosschongsheng.client.ClientPackets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：当前全部 Boss 重生规则；reloadSuccess 为 true 时同时提示重载成功
 */
public record BossRespawnListPayload(List<BossRespawnConfigClient.RuleEntry> rules, boolean reloadSuccess)
        implements CustomPacketPayload {
    public static final Type<BossRespawnListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BossChongsheng.MOD_ID, "boss_respawn_list"));

    public BossRespawnListPayload(List<BossRespawnConfigClient.RuleEntry> rules) {
        this(rules, false);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, BossRespawnListPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBoolean(payload.reloadSuccess());
                        buf.writeInt(payload.rules().size());
                        for (BossRespawnConfigClient.RuleEntry e : payload.rules()) {
                            buf.writeUtf(e.structureId());
                            buf.writeUtf(e.triggerItemId());
                            buf.writeUtf(e.entityTypeId());
                        }
                    },
                    buf -> {
                        boolean reloadSuccess = buf.readBoolean();
                        int size = buf.readInt();
                        List<BossRespawnConfigClient.RuleEntry> list = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            list.add(new BossRespawnConfigClient.RuleEntry(buf.readUtf(), buf.readUtf(), buf.readUtf()));
                        }
                        return new BossRespawnListPayload(list, reloadSuccess);
                    }
            );

    public static void handle(BossRespawnListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            BossRespawnConfigClient.setRuleList(payload.rules());
            if (payload.reloadSuccess()) {
                ClientPackets.showReloadSuccessMessage();
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
