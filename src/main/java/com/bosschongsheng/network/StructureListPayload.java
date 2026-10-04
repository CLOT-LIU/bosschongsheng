package com.bosschongsheng.network;

import com.bosschongsheng.BossChongsheng;
import com.bosschongsheng.client.BossRespawnConfigClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：当前存档全部已注册结构 ID 列表
 */
public record StructureListPayload(List<String> structureIds) implements CustomPacketPayload {
    public static final Type<StructureListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BossChongsheng.MOD_ID, "structure_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StructureListPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeInt(payload.structureIds().size());
                        for (String id : payload.structureIds()) {
                            buf.writeUtf(id);
                        }
                    },
                    buf -> {
                        int size = buf.readInt();
                        List<String> list = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            list.add(buf.readUtf());
                        }
                        return new StructureListPayload(list);
                    }
            );

    public static void handle(StructureListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BossRespawnConfigClient.setStructureIds(payload.structureIds()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
