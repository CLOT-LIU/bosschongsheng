package com.bosschongsheng;

import com.bosschongsheng.config.BossRespawnConfig;
import com.bosschongsheng.network.OpenConfigScreenPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * 游戏逻辑事件：命令注册 + 结构内右键物品召唤 Boss
 */
@EventBusSubscriber(modid = BossChongsheng.MOD_ID)
public final class BossRespawnEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BossRespawnEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("bosschongsheng")
                        .then(Commands.literal("config")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                                        PacketDistributor.sendToPlayer(player, new OpenConfigScreenPayload());
                                        return 1;
                                    }
                                    ctx.getSource().sendFailure(Component.literal("仅玩家可打开配置界面"));
                                    return 0;
                                }))
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    BossRespawnConfig.getInstance().reloadConfig();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable("gui.bosschongsheng.config.reloaded"), true);
                                    return 1;
                                }))
        );
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Level level = event.getLevel();
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getEntity();
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemIdStr = itemId.toString();

        BlockPos pos = player.blockPosition();
        String structureId = getStructureAt(serverLevel, pos);

        boolean isTriggerItem = BossRespawnConfig.getInstance().getRules().stream()
                .anyMatch(r -> itemIdStr.equals(r.triggerItemId()));
        if (isTriggerItem) {
            LOGGER.info("[BossChongsheng] 右键 触发物品={} 位置={} 检测到结构={}",
                    itemIdStr, pos.getX(), structureId != null ? structureId : "无（请确认站在结构内部）");
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[BossChongsheng] 右键 物品={} 位置={} 检测到结构={}",
                    itemIdStr, pos.getX(), structureId != null ? structureId : "无");
        }

        if (structureId == null) {
            return;
        }
        BossRespawnConfig.BossRespawnRule rule = BossRespawnConfig.getInstance().getRule(structureId);
        if (rule == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[BossChongsheng] 结构 {} 无对应规则", structureId);
            }
            return;
        }
        if (!itemIdStr.equals(rule.triggerItemId())) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[BossChongsheng] 物品不匹配 当前={} 规则要求={}", itemIdStr, rule.triggerItemId());
            }
            return;
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        ResourceLocation entityKey = ResourceLocation.tryParse(rule.entityTypeId());
        if (entityKey == null) {
            LOGGER.warn("[BossChongsheng] 实体 ID 非法：{}", rule.entityTypeId());
            return;
        }
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityKey);
        Entity entity = entityType.create(serverLevel);
        if (entity != null) {
            entity.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, player.getYRot(), 0.0f);
            serverLevel.addFreshEntity(entity);
            LOGGER.info("[BossChongsheng] 在结构 {} 内使用 {} 召唤 {}",
                    structureId, itemIdStr, rule.entityTypeId());
        }
    }

    private static String getStructureAt(ServerLevel level, BlockPos pos) {
        StructureManager structureManager = level.structureManager();
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (BossRespawnConfig.BossRespawnRule rule : BossRespawnConfig.getInstance().getRules()) {
            ResourceLocation key = ResourceLocation.tryParse(rule.structureId());
            if (key == null) {
                continue;
            }
            Structure structure = structureRegistry.get(key);
            if (structure == null) {
                continue;
            }
            StructureStart start = structureManager.getStructureAt(pos, structure);
            if (start != null && start.isValid()) {
                return rule.structureId();
            }
        }
        return null;
    }
}
