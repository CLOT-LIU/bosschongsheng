package com.bosschongsheng.client;

import com.bosschongsheng.network.AddBossRulePayload;
import com.bosschongsheng.network.RequestConfigPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 添加/编辑规则界面：结构 / 触发物品 / 召唤实体 三栏选择。
 *
 * <p>每栏支持排序切换（名称 / 维度 / 来源模组）与关键字搜索，
 * 搜索框中可用 {@code @模组名}（模组 id 或显示名）按来源过滤；
 * 列表带可拖拽滚动条；布局随窗口尺寸自适应。</p>
 */
public class RulePickerScreen extends Screen {
    private static final int ROW_H = 18;
    private static final int SORT_H = 15;
    private static final int SCROLL_BAR_W = 4;
    private static final int SCROLL_THUMB_MIN = 16;

    private static final int PANEL_BG = 0xFF2A2A35;
    private static final int PANEL_BORDER = 0xFF3D3D52;
    private static final int LIST_BG = 0xFF1E1E26;
    private static final int ROW_HOVER = 0xFF363648;
    private static final int SELECTED_BG = 0xFF2D3A5C;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int COUNT_COLOR = 0xFF9AA0B4;
    private static final int SCROLL_TRACK = 0xFF3A3A46;
    private static final int SCROLL_THUMB = 0xFF8A8AA0;
    private static final int SCROLL_THUMB_ACTIVE = 0xFFB8B8D0;
    private static final int HEADER_BG = 0xFF242430;
    private static final int HEADER_LINE = 0xFF4C5470;
    private static final int HEADER_COLOR = 0xFF9ECBFF;
    /** 显示杂项时，被过滤实体（投掷物、船、空名实体等）的名称颜色 */
    private static final int MISC_TEXT_COLOR = 0xFF8C8C9E;

    // 排序方式：0=名称，1=维度（主世界→下界→末地），2=来源模组
    private static final int SORT_NAME = 0;
    private static final int SORT_DIMENSION = 1;
    private static final int SORT_SOURCE = 2;
    private static final String[] SORT_KEYS = {"sort_name", "sort_dimension", "sort_source"};
    /** 维度分组标题语言键，下标与 {@link #dimensionOfStructure} 返回值对应 */
    private static final String[] DIM_KEYS = {
            "gui.bosschongsheng.config.dimension_overworld",
            "gui.bosschongsheng.config.dimension_nether",
            "gui.bosschongsheng.config.dimension_end"};

    private final BossRespawnConfigScreen parent;
    private final boolean isEdit;

    private final List<String> structureIds = new ArrayList<>();
    private final List<ItemEntry> allItems = new ArrayList<>();
    private final List<String> allEntities = new ArrayList<>();

    private EditBox searchStructure;
    private EditBox searchItem;
    private EditBox searchEntity;
    private final Button[] sortButtons = new Button[3];

    private List<String> filteredStructures = new ArrayList<>();
    private List<ItemEntry> filteredItems = new ArrayList<>();
    private List<String> filteredEntities = new ArrayList<>();

    /** 三栏实际渲染的行：条目（String/ItemEntry）或 {@link HeaderRow} 分组标题 */
    private final List<List<Object>> viewRows = List.of(
            new ArrayList<Object>(), new ArrayList<Object>(), new ArrayList<Object>());
    /** 首次构建视图时把已选中的项滚动到可见位置（编辑模式用），消费一次后失效 */
    private final boolean[] revealSelected = {true, true, true};

    private String selectedStructureId;
    private String selectedItemId;
    private String selectedEntityId;

    private int scrollStructure;
    private int scrollItem;
    private int scrollEntity;

    private final int[] sortMode = {SORT_NAME, SORT_NAME, SORT_NAME};
    /** 正在拖拽滚动条的栏，-1 表示未拖拽 */
    private int draggingScrollColumn = -1;

    private int panelLeft;
    private int panelTop;
    private int panelW;
    private int panelH;
    private boolean layoutHorizontal;
    private int colW;
    private int visibleRows;
    private int listAreaH;

    /** 三栏各自的 x、标签 y、排序按钮 y、搜索框 y、列表 y */
    private final int[] colX = new int[3];
    private final int[] labelY = new int[3];
    private final int[] sortY = new int[3];
    private final int[] searchBoxY = new int[3];
    private final int[] listY = new int[3];

    /** 生物 id → 是否为可召唤生物（名称非空且为活体或常规生物类别）的分类缓存 */
    private static final Map<String, Boolean> CREATURE_CACHE = new HashMap<>();
    /** 生物栏是否隐藏杂项实体（投掷物、船、空名实体等）；static 让本次游戏会话内保持选择 */
    private static boolean entityFilterEnabled = true;

    private Button miscFilterButton;

    /** 命名空间 → 模组显示名缓存 */
    private static final Map<String, String> MOD_NAMES = new HashMap<>();

    public RulePickerScreen(BossRespawnConfigScreen parent, String preStructureId, String preItemId,
                            String preEntityId, boolean isEdit) {
        super(Component.translatable(isEdit
                ? "gui.bosschongsheng.config.edit_rule"
                : "gui.bosschongsheng.config.add_rule"));
        this.parent = parent;
        this.isEdit = isEdit;
        this.selectedStructureId = preStructureId != null ? preStructureId : "";
        this.selectedItemId = preItemId != null ? preItemId : "";
        this.selectedEntityId = preEntityId != null ? preEntityId : "";

        // 编辑规则时把已选中的项固定到列表第一位，且滚动条置顶
        if (isEdit) {
            scrollStructure = 0;
            scrollItem = 0;
            scrollEntity = 0;
        }

        structureIds.addAll(BossRespawnConfigClient.getStructureIds());
        filteredStructures.addAll(structureIds);

        for (Item item : BuiltInRegistries.ITEM.stream().toList()) {
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            allItems.add(new ItemEntry(id.toString(), new ItemStack(item)));
        }
        filteredItems.addAll(allItems);

        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            allEntities.add(id.toString());
        }
        filteredEntities.addAll(allEntities);

        // 编辑规则时若已选中的生物属于被过滤的杂项，自动放开过滤，避免选中项看不见
        if (isEdit && !selectedEntityId.isEmpty() && !isCreature(selectedEntityId)) {
            entityFilterEnabled = false;
        }

        refreshStructureFilter();
        refreshItemFilter();
        refreshEntityFilter();
    }

    // ---------------- 名称 / 维度 / 来源 推断 ----------------

    private static String entityName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.get(key);
        return type == null ? id : type.getDescription().getString();
    }

    private static String namespaceOf(String id) {
        int i = id.indexOf(':');
        return i < 0 ? "minecraft" : id.substring(0, i);
    }

    /**
     * 是否适合作为召唤生物展示：名称非空，且为生物——
     * 生物类别非 MISC 的直接保留；MISC 类别（盔甲架、船、投掷物等）创建实例确认是
     * {@link LivingEntity} 才保留（盔甲架保留，箭/船/矿车等过滤）。结果缓存。
     */
    private static boolean isCreature(String id) {
        Boolean cached = CREATURE_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        boolean result = false;
        ResourceLocation key = ResourceLocation.tryParse(id);
        EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.get(key);
        if (type != null && !type.getDescription().getString().isBlank()) {
            result = type.getCategory() != MobCategory.MISC || entityForRender(id) != null;
        }
        CREATURE_CACHE.put(id, result);
        return result;
    }

    private static String modDisplayName(String namespace) {
        String cached = MOD_NAMES.get(namespace);
        if (cached != null) {
            return cached;
        }
        String name = namespace;
        try {
            name = ModList.get().getModContainerById(namespace)
                    .map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
        } catch (Throwable ignored) {
            // ModList 不可用时退回命名空间本身
        }
        MOD_NAMES.put(namespace, name);
        return name;
    }

    /** 结构的维度推断：end 走边界匹配避免误伤 legend 等 */
    private static int dimensionOfStructure(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        if (s.contains("end_") || s.contains("_end") || s.contains("/end")
                || s.contains("end/") || s.endsWith("/end") || s.equals("end")) {
            return 2;
        }
        return s.contains("nether") ? 1 : 0;
    }

    // ---------------- 布局 ----------------

    @Override
    protected void init() {
        super.init();
        layoutHorizontal = this.width >= 400;

        if (layoutHorizontal) {
            panelW = Math.min(660, this.width - 20);
            panelH = Math.min(300, Math.max(160, this.height - 20));
            panelLeft = (this.width - panelW) / 2;
            panelTop = (this.height - panelH) / 2;
            colW = (panelW - 40) / 3;
            visibleRows = Math.max(2, (panelH - 113) / ROW_H);
            listAreaH = visibleRows * ROW_H;

            for (int c = 0; c < 3; c++) {
                colX[c] = panelLeft + 10 + c * (colW + 10);
                labelY[c] = panelTop + 20;
                sortY[c] = panelTop + 31;
                searchBoxY[c] = panelTop + 49;
                listY[c] = panelTop + 73;
            }
        } else {
            panelW = Math.min(420, this.width - 20);
            int rows = Math.max(1, Math.min(8, (this.height - 20 - 241) / (3 * ROW_H)));
            listAreaH = rows * ROW_H;
            visibleRows = rows;
            panelH = 241 + 3 * listAreaH;
            panelLeft = (this.width - panelW) / 2;
            panelTop = Math.max(2, (this.height - panelH) / 2);
            colW = panelW - 20;

            for (int c = 0; c < 3; c++) {
                colX[c] = panelLeft + 10;
                int blockTop = panelTop + 24 + c * (61 + listAreaH);
                labelY[c] = blockTop;
                sortY[c] = blockTop + 12;
                searchBoxY[c] = blockTop + 29;
                listY[c] = blockTop + 53;
            }
        }

        searchStructure = new EditBox(this.font, colX[0], searchBoxY[0], colW, 20, Component.empty());
        searchStructure.setHint(Component.translatable("gui.bosschongsheng.config.search_structure_hint"));
        searchStructure.setResponder(s -> refreshStructureFilter());
        addRenderableWidget(searchStructure);

        searchItem = new EditBox(this.font, colX[1], searchBoxY[1], colW, 20, Component.empty());
        searchItem.setHint(Component.translatable("gui.bosschongsheng.config.search_item_hint"));
        searchItem.setResponder(s -> refreshItemFilter());
        addRenderableWidget(searchItem);

        searchEntity = new EditBox(this.font, colX[2], searchBoxY[2], colW, 20, Component.empty());
        searchEntity.setHint(Component.translatable("gui.bosschongsheng.config.search_entity_hint"));
        searchEntity.setResponder(s -> refreshEntityFilter());
        addRenderableWidget(searchEntity);

        for (int c = 0; c < 3; c++) {
            final int column = c;
            // 生物栏的排序按钮让出右侧 50px 给"杂项"过滤切换按钮
            int sortW = c == 2 ? colW - 50 : colW;
            Button btn = Button.builder(Component.empty(), b -> cycleSortMode(column))
                    .bounds(colX[c], sortY[c], sortW, SORT_H).build();
            sortButtons[c] = btn;
            addRenderableWidget(btn);
            updateSortButtonLabel(c);
        }

        // 生物栏：杂项实体（投掷物、船、空名实体等）显示/隐藏切换
        miscFilterButton = Button.builder(miscFilterLabel(), b -> {
            entityFilterEnabled = !entityFilterEnabled;
            miscFilterButton.setMessage(miscFilterLabel());
            refreshEntityFilter();
        }).bounds(colX[2] + colW - 46, sortY[2], 46, SORT_H).build();
        addRenderableWidget(miscFilterButton);

        int btnY = panelTop + panelH - 32;
        int btnW = 70;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.bosschongsheng.config.confirm"),
                b -> confirm())
                .bounds(panelLeft + 10, btnY, btnW, 22).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                b -> this.minecraft.setScreen(parent))
                .bounds(panelLeft + 10 + btnW + 10, btnY, btnW, 22).build());

        // 布局参数确定后，把构造时按未初始化布局计算的滚动量收敛到合法范围
        for (int c = 0; c < 3; c++) {
            setScroll(c, currentScroll(c));
        }
    }

    private void updateSortButtonLabel(int column) {
        String text = Component.translatable("gui.bosschongsheng.config.sort_prefix").getString()
                + Component.translatable(
                        "gui.bosschongsheng.config." + SORT_KEYS[sortMode[column]]).getString();
        int maxW = (column == 2 ? colW - 50 : colW) - 8;
        sortButtons[column].setMessage(Component.literal(this.font.plainSubstrByWidth(text, maxW)));
    }

    private Component miscFilterLabel() {
        return Component.translatable(entityFilterEnabled
                ? "gui.bosschongsheng.config.misc_hidden"
                : "gui.bosschongsheng.config.misc_shown");
    }

    private void cycleSortMode(int column) {
        if (column == 0) {
            // 结构：名称 → 维度 → 来源
            sortMode[0] = (sortMode[0] + 1) % 3;
        } else {
            // 物品 / 生物：名称 → 来源（无维度）
            sortMode[column] = sortMode[column] == SORT_SOURCE ? SORT_NAME : SORT_SOURCE;
        }
        updateSortButtonLabel(column);
        if (column == 0) {
            refreshStructureFilter();
        } else if (column == 1) {
            refreshItemFilter();
        } else {
            refreshEntityFilter();
        }
    }

    // ---------------- 搜索与排序 ----------------

    /** 解析搜索词：普通文本 + {@code @模组} 来源过滤词（模组 id 或显示名，大小写不敏感） */
    private static Query parseQuery(String raw) {
        List<String> sources = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (String token : raw.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("@") && token.length() > 1) {
                sources.add(token.substring(1));
            } else {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(token);
            }
        }
        return new Query(text.toString(), sources);
    }

    private static boolean matchesSource(String id, List<String> sourceFilters) {
        if (sourceFilters.isEmpty()) {
            return true;
        }
        String namespace = namespaceOf(id).toLowerCase(Locale.ROOT);
        String modName = modDisplayName(namespaceOf(id)).toLowerCase(Locale.ROOT);
        for (String f : sourceFilters) {
            if (!namespace.contains(f) && !modName.contains(f)) {
                return false;
            }
        }
        return true;
    }

    /** 编辑规则时把已选中的项固定到列表第一位（不受搜索/排序影响） */
    private static <T> List<T> pinSelectedFirst(List<T> list, java.util.function.Predicate<T> isSelected) {
        if (list.isEmpty()) {
            return list;
        }
        for (int i = 0; i < list.size(); i++) {
            if (isSelected.test(list.get(i))) {
                if (i == 0) {
                    return list;
                }
                List<T> result = new ArrayList<>(list.size());
                result.add(list.get(i));
                for (int j = 0; j < list.size(); j++) {
                    if (j != i) {
                        result.add(list.get(j));
                    }
                }
                return result;
            }
        }
        return list;
    }

    private void refreshStructureFilter() {
        Query q = parseQuery(searchStructure != null ? searchStructure.getValue() : "");
        int sort = sortMode[0];
        List<String> list = structureIds.stream()
                .filter(id -> matchesSource(id, q.sources()))
                .filter(id -> q.text().isEmpty()
                        || id.toLowerCase(Locale.ROOT).contains(q.text())
                        || DisplayNames.structure(id).toLowerCase(Locale.ROOT).contains(q.text()))
                .sorted(Comparator
                        .comparingInt((String id) -> sort == SORT_DIMENSION ? dimensionOfStructure(id) : 0)
                        .thenComparing(id -> sort == SORT_SOURCE
                                ? namespaceOf(id).toLowerCase(Locale.ROOT) : "")
                        .thenComparing(DisplayNames::structure))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        // 来源 / 维度排序时分组保持完整，选中项在组内置顶（由 rebuildView 处理）；名称排序全局置顶
        filteredStructures = sort == SORT_NAME
                ? pinSelectedFirst(list, id -> id.equals(selectedStructureId)) : list;
        rebuildView(0);
        scrollStructure = Math.min(scrollStructure, scrollMax(0));
    }

    private void refreshItemFilter() {
        Query q = parseQuery(searchItem != null ? searchItem.getValue() : "");
        int sort = sortMode[1];
        List<ItemEntry> list = allItems.stream()
                .filter(e -> matchesSource(e.id(), q.sources()))
                .filter(e -> q.text().isEmpty()
                        || e.id().toLowerCase(Locale.ROOT).contains(q.text())
                        || e.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q.text()))
                .sorted(Comparator
                        .comparing((ItemEntry e) -> sort == SORT_SOURCE
                                ? namespaceOf(e.id()).toLowerCase(Locale.ROOT) : "")
                        .thenComparing(e -> e.stack().getHoverName().getString()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        filteredItems = sort == SORT_SOURCE
                ? list : pinSelectedFirst(list, e -> e.id().equals(selectedItemId));
        rebuildView(1);
        scrollItem = Math.min(scrollItem, scrollMax(1));
    }

    private void refreshEntityFilter() {
        Query q = parseQuery(searchEntity != null ? searchEntity.getValue() : "");
        int sort = sortMode[2];
        List<String> list = allEntities.stream()
                .filter(id -> !entityFilterEnabled || isCreature(id))
                .filter(id -> matchesSource(id, q.sources()))
                .filter(id -> q.text().isEmpty()
                        || id.toLowerCase(Locale.ROOT).contains(q.text())
                        || entityName(id).toLowerCase(Locale.ROOT).contains(q.text()))
                .sorted(Comparator
                        .comparing((String id) -> sort == SORT_SOURCE
                                ? namespaceOf(id).toLowerCase(Locale.ROOT) : "")
                        .thenComparing(RulePickerScreen::entityName))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        filteredEntities = sort == SORT_SOURCE
                ? list : pinSelectedFirst(list, id -> id.equals(selectedEntityId));
        rebuildView(2);
        scrollEntity = Math.min(scrollEntity, scrollMax(2));
    }

    // ---------------- 来源分组视图 ----------------

    /** 视图行对象的 id 解析：结构/生物为 String，物品为 {@link ItemEntry} */
    private String entryId(int column, Object row) {
        return column == 1 ? ((ItemEntry) row).id() : (String) row;
    }

    private String selectedIdOf(int column) {
        return switch (column) {
            case 0 -> selectedStructureId;
            case 1 -> selectedItemId;
            default -> selectedEntityId;
        };
    }

    /**
     * 按当前排序构建实际渲染的视图行：
     * <ul>
     *   <li>来源排序：按模组分组，组按模组显示名排序；</li>
     *   <li>结构栏维度排序：按主世界/下界/末地分组；</li>
     *   <li>其余排序：平铺列表。</li>
     * </ul>
     * 分组视图中已选中的项固定在本组第一项。
     */
    private void rebuildView(int column) {
        List<Object> view = viewRows.get(column);
        view.clear();
        List<?> entries = switch (column) {
            case 0 -> filteredStructures;
            case 1 -> filteredItems;
            default -> filteredEntities;
        };

        boolean byDimension = column == 0 && sortMode[column] == SORT_DIMENSION;
        boolean grouped = byDimension || sortMode[column] == SORT_SOURCE;
        if (!grouped) {
            view.addAll(entries);
            return;
        }

        // entries 已按分组键 + 名称排序，用 LinkedHashMap 分组保持组内顺序
        Map<String, List<Object>> groups = new java.util.LinkedHashMap<>();
        for (Object e : entries) {
            groups.computeIfAbsent(groupKey(column, e), k -> new ArrayList<>()).add(e);
        }
        List<String> keys = new ArrayList<>(groups.keySet());
        if (byDimension) {
            // 维度键为 "0"/"1"/"2"，按维度顺序（主世界→下界→末地）排列
            keys.sort(Comparator.comparingInt(Integer::parseInt));
        } else {
            keys.sort(Comparator
                    .comparing((String ns) -> modDisplayName(ns).toLowerCase(Locale.ROOT))
                    .thenComparing(ns -> ns.toLowerCase(Locale.ROOT)));
        }

        String selected = selectedIdOf(column);
        for (String key : keys) {
            List<Object> group = groups.get(key);
            // 选中项在组内置顶
            if (selected != null && !selected.isEmpty()) {
                for (int i = 0; i < group.size(); i++) {
                    if (entryId(column, group.get(i)).equals(selected) && i > 0) {
                        group.add(0, group.remove(i));
                        break;
                    }
                }
            }
            view.add(new HeaderRow(groupTitle(column, key), group.size()));
            view.addAll(group);
        }

        // 首次构建（打开编辑界面）时滚动到已选中项所在分组
        if (revealSelected[column] && selected != null && !selected.isEmpty()) {
            revealSelected[column] = false;
            for (int i = 0; i < view.size(); i++) {
                Object row = view.get(i);
                if (!(row instanceof HeaderRow) && entryId(column, row).equals(selected)) {
                    setScroll(column, Math.max(0, i - 1));
                    break;
                }
            }
        }
    }

    /** 分组键：维度排序为维度序号字符串，其余为 id 的命名空间 */
    private String groupKey(int column, Object entry) {
        String id = entryId(column, entry);
        if (column == 0 && sortMode[column] == SORT_DIMENSION) {
            return String.valueOf(dimensionOfStructure(id));
        }
        return namespaceOf(id);
    }

    /** 分组标题：维度排序为维度显示名，其余为模组显示名 */
    private String groupTitle(int column, String key) {
        if (column == 0 && sortMode[column] == SORT_DIMENSION) {
            return Component.translatable(DIM_KEYS[Integer.parseInt(key)]).getString();
        }
        return modDisplayName(key);
    }

    private void confirm() {
        if (selectedStructureId == null || selectedStructureId.isEmpty()
                || selectedItemId == null || selectedItemId.isEmpty()
                || selectedEntityId == null || selectedEntityId.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(
                new AddBossRulePayload(selectedStructureId, selectedItemId, selectedEntityId));
        PacketDistributor.sendToServer(new RequestConfigPayload());
        this.minecraft.setScreen(parent);
    }

    // ---------------- 滚动条与点击 ----------------

    private int totalCount(int column) {
        return viewRows.get(column).size();
    }

    private int currentScroll(int column) {
        return switch (column) {
            case 0 -> scrollStructure;
            case 1 -> scrollItem;
            default -> scrollEntity;
        };
    }

    private void setScroll(int column, int value) {
        int v = Math.max(0, Math.min(scrollMax(column), value));
        switch (column) {
            case 0 -> scrollStructure = v;
            case 1 -> scrollItem = v;
            default -> scrollEntity = v;
        }
    }

    private int scrollMax(int column) {
        return Math.max(0, totalCount(column) - visibleRows);
    }

    private boolean scrollBarActive(int column) {
        return totalCount(column) > visibleRows;
    }

    private int scrollBarX(int column) {
        return colX[column] + colW - SCROLL_BAR_W - 1;
    }

    /** 滑块高度 */
    private int thumbHeight(int column) {
        return Math.max(SCROLL_THUMB_MIN, listAreaH * visibleRows / totalCount(column));
    }

    private int thumbY(int column) {
        int max = scrollMax(column);
        int thumbH = thumbHeight(column);
        int travel = listAreaH - thumbH;
        return listY[column] + (max == 0 ? 0 : travel * currentScroll(column) / max);
    }

    /** 根据鼠标在轨道上的位置换算滚动量（拖滑块与点轨道跳转共用） */
    private void applyDragScroll(int column, double mouseY) {
        int thumbH = thumbHeight(column);
        double ratio = (mouseY - listY[column] - thumbH / 2.0) / (listAreaH - thumbH);
        ratio = Math.max(0, Math.min(1, ratio));
        setScroll(column, (int) Math.round(ratio * scrollMax(column)));
    }

    private boolean isOverScrollBar(int column, double mouseX, double mouseY) {
        return scrollBarActive(column)
                && mouseX >= scrollBarX(column) - 2 && mouseX < scrollBarX(column) + SCROLL_BAR_W + 2
                && mouseY >= listY[column] && mouseY < listY[column] + listAreaH;
    }

    private boolean isOverList(int column, double mouseX, double mouseY) {
        return mouseX >= colX[column] && mouseX < colX[column] + colW
                && mouseY >= listY[column] && mouseY < listY[column] + listAreaH;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 1) 滚动条优先：开始拖拽（点轨道任意位置也可跳转）
            for (int c = 0; c < 3; c++) {
                if (isOverScrollBar(c, mouseX, mouseY)) {
                    draggingScrollColumn = c;
                    applyDragScroll(c, mouseY);
                    return true;
                }
            }
            // 2) 点击列表行进行选择
            for (int c = 0; c < 3; c++) {
                if (isOverList(c, mouseX, mouseY)) {
                    int row = (int) ((mouseY - listY[c]) / ROW_H) + currentScroll(c);
                    if (selectRow(c, row)) {
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** @return 该行是否为可选择的条目且已选中（分组标题行不可选） */
    private boolean selectRow(int column, int row) {
        List<Object> view = viewRows.get(column);
        if (row < 0 || row >= view.size()) {
            return false;
        }
        Object obj = view.get(row);
        if (obj instanceof HeaderRow) {
            return false;
        }
        String id = entryId(column, obj);
        if (column == 0) {
            selectedStructureId = id;
        } else if (column == 1) {
            selectedItemId = id;
        } else {
            selectedEntityId = id;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollColumn >= 0) {
            applyDragScroll(draggingScrollColumn, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollColumn >= 0) {
            draggingScrollColumn = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (int c = 0; c < 3; c++) {
            if (isOverList(c, mouseX, mouseY)) {
                setScroll(c, currentScroll(c) - (int) scrollY);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---------------- 渲染 ----------------

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.21.1 默认实现会对游戏背景运行模糊后处理，用一层 30% 黑色遮罩代替
        graphics.fill(0, 0, this.width, this.height, 0x4D000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, PANEL_BG);
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + 1, PANEL_BORDER);
        graphics.fill(panelLeft, panelTop + panelH - 1, panelLeft + panelW, panelTop + panelH, PANEL_BORDER);
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelTop + panelH, PANEL_BORDER);
        graphics.fill(panelLeft + panelW - 1, panelTop, panelLeft + panelW, panelTop + panelH, PANEL_BORDER);
        graphics.drawString(this.font, this.title, panelLeft + 10, panelTop + 6, TITLE_COLOR, true);

        graphics.drawString(this.font,
                Component.translatable("gui.bosschongsheng.config.structure"),
                colX[0], labelY[0], LABEL_COLOR, true);
        graphics.drawString(this.font,
                Component.translatable("gui.bosschongsheng.config.trigger_item"),
                colX[1], labelY[1], LABEL_COLOR, true);
        graphics.drawString(this.font,
                Component.translatable("gui.bosschongsheng.config.entity"),
                colX[2], labelY[2], LABEL_COLOR, true);
        drawCount(graphics, 0, filteredStructures.size(), structureIds.size());
        drawCount(graphics, 1, filteredItems.size(), allItems.size());
        drawCount(graphics, 2, filteredEntities.size(), allEntities.size());

        for (int c = 0; c < 3; c++) {
            graphics.fill(colX[c] - 2, listY[c] - 2,
                    colX[c] + colW + 2, listY[c] + listAreaH + 2, LIST_BG);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        drawStructureRows(graphics, mouseX, mouseY);
        drawItemRows(graphics, mouseX, mouseY);
        drawEntityRows(graphics, mouseX, mouseY);

        for (int c = 0; c < 3; c++) {
            drawScrollBar(graphics, c, mouseX, mouseY);
        }
    }

    private void drawCount(GuiGraphics graphics, int column, int matched, int total) {
        String text = matched + "/" + total;
        int w = this.font.width(text);
        graphics.drawString(this.font, text, colX[column] + colW - w, labelY[column], COUNT_COLOR, true);
    }

    private void drawRowBackground(GuiGraphics graphics, int column, int rowY, boolean selected,
                                   int mouseX, int mouseY) {
        int left = colX[column];
        int width = colW - (scrollBarActive(column) ? SCROLL_BAR_W + 2 : 0);
        if (selected) {
            graphics.fill(left, rowY, left + width, rowY + ROW_H, SELECTED_BG);
        } else if (mouseX >= left && mouseX < left + width
                && mouseY >= rowY && mouseY < rowY + ROW_H) {
            graphics.fill(left, rowY, left + width, rowY + ROW_H, ROW_HOVER);
        }
    }

    private void drawScrollBar(GuiGraphics graphics, int column, int mouseX, int mouseY) {
        if (!scrollBarActive(column)) {
            return;
        }
        int x = scrollBarX(column);
        int top = listY[column];
        graphics.fill(x, top, x + SCROLL_BAR_W, top + listAreaH, SCROLL_TRACK);

        int thumbH = thumbHeight(column);
        int y = thumbY(column);
        boolean active = draggingScrollColumn == column || isOverScrollBar(column, mouseX, mouseY);
        graphics.fill(x, y, x + SCROLL_BAR_W, y + thumbH, active ? SCROLL_THUMB_ACTIVE : SCROLL_THUMB);
    }

    /** 分组标题行：组名 + 右侧数量 */
    private record HeaderRow(String title, int count) {
    }

    /** 超长文本截断并加省略号；maxW 异常小（窄栏）时至少保留 1px */
    private String ellipsize(String text, int maxW) {
        if (this.font.width(text) <= maxW) {
            return text;
        }
        int dots = this.font.width("..");
        return this.font.plainSubstrByWidth(text, Math.max(1, maxW - dots)) + "..";
    }

    /** 分组标题行：组名 + 右侧数量 */
    private void drawHeaderRow(GuiGraphics graphics, int column, int rowY, HeaderRow header) {
        int left = colX[column];
        int width = colW - (scrollBarActive(column) ? SCROLL_BAR_W + 2 : 0);
        graphics.fill(left, rowY, left + width, rowY + ROW_H, HEADER_BG);
        graphics.fill(left, rowY + ROW_H - 1, left + width, rowY + ROW_H, HEADER_LINE);

        String count = String.valueOf(header.count());
        int countW = this.font.width(count);
        graphics.drawString(this.font, count, left + width - 4 - countW, rowY + 5, COUNT_COLOR, false);

        int titleMaxW = width - countW - 12;
        String shown = ellipsize(header.title(), titleMaxW);
        graphics.drawString(this.font, shown, left + 3, rowY + 5, HEADER_COLOR, false);
    }

    private void drawStructureRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = colX[0];
        int top = listY[0];
        List<Object> view = viewRows.get(0);
        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollStructure + i;
            if (idx >= view.size()) {
                break;
            }
            Object row = view.get(idx);
            int rowY = top + i * ROW_H;
            if (row instanceof HeaderRow header) {
                drawHeaderRow(graphics, 0, rowY, header);
                continue;
            }
            String id = (String) row;
            drawRowBackground(graphics, 0, rowY, id.equals(selectedStructureId), mouseX, mouseY);
            String fullName = DisplayNames.structure(id);
            int maxW = colW - (scrollBarActive(0) ? SCROLL_BAR_W + 8 : 10);
            String name = ellipsize(fullName, maxW);
            graphics.drawString(this.font, name, left + 4, rowY + 5, TEXT_COLOR, false);
        }
    }

    private void drawItemRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = colX[1];
        int top = listY[1];
        List<Object> view = viewRows.get(1);
        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollItem + i;
            if (idx >= view.size()) {
                break;
            }
            Object row = view.get(idx);
            int rowY = top + i * ROW_H;
            if (row instanceof HeaderRow header) {
                drawHeaderRow(graphics, 1, rowY, header);
                continue;
            }
            ItemEntry e = (ItemEntry) row;
            drawRowBackground(graphics, 1, rowY, e.id().equals(selectedItemId), mouseX, mouseY);
            graphics.renderItem(e.stack(), left + 2, rowY + 1);
            String fullName = e.stack().getHoverName().getString();
            int maxW = colW - (scrollBarActive(1) ? SCROLL_BAR_W + 26 : 28);
            String name = ellipsize(fullName, maxW);
            graphics.drawString(this.font, name, left + 20, rowY + 5, TEXT_COLOR, false);
        }
    }

    /** 生物 id → 实体实例缓存（用于 GUI 内 3D 渲染）；非 LivingEntity（船、盔甲架等）缓存 null */
    private static final Map<String, LivingEntity> ENTITY_RENDER_CACHE = new HashMap<>();

    private static LivingEntity entityForRender(String id) {
        if (ENTITY_RENDER_CACHE.containsKey(id)) {
            return ENTITY_RENDER_CACHE.get(id);
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.get(key);
        LivingEntity entity = null;
        if (type != null) {
            try {
                var created = type.create(net.minecraft.client.Minecraft.getInstance().level);
                if (created instanceof LivingEntity living) {
                    entity = living;
                }
            } catch (Throwable ignored) {
                // 某些实体在无世界环境创建会抛异常，退回 null
            }
        }
        ENTITY_RENDER_CACHE.put(id, entity);
        return entity;
    }

    /** 图标姿态：水平转向角（看到侧面）与俯视角（看到顶面），固定不随鼠标变化 */
    private static final float ICON_YAW_DEG = 40f;
    private static final float ICON_PITCH_DEG = 20f;

    /**
     * 在矩形框内渲染固定 45° 三视角的生物模型（正面+侧面+顶面）。
     * 参照原版 InventoryScreen#renderEntityInInventoryFollowsAngle 反编译实现，
     * 区别：身体与头部保持一致朝向，不做"眼睛跟随鼠标"的两倍头部偏转。
     */
    private static void renderEntityIcon(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                         LivingEntity entity, float renderScale) {
        float cx = (x1 + x2) / 2.0f;
        float cy = (y1 + y2) / 2.0f;
        graphics.enableScissor(x1, y1, x2, y2);

        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        // 负 X 旋转 = 相机在模型斜上方（正值会看到脚底）
        Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(-ICON_PITCH_DEG));
        pose.mul(camera);

        float oldBodyYaw = entity.yBodyRot;
        float oldYaw = entity.getYRot();
        float oldPitch = entity.getXRot();
        float oldHeadYawO = entity.yHeadRotO;
        float oldHeadYaw = entity.yHeadRot;
        entity.yBodyRot = 180.0f + ICON_YAW_DEG;
        entity.setYRot(180.0f + ICON_YAW_DEG);
        entity.setXRot(ICON_PITCH_DEG * 0.6f); // 头部略低，面向斜上方的相机
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();

        float scale = entity.getScale();
        Vector3f translate = new Vector3f(0.0f, entity.getBbHeight() / 2.0f + 0.0625f * scale, 0.0f);
        InventoryScreen.renderEntityInInventory(
                graphics, cx, cy, renderScale, translate, pose, camera, entity);

        entity.yBodyRot = oldBodyYaw;
        entity.setYRot(oldYaw);
        entity.setXRot(oldPitch);
        entity.yHeadRotO = oldHeadYawO;
        entity.yHeadRot = oldHeadYaw;
        graphics.disableScissor();
    }

    private void drawEntityRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = colX[2];
        int top = listY[2];
        List<Object> view = viewRows.get(2);
        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollEntity + i;
            if (idx >= view.size()) {
                break;
            }
            Object rowObj = view.get(idx);
            int rowY = top + i * ROW_H;
            if (rowObj instanceof HeaderRow header) {
                drawHeaderRow(graphics, 2, rowY, header);
                continue;
            }
            String id = (String) rowObj;
            drawRowBackground(graphics, 2, rowY, id.equals(selectedEntityId), mouseX, mouseY);
            LivingEntity entity = entityForRender(id);
            if (entity != null) {
                int box = ROW_H - 2;
                int boxX1 = left + 1;
                int boxY1 = rowY + 1;
                int boxX2 = left + 1 + box;
                int boxY2 = rowY + 1 + box;
                // 按最长边 fit：45° 转向后水平投影为 w*√2，俯视后垂直投影含 h·cos + w·sin；
                // 再留 5% 余量给翅膀、长角等超出包围盒的部件，保证整体落在框内
                float entityScale = entity.getScale();
                float baseH = entity.getBbHeight() / entityScale;
                float baseW = Math.max(entity.getBbWidth() / entityScale, 0.5f);
                double rad = Math.toRadians(ICON_PITCH_DEG);
                float effW = baseW * 1.4142f;
                float effH = (float) (baseH * Math.cos(rad) + baseW * Math.sin(rad));
                float fitted = (box - 2) / Math.max(effW, effH) * 0.95f;
                renderEntityIcon(graphics, boxX1, boxY1, boxX2, boxY2, entity, fitted);
            }
            // 显示杂项时，被过滤的实体名称用灰色区分；名称为空（无名实体）时退回显示 id
            boolean misc = !isCreature(id);
            String fullName = entityName(id);
            if (misc && fullName.isBlank()) {
                fullName = id;
            }
            int textX = left + (entity != null ? ROW_H + 2 : 4);
            int maxW = colW - (scrollBarActive(2) ? SCROLL_BAR_W + 8 : 10)
                    - (entity != null ? ROW_H + 6 : 4);
            String name = ellipsize(fullName, maxW);
            graphics.drawString(this.font, name, textX, rowY + 5,
                    misc ? MISC_TEXT_COLOR : TEXT_COLOR, false);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    /** 解析后的搜索条件 */
    private record Query(String text, List<String> sources) {
    }

    private record ItemEntry(String id, ItemStack stack) {
    }
}
