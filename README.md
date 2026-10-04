# Boss重生 (bosschongsheng) — NeoForge 1.21.1

在指定结构内右键使用设定物品，即可召唤配置的 Boss/实体。规则可通过游戏内可视化界面配置。

## 来源说明

本模组移植自**香草纪元整合包**中的 Forge 1.20.1 模组 **bosschongsheng**，**原作者为 qingye**。

因为我自己的整合包需要 1.21.1 版本，于是在 AI 的协助下将其重写/迁移到了 **Minecraft 1.21.1 + NeoForge**。功能与原模组保持一致，并对界面做了高清化与易用性改进。在此感谢原作者 qingye 的创作。

## 功能

- 玩家在规则指定的结构内，右键使用规则指定的触发物品，非创造模式下消耗 1 个该物品，并在玩家位置召唤规则指定的实体
- `/bosschongsheng config` — 打开可视化配置界面（需要权限等级 2）
- `/bosschongsheng reload` — 重载配置文件（需要权限等级 2）
- 配置界面支持：
  - 规则列表的搜索、添加、编辑、删除、保存、重载
  - 添加/编辑规则时，结构 / 触发物品 / 召唤生物三栏选择，每栏支持**分类过滤 + 关键字搜索**叠加筛选
  - 结构分类（村庄/前哨·营地/下界/末地/海洋/地牢·遗迹等）、物品分类（跟随游戏内创造模式物品栏，含模组自建页）、生物分类（友好/敌对/水生/环境/其他）
- 规则配置文件：`config/bosschongsheng/boss_respawn.json`

配置文件格式：

```json
{
  "rules": [
    {
      "structure": "minecraft:village_plains",
      "triggerItem": "minecraft:nether_star",
      "entity": "minecraft:wither"
    }
  ]
}
```

## 环境

- Minecraft `1.21.1`
- NeoForge `21.1.x`
- Java `21`

## 构建

Windows：

```text
gradlew.bat build
```

Linux/macOS：

```text
./gradlew build
```

生成的 JAR 位于 `build/libs/`。

## 许可证

本移植版代码使用 [MIT License](LICENSE)，版权主体为 CLOTLIU。
原模组创意与规则设计归属原作者 qingye。
