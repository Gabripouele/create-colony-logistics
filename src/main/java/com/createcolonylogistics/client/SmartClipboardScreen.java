package com.createcolonylogistics.client;

import com.createcolonylogistics.clipboard.SmartClipboardReport;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SmartClipboardScreen extends Screen {
    private static final int PANEL = 0xEE2B241C;
    private static final int CARD = 0xEE3B3327;
    private static final int CARD_HOVER = 0xEE514636;
    private static final int BORDER = 0xFFB28A4A;
    private static final int TEXT = 0xFFE8D8B8;
    private static final int MUTED = 0xFFB7A98D;
    private static final int GOOD = 0xFF7FCF84;
    private static final int WARN = 0xFFE0A044;

    private final SmartClipboardReport report;
    private final Set<Integer> expanded = new HashSet<>();
    private final Set<Integer> advanced = new HashSet<>();
    private int scroll;
    private int contentHeight;

    public SmartClipboardScreen(SmartClipboardReport report) {
        super(Component.translatable("screen.create_colony_logistics.smart_clipboard.title"));
        this.report = report;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                Component.translatable("screen.create_colony_logistics.smart_clipboard.open_minecolonies"),
                button -> {
                    if (!SmartClipboardClient.openMineColoniesClipboard()) {
                        button.setMessage(Component.translatable("screen.create_colony_logistics.smart_clipboard.open_minecolonies_missing"));
                    }
                }
        ).bounds(this.width - 178, 12, 160, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(16, 38, width - 16, height - 16, PANEL);
        graphics.fill(16, 38, width - 16, 39, BORDER);

        graphics.drawString(font, title, 20, 15, TEXT, false);
        graphics.drawString(font, Component.translatable(
                "screen.create_colony_logistics.smart_clipboard.subtitle",
                report.colonyName(),
                report.colonyId(),
                report.activeRequestCount(),
                report.buildingCount()
        ), 20, 28, MUTED, false);

        int listTop = 46;
        int listBottom = height - 24;
        graphics.enableScissor(18, listTop, width - 18, listBottom);
        contentHeight = renderEntries(graphics, mouseX, mouseY, listTop, listBottom);
        graphics.disableScissor();

        if (contentHeight > listBottom - listTop) {
            int trackTop = listTop;
            int trackHeight = listBottom - listTop;
            int thumbHeight = Math.max(18, trackHeight * trackHeight / contentHeight);
            int maxScroll = maxScroll(listTop, listBottom);
            int thumbY = trackTop + (maxScroll == 0 ? 0 : scroll * (trackHeight - thumbHeight) / maxScroll);
            graphics.fill(width - 12, trackTop, width - 8, listBottom, 0x66000000);
            graphics.fill(width - 12, thumbY, width - 8, thumbY + thumbHeight, BORDER);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int renderEntries(GuiGraphics graphics, int mouseX, int mouseY, int listTop, int listBottom) {
        int x = 26;
        int y = listTop - scroll;
        int cardWidth = width - 60;

        if (report.entries().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.create_colony_logistics.smart_clipboard.empty"), x, y + 12, MUTED, false);
            if (report.capped()) {
                graphics.drawString(font, Component.translatable("screen.create_colony_logistics.smart_clipboard.capped"), x, y + 26, WARN, false);
            }
            return 48;
        }

        for (int i = 0; i < report.entries().size(); i++) {
            SmartClipboardReport.Entry entry = report.entries().get(i);
            int cardHeight = entryHeight(i);
            if (y + cardHeight >= listTop && y <= listBottom) {
                renderEntry(graphics, entry, i, x, y, cardWidth, cardHeight, mouseX, mouseY);
            }
            y += cardHeight + 6;
        }

        if (report.capped()) {
            graphics.drawString(font, Component.translatable("screen.create_colony_logistics.smart_clipboard.capped"), x, y + 4, WARN, false);
            y += 20;
        }

        return Math.max(0, y - (listTop - scroll));
    }

    private void renderEntry(GuiGraphics graphics, SmartClipboardReport.Entry entry, int index, int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        graphics.fill(x, y, x + width, y + height, hovered ? CARD_HOVER : CARD);
        graphics.fill(x, y, x + width, y + 1, BORDER);

        graphics.renderItem(entry.requestedStack(), x + 8, y + 8);
        graphics.renderItemDecorations(font, entry.requestedStack(), x + 8, y + 8);

        graphics.drawString(font, entry.requestedStack().getHoverName(), x + 32, y + 7, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.create_colony_logistics.smart_clipboard.request_line", entry.requestedCount(), entry.requestingBuildingName()), x + 32, y + 20, MUTED, false);

        Component status = entry.exactComboAlreadyTaught()
                ? Component.translatable("screen.create_colony_logistics.smart_clipboard.status_known")
                : Component.translatable("screen.create_colony_logistics.smart_clipboard.status_missing");
        graphics.drawString(font, status, x + width - font.width(status) - 10, y + 14, entry.exactComboAlreadyTaught() ? GOOD : WARN, false);

        if (expanded.contains(index)) {
            int detailY = y + 42;
            detailY = section(graphics, x + 12, detailY, Component.translatable("screen.create_colony_logistics.smart_clipboard.section.availability"));
            detailY = value(graphics, x + 20, detailY, "screen.create_colony_logistics.smart_clipboard.stock", stock(entry.warehouseStock()));
            detailY = value(graphics, x + 20, detailY, "screen.create_colony_logistics.smart_clipboard.known_by", listOrUnknown(entry.recipeKnownBy()));
            detailY = value(graphics, x + 20, detailY, "screen.create_colony_logistics.smart_clipboard.can_learn", listOrUnknown(entry.canLearnCombo()));

            detailY = section(graphics, x + 12, detailY + 2, Component.translatable("screen.create_colony_logistics.smart_clipboard.section.requester"));
            detailY = value(graphics, x + 20, detailY, "screen.create_colony_logistics.smart_clipboard.worker", entry.requestingWorkerName().orElse(null));
            detailY = value(graphics, x + 20, detailY, "screen.create_colony_logistics.smart_clipboard.position", entry.requestingBuildingPos().map(this::pos).orElse(null));

            detailY = section(graphics, x + 12, detailY + 2, Component.translatable("screen.create_colony_logistics.smart_clipboard.section.smart"));
            detailY = value(graphics, x + 20, detailY, "screen.create_colony_logistics.smart_clipboard.taught", yesNo(entry.exactComboAlreadyTaught()));
            detailY = value(graphics, x + 20, detailY, "screen.create_colony_logistics.smart_clipboard.do_block", entry.doBlockId());
            detailY = value(graphics, x + 20, detailY, "screen.create_colony_logistics.smart_clipboard.cutter_recipe", entry.cutterRecipeId().orElse(null));
            detailY = value(graphics, x + 20, detailY, "screen.create_colony_logistics.smart_clipboard.fingerprint", entry.comboFingerprintShort());

            Component advancedLabel = Component.translatable(advanced.contains(index)
                    ? "screen.create_colony_logistics.smart_clipboard.advanced.hide"
                    : "screen.create_colony_logistics.smart_clipboard.advanced.show");
            graphics.drawString(font, advancedLabel, x + 20, detailY + 4, BORDER, false);

            if (advanced.contains(index)) {
                value(graphics, x + 28, detailY + 18, "screen.create_colony_logistics.smart_clipboard.request_token", entry.requestToken().orElse(null));
                value(graphics, x + 28, detailY + 31, "screen.create_colony_logistics.smart_clipboard.fingerprint_full", entry.comboFingerprintFull());
            }
        }
    }

    private int section(GuiGraphics graphics, int x, int y, Component label) {
        graphics.drawString(font, label.copy().withStyle(ChatFormatting.BOLD), x, y, BORDER, false);
        return y + 13;
    }

    private int value(GuiGraphics graphics, int x, int y, String key, String value) {
        if (value == null || value.isBlank()) {
            value = Component.translatable("screen.create_colony_logistics.smart_clipboard.unknown").getString();
        }
        Component line = Component.translatable(key, value);
        graphics.drawString(font, font.plainSubstrByWidth(line.getString(), Math.max(40, width - x - 40)), x, y, MUTED, false);
        return y + 12;
    }

    private int entryHeight(int index) {
        if (!expanded.contains(index)) {
            return 38;
        }
        return advanced.contains(index) ? 190 : 166;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listTop = 46;
        int y = listTop - scroll;
        int x = 26;
        int cardWidth = width - 60;

        for (int i = 0; i < report.entries().size(); i++) {
            int cardHeight = entryHeight(i);
            if (mouseX >= x && mouseX <= x + cardWidth && mouseY >= y && mouseY <= y + cardHeight) {
                if (expanded.contains(i) && mouseY >= y + cardHeight - (advanced.contains(i) ? 48 : 24)) {
                    toggle(advanced, i);
                } else {
                    toggle(expanded, i);
                }
                return true;
            }
            y += cardHeight + 6;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visible = height - 70;
        scroll = Math.max(0, Math.min(scroll - (int) (scrollY * 18), Math.max(0, contentHeight - visible)));
        return true;
    }

    private int maxScroll(int top, int bottom) {
        return Math.max(0, contentHeight - (bottom - top));
    }

    private static void toggle(Set<Integer> set, int value) {
        if (!set.add(value)) {
            set.remove(value);
        }
    }

    private static String stock(int value) {
        return value < 0 ? null : Integer.toString(value);
    }

    private static String yesNo(boolean value) {
        return Component.translatable(value
                ? "screen.create_colony_logistics.smart_clipboard.yes"
                : "screen.create_colony_logistics.smart_clipboard.no").getString();
    }

    private String listOrUnknown(List<String> values) {
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private String pos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
