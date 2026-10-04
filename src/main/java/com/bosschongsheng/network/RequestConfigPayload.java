package com.bosschongsheng.network;

import com.bosschongsheng.BossChongsheng;
import com.bosschongsheng.client.BossRespawnConfigClient;
import com.bosschongsheng.config.BossRespawnConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 -> 服务端：打开配置界面时请求结构列表与当前规则
 */
public record RequestConfigPayload() implements CustomPacketPayload {
    public static final Type<RequestConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BossChongsheng.MOD_ID, "request_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestConfigPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestConfigPayload());

    public static void handle(RequestConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
                return;
            }
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }
            List<String> structureIds = new ArrayList<>();
            try {
                server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet()
                        .forEach(key -> structureIds.add(key.toString()));
            } catch (Exception e) {
                structureIds.clear();
            }
            PacketDistributor.sendToPlayer(player, new StructureListPayload(structureIds));

            List<BossRespawnConfigClient.RuleEntry> rules = BossRespawnConfig.getInstance().getRules().stream()
                    .map(r -> new BossRespawnConfigClient.RuleEntry(
                            r.structureId(), r.triggerItemId(), r.entityTypeId()))
                    .toList();
            PacketDistributor.sendToPlayer(player, new BossRespawnListPayload(rules));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
