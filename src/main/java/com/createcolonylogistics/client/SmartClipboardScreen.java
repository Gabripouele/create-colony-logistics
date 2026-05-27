package com.createcolonylogistics.client;

import com.createcolonylogistics.clipboard.SmartClipboardReport;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SmartClipboardScreen extends Screen {
    private static final ResourceLocation CREATE_CLIPBOARD_TEXTURE = ResourceLocation.fromNamespaceAndPath("create", "textures/gui/clipboard.png");
    // Measured from Create 6.0.6: ClipboardScreen#setWindowSize(256, 256), rendered with clipboard.png at guiTop - 8.
    private static final int IMAGE_WIDTH = 256;
    private static final int IMAGE_HEIGHT = 256;
    private static final int TEXTURE_TOP_OFFSET = -8;
    // Create clipboard entries occupy the parchment column around x + 44..214 and y + 50..229 on the texture.
    private static final int LIST_X = 44;
    private static final int LIST_WIDTH = 170;
    private static final int LIST_TOP = 42;
    private static final int LIST_BOTTOM = 218;
    private static final int SCROLL_X = 218;
    private static final int ROW_GAP = 3;
    private static final int CARD = 0x20F7E3B0;
    private static final int CARD_HOVER = 0x45FFFFFF;
    private static final int BORDER = 0xFF8A5D2A;
    private static final int TEXT = 0xFF311A00;
    private static final int MUTED = 0xFF6E5330;
    private static final int GOOD = 0xFF7FCF84;
    private static final int WARN = 0xFFC06B22;

    private final SmartClipboardReport report;
    private final Set<Integer> expanded = new HashSet<>();
    private final Set<Integer> advanced = new HashSet<>();
    private int leftPos;
    private int topPos;
    private int scroll;
    private int contentHeight;

    public SmartClipboardScreen(SmartClipboardReport report) {
        super(Component.translatable("screen.create_colony_logistics.smart_clipboard.title"));
        this.report = report;
    }

    @Override
    protected void init() {
        leftPos = (width - IMAGE_WIDTH) / 2;
        topPos = (height - IMAGE_HEIGHT) / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("screen.create_colony_logistics.smart_clipboard.open_minecolonies"),
                button -> {
                    if (!SmartClipboardClient.openMineColoniesClipboard()) {
                        button.setMessage(Component.translatable("screen.create_colony_logistics.smart_clipboard.open_minecolonies_missing"));
                    }
                }
        ).bounds(leftPos + 134, topPos + 226, 96, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.blit(CREATE_CLIPBOARD_TEXTURE, leftPos, topPos + TEXTURE_TOP_OFFSET, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        graphics.drawString(font, title, leftPos + LIST_X, topPos + 12, TEXT, false);
        graphics.drawString(font, Component.translatable(
                "screen.create_colony_logistics.smart_clipboard.subtitle",
                report.colonyName(),
                report.colonyId(),
                report.activeRequestCount(),
                report.buildingCount()
        ), leftPos + LIST_X, topPos + 25, MUTED, false);

        int listTop = topPos + LIST_TOP;
        int listBottom = topPos + LIST_BOTTOM;
        scroll = Math.min(scroll, maxScroll(listTop, listBottom));
        graphics.enableScissor(leftPos + LIST_X, listTop, leftPos + LIST_X + LIST_WIDTH, listBottom);
        contentHeight = renderEntries(graphics, mouseX, mouseY, listTop, listBottom);
        graphics.disableScissor();

        if (contentHeight > listBottom - listTop) {
            int trackTop = listTop;
            int trackHeight = listBottom - listTop;
            int thumbHeight = Math.max(18, trackHeight * trackHeight / contentHeight);
            int maxScroll = maxScroll(listTop, listBottom);
            int thumbY = trackTop + (maxScroll == 0 ? 0 : scroll * (trackHeight - thumbHeight) / maxScroll);
            // Stock keeper screens use a very narrow Create-style handle; keep this textureless but equally compact.
            graphics.fill(leftPos + SCROLL_X, trackTop, leftPos + SCROLL_X + 3, listBottom, 0x33735A38);
            graphics.fill(leftPos + SCROLL_X - 1, thumbY, leftPos + SCROLL_X + 4, thumbY + thumbHeight, BORDER);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int renderEntries(GuiGraphics graphics, int mouseX, int mouseY, int listTop, int listBottom) {
        int x = leftPos + LIST_X;
        int y = listTop - scroll;
        int cardWidth = LIST_WIDTH;

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
            y += cardHeight + ROW_GAP;
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
        graphics.fill(x, y + height - 1, x + width, y + height, 0x66735A38);

        graphics.renderItem(entry.requestedStack(), x + 4, y + 6);
        graphics.renderItemDecorations(font, entry.requestedStack(), x + 4, y + 6);

        Component status = entry.exactComboAlreadyTaught()
                ? Component.translatable("screen.create_colony_logistics.smart_clipboard.status_known")
                : Component.translatable("screen.create_colony_logistics.smart_clipboard.status_missing");
        int textX = x + 26;
        int statusX = x + width - font.width(status) - 4;
        graphics.drawString(font, font.plainSubstrByWidth(entry.requestedStack().getHoverName().getString(), Math.max(20, statusX - textX - 4)), textX, y + 5, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.create_colony_logistics.smart_clipboard.request_line", entry.requestedCount(), entry.requestingBuildingName()), textX, y + 17, MUTED, false);
        graphics.drawString(font, status, statusX, y + 17, entry.exactComboAlreadyTaught() ? GOOD : WARN, false);

        if (expanded.contains(index)) {
            int detailY = y + 34;
            detailY = section(graphics, x + 8, detailY, Component.translatable("screen.create_colony_logistics.smart_clipboard.section.availability"));
            detailY = value(graphics, x + 14, detailY, "screen.create_colony_logistics.smart_clipboard.stock", stock(entry.warehouseStock()));
            detailY = value(graphics, x + 14, detailY, "screen.create_colony_logistics.smart_clipboard.known_by", listOrUnknown(entry.recipeKnownBy()));
            detailY = value(graphics, x + 14, detailY, "screen.create_colony_logistics.smart_clipboard.can_learn", listOrUnknown(entry.canLearnCombo()));

            detailY = section(graphics, x + 8, detailY + 1, Component.translatable("screen.create_colony_logistics.smart_clipboard.section.requester"));
            detailY = value(graphics, x + 14, detailY, "screen.create_colony_logistics.smart_clipboard.worker", entry.requestingWorkerName().orElse(null));
            detailY = value(graphics, x + 14, detailY, "screen.create_colony_logistics.smart_clipboard.position", entry.requestingBuildingPos().map(this::pos).orElse(null));

            detailY = section(graphics, x + 8, detailY + 1, Component.translatable("screen.create_colony_logistics.smart_clipboard.section.smart"));
            detailY = value(graphics, x + 14, detailY, "screen.create_colony_logistics.smart_clipboard.taught", yesNo(entry.exactComboAlreadyTaught()));
            detailY = value(graphics, x + 14, detailY, "screen.create_colony_logistics.smart_clipboard.do_block", entry.doBlockId());
            detailY = value(graphics, x + 14, detailY, "screen.create_colony_logistics.smart_clipboard.cutter_recipe", entry.cutterRecipeId().orElse(null));
            detailY = value(graphics, x + 14, detailY, "screen.create_colony_logistics.smart_clipboard.fingerprint", entry.comboFingerprintShort());

            Component advancedLabel = Component.translatable(advanced.contains(index)
                    ? "screen.create_colony_logistics.smart_clipboard.advanced.hide"
                    : "screen.create_colony_logistics.smart_clipboard.advanced.show");
            graphics.drawString(font, advancedLabel, x + 14, detailY + 3, BORDER, false);

            if (advanced.contains(index)) {
                value(graphics, x + 20, detailY + 16, "screen.create_colony_logistics.smart_clipboard.request_token", entry.requestToken().orElse(null));
                value(graphics, x + 20, detailY + 28, "screen.create_colony_logistics.smart_clipboard.fingerprint_full", entry.comboFingerprintFull());
            }
        }
    }

    private int section(GuiGraphics graphics, int x, int y, Component label) {
        graphics.drawString(font, label.copy().withStyle(ChatFormatting.BOLD), x, y, BORDER, false);
        return y + 11;
    }

    private int value(GuiGraphics graphics, int x, int y, String key, String value) {
        if (value == null || value.isBlank()) {
            value = Component.translatable("screen.create_colony_logistics.smart_clipboard.unknown").getString();
        }
        Component line = Component.translatable(key, value);
        graphics.drawString(font, font.plainSubstrByWidth(line.getString(), Math.max(40, leftPos + LIST_X + LIST_WIDTH - x - 4)), x, y, MUTED, false);
        return y + 11;
    }

    private int entryHeight(int index) {
        if (!expanded.contains(index)) {
            return 38;
        }
        return advanced.contains(index) ? 178 : 154;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listTop = topPos + LIST_TOP;
        int y = listTop - scroll;
        int x = leftPos + LIST_X;
        int cardWidth = LIST_WIDTH;

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
            y += cardHeight + ROW_GAP;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visible = LIST_BOTTOM - LIST_TOP;
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
