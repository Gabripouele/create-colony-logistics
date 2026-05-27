package com.createcolonylogistics.client;

import com.createcolonylogistics.clipboard.SmartClipboardReport;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class SmartClipboardScreen extends Screen {
    private static final ResourceLocation STOCK_KEEPER_TEXTURE = ResourceLocation.fromNamespaceAndPath("create", "textures/gui/stock_keeper.png");
    // Measured from Create 6.0.6 StockKeeperRequestScreen/AllGuiTextures.
    private static final int IMAGE_WIDTH = 256;
    private static final int IMAGE_HEIGHT = 316;
    private static final int HEADER_HEIGHT = 36;
    private static final int BODY_HEIGHT = 20;
    private static final int BOTTOM_HEIGHT = 20;
    private static final int LIST_X = 22;
    private static final int LIST_WIDTH = 202;
    private static final int LIST_TOP = 48;
    private static final int LIST_BOTTOM = 294;
    private static final int SCROLL_X = 230;
    private static final int ROW_GAP = 3;
    private static final int LINE_HEIGHT = 11;
    private static final int COLLAPSED_HEIGHT = 44;
    private static final int EXPANDED_TOP_PADDING = 46;
    private static final int EXPANDED_BOTTOM_PADDING = 8;
    private static final int CARD = 0x18F7E3B0;
    private static final int CARD_HOVER = 0x35FFFFFF;
    private static final int BORDER = 0xFF8A5D2A;
    private static final int TEXT = 0xFF311A00;
    private static final int MUTED = 0xFF6E5330;
    private static final int GOOD = 0xFF7FCF84;
    private static final int WARN = 0xFFC06B22;

    private final SmartClipboardReport report;
    private final Set<Integer> expanded = new HashSet<>();
    private final Set<Integer> advanced = new HashSet<>();
    private EditBox searchBox;
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
        searchBox = new EditBox(font, leftPos + 71, topPos + 22, 100, 9,
                Component.translatable("screen.create_colony_logistics.smart_clipboard.search"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setTextColor(0x4A2D11);
        searchBox.setHint(Component.translatable("screen.create_colony_logistics.smart_clipboard.search"));
        searchBox.setResponder(ignored -> scroll = 0);
        addWidget(searchBox);

        addRenderableWidget(Button.builder(
                Component.translatable("screen.create_colony_logistics.smart_clipboard.open_minecolonies_short"),
                button -> {
                    if (!SmartClipboardClient.openMineColoniesClipboard()) {
                        button.setMessage(Component.translatable("screen.create_colony_logistics.smart_clipboard.open_minecolonies_missing"));
                    }
                }
        ).bounds(leftPos + 176, topPos + 18, 48, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        renderStockKeeperPanel(graphics);

        graphics.drawString(font, title, leftPos + 22, topPos + 8, TEXT, false);
        graphics.drawString(font, Component.translatable(
                "screen.create_colony_logistics.smart_clipboard.subtitle",
                report.colonyName(),
                report.colonyId(),
                report.activeRequestCount(),
                report.buildingCount()
        ), leftPos + LIST_X, topPos + 37, MUTED, false);

        int listTop = topPos + LIST_TOP;
        int listBottom = topPos + LIST_BOTTOM;
        scroll = Math.min(scroll, maxScroll(listTop, listBottom));
        graphics.enableScissor(leftPos + LIST_X, listTop, leftPos + LIST_X + LIST_WIDTH, listBottom);
        contentHeight = renderEntries(graphics, mouseX, mouseY, listTop, listBottom);
        graphics.disableScissor();

        if (contentHeight > listBottom - listTop) {
            int trackTop = listTop + 2;
            int trackHeight = listBottom - listTop;
            int thumbHeight = Math.max(18, trackHeight * trackHeight / contentHeight);
            int maxScroll = maxScroll(listTop, listBottom);
            int thumbY = trackTop + (maxScroll == 0 ? 0 : scroll * (trackHeight - thumbHeight) / maxScroll);
            renderStockKeeperScrollbar(graphics, thumbY, thumbHeight);
        }

        // Do not call Screen#render here: vanilla starts by rendering a blurred background,
        // which would blur the already-drawn clipboard, text, and item icons.
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        searchBox.render(graphics, mouseX, mouseY, partialTick);
    }

    private int renderEntries(GuiGraphics graphics, int mouseX, int mouseY, int listTop, int listBottom) {
        int x = leftPos + LIST_X;
        int y = listTop - scroll;
        int cardWidth = LIST_WIDTH;
        List<Integer> visibleIndexes = filteredEntryIndexes();

        if (visibleIndexes.isEmpty()) {
            Component empty = report.entries().isEmpty()
                    ? Component.translatable("screen.create_colony_logistics.smart_clipboard.empty")
                    : Component.translatable("screen.create_colony_logistics.smart_clipboard.no_search_results");
            graphics.drawString(font, truncate(empty, cardWidth), x, y + 12, MUTED, false);
            if (report.capped()) {
                graphics.drawString(font, Component.translatable("screen.create_colony_logistics.smart_clipboard.capped"), x, y + 26, WARN, false);
            }
            return 48;
        }

        for (int i : visibleIndexes) {
            SmartClipboardReport.Entry entry = report.entries().get(i);
            int cardHeight = entryHeight(entry, i);
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

        graphics.renderItem(entry.requestedStack(), x + 6, y + 7);
        graphics.renderItemDecorations(font, entry.requestedStack(), x + 6, y + 7);

        Component status = entry.exactComboAlreadyTaught()
                ? Component.translatable("screen.create_colony_logistics.smart_clipboard.yes")
                : Component.translatable("screen.create_colony_logistics.smart_clipboard.no");
        int textX = x + 30;
        Component name = Component.translatable("screen.create_colony_logistics.smart_clipboard.item_count",
                entry.requestedStack().getHoverName(), entry.requestedCount());
        graphics.drawString(font, truncate(name, width - 34), textX, y + 5, TEXT, false);

        Component known = Component.translatable("screen.create_colony_logistics.smart_clipboard.known_short", status);
        int knownX = x + width - font.width(known) - 4;
        graphics.drawString(font, known, knownX, y + 18, entry.exactComboAlreadyTaught() ? GOOD : WARN, false);
        graphics.drawString(font, truncate(Component.literal(entry.requestingBuildingName()), Math.max(20, knownX - textX - 4)), textX, y + 18, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.create_colony_logistics.smart_clipboard.can_learn_short", countLabel(entry.canLearnCombo())), textX, y + 30, MUTED, false);

        if (expanded.contains(index)) {
            int detailY = y + EXPANDED_TOP_PADDING;
            detailY = value(graphics, x + 8, detailY, "screen.create_colony_logistics.smart_clipboard.requester", entry.requestingBuildingName());
            detailY = value(graphics, x + 8, detailY, "screen.create_colony_logistics.smart_clipboard.warehouse", stock(entry.warehouseStock()));
            detailY = value(graphics, x + 8, detailY, "screen.create_colony_logistics.smart_clipboard.worker", entry.requestingWorkerName().orElse(null));

            detailY = section(graphics, x + 8, detailY + 2, Component.translatable("screen.create_colony_logistics.smart_clipboard.section.smart"));
            detailY = value(graphics, x + 14, detailY, "screen.create_colony_logistics.smart_clipboard.shape", humanizeDomumShape(entry));
            detailY = value(graphics, x + 14, detailY, "screen.create_colony_logistics.smart_clipboard.material", humanizeMaterial(entry));
            detailY = value(graphics, x + 14, detailY, "screen.create_colony_logistics.smart_clipboard.known_by", formatHutList(entry.recipeKnownBy()));
            detailY = value(graphics, x + 14, detailY, "screen.create_colony_logistics.smart_clipboard.can_learn", formatHutList(entry.canLearnCombo()));
        }
    }

    private int section(GuiGraphics graphics, int x, int y, Component label) {
        graphics.drawString(font, label.copy().withStyle(ChatFormatting.BOLD), x, y, BORDER, false);
        return y + LINE_HEIGHT;
    }

    private int value(GuiGraphics graphics, int x, int y, String key, String value) {
        if (value == null || value.isBlank()) {
            return y;
        }
        Component line = Component.translatable(key, value);
        graphics.drawString(font, truncate(line, Math.max(40, leftPos + LIST_X + LIST_WIDTH - x - 4)), x, y, MUTED, false);
        return y + LINE_HEIGHT;
    }

    private int entryHeight(SmartClipboardReport.Entry entry, int index) {
        if (!expanded.contains(index)) {
            return COLLAPSED_HEIGHT;
        }
        int height = EXPANDED_TOP_PADDING;
        height += valueLineHeight(entry.requestingBuildingName());
        height += valueLineHeight(stock(entry.warehouseStock()));
        height += valueLineHeight(entry.requestingWorkerName().orElse(null));
        height += 2 + LINE_HEIGHT;
        height += valueLineHeight(humanizeDomumShape(entry));
        height += valueLineHeight(humanizeMaterial(entry));
        height += valueLineHeight(formatHutList(entry.recipeKnownBy()));
        height += valueLineHeight(formatHutList(entry.canLearnCombo()));

        return height + EXPANDED_BOTTOM_PADDING;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchBox.isMouseOver(mouseX, mouseY)) {
            return searchBox.mouseClicked(mouseX, mouseY, button);
        }
        int listTop = topPos + LIST_TOP;
        int y = listTop - scroll;
        int x = leftPos + LIST_X;
        int cardWidth = LIST_WIDTH;

        for (int i : filteredEntryIndexes()) {
            SmartClipboardReport.Entry entry = report.entries().get(i);
            int cardHeight = entryHeight(entry, i);
            if (mouseX >= x && mouseX <= x + cardWidth && mouseY >= y && mouseY <= y + cardHeight) {
                toggle(expanded, i);
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

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return searchBox.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return searchBox.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renderStockKeeperPanel(GuiGraphics graphics) {
        graphics.blit(STOCK_KEEPER_TEXTURE, leftPos, topPos, 0, 0, IMAGE_WIDTH, HEADER_HEIGHT);
        for (int y = topPos + HEADER_HEIGHT; y < topPos + IMAGE_HEIGHT - BOTTOM_HEIGHT; y += BODY_HEIGHT) {
            int height = Math.min(BODY_HEIGHT, topPos + IMAGE_HEIGHT - BOTTOM_HEIGHT - y);
            graphics.blit(STOCK_KEEPER_TEXTURE, leftPos, y, 0, 48, IMAGE_WIDTH, height);
        }
        graphics.blit(STOCK_KEEPER_TEXTURE, leftPos, topPos + IMAGE_HEIGHT - BOTTOM_HEIGHT, 0, 140, IMAGE_WIDTH, BOTTOM_HEIGHT);
        graphics.blit(STOCK_KEEPER_TEXTURE, leftPos + 57, topPos + 17, 57, 17, 142, 18);
    }

    private void renderStockKeeperScrollbar(GuiGraphics graphics, int thumbY, int thumbHeight) {
        int x = leftPos + SCROLL_X;
        graphics.blit(STOCK_KEEPER_TEXTURE, x, topPos + LIST_TOP, 219, 192, 5, 4);
        for (int y = topPos + LIST_TOP + 4; y < topPos + LIST_BOTTOM - 5; y++) {
            graphics.blit(STOCK_KEEPER_TEXTURE, x, y, 219, 196, 5, 1);
        }
        graphics.blit(STOCK_KEEPER_TEXTURE, x, topPos + LIST_BOTTOM - 5, 219, 207, 5, 5);
        graphics.blit(STOCK_KEEPER_TEXTURE, x, thumbY, 219, 197, 5, Math.min(9, thumbHeight));
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

    private int valueLineHeight(String value) {
        return value == null || value.isBlank() ? 0 : LINE_HEIGHT;
    }

    private List<Integer> filteredEntryIndexes() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < report.entries().size(); i++) {
            SmartClipboardReport.Entry entry = report.entries().get(i);
            if (query.isEmpty() || matchesSearch(entry, query)) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private boolean matchesSearch(SmartClipboardReport.Entry entry, String query) {
        return contains(entry.requestedStack().getHoverName().getString(), query)
                || contains(entry.requestingBuildingName(), query)
                || contains(humanizeDomumShape(entry), query)
                || contains(humanizeMaterial(entry), query)
                || entry.recipeKnownBy().stream().anyMatch(value -> contains(value, query))
                || entry.canLearnCombo().stream().anyMatch(value -> contains(value, query));
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String countLabel(List<String> values) {
        if (values.isEmpty()) {
            return Component.translatable("screen.create_colony_logistics.smart_clipboard.no").getString();
        }
        if (values.size() == 1) {
            return Component.translatable("screen.create_colony_logistics.smart_clipboard.one_hut").getString();
        }
        return Component.translatable("screen.create_colony_logistics.smart_clipboard.hut_count", values.size()).getString();
    }

    private Component truncate(Component component, int width) {
        String text = component.getString();
        if (font.width(text) <= width) {
            return component;
        }
        return Component.literal(font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...");
    }

    private String formatHutList(List<String> values) {
        if (values.isEmpty()) {
            return Component.translatable("screen.create_colony_logistics.smart_clipboard.none").getString();
        }
        String joined = String.join(", ", values.stream().map(this::shortenHutName).toList());
        if (values.size() > 2) {
            joined = shortenHutName(values.get(0)) + ", " + shortenHutName(values.get(1)) + " +" + (values.size() - 2);
        }
        return joined;
    }

    private String shortenHutName(String name) {
        return name.replace(" Hut", "").replace("Building ", "");
    }

    private String humanizeDomumShape(SmartClipboardReport.Entry entry) {
        return humanizeResourcePath(entry.doBlockId());
    }

    private String humanizeMaterial(SmartClipboardReport.Entry entry) {
        String itemName = entry.requestedStack().getHoverName().getString();
        String shape = humanizeDomumShape(entry);
        String material = itemName.replace(shape, "").replace(shape.toLowerCase(), "").trim();
        material = material.replaceAll("(?i)\\b(panel|block|slab|stairs|stair|door|trapdoor|fence|gate|wall|framed|shingle|shingles)\\b", "").trim();
        if (material.isBlank() || material.equals(itemName)) {
            return null;
        }
        String[] words = material.split("\\s+");
        if (words.length > 2) {
            return words[0] + " + " + words[1];
        }
        return material;
    }

    private String humanizeResourcePath(String id) {
        String path = id;
        int colon = path.indexOf(':');
        if (colon >= 0) {
            path = path.substring(colon + 1);
        }
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        path = path.replace('_', ' ').replace('-', ' ').trim();
        if (path.isBlank()) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (String word : path.split("\\s+")) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private String pos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
