package com.bosschongsheng.client;

import com.bosschongsheng.network.AddBossRulePayload;
import com.bosschongsheng.network.RequestConfigPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 添加/编辑规则界面：结构 / 触发物品 / 召唤实体 三栏选择。
 *
 * <p>每栏支持「分类下拉 + 关键字搜索」叠加过滤；布局随窗口尺寸自适应。</p>
 */
public class RulePickerScreen extends Screen {
    private static final int ROW_H = 18;
    private static final int CAT_H = 15;
    private static final int POPUP_ITEM_H = 15;
    private static final int POPUP_MAX_VISIBLE = 8;

    private static final int PANEL_BG = 0xFF2A2A35;
    private static final int PANEL_BORDER = 0xFF3D3D52;
    private static final int LIST_BG = 0xFF1E1E26;
    private static final int ROW_HOVER = 0xFF363648;
    private static final int SELECTED_BG = 0xFF2D3A5C;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int COUNT_COLOR = 0xFF9AA0B4;

    // ---- 结构固定分组（顺序即优先级，other 为兜底）----
    private static final String[] STRUCTURE_GROUPS = {"village", "outpost", "nether", "end", "ocean", "dungeon"};
    private static final String[][] STRUCTURE_KEYWORDS = {
            {"village"},
            {"outpost", "camp", "pillager"},
            {"nether"},
            {}, // end 走边界匹配，避免误伤 legend/friend 等
            {"ocean", "sea", "beach", "shipwreck", "reef", "aquatic", "underwater", "monument"},
            {"dungeon", "ruins", "stronghold", "fortress", "bastion", "citadel", "castle", "tower",
                    "temple", "pyramid", "mansion", "manor", "palace", "tomb", "mineshaft", "nest",
                    "arena", "labyrinth", "city", "forge", "keep", "shrine", "igloo", "hut", "well",
                    "monument", "altar", "shack"}
    };
    private static final int STRUCT_OTHER = 6;

    private final BossRespawnConfigScreen parent;
    private final boolean isEdit;

    private final List<String> structureIds = new ArrayList<>();
    private final List<ItemEntry> allItems = new ArrayList<>();
    private final List<String> allEntities = new ArrayList<>();

    private EditBox searchStructure;
    private EditBox searchItem;
    private EditBox searchEntity;
    private final Button[] categoryButtons = new Button[3];

    private List<String> filteredStructures = new ArrayList<>();
    private List<ItemEntry> filteredItems = new ArrayList<>();
    private List<String> filteredEntities = new ArrayList<>();

    private String selectedStructureId;
    private String selectedItemId;
    private String selectedEntityId;

    private int scrollStructure;
    private int scrollItem;
    private int scrollEntity;

    // 三栏的分类列表（首项恒为"全部"）、当前选中索引
    private final List<Category>[] categories = new List[3];
    private final int[] categorySelection = {0, 0, 0};
    // 条目 → 在 categories 中的索引（-1 表示该组因数量为 0 未列入）
    private int[] structureGroupToCat = new int[0];
    private int[] entityGroupToCat = new int[0];
    private int[] tabCategoryIndex = new int[0];
    private int itemOtherCat = -1;

    // 下拉弹窗状态：-1 关闭，0/1/2 对应三栏
    private int openCategoryColumn = -1;
    private int popupScroll;

    private int panelLeft;
    private int panelTop;
    private int panelW;
    private int panelH;
    private boolean layoutHorizontal;
    private int colW;
    private int visibleRows;
    private int listAreaH;

    /** 三栏各自的 x、标签 y、分类按钮 y、搜索框 y、列表 y */
    private final int[] colX = new int[3];
    private final int[] labelY = new int[3];
    private final int[] catY = new int[3];
    private final int[] searchBoxY = new int[3];
    private final int[] listY = new int[3];

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

        structureIds.addAll(BossRespawnConfigClient.getStructureIds());
        structureIds.sort(Comparator.comparing(DisplayNames::structure));
        filteredStructures.addAll(structureIds);

        for (Item item : BuiltInRegistries.ITEM.stream().toList()) {
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            allItems.add(new ItemEntry(id.toString(), new ItemStack(item)));
        }
        allItems.sort(Comparator.comparing(e -> e.stack().getHoverName().getString()));
        filteredItems.addAll(allItems);

        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            allEntities.add(id.toString());
        }
        allEntities.sort(Comparator.comparing(RulePickerScreen::entityName));
        filteredEntities.addAll(allEntities);

        buildStructureCategories();
        buildItemCategories();
        buildEntityCategories();
    }

    // ---------------- 分类构建 ----------------

    private static String category(String suffix) {
        return Component.translatable("gui.bosschongsheng.config.category." + suffix).getString();
    }

    private void buildStructureCategories() {
        int[] counts = new int[STRUCT_OTHER + 1];
        for (String id : structureIds) {
            counts[classifyStructure(id)]++;
        }
        List<Category> cats = new ArrayList<>();
        cats.add(new Category(category("all"), structureIds.size()));
        structureGroupToCat = new int[STRUCT_OTHER + 1];
        for (int g = 0; g < STRUCTURE_GROUPS.length; g++) {
            structureGroupToCat[g] = counts[g] > 0
                    ? addCategory(cats, category("structure." + STRUCTURE_GROUPS[g]), counts[g]) : -1;
        }
        structureGroupToCat[STRUCT_OTHER] = counts[STRUCT_OTHER] > 0
                ? addCategory(cats, category("structure.other"), counts[STRUCT_OTHER]) : -1;
        categories[0] = cats;
    }

    private void buildItemCategories() {
        // 物品分类跟随游戏内实际的创造模式物品栏（含各模组自建页）
        List<CreativeModeTab> tabs = CreativeModeTabs.allTabs();
        List<Set<Item>> itemsPerTab = new ArrayList<>(tabs.size());
        for (CreativeModeTab tab : tabs) {
            Set<Item> set = new HashSet<>();
            for (ItemStack stack : tab.getDisplayItems()) {
                set.add(stack.getItem());
            }
            itemsPerTab.add(set);
        }

        int[] tabCounts = new int[tabs.size()];
        int otherCount = 0;
        for (ItemEntry entry : allItems) {
            int tab = -1;
            Item item = entry.stack().getItem();
            for (int t = 0; t < tabs.size(); t++) {
                if (itemsPerTab.get(t).contains(item)) {
                    tab = t;
                    break;
                }
            }
            entry.categoryGroup = tab;
            if (tab >= 0) {
                tabCounts[tab]++;
            } else {
                otherCount++;
            }
        }

        List<Category> cats = new ArrayList<>();
        cats.add(new Category(category("all"), allItems.size()));
        tabCategoryIndex = new int[tabs.size()];
        for (int t = 0; t < tabs.size(); t++) {
            tabCategoryIndex[t] = tabCounts[t] > 0
                    ? addCategory(cats, tabs.get(t).getDisplayName().getString(), tabCounts[t]) : -1;
        }
        itemOtherCat = otherCount > 0 ? addCategory(cats, category("item.other"), otherCount) : -1;
        categories[1] = cats;
    }

    private void buildEntityCategories() {
        int[] counts = new int[5];
        for (String id : allEntities) {
            counts[classifyEntity(id)]++;
        }
        String[] suffix = {"creature", "monster", "water", "ambient", "other"};
        List<Category> cats = new ArrayList<>();
        cats.add(new Category(category("all"), allEntities.size()));
        entityGroupToCat = new int[5];
        for (int g = 0; g < 5; g++) {
            entityGroupToCat[g] = counts[g] > 0
                    ? addCategory(cats, category("entity." + suffix[g]), counts[g]) : -1;
        }
        categories[2] = cats;
    }

    private static int addCategory(List<Category> cats, String label, int count) {
        cats.add(new Category(label, count));
        return cats.size() - 1;
    }

    private static int classifyStructure(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (int g = 0; g < STRUCTURE_GROUPS.length; g++) {
            if (g == 3) {
                // end 边界匹配，避免误伤 legend、defend 等
                if (s.contains("end_") || s.contains("_end") || s.contains("/end")
                        || s.contains("end/") || s.endsWith("/end") || s.equals("end")) {
                    return 3;
                }
                continue;
            }
            for (String kw : STRUCTURE_KEYWORDS[g]) {
                if (s.contains(kw)) {
                    return g;
                }
            }
        }
        return STRUCT_OTHER;
    }

    private static int classifyEntity(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.get(key);
        if (type == null) {
            return 4;
        }
        MobCategory cat = type.getCategory();
        return switch (cat) {
            case CREATURE -> 0;
            case MONSTER -> 1;
            case WATER_CREATURE, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> 2;
            case AMBIENT -> 3;
            default -> 4;
        };
    }

    private static String entityName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.get(key);
        return type == null ? id : type.getDescription().getString();
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
                catY[c] = panelTop + 31;
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
                catY[c] = blockTop + 12;
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
            Button btn = Button.builder(Component.empty(), b -> toggleCategoryPopup(column))
                    .bounds(colX[c], catY[c], colW, CAT_H).build();
            categoryButtons[c] = btn;
            addRenderableWidget(btn);
            updateCategoryButtonLabel(c);
        }

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
    }

    private void updateCategoryButtonLabel(int column) {
        Category cat = categories[column].get(categorySelection[column]);
        String text = cat.label() + " ▾";
        String clipped = this.font.plainSubstrByWidth(text, colW - 8);
        categoryButtons[column].setMessage(Component.literal(clipped));
    }

    // ---------------- 过滤 ----------------

    private boolean matchesStructureCategory(String id) {
        if (categorySelection[0] == 0) {
            return true;
        }
        return structureGroupToCat[classifyStructure(id)] == categorySelection[0];
    }

    private boolean matchesItemCategory(ItemEntry entry) {
        if (categorySelection[1] == 0) {
            return true;
        }
        int cat;
        if (entry.categoryGroup < 0) {
            cat = itemOtherCat;
        } else {
            cat = tabCategoryIndex[entry.categoryGroup];
        }
        return cat == categorySelection[1];
    }

    private boolean matchesEntityCategory(String id) {
        if (categorySelection[2] == 0) {
            return true;
        }
        return entityGroupToCat[classifyEntity(id)] == categorySelection[2];
    }

    private void refreshStructureFilter() {
        String q = searchStructure != null ? searchStructure.getValue().toLowerCase(Locale.ROOT).trim() : "";
        filteredStructures = structureIds.stream()
                .filter(this::matchesStructureCategory)
                .filter(id -> q.isEmpty()
                        || id.toLowerCase(Locale.ROOT).contains(q)
                        || DisplayNames.structure(id).toLowerCase(Locale.ROOT).contains(q))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        scrollStructure = Math.min(scrollStructure, Math.max(0, filteredStructures.size() - visibleRows));
    }

    private void refreshItemFilter() {
        String q = searchItem != null ? searchItem.getValue().toLowerCase(Locale.ROOT).trim() : "";
        filteredItems = allItems.stream()
                .filter(this::matchesItemCategory)
                .filter(e -> q.isEmpty()
                        || e.id().toLowerCase(Locale.ROOT).contains(q)
                        || e.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        scrollItem = Math.min(scrollItem, Math.max(0, filteredItems.size() - visibleRows));
    }

    private void refreshEntityFilter() {
        String q = searchEntity != null ? searchEntity.getValue().toLowerCase(Locale.ROOT).trim() : "";
        filteredEntities = allEntities.stream()
                .filter(this::matchesEntityCategory)
                .filter(id -> q.isEmpty()
                        || id.toLowerCase(Locale.ROOT).contains(q)
                        || entityName(id).toLowerCase(Locale.ROOT).contains(q))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        scrollEntity = Math.min(scrollEntity, Math.max(0, filteredEntities.size() - visibleRows));
    }

    private void selectCategory(int column, int index) {
        categorySelection[column] = index;
        updateCategoryButtonLabel(column);
        if (column == 0) {
            refreshStructureFilter();
        } else if (column == 1) {
            refreshItemFilter();
        } else {
            refreshEntityFilter();
        }
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

    @Override
    public void tick() {
        super.tick();
        refreshStructureFilter();
        refreshItemFilter();
        refreshEntityFilter();
    }

    // ---------------- 分类下拉弹窗 ----------------

    private void toggleCategoryPopup(int column) {
        if (openCategoryColumn == column) {
            openCategoryColumn = -1;
        } else {
            openCategoryColumn = column;
            popupScroll = 0;
        }
    }

    /** 返回 [x, y, w, h]；空间不足时向上展开 */
    private int[] popupBounds() {
        int x = colX[openCategoryColumn];
        int h = Math.min(categories[openCategoryColumn].size(), POPUP_MAX_VISIBLE) * POPUP_ITEM_H + 2;
        int y = catY[openCategoryColumn] + CAT_H + 2;
        if (y + h > this.height - 2) {
            y = catY[openCategoryColumn] - h - 2;
        }
        return new int[]{x, y, colW, h};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (openCategoryColumn >= 0) {
            int[] b = popupBounds();
            if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                int index = (int) ((mouseY - b[1] - 1) / POPUP_ITEM_H) + popupScroll;
                List<Category> cats = categories[openCategoryColumn];
                if (index >= 0 && index < cats.size()) {
                    selectCategory(openCategoryColumn, index);
                }
                openCategoryColumn = -1;
                return true;
            }
            // 点到弹窗外部：关闭，同时把点击继续交给下方控件（如搜索框）
            openCategoryColumn = -1;
            return super.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (openCategoryColumn >= 0) {
            int[] b = popupBounds();
            if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                int max = Math.max(0, categories[openCategoryColumn].size() - POPUP_MAX_VISIBLE);
                popupScroll = (int) Math.max(0, Math.min(max, popupScroll - scrollY));
                return true;
            }
        }
        for (int c = 0; c < 3; c++) {
            int left = colX[c];
            int top = listY[c];
            if (mouseX >= left && mouseX < left + colW
                    && mouseY >= top && mouseY < top + listAreaH) {
                int max;
                if (c == 0) {
                    max = Math.max(0, filteredStructures.size() - visibleRows);
                    scrollStructure = (int) Math.max(0, Math.min(max, scrollStructure - scrollY));
                } else if (c == 1) {
                    max = Math.max(0, filteredItems.size() - visibleRows);
                    scrollItem = (int) Math.max(0, Math.min(max, scrollItem - scrollY));
                } else {
                    max = Math.max(0, filteredEntities.size() - visibleRows);
                    scrollEntity = (int) Math.max(0, Math.min(max, scrollEntity - scrollY));
                }
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

        if (openCategoryColumn >= 0) {
            // 物品图标(renderItem)在更高的 Z 层渲染，会穿透普通 z=0 的弹窗填充，
            // 把整个弹窗抬到更高 Z 层，避免图标/下层文字与弹窗文字重叠
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            drawCategoryPopup(graphics, mouseX, mouseY);
            graphics.pose().popPose();
        }
    }

    private void drawCount(GuiGraphics graphics, int column, int matched, int total) {
        String text = matched + "/" + total;
        int w = this.font.width(text);
        graphics.drawString(this.font, text, colX[column] + colW - w, labelY[column], COUNT_COLOR, true);
    }

    private void drawStructureRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = colX[0];
        int top = listY[0];
        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollStructure + i;
            if (idx >= filteredStructures.size()) {
                break;
            }
            String id = filteredStructures.get(idx);
            int rowY = top + i * ROW_H;
            if (id.equals(selectedStructureId)) {
                graphics.fill(left, rowY, left + colW, rowY + ROW_H, SELECTED_BG);
            } else if (mouseX >= left && mouseX < left + colW
                    && mouseY >= rowY && mouseY < rowY + ROW_H) {
                graphics.fill(left, rowY, left + colW, rowY + ROW_H, ROW_HOVER);
            }
            String name = DisplayNames.structure(id);
            if (this.font.width(name) > colW - 6) {
                name = this.font.plainSubstrByWidth(name, colW - 10) + "..";
            }
            graphics.drawString(this.font, name, left + 4, rowY + 5, TEXT_COLOR, false);
        }
    }

    private void drawItemRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = colX[1];
        int top = listY[1];
        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollItem + i;
            if (idx >= filteredItems.size()) {
                break;
            }
            ItemEntry e = filteredItems.get(idx);
            int rowY = top + i * ROW_H;
            if (e.id().equals(selectedItemId)) {
                graphics.fill(left, rowY, left + colW, rowY + ROW_H, SELECTED_BG);
            } else if (mouseX >= left && mouseX < left + colW
                    && mouseY >= rowY && mouseY < rowY + ROW_H) {
                graphics.fill(left, rowY, left + colW, rowY + ROW_H, ROW_HOVER);
            }
            graphics.renderItem(e.stack(), left + 2, rowY + 1);
            String name = e.stack().getHoverName().getString();
            if (this.font.width(name) > colW - 24) {
                name = this.font.plainSubstrByWidth(name, colW - 28) + "..";
            }
            graphics.drawString(this.font, name, left + 20, rowY + 5, TEXT_COLOR, false);
        }
    }

    private void drawEntityRows(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = colX[2];
        int top = listY[2];
        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollEntity + i;
            if (idx >= filteredEntities.size()) {
                break;
            }
            String id = filteredEntities.get(idx);
            int rowY = top + i * ROW_H;
            if (id.equals(selectedEntityId)) {
                graphics.fill(left, rowY, left + colW, rowY + ROW_H, SELECTED_BG);
            } else if (mouseX >= left && mouseX < left + colW
                    && mouseY >= rowY && mouseY < rowY + ROW_H) {
                graphics.fill(left, rowY, left + colW, rowY + ROW_H, ROW_HOVER);
            }
            String name = entityName(id);
            if (this.font.width(name) > colW - 6) {
                name = this.font.plainSubstrByWidth(name, colW - 10) + "..";
            }
            graphics.drawString(this.font, name, left + 4, rowY + 5, TEXT_COLOR, false);
        }
    }

    private void drawCategoryPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        int[] b = popupBounds();
        int x = b[0], y = b[1], w = b[2], h = b[3];
        List<Category> cats = categories[openCategoryColumn];

        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, PANEL_BORDER);
        graphics.fill(x, y, x + w, y + h, LIST_BG);

        int visible = Math.min(POPUP_MAX_VISIBLE, cats.size());
        for (int i = 0; i < visible; i++) {
            int index = i + popupScroll;
            Category cat = cats.get(index);
            int rowY = y + 1 + i * POPUP_ITEM_H;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + POPUP_ITEM_H;
            boolean selected = index == categorySelection[openCategoryColumn];
            if (selected) {
                graphics.fill(x, rowY, x + w, rowY + POPUP_ITEM_H, SELECTED_BG);
            } else if (hovered) {
                graphics.fill(x, rowY, x + w, rowY + POPUP_ITEM_H, ROW_HOVER);
            }
            String label = this.font.plainSubstrByWidth(
                    (selected ? "✔ " : "") + cat.label(), w - 34);
            graphics.drawString(this.font, label, x + 4, rowY + 3, TEXT_COLOR, false);
            String count = Integer.toString(cat.count());
            graphics.drawString(this.font, count, x + w - 4 - this.font.width(count), rowY + 3,
                    COUNT_COLOR, false);
        }

        // 滚动条
        if (cats.size() > POPUP_MAX_VISIBLE) {
            int trackX = x + w - 3;
            graphics.fill(trackX, y + 1, trackX + 2, y + h - 1, 0xFF4A4A5A);
            int sliderH = Math.max(8, (h - 2) * POPUP_MAX_VISIBLE / cats.size());
            int maxScroll = cats.size() - POPUP_MAX_VISIBLE;
            int sliderY = y + 1 + (maxScroll == 0 ? 0
                    : (h - 2 - sliderH) * popupScroll / maxScroll);
            graphics.fill(trackX, sliderY, trackX + 2, sliderY + sliderH, 0xFFB0B0C8);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private record Category(String label, int count) {
    }

    private static final class ItemEntry {
        private final String id;
        private final ItemStack stack;
        /** 所属创造物品栏在 CreativeModeTabs.allTabs() 中的下标，-1 表示不属于任何物品栏 */
        private int categoryGroup = -1;

        private ItemEntry(String id, ItemStack stack) {
            this.id = id;
            this.stack = stack;
        }

        private String id() {
            return id;
        }

        private ItemStack stack() {
            return stack;
        }
    }
}
