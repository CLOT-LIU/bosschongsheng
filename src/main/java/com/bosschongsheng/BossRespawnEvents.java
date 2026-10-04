package com.bosschongsheng;

import com.bosschongsheng.config.BossRespawnConfig;
import com.bosschongsheng.network.OpenConfigScreenPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 游戏逻辑事件：命令注册 + 结构内右键物品召唤 Boss
 */
@EventBusSubscriber(modid = BossChongsheng.MOD_ID)
public final class BossRespawnEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 模组附加数据注册容器（需在 MOD 事件总线上注册） */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, BossChongsheng.MOD_ID);

    /**
     * 实体的「出生保护」截止游戏刻（绝对时间）；值大于当前游戏刻时处于保护期。
     * 默认 -1 表示该实体没有出生保护。随存档序列化，区块卸载后依然有效。
     */
    public static final Supplier<AttachmentType<Long>> SPAWN_INVULN_UNTIL =
            ATTACHMENTS.register("spawn_invuln_until",
                    () -> AttachmentType.builder(() -> -1L).serialize(Codec.LONG).build());

    /**
     * 出生位置尝试距离（格），沿玩家正前方由远到近依次尝试；
     * 具体保护时长由配置决定（见 {@link BossRespawnConfig#getSpawnProtectionSeconds()}）。
     */
    private static final double[] SPAWN_DISTANCES = {6.0D, 5.0D, 4.0D, 3.0D};

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
            // 在玩家正前方找安全出生点；找不到时退回玩家身旁
            Vec3 spawnPos = findSpawnPosInFront(serverLevel, entityType, player);
            double sx;
            double sy;
            double sz;
            if (spawnPos != null) {
                sx = spawnPos.x;
                sy = spawnPos.y;
                sz = spawnPos.z;
            } else {
                sx = pos.getX() + 0.5;
                sy = pos.getY() + 1;
                sz = pos.getZ() + 0.5;
            }
            // 生成后面向玩家
            float yaw = (float) Math.toDegrees(Math.atan2(player.getX() - sx, player.getZ() - sz));
            entity.moveTo(sx, sy, sz, yaw, 0.0f);

            // 出生保护（可在配置中开关、调时长）：期间不受伤、不能攻击，并全身发光提示
            BossRespawnConfig cfg = BossRespawnConfig.getInstance();
            if (cfg.isSpawnProtectionEnabled() && cfg.getSpawnProtectionSeconds() > 0) {
                int ticks = cfg.getSpawnProtectionSeconds() * 20;
                entity.setData(SPAWN_INVULN_UNTIL, serverLevel.getGameTime() + ticks);
                entity.setGlowingTag(true);
            }

            serverLevel.addFreshEntity(entity);
            LOGGER.info("[BossChongsheng] 在结构 {} 内使用 {} 召唤 {}，出生点=({}, {}, {})",
                    structureId, itemIdStr, rule.entityTypeId(),
                    String.format("%.1f", sx), String.format("%.1f", sy), String.format("%.1f", sz));
        }
    }

    /**
     * 沿玩家水平朝向（正前方），按 {@link #SPAWN_DISTANCES} 由远到近逐列寻找地面：
     * 要求脚下有碰撞面、身体空间不被方块卡住且不在液体中。
     *
     * @return 安全出生点（y 为脚底高度）；全部距离都不合适时返回 null
     */
    @Nullable
    private static Vec3 findSpawnPosInFront(ServerLevel level, EntityType<?> type, ServerPlayer player) {
        // Minecraft 偏航角：0=南(+Z)，90=西(-X)，180=北(-Z)，-90=东(+X)
        // 前向向量公式 x=-sin(yaw), z=cos(yaw)，yaw 不能取负（取负会导致东西方向反转）
        double yawRad = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        int baseY = player.blockPosition().getY();
        for (double dist : SPAWN_DISTANCES) {
            double x = player.getX() + forwardX * dist;
            double z = player.getZ() + forwardZ * dist;
            for (int dy = 2; dy >= -6; dy--) {
                int y = baseY + dy;
                BlockPos ground = BlockPos.containing(x, y, z);
                BlockState groundState = level.getBlockState(ground);
                if (groundState.getCollisionShape(level, ground).isEmpty()) {
                    continue; // 此处没有实体地面，继续向下
                }
                BlockPos feet = ground.above();
                if (!level.getFluidState(feet).isEmpty()
                        || !level.getFluidState(feet.above()).isEmpty()) {
                    break; // 地面被液体覆盖，这一列不必再找
                }
                AABB box = type.getDimensions().makeBoundingBox(x, feet.getY(), z);
                if (level.noCollision(box)) {
                    return new Vec3(x, feet.getY(), z);
                }
                // 身体被方块卡住，继续尝试这一列更低的地面
            }
        }
        return null;
    }

    /**
     * 出生保护期内双向免伤：
     * <ul>
     *   <li>被保护的 Boss 不受到任何伤害（玩家攻击、跌落、火焰等均无效）；</li>
     *   <li>被保护的 Boss 也不能对任何生物造成伤害（含玩家），投射物以发射者为准。</li>
     * </ul>
     * 注意：虚空（/kill、掉出世界）类伤害不走此事件，仍可正常清除实体。
     */
    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        long now = victim.level().getGameTime();
        if (isSpawnProtected(victim, now)) {
            event.setCanceled(true);
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && isSpawnProtected(attacker, now)) {
            event.setCanceled(true);
        }
    }

    /**
     * 保护期内每 tick 清除攻击目标：怪物不会追击、扑咬、射箭或蓄力爆炸；
     * 保护到期后关闭发光并移除标记，恢复正常 AI。
     */
    @SubscribeEvent
    public static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        long until = entity.getData(SPAWN_INVULN_UNTIL);
        if (until < 0) {
            return;
        }
        if (entity.level().getGameTime() < until) {
            if (entity instanceof Mob mob) {
                mob.setTarget(null);
                mob.setAggressive(false);
            }
        } else {
            entity.setGlowingTag(false);
            entity.removeData(SPAWN_INVULN_UNTIL);
        }
    }

    /** 该实体当前是否处于出生保护期 */
    private static boolean isSpawnProtected(Entity entity, long now) {
        return entity.getData(SPAWN_INVULN_UNTIL) > now;
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
