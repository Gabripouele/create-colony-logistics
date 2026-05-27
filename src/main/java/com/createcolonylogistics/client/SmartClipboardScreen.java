package com.createcolonylogistics.client;

import com.createcolonylogistics.clipboard.SmartClipboardReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SmartClipboardScreen extends Screen {
    private static final ResourceLocation STOCK_KEEPER_TEXTURE = ResourceLocation.fromNamespaceAndPath("create", "textures/gui/stock_keeper.png");
    private static final int IMAGE_WIDTH = 256;
    private static final int IMAGE_HEIGHT = 316;
    private static final int HEADER_HEIGHT = 36;
    private static final int BODY_HEIGHT = 20;
    private static final int BOTTOM_HEIGHT = 20;
    private static final int LIST_X = 39;
    private static final int LIST_WIDTH = 180;
    private static final int LIST_TOP = 50;
    private static final int LIST_BOTTOM = 294;
    private static final int SCROLL_X = 219;
    private static final int ROW_GAP = 2;
    private static final int LINE_HEIGHT = 10;
    private static final int COLLAPSED_HEIGHT = 28;
    private static final int EXPANDED_TOP_PADDING = 30;
    private static final int EXPANDED_BOTTOM_PADDING = 6;
    private static final int HEADER_TEXT = 0xFF4A2D11;
    private static final int SEPARATOR = 0xAA3C2412;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFB7A98D;
    private static final int DIM = 0xFF8D7F6B;
    private static final int SMART_INFO_HEADER_COLOR = 0xA0A0A0;
    private static final int SMART_INFO_LABEL_COLOR = 0xFFF2D78C;
    private static final int SMART_INFO_VALUE_COLOR = 0xFF8FA7FF;

    private final SmartClipboardReport report;
    private final Set<Integer> expanded = new HashSet<>();
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
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        renderStockKeeperPanel(graphics);

        Component headerTitle = truncate(title, 196);
        graphics.drawString(font, headerTitle, leftPos + (IMAGE_WIDTH - font.width(headerTitle)) / 2, topPos + 4, HEADER_TEXT, false);
        graphics.drawString(font, truncate(Component.literal(report.colonyName()), LIST_WIDTH), leftPos + LIST_X, topPos + 27, TEXT, false);
        graphics.drawString(font, truncate(Component.translatable(
                "screen.create_colony_logistics.smart_clipboard.summary",
                report.activeRequestCount(),
                report.buildingCount()
        ), LIST_WIDTH), leftPos + LIST_X, topPos + 39, MUTED, false);
        graphics.fill(leftPos + LIST_X, topPos + LIST_TOP - 3, leftPos + LIST_X + LIST_WIDTH, topPos + LIST_TOP - 2, SEPARATOR);

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

        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        renderHoveredItemTooltip(graphics, mouseX, mouseY, listTop);
    }

    private int renderEntries(GuiGraphics graphics, int mouseX, int mouseY, int listTop, int listBottom) {
        int x = leftPos + LIST_X;
        int y = listTop - scroll;
        int cardWidth = LIST_WIDTH;
        List<Integer> visibleIndexes = filteredEntryIndexes();

        if (visibleIndexes.isEmpty()) {
            Component empty = Component.translatable("screen.create_colony_logistics.smart_clipboard.empty");
            graphics.drawString(font, truncate(empty, cardWidth), x, y + 12, MUTED, false);
            if (report.capped()) {
                graphics.drawString(font, Component.translatable("screen.create_colony_logistics.smart_clipboard.capped"), x, y + 26, DIM, false);
            }
            return 48;
        }

        for (int i : visibleIndexes) {
            SmartClipboardReport.Entry entry = report.entries().get(i);
            int cardHeight = entryHeight(entry, i);
            if (y + cardHeight >= listTop && y <= listBottom) {
                renderEntry(graphics, entry, i, x, y, cardWidth, cardHeight);
            }
            y += cardHeight + ROW_GAP;
        }

        if (report.capped()) {
            graphics.drawString(font, Component.translatable("screen.create_colony_logistics.smart_clipboard.capped"), x, y + 4, DIM, false);
            y += 20;
        }

        return Math.max(0, y - (listTop - scroll));
    }

    private void renderEntry(GuiGraphics graphics, SmartClipboardReport.Entry entry, int index, int x, int y, int width, int height) {
        graphics.fill(x, y + height - 1, x + width, y + height, SEPARATOR);
        graphics.renderItem(entry.requestedStack(), x + 2, y + 4);
        graphics.renderItemDecorations(font, entry.requestedStack(), x + 2, y + 4);

        int textX = x + 24;
        Component name = Component.translatable("screen.create_colony_logistics.smart_clipboard.item_count",
                entry.requestedStack().getHoverName(), entry.requestedCount());
        graphics.drawString(font, truncate(name, width - 28), textX, y + 3, TEXT, false);
        graphics.drawString(font, truncate(Component.translatable("screen.create_colony_logistics.smart_clipboard.requester", displayRequester(entry)), width - 28), textX, y + 15, MUTED, false);

        if (expanded.contains(index)) {
            int detailY = y + EXPANDED_TOP_PADDING;
            detailY = value(graphics, x + 8, detailY, "screen.create_colony_logistics.smart_clipboard.requested", entry.requestedStack().getHoverName().getString() + " x" + entry.requestedCount());
            detailY = value(graphics, x + 8, detailY, "screen.create_colony_logistics.smart_clipboard.worker", entry.requestingWorkerName().orElse(null));
            detailY = value(graphics, x + 8, detailY, "screen.create_colony_logistics.smart_clipboard.resolver", entry.resolverName().map(this::humanizeResolver).orElse(null));
            List<SmartClipboardReport.RequestTreeNode> dependencies = dependencyNodes(entry);
            if (!dependencies.isEmpty()) {
                detailY = label(graphics, x + 8, detailY + 2, "screen.create_colony_logistics.smart_clipboard.needs_label");
                for (SmartClipboardReport.RequestTreeNode node : dependencies) {
                    detailY = treeValue(graphics, x + 8, detailY, node);
                }
            } else {
                detailY = value(graphics, x + 8, detailY + 2, "screen.create_colony_logistics.smart_clipboard.needs", Component.translatable("screen.create_colony_logistics.smart_clipboard.none").getString());
            }
        }
    }

    private int value(GuiGraphics graphics, int x, int y, String key, String value) {
        if (value == null || value.isBlank()) {
            return y;
        }
        Component label = Component.translatable(key + "_label");
        int maxWidth = Math.max(40, leftPos + LIST_X + LIST_WIDTH - x - 4);
        graphics.drawString(font, truncate(label, maxWidth), x, y, MUTED, false);
        int valueX = x + font.width(label);
        graphics.drawString(font, truncate(Component.literal(value), Math.max(20, leftPos + LIST_X + LIST_WIDTH - valueX - 4)), valueX, y, HEADER_TEXT, false);
        return y + LINE_HEIGHT;
    }

    private int label(GuiGraphics graphics, int x, int y, String key) {
        graphics.drawString(font, Component.translatable(key), x, y, HEADER_TEXT, false);
        return y + LINE_HEIGHT;
    }

    private int treeValue(GuiGraphics graphics, int x, int y, SmartClipboardReport.RequestTreeNode node) {
        int indent = Math.min(48, Math.max(0, node.depth() - 1) * 12);
        ItemStack stack = node.stack();
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + indent, y);
        }
        String label = !stack.isEmpty() ? stack.getHoverName().getString() + " x" + node.count() : node.label();
        graphics.drawString(font, truncate(Component.literal(label), Math.max(40, leftPos + LIST_X + LIST_WIDTH - x - indent - 22)),
                x + indent + 20, y + 4, MUTED, false);
        return y + 18;
    }

    private int entryHeight(SmartClipboardReport.Entry entry, int index) {
        if (!expanded.contains(index)) {
            return COLLAPSED_HEIGHT;
        }
        int height = EXPANDED_TOP_PADDING;
        height += valueLineHeight(entry.requestedStack().getHoverName().getString());
        height += valueLineHeight(entry.requestingWorkerName().orElse(null));
        height += valueLineHeight(entry.resolverName().orElse(null));
        List<SmartClipboardReport.RequestTreeNode> dependencies = dependencyNodes(entry);
        if (!dependencies.isEmpty()) {
            height += LINE_HEIGHT + 2;
            height += dependencies.size() * 18;
        } else {
            height += LINE_HEIGHT + 2;
        }
        return height + EXPANDED_BOTTOM_PADDING;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listTop = topPos + LIST_TOP;
        int y = listTop - scroll;
        int x = leftPos + LIST_X;

        for (int i : filteredEntryIndexes()) {
            SmartClipboardReport.Entry entry = report.entries().get(i);
            int cardHeight = entryHeight(entry, i);
            if (mouseX >= x && mouseX <= x + LIST_WIDTH && mouseY >= y && mouseY <= y + cardHeight) {
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

    private void renderStockKeeperPanel(GuiGraphics graphics) {
        graphics.blit(STOCK_KEEPER_TEXTURE, leftPos, topPos, 0, 0, IMAGE_WIDTH, HEADER_HEIGHT);
        graphics.blit(STOCK_KEEPER_TEXTURE, leftPos + 31, topPos + 17, 31, 48, 194, 18);
        for (int y = topPos + HEADER_HEIGHT; y < topPos + IMAGE_HEIGHT - BOTTOM_HEIGHT; y += BODY_HEIGHT) {
            int height = Math.min(BODY_HEIGHT, topPos + IMAGE_HEIGHT - BOTTOM_HEIGHT - y);
            graphics.blit(STOCK_KEEPER_TEXTURE, leftPos, y, 0, 48, IMAGE_WIDTH, height);
        }
        graphics.blit(STOCK_KEEPER_TEXTURE, leftPos, topPos + IMAGE_HEIGHT - BOTTOM_HEIGHT, 0, 140, IMAGE_WIDTH, BOTTOM_HEIGHT);
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

    private void renderHoveredItemTooltip(GuiGraphics graphics, int mouseX, int mouseY, int listTop) {
        int x = leftPos + LIST_X;
        int y = listTop - scroll;
        for (int i : filteredEntryIndexes()) {
            SmartClipboardReport.Entry entry = report.entries().get(i);
            int cardHeight = entryHeight(entry, i);
            if (mouseX >= x + 2 && mouseX < x + 18 && mouseY >= y + 4 && mouseY < y + 20) {
                graphics.renderComponentTooltip(font, buildSmartTooltip(entry), mouseX, mouseY, entry.requestedStack());
                return;
            }
            y += cardHeight + ROW_GAP;
        }
    }

    private List<Component> buildSmartTooltip(SmartClipboardReport.Entry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        Item.TooltipContext context = minecraft.level == null ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(minecraft.level);
        List<Component> lines = new ArrayList<>(entry.requestedStack().getTooltipLines(context, minecraft.player, TooltipFlag.NORMAL));
        if (!isDomumOrnamentumEntry(entry)) {
            return lines;
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("screen.create_colony_logistics.smart_clipboard.tooltip.smart")
                .withStyle(style -> style.withColor(SMART_INFO_HEADER_COLOR)));
        addSmartTooltipLine(lines, "screen.create_colony_logistics.smart_clipboard.tooltip.shape", humanizeDomumShape(entry));
        addWrappedSmartTooltipLine(lines, "screen.create_colony_logistics.smart_clipboard.tooltip.can_learn", entry.canLearnCombo());
        return lines;
    }

    private void addSmartTooltipLine(List<Component> lines, String key, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(Component.translatable(key).withStyle(style -> style.withColor(SMART_INFO_LABEL_COLOR))
                    .append(Component.literal(value).withStyle(style -> style.withColor(SMART_INFO_VALUE_COLOR))));
        }
    }

    private void addWrappedSmartTooltipLine(List<Component> lines, String key, List<String> values) {
        String value = fullHutList(values);
        if (value == null || value.isBlank()) {
            return;
        }
        String label = Component.translatable(key).getString();
        int maxWidth = 190;
        int labelWidth = font.width(label);
        boolean firstLine = true;
        String remaining = value;
        while (!remaining.isBlank()) {
            int available = firstLine ? maxWidth - labelWidth : maxWidth - font.width("  ");
            String part = takeTooltipPart(remaining, Math.max(40, available));
            if (firstLine) {
                lines.add(Component.translatable(key).withStyle(style -> style.withColor(SMART_INFO_LABEL_COLOR))
                        .append(Component.literal(part).withStyle(style -> style.withColor(SMART_INFO_VALUE_COLOR))));
            } else {
                lines.add(Component.literal("           ").append(Component.literal(part)
                        .withStyle(style -> style.withColor(SMART_INFO_VALUE_COLOR))));
            }
            firstLine = false;
            remaining = remaining.substring(Math.min(part.length(), remaining.length())).stripLeading();
            if (remaining.startsWith(",")) {
                remaining = remaining.substring(1).stripLeading();
            }
        }
    }

    private String takeTooltipPart(String value, int width) {
        if (font.width(value) <= width) {
            return value;
        }
        String part = font.plainSubstrByWidth(value, width);
        int comma = part.lastIndexOf(", ");
        return comma > 0 ? part.substring(0, comma) : part;
    }

    private int maxScroll(int top, int bottom) {
        return Math.max(0, contentHeight - (bottom - top));
    }

    private static void toggle(Set<Integer> set, int value) {
        if (!set.add(value)) {
            set.remove(value);
        }
    }

    private int valueLineHeight(String value) {
        return value == null || value.isBlank() ? 0 : LINE_HEIGHT;
    }

    private List<SmartClipboardReport.RequestTreeNode> dependencyNodes(SmartClipboardReport.Entry entry) {
        return entry.requestTree().stream()
                .filter(node -> node.depth() > 0)
                .toList();
    }

    private List<Integer> filteredEntryIndexes() {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < report.entries().size(); i++) {
            indexes.add(i);
        }
        return indexes;
    }

    private String displayRequester(SmartClipboardReport.Entry entry) {
        String building = displayRequester(entry.requestingBuildingName());
        if (!isUnknownHut(building)) {
            return building;
        }
        return entry.requestingWorkerName()
                .filter(name -> !name.isBlank())
                .map(this::displayRequester)
                .filter(name -> !isUnknownHut(name))
                .orElse(building);
    }

    private String displayRequester(String requesterName) {
        return requesterName == null || requesterName.isBlank() ? unknownHut() : shortenHutName(requesterName);
    }

    private boolean isUnknownHut(String value) {
        return value.equals(unknownHut());
    }

    private String unknownHut() {
        return Component.translatable("screen.create_colony_logistics.smart_clipboard.unknown_hut").getString();
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
        return String.join(", ", values.stream().map(this::shortenHutName).distinct().toList());
    }

    private String fullHutList(List<String> values) {
        return formatHutList(values);
    }

    private String shortenHutName(String name) {
        return humanizeHutName(name);
    }

    private String humanizeHutName(String name) {
        if (name == null || name.isBlank()) {
            return unknownHut();
        }
        String value = name;
        int separator = value.indexOf(" / ");
        if (separator >= 0) {
            value = value.substring(0, separator);
        }
        int colon = value.lastIndexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        int dot = value.lastIndexOf('.');
        if (dot >= 0) {
            value = value.substring(dot + 1);
        }
        value = value.replaceAll("(?i)\\bcom\\b|\\bminecolonies\\b|\\bcore\\b|\\bcolony\\b|\\bworkerbuildings\\b", " ");
        value = value.replaceAll("(?i)building(?=[A-Z])", " ");
        value = value.replaceAll("(?i)\\b(building|build|module|crafting|hut)\\b", " ");
        value = value.replaceAll("[_\\-{}\\[\\]().:]+", " ").trim();
        if (value.isBlank()) {
            return unknownHut();
        }
        String[] words = value.split("\\s+");
        String chosen = words[0];
        if (words.length > 1 && chosen.matches("\\d+")) {
            chosen = words[1];
        }
        return Character.toUpperCase(chosen.charAt(0)) + chosen.substring(1).toLowerCase(Locale.ROOT);
    }

    private String humanizeDomumShape(SmartClipboardReport.Entry entry) {
        return humanizeResourcePath(entry.doBlockId());
    }

    private boolean isDomumOrnamentumEntry(SmartClipboardReport.Entry entry) {
        return entry.doBlockId().startsWith("domum_ornamentum:");
    }

    private String humanizeDimension(String id) {
        if ("minecraft:overworld".equals(id)) {
            return "Overworld";
        }
        if ("minecraft:the_nether".equals(id)) {
            return "Nether";
        }
        if ("minecraft:the_end".equals(id)) {
            return "End";
        }
        return humanizeResourcePath(id);
    }

    private String humanizeResolver(String resolverName) {
        if (resolverName == null || resolverName.isBlank()) {
            return null;
        }
        String value = resolverName
                .replace("RequestResolver", "")
                .replace("Resolver", "")
                .replace("Standard", "")
                .replace("Concrete", "");
        value = value.replaceAll("(?<=[a-z])(?=[A-Z])", " ").trim();
        return value.isBlank() ? null : value;
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
}
