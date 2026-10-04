package com.bosschongsheng.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包统一注册（NeoForge 1.21.1 CustomPacketPayload 体系），
 * 由主类在 MOD 事件总线上挂载 {@link #onRegisterPayloads}。
 */
public final class Networking {
    private Networking() {
    }

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // 服务端 -> 客户端
        registrar.playToClient(StructureListPayload.TYPE, StructureListPayload.STREAM_CODEC,
                StructureListPayload::handle);
        registrar.playToClient(BossRespawnListPayload.TYPE, BossRespawnListPayload.STREAM_CODEC,
                BossRespawnListPayload::handle);
        registrar.playToClient(OpenConfigScreenPayload.TYPE, OpenConfigScreenPayload.STREAM_CODEC,
                OpenConfigScreenPayload::handle);

        // 客户端 -> 服务端
        registrar.playToServer(RequestConfigPayload.TYPE, RequestConfigPayload.STREAM_CODEC,
                RequestConfigPayload::handle);
        registrar.playToServer(AddBossRulePayload.TYPE, AddBossRulePayload.STREAM_CODEC,
                AddBossRulePayload::handle);
        registrar.playToServer(RemoveBossRulePayload.TYPE, RemoveBossRulePayload.STREAM_CODEC,
                RemoveBossRulePayload::handle);
        registrar.playToServer(SaveBossRulesPayload.TYPE, SaveBossRulesPayload.STREAM_CODEC,
                SaveBossRulesPayload::handle);
        registrar.playToServer(ReloadBossRulesPayload.TYPE, ReloadBossRulesPayload.STREAM_CODEC,
                ReloadBossRulesPayload::handle);
    }
}
