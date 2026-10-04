package com.bosschongsheng.client;

import com.bosschongsheng.client.BossRespawnConfigClient.RuleEntry;
import com.bosschongsheng.network.ReloadBossRulesPayload;
import com.bosschongsheng.network.RemoveBossRulePayload;
import com.bosschongsheng.network.RequestConfigPayload;
import com.bosschongsheng.network.SaveBossRulesPayload;
import com.bosschongsheng.network.UpdateSettingsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Boss 重生规则列表配置界面
 */
public class BossRespawnConfigScreen extends Screen {
    private static final int ROW_H = 24;
    private static final int MAX_PANEL_W = 600;
    private static final int LIST_TOP_OFFSET = 86;
    private static final int ROW_BTN_W = 36;
    private static final int ROW_BTN_H = 18;

    private static final int PANEL_BG = 0xFF2A2A35;
    private static final int PANEL_BORDER = 0xFF3D3D52;
    private static final int LIST_BG = 0xFF1E1E26;
    private static final int ROW_HOVER = 0xFF363648;
    private static final int BTN_CONFIRM = 0xFF2C2C2C;
    private static final int BTN_DEL = 0xFF4A2A2A;

    private int leftPos;
    private int topPos;
    private int panelW;
    private int panelH;
    private int visibleRows;
    private int listAreaH;
    private EditBox searchBox;
    private Button protectionToggle;
    private EditBox secondsBox;
    private int secondsLabelX;
    private int secondsUnitX;
    private int settingsRowY;
    private boolean protectionEnabled;
    private int protectionSeconds;
    private List<RuleEntry> filteredList = new ArrayList<>();
    private int scrollOffset;
    private String pendingDeleteId;

    public BossRespawnConfigScreen() {
        super(Component.translatable("gui.bosschongsheng.config.title"));
    }

    private void refreshFilter() {
        List<RuleEntry> all = BossRespawnConfigClient.getRuleList();
        String query = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT).trim() : "";
        if (query.isEmpty()) {
            filteredList = new ArrayList<>(all);
        } else {
            filteredList = all.stream()
                    .filter(e -> e.structureId().toLowerCase(Locale.ROOT).contains(query)
                            || structureName(e.structureId()).toLowerCase(Locale.ROOT).contains(query)
                            || e.triggerItemId().toLowerCase(Locale.ROOT).contains(query)
                            || e.entityTypeId().toLowerCase(Locale.ROOT).contains(query)
                            || entityName(e.entityTypeId()).toLowerCase(Locale.ROOT).contains(query))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        scrollOffset = Math.min(scrollOffset, Math.max(0, filteredList.size() - visibleRows));
    }

    private static String structureName(String id) {
        return DisplayNames.structure(id);
    }

    private static String entityName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.get(key);
        return type == null ? id : type.getDescription().getString();
    }

    private static ItemStack itemStack(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return new ItemStack(Items.BARRIER);
        }
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || BuiltInRegistries.ITEM.get(key) == Items.AIR) {
            return new ItemStack(Items.BARRIER);
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    @Override
    protected void init() {
        super.init();
        // 面板宽高都按当前窗口尺寸自适应，保证小窗口下标题、列表、按钮都不超出屏幕
        panelW = Math.min(MAX_PANEL_W, this.width - 20);
        panelH = Math.min(332, Math.max(170, this.height - 20));
        leftPos = (this.width - panelW) / 2;
        topPos = (this.height - panelH) / 2;
        // 列表顶 60，按钮行在 panelH-27，中间留 12px 间隔
        visibleRows = Math.max(1, (panelH - 27 - LIST_TOP_OFFSET - 12) / ROW_H);
        listAreaH = visibleRows * ROW_H;

        BossRespawnConfigClient.clearError();
        PacketDistributor.sendToServer(new RequestConfigPayload());

        searchBox = new EditBox(this.font, leftPos + 10, topPos + 30, panelW - 20, 20, Component.empty());
        searchBox.setHint(Component.translatable("gui.bosschongsheng.config.search_hint"));
        searchBox.setResponder(s -> refreshFilter());
        searchBox.setMaxLength(64);
        addRenderableWidget(searchBox);

        // 出生保护设置行：开关按钮 + 无敌时长输入
        protectionEnabled = BossRespawnConfigClient.isSpawnProtectionEnabled();
        protectionSeconds = BossRespawnConfigClient.getSpawnProtectionSeconds();
        int settingsY = topPos + 54;
        settingsRowY = settingsY;
        protectionToggle = Button.builder(protectionLabel(), b -> toggleProtection())
                .bounds(leftPos + 10, settingsY, 110, 20).build();
        addRenderableWidget(protectionToggle);

        secondsLabelX = leftPos + 10 + 110 + 8;
        int secondsX = secondsLabelX + this.font.width(protectionSecondsLabel()) + 4;
        secondsBox = new EditBox(this.font, secondsX, settingsY, 38, 20, Component.empty());
        secondsBox.setMaxLength(3);
        secondsBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));
        secondsBox.setValue(String.valueOf(protectionSeconds));
        secondsBox.setEditable(protectionEnabled);
        addRenderableWidget(secondsBox);
        secondsUnitX = secondsX + 38 + 4;

        int btnY = topPos + panelH - 27;
        int bw = 52;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.bosschongsheng.config.add"),
                b -> this.minecraft.setScreen(new RulePickerScreen(this, null, null, null, false)))
                .bounds(leftPos + 10, btnY, bw, 22).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.bosschongsheng.config.save"),
                b -> PacketDistributor.sendToServer(new SaveBossRulesPayload()))
                .bounds(leftPos + 10 + bw + 6, btnY, bw, 22).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.bosschongsheng.config.reload"),
                b -> {
                    PacketDistributor.sendToServer(new ReloadBossRulesPayload());
                    refreshFilter();
                })
                .bounds(leftPos + 10 + (bw + 6) * 2, btnY, bw, 22).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.bosschongsheng.config.close"),
                b -> onClose())
                .bounds(leftPos + panelW - 10 - 60, btnY, 60, 22).build());
    }

    @Override
    public void tick() {
        super.tick();
        refreshFilter();
    }

    public void onRuleAddedOrEdited() {
        PacketDistributor.sendToServer(new RequestConfigPayload());
        refreshFilter();
    }

    private Component protectionLabel() {
        return Component.translatable(protectionEnabled
                ? "gui.bosschongsheng.config.protection_on"
                : "gui.bosschongsheng.config.protection_off");
    }

    private String protectionSecondsLabel() {
        return Component.translatable("gui.bosschongsheng.config.protection_seconds").getString();
    }

    private void toggleProtection() {
        protectionEnabled = !protectionEnabled;
        protectionToggle.setMessage(protectionLabel());
        secondsBox.setEditable(protectionEnabled);
        if (protectionEnabled) {
            commitSeconds(false);
        }
        sendSettings();
    }

    /** 读取时长输入并收敛到 1～600；resetBox 为 true 时把收敛结果写回输入框 */
    private void commitSeconds(boolean resetBox) {
        int seconds;
        try {
            seconds = Integer.parseInt(secondsBox.getValue().trim());
        } catch (NumberFormatException e) {
            seconds = 5;
        }
        seconds = Math.max(1, Math.min(600, seconds));
        protectionSeconds = seconds;
        if (resetBox) {
            secondsBox.setValue(String.valueOf(seconds));
            secondsBox.setFocused(false);
        }
    }

    private void sendSettings() {
        commitSeconds(false);
        BossRespawnConfigClient.setSettings(protectionEnabled, protectionSeconds);
        PacketDistributor.sendToServer(
                new UpdateSettingsPayload(protectionEnabled, protectionSeconds));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 时长输入框内按回车（含小键盘回车）即提交
        if (secondsBox != null && secondsBox.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            commitSeconds(true);
            sendSettings();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftPos + 10 && mouseX <= leftPos + panelW - 10
                && mouseY >= topPos + LIST_TOP_OFFSET
                && mouseY <= topPos + LIST_TOP_OFFSET + listAreaH) {
            scrollOffset = (int) Math.max(0,
                    Math.min(filteredList.size() - visibleRows, scrollOffset - scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listTop = topPos + LIST_TOP_OFFSET;
            int editX = leftPos + panelW - 10 - 72 - 8;
            int delX = leftPos + panelW - 10 - ROW_BTN_W;
            for (int i = 0; i < visibleRows; i++) {
                int idx = scrollOffset + i;
                if (idx >= filteredList.size()) {
                    break;
                }
                RuleEntry e = filteredList.get(idx);
                int rowY = listTop + i * ROW_H;
                if (mouseY < rowY || mouseY >= rowY + ROW_H
                        || mouseX < leftPos + 10 || mouseX >= leftPos + panelW - 10) {
                    continue;
                }
                if (e.structureId().equals(pendingDeleteId)) {
                    if (mouseX >= editX && mouseX < editX + ROW_BTN_W) {
                        PacketDistributor.sendToServer(new RemoveBossRulePayload(pendingDeleteId));
                        PacketDistributor.sendToServer(new RequestConfigPayload());
                        pendingDeleteId = null;
                        refreshFilter();
                    } else if (mouseX >= delX && mouseX < delX + ROW_BTN_W) {
                        pendingDeleteId = null;
                    }
                } else if (mouseX >= delX && mouseX < delX + ROW_BTN_W) {
                    pendingDeleteId = e.structureId();
                } else {
                    this.minecraft.setScreen(new RulePickerScreen(
                            this, e.structureId(), e.triggerItemId(), e.entityTypeId(), true));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.21.1 默认实现会对游戏背景运行模糊后处理（1.20.1 无此效果），
        // 用一层半透明黑色遮罩代替模糊，30% 不透明度保证游戏背景仍清晰可见
        graphics.fill(0, 0, this.width, this.height, 0x4D000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + panelW, y + panelH, PANEL_BG);
        graphics.fill(x, y, x + panelW, y + 1, PANEL_BORDER);
        graphics.fill(x, y + panelH - 1, x + panelW, y + panelH, PANEL_BORDER);
        graphics.fill(x, y, x + 1, y + panelH, PANEL_BORDER);
        graphics.fill(x + panelW - 1, y, x + panelW, y + panelH, PANEL_BORDER);
        graphics.drawString(this.font, this.title, x + 10, y + 6, 0xFFFFFFFF, true);
        graphics.fill(x + 10, y + LIST_TOP_OFFSET - 2,
                x + panelW - 10, y + LIST_TOP_OFFSET + listAreaH + 2, LIST_BG);

        super.render(graphics, mouseX, mouseY, partialTick);

        int labelColor = protectionEnabled ? 0xE0E0E0 : 0xFF6B6B6B;
        graphics.drawString(this.font, protectionSecondsLabel(),
                secondsLabelX, settingsRowY + 6, labelColor, false);
        graphics.drawString(this.font,
                Component.translatable("gui.bosschongsheng.config.seconds_unit").getString(),
                secondsUnitX, settingsRowY + 6, labelColor, false);

        int listTop = y + LIST_TOP_OFFSET;
        int colStruct = x + 10 + 4;
        int colItem = colStruct + (panelW - 20) / 4;
        int colEntity = colItem + (panelW - 20) / 4;
        int editX = x + panelW - 10 - 72 - 8;
        int delX = x + panelW - 10 - ROW_BTN_W;

        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredList.size()) {
                break;
            }
            RuleEntry e = filteredList.get(idx);
            int rowY = listTop + i * ROW_H;
            boolean hover = mouseX >= x + 10 && mouseX < x + panelW - 10
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hover) {
                graphics.fill(x + 10, rowY, x + panelW - 10, rowY + ROW_H, ROW_HOVER);
            }

            String struct = structureName(e.structureId());
            if (this.font.width(struct) > colItem - colStruct - 8) {
                struct = this.font.plainSubstrByWidth(struct, colItem - colStruct - 12) + "..";
            }
            graphics.drawString(this.font, struct, colStruct, rowY + 8, 0xE0E0E0, false);

            ItemStack stack = itemStack(e.triggerItemId());
            graphics.renderItem(stack, colItem, rowY + 4);
            String itemStr = stack.getHoverName().getString();
            if (this.font.width(itemStr) > colEntity - colItem - 24) {
                itemStr = this.font.plainSubstrByWidth(itemStr, colEntity - colItem - 28) + "..";
            }
            graphics.drawString(this.font, itemStr, colItem + 18, rowY + 8, 0xE0E0E0, false);

            String ent = entityName(e.entityTypeId());
            if (this.font.width(ent) > editX - colEntity - 8) {
                ent = this.font.plainSubstrByWidth(ent, editX - colEntity - 12) + "..";
            }
            graphics.drawString(this.font, ent, colEntity, rowY + 8, 0xB0B0B0, false);

            int btnY = rowY + 3;
            if (e.structureId().equals(pendingDeleteId)) {
                graphics.fill(editX, btnY, editX + ROW_BTN_W, btnY + ROW_BTN_H, BTN_CONFIRM);
                graphics.drawCenteredString(this.font,
                        Component.translatable("gui.bosschongsheng.config.confirm").getString(),
                        editX + ROW_BTN_W / 2, btnY + 4, 0xE0FFE0);
                graphics.fill(delX, btnY, delX + ROW_BTN_W, btnY + ROW_BTN_H, PANEL_BORDER);
                graphics.drawCenteredString(this.font,
                        Component.translatable("gui.cancel").getString(),
                        delX + ROW_BTN_W / 2, btnY + 4, 0xE0E0E0);
            } else {
                graphics.fill(editX, btnY, editX + ROW_BTN_W, btnY + ROW_BTN_H, PANEL_BORDER);
                graphics.drawCenteredString(this.font,
                        Component.translatable("gui.bosschongsheng.config.edit").getString(),
                        editX + ROW_BTN_W / 2, btnY + 4, 0xE0E0E0);
                graphics.fill(delX, btnY, delX + ROW_BTN_W, btnY + ROW_BTN_H, BTN_DEL);
                graphics.drawCenteredString(this.font,
                        Component.translatable("gui.bosschongsheng.config.delete").getString(),
                        delX + ROW_BTN_W / 2, btnY + 4, 0xFFAAAA);
            }
        }

        String err = BossRespawnConfigClient.getLastError();
        if (err != null) {
            graphics.drawString(this.font, err, x + 10, y + panelH - 14, 0xFF6666, false);
        }
    }
}
