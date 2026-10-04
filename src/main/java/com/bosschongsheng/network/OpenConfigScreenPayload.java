package com.bosschongsheng.network;

import com.bosschongsheng.BossChongsheng;
import com.bosschongsheng.client.ClientPackets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 -> 客户端：要求客户端打开 Boss 重生配置界面
 */
public record OpenConfigScreenPayload() implements CustomPacketPayload {
    public static final Type<OpenConfigScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BossChongsheng.MOD_ID, "open_config_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenConfigScreenPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenConfigScreenPayload());

    public static void handle(OpenConfigScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(ClientPackets::openConfigScreen);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
