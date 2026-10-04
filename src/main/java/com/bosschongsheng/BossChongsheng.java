package com.bosschongsheng;

import com.bosschongsheng.network.Networking;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Boss 重生模组主入口
 *
 * <p>网络包在 MOD 事件总线上注册，游戏事件由 {@link BossRespawnEvents} 自动订阅。</p>
 */
@Mod(BossChongsheng.MOD_ID)
public class BossChongsheng {
    public static final String MOD_ID = "bosschongsheng";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BossChongsheng(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(Networking::onRegisterPayloads);
        BossRespawnEvents.ATTACHMENTS.register(modEventBus);
        LOGGER.info("bosschongsheng (Boss重生) loaded");
    }
}
