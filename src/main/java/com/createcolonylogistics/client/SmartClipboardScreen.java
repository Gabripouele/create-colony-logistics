package com.createcolonylogistics.client;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.clipboard.DomumOrnamentumRequestInspector;
import com.createcolonylogistics.clipboard.SmartClipboardReport;
import com.createcolonylogistics.clipboard.SmartClipboardScrollStorage;
import com.createcolonylogistics.clipboard.SmartClipboardScrollStorage.ScrollLinkSnapshot;
import com.createcolonylogistics.network.ServerboundSmartClipboardDebugPacket;
import com.createcolonylogistics.network.ServerboundSmartClipboardScrollPacket;
import com.createcolonylogistics.network.ServerboundSmartScrollDebugPacket;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.items.component.BuildingId;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.items.component.WarehouseSnapshot;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.colony.workorders.IWorkOrderView;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingResourcesModuleView;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource.RessourceAvailability;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource.ResourceComparator;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.client.gui.WindowResourceList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SmartClipboardScreen extends Screen {
    private static final ResourceLocation STOCK_KEEPER_TEXTURE = ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MOD_ID, "textures/gui/smart_clipboard_gui.png");
    private static final ResourceLocation RESOURCE_SCROLL_ID = ResourceLocation.fromNamespaceAndPath("minecolonies", "resourcescroll");
    private static final ResourceLocation MINECOLONIES_CLIPBOARD_ID = ResourceLocation.fromNamespaceAndPath("minecolonies", "clipboard");
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int PANEL_WIDTH = 256;
    private static final int PANEL_HEIGHT = 316;
    private static final int TOP_CAP_HEIGHT = 36;
    private static final int BODY_SLICE_HEIGHT = 20;
    private static final int BOTTOM_CAP_HEIGHT = 20;
    private static final int BODY_SLICE_U = 0;
    private static final int BODY_SLICE_V = 48;
    private static final int BOTTOM_CAP_U = 0;
    private static final int BOTTOM_CAP_V = 140;
    private static final int TAB_WIDTH = 20;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_ICON_PADDING = 2;
    private static final int TITLE_Y = 9;
    private static final int TAB_Y = TITLE_Y + 20;
    private static final int COLONY_LINE_Y = TAB_Y + TAB_HEIGHT + 4;
    private static final int SUMMARY_LINE_Y = COLONY_LINE_Y + 12;
    private static final int CONTENT_LIST_TOP = SUMMARY_LINE_Y + 19;
    private static final int SCROLL_DETAILS_TOP = COLONY_LINE_Y + 16;
    private static final int CONTENT_LIST_BOTTOM = 294;
    private static final int SCROLL_STORAGE_GRID_TOP = 269;
    private static final int SCROLL_DETAILS_BOTTOM_GAP = 8;
    private static final int LIST_X = 39;
    private static final int LIST_WIDTH = 180;
    private static final int SCROLL_X = 219;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final int SCROLLBAR_TRACK_TOP_INSET = 2;
    private static final int SCROLLBAR_TRACK_BOTTOM_INSET = 2;
    private static final int SCROLLBAR_THUMB_HEIGHT = 9;
    private static final int IMPORTANT_BUTTON_SIZE = 11;
    private static final int TREE_INDENT = 8;
    private static final int TREE_ROW_HEIGHT = 18;
    private static final int RESOURCE_ROW_HEIGHT = 36;
    private static final int RESOURCE_SEPARATOR_Y = 33;
    private static final int ROW_GAP = 2;
    private static final int LINE_HEIGHT = 10;
    private static final int COLLAPSED_HEIGHT = 28;
    private static final int EXPANDED_TOP_PADDING = 30;
    private static final int EXPANDED_BOTTOM_PADDING = 6;
    private static final int PRIMARY_TEXT = 0xFF5F4939;
    private static final int TITLE_TEXT = 0xFF3F2A19;
    private static final int LABEL_TEXT = 0xFF4A2D18;
    private static final int SECONDARY_TEXT = 0xFF8A6B4F;
    private static final int MUTED_TEXT = 0xFFA88F73;
    private static final int QUANTITY_TEXT = 0xFF7C5F45;
    private static final int DIVIDER_LINE = 0xFF7B5E46;
    private static final int SMART_INFO_HEADER_COLOR = 0xA0A0A0;
    private static final int SMART_INFO_LABEL_COLOR = 0xFFF2D78C;
    private static final int SMART_INFO_VALUE_COLOR = 0xFF8FA7FF;
    private static final int RESOURCE_SCROLL_SOFT_RED = 0xFFB84A3A;
    private static final int SCROLL_SLOT_SIZE = 20;
    private static final int SCROLL_SLOT_COLUMNS = 9;

    private final SmartClipboardReport report;
    private final Set<Integer> expanded = new HashSet<>();
    private final Set<String> expandedDependencies = new HashSet<>();
    private int leftPos;
    private int topPos;
    private int scroll;
    private int contentHeight;
    private boolean importantOnly;
    private Tab activeTab = Tab.REQUESTS;
    private int selectedScroll;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;
    private static Tab rememberedTab = Tab.REQUESTS;
    private static int rememberedSelectedScroll;

    public SmartClipboardScreen(SmartClipboardReport report) {
        super(Component.translatable("screen.create_colony_logistics.smart_clipboard.title"));
        this.report = report;
        this.activeTab = rememberedTab;
        this.selectedScroll = clampSelectedScroll(report, rememberedSelectedScroll);
    }

    @Override
    protected void init() {
        leftPos = (width - PANEL_WIDTH) / 2;
        topPos = (height - PANEL_HEIGHT) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        renderStockKeeperPanel(graphics);
        renderPageTabs(graphics, mouseX, mouseY);

        Component headerTitle = truncate(activeTab == Tab.REQUESTS
                ? title
                : Component.translatable("screen.create_colony_logistics.smart_clipboard.scrolls_title"), 196);
        graphics.drawString(font, headerTitle, leftPos + (PANEL_WIDTH - font.width(headerTitle)) / 2, topPos + TITLE_Y, TITLE_TEXT, false);
        graphics.drawString(font, truncate(Component.literal(displayedColonyName()), activeTab == Tab.REQUESTS ? LIST_WIDTH - IMPORTANT_BUTTON_SIZE - 5 : LIST_WIDTH), leftPos + LIST_X, topPos + COLONY_LINE_Y, SECONDARY_TEXT, false);
        if (activeTab == Tab.REQUESTS) {
            renderImportantButton(graphics, mouseX, mouseY);
            graphics.drawString(font, truncate(Component.translatable("screen.create_colony_logistics.smart_clipboard.summary", report.activeRequestCount(), report.buildingCount()), LIST_WIDTH), leftPos + LIST_X, topPos + SUMMARY_LINE_Y, SECONDARY_TEXT, false);
        }

        int listTop = listTop();
        int listBottom = listBottom();
        graphics.fill(leftPos + LIST_X, listTop - 3, leftPos + LIST_X + LIST_WIDTH, listTop - 2, DIVIDER_LINE);
        scroll = clampScroll(scroll, listTop, listBottom);
        if (activeTab == Tab.SCROLLS) {
            renderScrollStorageGrid(graphics);
        }
        graphics.enableScissor(leftPos + LIST_X, listTop, leftPos + LIST_X + LIST_WIDTH, listBottom);
        contentHeight = activeTab == Tab.REQUESTS
                ? renderEntries(graphics, mouseX, mouseY, listTop, listBottom)
                : renderScrollDetails(graphics, listTop);
        graphics.disableScissor();

        scroll = clampScroll(scroll, listTop, listBottom);
        ScrollbarMetrics scrollbar = scrollbarMetrics(listTop, listBottom);
        if (scrollbar != null) {
            renderStockKeeperScrollbar(graphics, scrollbar);
        }

        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        if (renderHoveredTabTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        if (activeTab == Tab.SCROLLS && (renderHoveredScrollTooltip(graphics, mouseX, mouseY)
                || renderHoveredScrollResourceTooltip(graphics, mouseX, mouseY, listTop))) {
            return;
        }
        if (activeTab == Tab.REQUESTS && !renderHoveredItemTooltip(graphics, mouseX, mouseY, listTop)) {
            if (!renderHoveredTreeItemTooltip(graphics, mouseX, mouseY, listTop)) {
                renderHoveredOverflowTooltip(graphics, mouseX, mouseY, listTop);
            }
        }
    }

    private void renderPageTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        renderPageTab(graphics, Tab.REQUESTS, tabX(Tab.REQUESTS), tabY());
        renderPageTab(graphics, Tab.SCROLLS, tabX(Tab.SCROLLS), tabY());
    }

    private void renderPageTab(GuiGraphics graphics, Tab tab, int x, int y) {
        boolean active = activeTab == tab;
        if (active) {
            drawTabOutline(graphics, x, y);
        }
        graphics.renderItem(tabIcon(tab), x + TAB_ICON_PADDING, y + TAB_ICON_PADDING);
    }

    private void drawTabOutline(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + TAB_WIDTH, y + 1, 0xFF55DD55);
        graphics.fill(x, y + TAB_HEIGHT - 1, x + TAB_WIDTH, y + TAB_HEIGHT, 0xFF55DD55);
        graphics.fill(x, y, x + 1, y + TAB_HEIGHT, 0xFF55DD55);
        graphics.fill(x + TAB_WIDTH - 1, y, x + TAB_WIDTH, y + TAB_HEIGHT, 0xFF55DD55);
    }

    private boolean renderHoveredTabTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Tab tab : Tab.values()) {
            if (tabHit(mouseX, mouseY, tabX(tab), tabY())) {
                graphics.renderTooltip(font, Component.literal(tabTooltip(tab)), mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    private ItemStack tabIcon(Tab tab) {
        if (tab == Tab.REQUESTS) {
            return BuiltInRegistries.ITEM.getOptional(MINECOLONIES_CLIPBOARD_ID)
                    .map(ItemStack::new)
                    .orElse(ItemStack.EMPTY);
        }
        return BuiltInRegistries.ITEM.getOptional(RESOURCE_SCROLL_ID)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }

    private String tabTooltip(Tab tab) {
        return tab == Tab.REQUESTS ? "Smart Clipboard Tab" : "Smart Resource Tab";
    }

    private int tabX(Tab tab) {
        int center = leftPos + PANEL_WIDTH / 2;
        return tab == Tab.REQUESTS ? center - TAB_WIDTH - 3 : center + 3;
    }

    private int tabY() {
        return topPos + TAB_Y;
    }

    private void renderImportantButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = importantButtonX();
        int y = importantButtonY();
        int fill = 0x805E5A52;
        int bangColor = importantOnly ? 0xFFFF5555 : 0xFF55DD55;
        graphics.fill(x, y, x + IMPORTANT_BUTTON_SIZE, y + IMPORTANT_BUTTON_SIZE, fill);
        graphics.fill(x, y, x + IMPORTANT_BUTTON_SIZE, y + 1, DIVIDER_LINE);
        graphics.fill(x, y + IMPORTANT_BUTTON_SIZE - 1, x + IMPORTANT_BUTTON_SIZE, y + IMPORTANT_BUTTON_SIZE, DIVIDER_LINE);
        graphics.fill(x, y, x + 1, y + IMPORTANT_BUTTON_SIZE, DIVIDER_LINE);
        graphics.fill(x + IMPORTANT_BUTTON_SIZE - 1, y, x + IMPORTANT_BUTTON_SIZE, y + IMPORTANT_BUTTON_SIZE, DIVIDER_LINE);
        int textX = x + (IMPORTANT_BUTTON_SIZE - font.width("!")) / 2;
        int textY = y + (IMPORTANT_BUTTON_SIZE - font.lineHeight) / 2;
        graphics.drawString(font, "!", textX, textY, bangColor, false);
        if (mouseX >= x && mouseX < x + IMPORTANT_BUTTON_SIZE && mouseY >= y && mouseY < y + IMPORTANT_BUTTON_SIZE) {
            graphics.renderTooltip(font, Component.translatable(importantOnly
                    ? "screen.create_colony_logistics.smart_clipboard.filtering_important"
                    : "screen.create_colony_logistics.smart_clipboard.filtering_all"), mouseX, mouseY);
        }
    }

    private int renderEntries(GuiGraphics graphics, int mouseX, int mouseY, int listTop, int listBottom) {
        int x = leftPos + LIST_X;
        int y = listTop - scroll;
        int cardWidth = LIST_WIDTH;
        List<Integer> visibleIndexes = filteredEntryIndexes();

        if (visibleIndexes.isEmpty()) {
            Component empty = Component.translatable("screen.create_colony_logistics.smart_clipboard.empty");
            graphics.drawString(font, truncate(empty, cardWidth), x, y + 12, MUTED_TEXT, false);
            if (report.capped()) {
                graphics.drawString(font, Component.translatable("screen.create_colony_logistics.smart_clipboard.capped"), x, y + 26, MUTED_TEXT, false);
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
            graphics.drawString(font, Component.translatable("screen.create_colony_logistics.smart_clipboard.capped"), x, y + 4, MUTED_TEXT, false);
            y += 20;
        }

        return Math.max(0, y - (listTop - scroll));
    }

    private void renderScrollStorageGrid(GuiGraphics graphics) {
        int x = leftPos + LIST_X;
        int y = scrollStorageGridTop();
        List<ItemStack> scrolls = report.resourceScrolls();

        for (int i = 0; i < SmartClipboardScrollStorage.SLOT_COUNT; i++) {
            int slotX = scrollSlotX(x, i);
            int slotY = scrollSlotY(y, i);
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0x805E5A52);
            graphics.fill(slotX, slotY, slotX + 18, slotY + 1, DIVIDER_LINE);
            graphics.fill(slotX, slotY + 17, slotX + 18, slotY + 18, DIVIDER_LINE);
            graphics.fill(slotX, slotY, slotX + 1, slotY + 18, DIVIDER_LINE);
            graphics.fill(slotX + 17, slotY, slotX + 18, slotY + 18, DIVIDER_LINE);
            ItemStack stack = i < scrolls.size() ? scrolls.get(i) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, slotX + 1, slotY + 1);
                if (i == selectedScroll) {
                    graphics.fill(slotX, slotY, slotX + 18, slotY + 1, 0xFF55DD55);
                    graphics.fill(slotX, slotY + 17, slotX + 18, slotY + 18, 0xFF55DD55);
                    graphics.fill(slotX, slotY, slotX + 1, slotY + 18, 0xFF55DD55);
                    graphics.fill(slotX + 17, slotY, slotX + 18, slotY + 18, 0xFF55DD55);
                }
            } else {
                graphics.drawString(font, "+", slotX + 6, slotY + 5, MUTED_TEXT, false);
            }
        }
    }

    private int renderScrollDetails(GuiGraphics graphics, int listTop) {
        int x = leftPos + LIST_X;
        int y = listTop - scroll;
        List<ItemStack> scrolls = report.resourceScrolls();
        ItemStack selected = selectedScroll >= 0 && selectedScroll < scrolls.size() ? scrolls.get(selectedScroll) : ItemStack.EMPTY;
        if (selected.isEmpty()) {
            graphics.drawString(font, truncate(Component.literal("No Resource Scroll selected."), LIST_WIDTH), x, y, MUTED_TEXT, false);
            return y + 14 - (listTop - scroll);
        }

        y = renderSelectedScrollFromClientStack(graphics, selected, x, y);
        return Math.max(0, y - (listTop - scroll));
    }

    private int renderSelectedScrollFromClientStack(GuiGraphics graphics, ItemStack selectedClientScroll, int x, int y) {
        ResourceScrollContent content = buildClientResourceScrollRows(selectedClientScroll);
        if (!content.error().isBlank()) {
            graphics.drawString(font, truncate(Component.literal(content.error()), LIST_WIDTH), x, y, MUTED_TEXT, false);
            return y + 14;
        }
        if (content.resources().isEmpty()) {
            graphics.drawString(font, truncate(Component.literal("This Builder is Idle."), LIST_WIDTH), x, y, MUTED_TEXT, false);
            return y + 14;
        }

        graphics.drawString(font, truncate(Component.literal(sanitizeScrollLine(content.buildingTitle())), LIST_WIDTH), x, y, PRIMARY_TEXT, false);
        y += LINE_HEIGHT;
        if (!content.projectTitle().isBlank()) {
            graphics.drawString(font, truncate(Component.literal(sanitizeScrollLine(content.projectTitle())), LIST_WIDTH), x, y, SECONDARY_TEXT, false);
            y += LINE_HEIGHT;
        }
        graphics.drawString(font, truncate(Component.translatable("screen.create_colony_logistics.smart_clipboard.progress", content.suppliedPercent(), content.usedPercent()), LIST_WIDTH), x, y, SECONDARY_TEXT, false);
        y += LINE_HEIGHT + 3;

        for (int i = 0; i < content.resources().size(); i++) {
            ResourceLine resource = content.resources().get(i);
            int textWidth = Math.max(30, LIST_WIDTH - 22);
            graphics.renderItem(resource.stack(), x, y);
            graphics.drawString(font, truncate(Component.literal(sanitizeScrollLine(resource.name())), textWidth), x + 22, y + 1, PRIMARY_TEXT, false);
            drawNeededLine(graphics, resource, x + 22, y + 11, textWidth);
            graphics.drawString(font, truncate(Component.translatable("screen.create_colony_logistics.smart_clipboard.supplied", resource.available(), resource.required()), textWidth), x + 22, y + 21, resource.suppliedColor(), false);
            if (resource.deliveryOrWarehouseAmount() > 0) {
                graphics.drawString(font, truncate(Component.literal(String.valueOf(resource.deliveryOrWarehouseAmount())), 24), x + LIST_WIDTH - 24, y + 11, SECONDARY_TEXT, false);
            }
            if (i < content.resources().size() - 1) {
                graphics.fill(x + 22, y + RESOURCE_SEPARATOR_Y, x + LIST_WIDTH, y + RESOURCE_SEPARATOR_Y + 1, DIVIDER_LINE);
            }
            y += RESOURCE_ROW_HEIGHT;
        }
        return y;
    }

    private void drawNeededLine(GuiGraphics graphics, ResourceLine resource, int x, int y, int width) {
        Component label = Component.literal("Needed: ");
        graphics.drawString(font, truncate(label, width), x, y, LABEL_TEXT, false);
        int valueX = x + font.width(label);
        graphics.drawString(font, truncate(Component.literal(String.valueOf(resource.missing())), Math.max(0, width - font.width(label))), valueX, y, resource.neededValueColor(), false);
    }

    private ResourceScrollContent buildClientResourceScrollRows(ItemStack selectedClientScroll) {
        return ResourceScrollContent.fromClientSelectedStack(selectedClientScroll);
    }

    private String displayedColonyName() {
        if (activeTab == Tab.REQUESTS) {
            return report.colonyName();
        }
        return selectedResourceScroll().map(this::resourceScrollColonyName).orElse("");
    }

    private Optional<ItemStack> selectedResourceScroll() {
        List<ItemStack> scrolls = report.resourceScrolls();
        if (selectedScroll >= 0 && selectedScroll < scrolls.size() && !scrolls.get(selectedScroll).isEmpty()) {
            return Optional.of(scrolls.get(selectedScroll));
        }
        return Optional.empty();
    }

    private String resourceScrollColonyName(ItemStack scroll) {
        IColonyView colony = ColonyId.readColonyViewFromItemStack(scroll);
        return colony == null ? "" : colony.getName();
    }

    private String sanitizeScrollLine(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').strip();
    }

    private void renderEntry(GuiGraphics graphics, SmartClipboardReport.Entry entry, int index, int x, int y, int width, int height) {
        graphics.fill(x, y + height - 1, x + width, y + height, DIVIDER_LINE);
        ItemStack shownStack = displayStack(entry);
        graphics.renderItem(shownStack, x + 2, y + 4);
        graphics.renderItemDecorations(font, shownStack, x + 2, y + 4);

        int textX = x + 24;
        drawNameWithQuantity(graphics, textX, y + 3, entry.requestedStack().getHoverName(), entry.quantityDisplay(), width - 28);
        drawLabelValue(graphics, textX, y + 15,
                Component.translatable("screen.create_colony_logistics.smart_clipboard.requester_label"),
                Component.literal(displayRequester(entry)), width - 28);

        if (expanded.contains(index) && hasExpandedDetails(entry)) {
            int detailY = y + EXPANDED_TOP_PADDING;
            detailY = value(graphics, x + 8, detailY, "screen.create_colony_logistics.smart_clipboard.type",
                    entry.minimumStockRequest() ? Component.translatable("screen.create_colony_logistics.smart_clipboard.minimum_stock_request").getString() : null);
            if (!entry.minimumStockRequest()) {
                detailY = value(graphics, x + 8, detailY, "screen.create_colony_logistics.smart_clipboard.worker", entry.requestingWorkerName().orElse(null));
            }
            List<SmartClipboardReport.RequestTreeNode> dependencies = dependencyNodes(entry);
            if (!dependencies.isEmpty()) {
                detailY += 2;
                for (int nodeIndex : visibleDependencyIndexes(entry, index)) {
                    detailY = dependencyValue(graphics, x + 4, detailY, entry, index, nodeIndex);
                }
            }
        }
    }

    private int value(GuiGraphics graphics, int x, int y, String key, String value) {
        if (value == null || value.isBlank()) {
            return y;
        }
        Component label = Component.translatable(key + "_label");
        int maxWidth = Math.max(40, leftPos + LIST_X + LIST_WIDTH - x - 4);
        graphics.drawString(font, truncate(label, maxWidth), x, y, LABEL_TEXT, false);
        int valueX = x + font.width(label);
        graphics.drawString(font, truncate(Component.literal(value), Math.max(20, leftPos + LIST_X + LIST_WIDTH - valueX - 4)), valueX, y, SECONDARY_TEXT, false);
        return y + LINE_HEIGHT;
    }

    private void drawLabelValue(GuiGraphics graphics, int x, int y, Component label, Component value, int width) {
        int labelWidth = Math.min(font.width(label), width);
        graphics.drawString(font, truncate(label, width), x, y, LABEL_TEXT, false);
        int valueX = x + labelWidth;
        graphics.drawString(font, truncate(value, Math.max(10, width - labelWidth)), valueX, y, SECONDARY_TEXT, false);
    }

    private void drawNameWithQuantity(GuiGraphics graphics, int x, int y, Component name, String quantity, int width) {
        drawComponentWithQuantity(graphics, x, y, name, quantity, width, PRIMARY_TEXT);
    }

    private void drawTreeNodeText(GuiGraphics graphics, int x, int y, SmartClipboardReport.RequestTreeNode node, int width) {
        Component name = node.stack().isEmpty()
                ? Component.literal(cleanTreeLabel(node.label()))
                : node.stack().getHoverName();
        String quantity = node.quantityDisplay() == null || node.quantityDisplay().isBlank()
                ? "x" + Math.max(1, node.count())
                : node.quantityDisplay();
        int nameColor = node.depth() <= 1 ? PRIMARY_TEXT : SECONDARY_TEXT;
        drawComponentWithQuantity(graphics, x, y, name, quantity, width, nameColor);
    }

    private void drawComponentWithQuantity(GuiGraphics graphics, int x, int y, Component name, String quantity, int width, int nameColor) {
        String safeQuantity = quantity == null ? "" : quantity.strip();
        int quantityWidth = safeQuantity.isBlank() ? 0 : font.width(" " + safeQuantity);
        int nameWidth = Math.max(0, width - quantityWidth);
        Component shownName = truncate(name, nameWidth);
        graphics.drawString(font, shownName, x, y, nameColor, false);
        if (!safeQuantity.isBlank() && width >= quantityWidth) {
            graphics.drawString(font, " " + safeQuantity, x + font.width(shownName), y, QUANTITY_TEXT, false);
        }
    }

    private int label(GuiGraphics graphics, int x, int y, String key) {
        graphics.drawString(font, Component.translatable(key), x, y, LABEL_TEXT, false);
        return y + LINE_HEIGHT;
    }

    private int dependencyValue(GuiGraphics graphics, int x, int y, SmartClipboardReport.Entry entry, int entryIndex, int nodeIndex) {
        List<SmartClipboardReport.RequestTreeNode> dependencies = dependencyNodes(entry);
        SmartClipboardReport.RequestTreeNode node = dependencies.get(nodeIndex);
        int visibleDepth = Math.max(1, node.depth());
        int indent = Math.min(36, (visibleDepth - 1) * TREE_INDENT);
        boolean expandable = hasDependencyChildren(dependencies, nodeIndex);
        boolean expanded = expandedDependencies.contains(dependencyKey(entryIndex, nodeIndex));
        String marker = expandable ? (expanded ? "v" : ">") : "-";
        graphics.drawString(font, marker, x + indent, y + 4, LABEL_TEXT, false);

        ItemStack stack = node.stack();
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + indent + 9, y);
        }
        drawTreeNodeText(graphics, x + indent + 28, y + 4, node, Math.max(40, leftPos + LIST_X + LIST_WIDTH - x - indent - 29));
        return y + TREE_ROW_HEIGHT;
    }

    private int entryHeight(SmartClipboardReport.Entry entry, int index) {
        if (!expanded.contains(index) || !hasExpandedDetails(entry)) {
            return COLLAPSED_HEIGHT;
        }
        int height = EXPANDED_TOP_PADDING;
        height += valueLineHeight(entry.minimumStockRequest()
                ? Component.translatable("screen.create_colony_logistics.smart_clipboard.minimum_stock_request").getString()
                : null);
        if (!entry.minimumStockRequest()) {
            height += valueLineHeight(entry.requestingWorkerName().orElse(null));
        }
        List<SmartClipboardReport.RequestTreeNode> dependencies = dependencyNodes(entry);
        if (!dependencies.isEmpty()) {
            height += 2 + visibleDependencyIndexes(entry, index).size() * TREE_ROW_HEIGHT;
        }
        return height + EXPANDED_BOTTOM_PADDING;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleTabClick(mouseX, mouseY)) {
            return true;
        }

        int listTop = listTop();
        if (activeTab == Tab.SCROLLS) {
            if (Screen.hasShiftDown() && button != 1 && scrollDebugHit(mouseX, mouseY)) {
                dumpSelectedScrollDebug();
                return true;
            }
            if (handleScrollbarClick(mouseX, mouseY, button)) {
                return true;
            }
            return handleScrollClick(mouseX, mouseY, button);
        }
        if (handleScrollbarClick(mouseX, mouseY, button)) {
            return true;
        }
        if (Screen.hasShiftDown() && scrollDebugHit(mouseX, mouseY)) {
            CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] ignored: not Scrolls tab activeTab={}", activeTab);
            showScrollDebugMessage("Smart Scroll debug ignored: not Scrolls tab");
        }

        if (mouseX >= importantButtonX() && mouseX < importantButtonX() + IMPORTANT_BUTTON_SIZE
                && mouseY >= importantButtonY() && mouseY < importantButtonY() + IMPORTANT_BUTTON_SIZE) {
            importantOnly = !importantOnly;
            scroll = 0;
            return true;
        }

        int y = listTop - scroll;
        int x = leftPos + LIST_X;

        for (int i : filteredEntryIndexes()) {
            SmartClipboardReport.Entry entry = report.entries().get(i);
            int cardHeight = entryHeight(entry, i);
            if (mouseX >= x && mouseX <= x + LIST_WIDTH && mouseY >= y && mouseY <= y + cardHeight) {
                if (Screen.hasShiftDown()) {
                    sendDebugDumpRequest(entry);
                    return true;
                }
                if (expanded.contains(i) && toggleDependencyAt(entry, i, mouseY, y)) {
                    return true;
                }
                if (!hasExpandedDetails(entry)) {
                    return false;
                }
                toggle(expanded, i);
                return true;
            }
            y += cardHeight + ROW_GAP;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean scrollDebugHit(double mouseX, double mouseY) {
        return mouseX >= leftPos + LIST_X
                && mouseX < leftPos + LIST_X + LIST_WIDTH
                && mouseY >= listTop()
                && mouseY < listBottom();
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        if (tabHit(mouseX, mouseY, tabX(Tab.REQUESTS), tabY())) {
            activeTab = Tab.REQUESTS;
            rememberState();
            scroll = 0;
            return true;
        }
        if (tabHit(mouseX, mouseY, tabX(Tab.SCROLLS), tabY())) {
            activeTab = Tab.SCROLLS;
            rememberState();
            scroll = 0;
            return true;
        }
        return false;
    }

    private boolean tabHit(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT;
    }

    private boolean handleScrollbarClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        ScrollbarMetrics metrics = scrollbarMetrics(listTop(), listBottom());
        if (metrics == null || !metrics.trackHit(mouseX, mouseY)) {
            return false;
        }
        if (metrics.thumbHit(mouseX, mouseY)) {
            draggingScrollbar = true;
            scrollbarDragOffset = (int) Math.round(mouseY) - metrics.thumbY();
        } else {
            draggingScrollbar = true;
            scrollbarDragOffset = SCROLLBAR_THUMB_HEIGHT / 2;
            setScrollFromThumbY((int) Math.round(mouseY) - scrollbarDragOffset, metrics);
        }
        return true;
    }

    private boolean handleScrollClick(double mouseX, double mouseY, int button) {
        int x = leftPos + LIST_X;
        int y = scrollStorageGridTop();
        for (int i = 0; i < SmartClipboardScrollStorage.SLOT_COUNT; i++) {
            int slotX = scrollSlotX(x, i);
            int slotY = scrollSlotY(y, i);
            if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                ItemStack stack = i < report.resourceScrolls().size() ? report.resourceScrolls().get(i) : ItemStack.EMPTY;
                if (stack.isEmpty()) {
                    activeTab = Tab.SCROLLS;
                    selectedScroll = i;
                    rememberState();
                    PacketDistributor.sendToServer(new ServerboundSmartClipboardScrollPacket(
                            ServerboundSmartClipboardScrollPacket.INSERT,
                            i,
                            firstInventoryResourceScroll()
                    ));
                } else if (button == 1 && Screen.hasShiftDown()) {
                    activeTab = Tab.SCROLLS;
                    selectedScroll = i;
                    rememberState();
                    openMineColoniesResourceScrollWindow(i, stack);
                } else if (button == 1 || Screen.hasShiftDown()) {
                    activeTab = Tab.SCROLLS;
                    selectedScroll = nearestSelectedAfterRemoval(i);
                    rememberState();
                    PacketDistributor.sendToServer(new ServerboundSmartClipboardScrollPacket(
                            ServerboundSmartClipboardScrollPacket.REMOVE,
                            i,
                            ScrollLinkSnapshot.EMPTY
                    ));
                } else {
                    selectedScroll = i;
                    rememberState();
                }
                return true;
            }
        }
        return false;
    }

    private void openMineColoniesResourceScrollWindow(int slot, ItemStack selectedClientScroll) {
        ResourceLocation itemId = selectedClientScroll.isEmpty() ? ResourceLocation.withDefaultNamespace("air") : BuiltInRegistries.ITEM.getKey(selectedClientScroll.getItem());
        ColonyId colonyId = ColonyId.readFromItemStack(selectedClientScroll);
        BuildingId buildingId = BuildingId.readFromItemStack(selectedClientScroll);
        WarehouseSnapshot warehouseSnapshot = WarehouseSnapshot.readFromItemStack(selectedClientScroll);
        IBuildingView buildingView = selectedClientScroll.isEmpty() ? null : BuildingId.readBuildingViewFromItemStack(selectedClientScroll);
        boolean opened = false;
        String failureReason = "";
        try {
            if (Minecraft.getInstance().player == null) {
                failureReason = "no client player";
            } else if (!(buildingView instanceof BuildingBuilder.View builder)) {
                failureReason = buildingView == null ? "BuildingId.readBuildingViewFromItemStack returned null" : "resolved view is not BuildingBuilder.View";
            } else {
                Object window = new WindowResourceList(builder, warehouseSnapshot.snapshot());
                window.getClass().getMethod("open").invoke(window);
                opened = true;
            }
        } catch (ReflectiveOperationException exception) {
            failureReason = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        } catch (RuntimeException exception) {
            failureReason = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }

        CreateColonyLogistics.LOGGER.info("[SmartScrollParityTest] selectedStack={} slot={} hasColonyId={} colonyId={} dimension={} hasBuildingId={} buildingPos={} attemptingMineColoniesWindow=true builderView={} openedWindowResourceList={} failureReason='{}'",
                itemId,
                slot,
                colonyId.hasColonyId(),
                colonyId.id(),
                colonyId.dimension().location(),
                buildingId.hasId(),
                buildingId.id(),
                buildingView == null ? "none" : buildingView.getClass().getName(),
                opened,
                failureReason);
        showScrollDebugMessage(opened
                ? "Smart Scroll parity test opened MineColonies Resource Scroll window for slot " + slot
                : "Smart Scroll parity test failed for slot " + slot + ": " + failureReason);
    }

    private void dumpSelectedScrollDebug() {
        List<ItemStack> scrolls = report.resourceScrolls();
        ItemStack selected = selectedScroll >= 0 && selectedScroll < scrolls.size() ? scrolls.get(selectedScroll) : ItemStack.EMPTY;
        if (selected.isEmpty()) {
            CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] no selected scroll activeTab={} selectedIndex={} storedScrolls={}",
                    activeTab, selectedScroll, scrolls.size());
            showScrollDebugMessage("Smart Scroll debug: no selected scroll");
            PacketDistributor.sendToServer(new ServerboundSmartScrollDebugPacket(
                    selectedScroll,
                    "empty",
                    0,
                    false,
                    ScrollLinkSnapshot.EMPTY,
                    false,
                    false,
                    "none",
                    false,
                    false,
                    "no selected scroll",
                    0,
                    0,
                    0
            ));
            return;
        }
        ResourceScrollContent content = dumpSelectedScrollDebug(selectedScroll, selected);
        ResolvedScrollBuilding resolved = ResourceScrollContent.resolveBuilderView(selected);
        IBuildingView selectedView = resolved.building();
        PacketDistributor.sendToServer(new ServerboundSmartScrollDebugPacket(
                selectedScroll,
                String.valueOf(BuiltInRegistries.ITEM.getKey(selected.getItem())),
                selected.getCount(),
                !selected.getComponentsPatch().isEmpty(),
                ScrollLinkSnapshot.from(selected),
                true,
                selectedView != null,
                selectedView == null ? "none" : selectedView.getClass().getName(),
                selectedView instanceof BuildingBuilder.View,
                content.error().isBlank() && !content.resources().isEmpty(),
                content.error(),
                content.moduleResourceCount(),
                content.adaptedResourceCount(),
                content.resources().size()
        ));
        showScrollDebugMessage("Smart Scroll debug dumped for slot " + selectedScroll + " (server dump requested)");
    }

    private ResourceScrollContent dumpSelectedScrollDebug(int slot, ItemStack scrollStack) {
        ResourceScrollContent content = buildClientResourceScrollRows(scrollStack);
        ColonyId colonyId = ColonyId.readFromItemStack(scrollStack);
        BuildingId buildingId = BuildingId.readFromItemStack(scrollStack);
        WarehouseSnapshot warehouseSnapshot = WarehouseSnapshot.readFromItemStack(scrollStack);
        ResolvedScrollBuilding resolved = ResourceScrollContent.resolveBuilderView(scrollStack);
        IBuildingView building = resolved.building();
        BuildingResourcesModuleView module = building instanceof BuildingBuilder.View builder
                ? builder.getModuleViewByType(BuildingResourcesModuleView.class)
                : null;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(scrollStack.getItem());
        boolean hasComponents = !scrollStack.getComponentsPatch().isEmpty();

        CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] Selection: activeTab={} selectedIndex={} slot={} item={} count={} hasComponents={}",
                activeTab, selectedScroll, slot, itemId, scrollStack.getCount(), hasComponents);
        CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] Components: hasColonyId={} colonyId={} dimension={} hasBuildingId={} buildingPos={} hasWarehouseSnapshot={} warehouseEntries={}",
                colonyId.hasColonyId(), colonyId.id(), colonyId.dimension().location(), buildingId.hasId(), buildingId.id(),
                !warehouseSnapshot.hash().isEmpty() || !warehouseSnapshot.snapshot().isEmpty(), warehouseSnapshot.snapshot().size());
        CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] RenderAuthority: renderAuthority=client serverPathAuthoritative=false selectedStackSource=client-selected-index");
        CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] ClientResolution: source={} clientViewResolved={} clientViewClass={} isBuildingBuilderView={} colonyView={} resourcesModuleFound={} workOrderId={} progress={}",
                resolved.source(), building != null, building == null ? "none" : building.getClass().getName(), building instanceof BuildingBuilder.View,
                building != null && building.getColony() != null, module != null, module == null ? "n/a" : module.getWorkOrderId(),
                module == null ? "n/a" : module.getProgress());
        CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] ClientAdapter: entered=true resolutionSource={} moduleResources={} adaptedResources={} inventoryOverlay={} deliveryOverlay={} warehouseOverlay={} renderedRows={} invalidReason='{}'",
                content.resolutionSource(),
                content.moduleResourceCount(), content.adaptedResourceCount(), content.inventoryOverlayCount(),
                content.deliveryOverlayCount(), content.warehouseOverlayCount(), content.resources().size(), content.error());
        CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] RenderPath: method=renderSelectedScrollFromClientStack renderAuthority=client oldTooltipSummary=false clientAdapterList={} invalidMessage={} bounds={}x{}+{},{}",
                content.error().isBlank() && !content.resources().isEmpty(), !content.error().isBlank(), LIST_WIDTH,
                listBottom(Tab.SCROLLS) - listTop(Tab.SCROLLS), leftPos + LIST_X, listTop(Tab.SCROLLS));
        logHeldScrollComparison(scrollStack);
        for (int i = 0; i < Math.min(5, content.resources().size()); i++) {
            ResourceLine line = content.resources().get(i);
            CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] Row[{}]: name='{}' item={} needed={} available={} required={} deliveryOrWarehouse={} neededValueColor={} suppliedColor={}",
                    i, line.name(), BuiltInRegistries.ITEM.getKey(line.stack().getItem()), line.missing(), line.available(),
                    line.required(), line.deliveryOrWarehouseAmount(), line.neededValueColor(), line.suppliedColor());
        }
        return content;
    }

    private void logHeldScrollComparison(ItemStack selectedStack) {
        ItemStack carriedScroll = firstInventoryResourceScrollStack();
        if (carriedScroll.isEmpty()) {
            CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] StackCompare: no resource scroll found in player inventory for comparison");
            return;
        }
        logScrollStackSnapshot("selected-client", selectedStack);
        logScrollStackSnapshot("inventory-scroll", carriedScroll);

        ColonyId selectedColony = ColonyId.readFromItemStack(selectedStack);
        ColonyId inventoryColony = ColonyId.readFromItemStack(carriedScroll);
        BuildingId selectedBuilding = BuildingId.readFromItemStack(selectedStack);
        BuildingId inventoryBuilding = BuildingId.readFromItemStack(carriedScroll);
        WarehouseSnapshot selectedWarehouse = WarehouseSnapshot.readFromItemStack(selectedStack);
        WarehouseSnapshot inventoryWarehouse = WarehouseSnapshot.readFromItemStack(carriedScroll);
        CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] StackCompare: sameItem={} sameColony={} sameDimension={} sameBuilding={} sameWarehouseHash={} selectedPatch={} inventoryPatch={}",
                selectedStack.getItem() == carriedScroll.getItem(),
                selectedColony.id() == inventoryColony.id(),
                selectedColony.dimension().equals(inventoryColony.dimension()),
                selectedBuilding.id().equals(inventoryBuilding.id()),
                selectedWarehouse.hash().equals(inventoryWarehouse.hash()),
                selectedStack.getComponentsPatch(),
                carriedScroll.getComponentsPatch());
    }

    private void logScrollStackSnapshot(String label, ItemStack stack) {
        ColonyId colonyId = ColonyId.readFromItemStack(stack);
        BuildingId buildingId = BuildingId.readFromItemStack(stack);
        WarehouseSnapshot warehouseSnapshot = WarehouseSnapshot.readFromItemStack(stack);
        IBuildingView buildingView = stack.isEmpty() ? null : BuildingId.readBuildingViewFromItemStack(stack);
        CreateColonyLogistics.LOGGER.info("[SmartScrollDebug] Stack[{}]: item={} count={} hasComponents={} hasColonyId={} colonyId={} dimension={} hasBuildingId={} buildingPos={} hasWarehouseSnapshot={} warehouseEntries={} exactMineColoniesViewResolved={} viewClass={} isBuilderView={}",
                label,
                stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()),
                stack.getCount(),
                !stack.getComponentsPatch().isEmpty(),
                colonyId.hasColonyId(),
                colonyId.id(),
                colonyId.dimension().location(),
                buildingId.hasId(),
                buildingId.id(),
                !warehouseSnapshot.hash().isEmpty() || !warehouseSnapshot.snapshot().isEmpty(),
                warehouseSnapshot.snapshot().size(),
                buildingView != null,
                buildingView == null ? "none" : buildingView.getClass().getName(),
                buildingView instanceof BuildingBuilder.View);
    }

    private void showScrollDebugMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
    }

    private ScrollLinkSnapshot firstInventoryResourceScroll() {
        return ScrollLinkSnapshot.from(firstInventoryResourceScrollStack());
    }

    private ItemStack firstInventoryResourceScrollStack() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < minecraft.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = minecraft.player.getInventory().getItem(i);
            if (!stack.isEmpty() && RESOURCE_SCROLL_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeTab == Tab.SCROLLS && (mouseX < leftPos + LIST_X || mouseX >= leftPos + LIST_X + LIST_WIDTH
                || mouseY < listTop() || mouseY >= listBottom())) {
            return false;
        }
        scroll = clampScroll(scroll - (int) (scrollY * 18), listTop(), listBottom());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            ScrollbarMetrics metrics = scrollbarMetrics(listTop(), listBottom());
            if (metrics != null) {
                setScrollFromThumbY((int) Math.round(mouseY) - scrollbarDragOffset, metrics);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void renderStockKeeperPanel(GuiGraphics graphics) {
        int panelBottom = topPos + PANEL_HEIGHT;
        int bottomCapY = panelBottom - BOTTOM_CAP_HEIGHT;
        int bodyStartY = topPos + TOP_CAP_HEIGHT;
        int bodyEndY = bottomCapY;
        graphics.blit(STOCK_KEEPER_TEXTURE, leftPos, topPos, 0, 0, PANEL_WIDTH, TOP_CAP_HEIGHT);
        for (int y = bodyStartY; y < bodyEndY; y += BODY_SLICE_HEIGHT) {
            int height = Math.min(BODY_SLICE_HEIGHT, bodyEndY - y);
            graphics.blit(STOCK_KEEPER_TEXTURE, leftPos, y, BODY_SLICE_U, BODY_SLICE_V, PANEL_WIDTH, height);
        }
        graphics.blit(STOCK_KEEPER_TEXTURE, leftPos, bottomCapY, BOTTOM_CAP_U, BOTTOM_CAP_V, PANEL_WIDTH, BOTTOM_CAP_HEIGHT);
    }

    private void renderStockKeeperScrollbar(GuiGraphics graphics, ScrollbarMetrics metrics) {
        int x = metrics.x();
        graphics.blit(STOCK_KEEPER_TEXTURE, x, metrics.trackTop(), 219, 192, SCROLLBAR_WIDTH, 4);
        for (int y = metrics.trackTop() + 4; y < metrics.trackBottom() - 5; y++) {
            graphics.blit(STOCK_KEEPER_TEXTURE, x, y, 219, 196, SCROLLBAR_WIDTH, 1);
        }
        graphics.blit(STOCK_KEEPER_TEXTURE, x, metrics.trackBottom() - 5, 219, 207, SCROLLBAR_WIDTH, 5);
        graphics.blit(STOCK_KEEPER_TEXTURE, x, metrics.thumbY(), 219, 197, SCROLLBAR_WIDTH, SCROLLBAR_THUMB_HEIGHT);
    }

    private boolean renderHoveredItemTooltip(GuiGraphics graphics, int mouseX, int mouseY, int listTop) {
        int x = leftPos + LIST_X;
        int y = listTop - scroll;
        for (int i : filteredEntryIndexes()) {
            SmartClipboardReport.Entry entry = report.entries().get(i);
            int cardHeight = entryHeight(entry, i);
            if (mouseX >= x + 2 && mouseX < x + 18 && mouseY >= y + 4 && mouseY < y + 20) {
                ItemStack shownStack = displayStack(entry);
                graphics.renderComponentTooltip(font, buildApprovedSmartInfoTooltip(entry, shownStack), mouseX, mouseY, shownStack);
                return true;
            }
            y += cardHeight + ROW_GAP;
        }
        return false;
    }

    private void sendDebugDumpRequest(SmartClipboardReport.Entry entry) {
        String token = entry.requestToken().orElse("");
        if (token.isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new ServerboundSmartClipboardDebugPacket(
                token,
                entry.requestedStack().getHoverName().getString(),
                entry.quantityDisplay(),
                displayRequester(entry),
                entry.requestingWorkerName(),
                entry.minimumStockRequest(),
                hasExpandedDetails(entry),
                expandableReason(entry),
                dependencyNodes(entry).size()
        ));
    }

    private boolean renderHoveredScrollTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + LIST_X;
        int y = scrollStorageGridTop();
        for (int i = 0; i < SmartClipboardScrollStorage.SLOT_COUNT; i++) {
            int slotX = scrollSlotX(x, i);
            int slotY = scrollSlotY(y, i);
            if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                ItemStack stack = i < report.resourceScrolls().size() ? report.resourceScrolls().get(i) : ItemStack.EMPTY;
                if (!stack.isEmpty()) {
                    graphics.renderTooltip(font, stack, mouseX, mouseY);
                } else {
                    graphics.renderTooltip(font, Component.translatable("screen.create_colony_logistics.smart_clipboard.scroll_slot_empty"), mouseX, mouseY);
                }
                return true;
            }
        }
        return false;
    }

    private boolean renderHoveredScrollResourceTooltip(GuiGraphics graphics, int mouseX, int mouseY, int listTop) {
        if (mouseX < leftPos + LIST_X || mouseX >= leftPos + LIST_X + LIST_WIDTH
                || mouseY < listTop || mouseY >= listBottom(Tab.SCROLLS)) {
            return false;
        }
        List<ItemStack> scrolls = report.resourceScrolls();
        ItemStack selected = selectedScroll >= 0 && selectedScroll < scrolls.size() ? scrolls.get(selectedScroll) : ItemStack.EMPTY;
        if (selected.isEmpty()) {
            return false;
        }
        ResourceScrollContent content = buildClientResourceScrollRows(selected);
        if (!content.error().isBlank() || content.resources().isEmpty()) {
            return false;
        }

        int x = leftPos + LIST_X;
        int y = listTop - scroll;
        y += LINE_HEIGHT;
        if (!content.projectTitle().isBlank()) {
            y += LINE_HEIGHT;
        }
        y += LINE_HEIGHT + 3;

        for (ResourceLine resource : content.resources()) {
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                ItemStack stack = resource.stack();
                renderSmartInfoOrItemTooltip(graphics, stack, mouseX, mouseY);
                return true;
            }
            y += RESOURCE_ROW_HEIGHT;
        }
        return false;
    }

    private boolean renderHoveredTreeItemTooltip(GuiGraphics graphics, int mouseX, int mouseY, int listTop) {
        if (mouseX < leftPos + LIST_X || mouseX >= leftPos + LIST_X + LIST_WIDTH
                || mouseY < listTop || mouseY >= listBottom(Tab.REQUESTS)) {
            return false;
        }

        int x = leftPos + LIST_X;
        int y = listTop - scroll;
        for (int i : filteredEntryIndexes()) {
            SmartClipboardReport.Entry entry = report.entries().get(i);
            int cardHeight = entryHeight(entry, i);
            if (expanded.contains(i) && hasExpandedDetails(entry)) {
                int detailY = y + EXPANDED_TOP_PADDING;
                detailY += valueLineHeight(entry.minimumStockRequest()
                        ? Component.translatable("screen.create_colony_logistics.smart_clipboard.minimum_stock_request").getString()
                        : null);
                if (!entry.minimumStockRequest()) {
                    detailY += valueLineHeight(entry.requestingWorkerName().orElse(null));
                }
                List<SmartClipboardReport.RequestTreeNode> dependencies = dependencyNodes(entry);
                if (!dependencies.isEmpty()) {
                    detailY += 2;
                    for (int nodeIndex : visibleDependencyIndexes(entry, i)) {
                        SmartClipboardReport.RequestTreeNode node = dependencies.get(nodeIndex);
                        ItemStack stack = node.stack();
                        if (!stack.isEmpty()) {
                            int visibleDepth = Math.max(1, node.depth());
                            int indent = Math.min(36, (visibleDepth - 1) * TREE_INDENT);
                            int iconX = x + 4 + indent + 9;
                            if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= detailY && mouseY < detailY + 16) {
                                renderSmartInfoOrItemTooltip(graphics, stack, mouseX, mouseY);
                                return true;
                            }
                        }
                        detailY += TREE_ROW_HEIGHT;
                    }
                }
            }
            y += cardHeight + ROW_GAP;
        }
        return false;
    }

    private void renderSmartInfoOrItemTooltip(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        SmartClipboardReport.Entry smartInfoEntry = smartInfoEntryForStack(stack);
        if (smartInfoEntry != null) {
            graphics.renderComponentTooltip(font, buildApprovedSmartInfoTooltip(smartInfoEntry, stack), mouseX, mouseY, stack);
        } else {
            graphics.renderTooltip(font, stack, mouseX, mouseY);
        }
    }

    private SmartClipboardReport.Entry smartInfoEntryForStack(ItemStack stack) {
        for (SmartClipboardReport.Entry entry : report.entries()) {
            if (isArchitectsCutterEntry(entry) && ItemStack.isSameItemSameComponents(entry.requestedStack(), stack)) {
                return entry;
            }
        }
        for (SmartClipboardReport.Entry entry : report.entries()) {
            if (isArchitectsCutterEntry(entry) && entry.requestedStack().is(stack.getItem())) {
                return entry;
            }
        }
        return null;
    }

    private String expandableReason(SmartClipboardReport.Entry entry) {
        List<String> reasons = new ArrayList<>();
        if (entry.requestingWorkerName().filter(name -> !name.isBlank()).isPresent() && !entry.minimumStockRequest()) {
            reasons.add("worker present");
        }
        if (entry.minimumStockRequest()) {
            reasons.add("minimum stock");
        }
        if (!dependencyNodes(entry).isEmpty()) {
            reasons.add("dependency nodes");
        }
        return reasons.isEmpty() ? "none" : String.join(", ", reasons);
    }

    private void renderHoveredOverflowTooltip(GuiGraphics graphics, int mouseX, int mouseY, int listTop) {
        int x = leftPos + LIST_X;
        int y = listTop - scroll;
        for (int i : filteredEntryIndexes()) {
            SmartClipboardReport.Entry entry = report.entries().get(i);
            int cardHeight = entryHeight(entry, i);
            String itemText = entry.requestedStack().getHoverName().getString() + " " + entry.quantityDisplay();
            if (hoveredTruncatedText(mouseX, mouseY, x + 24, y + 3, LIST_WIDTH - 28, itemText)) {
                graphics.renderTooltip(font, Component.literal(itemText), mouseX, mouseY);
                return;
            }

            String requesterText = Component.translatable("screen.create_colony_logistics.smart_clipboard.requester_label").getString() + displayRequester(entry);
            if (hoveredTruncatedText(mouseX, mouseY, x + 24, y + 15, LIST_WIDTH - 28, requesterText)) {
                graphics.renderTooltip(font, Component.literal(requesterText), mouseX, mouseY);
                return;
            }

            if (expanded.contains(i) && hasExpandedDetails(entry)) {
                int detailY = y + EXPANDED_TOP_PADDING;
                detailY += valueLineHeight(entry.minimumStockRequest()
                        ? Component.translatable("screen.create_colony_logistics.smart_clipboard.minimum_stock_request").getString()
                        : null);
                if (!entry.minimumStockRequest()) {
                    detailY += valueLineHeight(entry.requestingWorkerName().orElse(null));
                }
                List<SmartClipboardReport.RequestTreeNode> dependencies = dependencyNodes(entry);
                if (!dependencies.isEmpty()) {
                    detailY += 2;
                    for (int nodeIndex : visibleDependencyIndexes(entry, i)) {
                        SmartClipboardReport.RequestTreeNode node = dependencies.get(nodeIndex);
                        int visibleDepth = Math.max(1, node.depth());
                        int indent = Math.min(36, (visibleDepth - 1) * TREE_INDENT);
                        int textX = x + 4 + indent + 28;
                        int textWidth = Math.max(40, leftPos + LIST_X + LIST_WIDTH - (x + 4) - indent - 29);
                        String text = treeNodeText(node);
                        if (hoveredTruncatedText(mouseX, mouseY, textX, detailY + 4, textWidth, text)) {
                            graphics.renderTooltip(font, Component.literal(text), mouseX, mouseY);
                            return;
                        }
                        detailY += TREE_ROW_HEIGHT;
                    }
                }
            }
            y += cardHeight + ROW_GAP;
        }
    }

    private boolean hoveredTruncatedText(int mouseX, int mouseY, int x, int y, int width, String text) {
        return font.width(text) > width
                && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + font.lineHeight;
    }

    // DO NOT MODIFY: approved Smart Info tooltip golden reference.
    // Keep this separate from dependency/tree and row-label formatting so unrelated UI changes cannot alter it.
    private List<Component> buildApprovedSmartInfoTooltip(SmartClipboardReport.Entry entry, ItemStack shownStack) {
        Minecraft minecraft = Minecraft.getInstance();
        Item.TooltipContext context = minecraft.level == null ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(minecraft.level);
        List<Component> lines = new ArrayList<>(shownStack.getTooltipLines(context, minecraft.player, TooltipFlag.NORMAL));
        if (!isArchitectsCutterEntry(entry)) {
            return lines;
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("screen.create_colony_logistics.smart_clipboard.tooltip.smart")
                .withStyle(style -> style.withColor(SMART_INFO_HEADER_COLOR)));
        addApprovedSmartTooltipLine(lines, "screen.create_colony_logistics.smart_clipboard.tooltip.shape", humanizeDomumShape(entry));
        if (shouldShowTeachingFeedback(entry)) {
            addWrappedApprovedSmartTooltipLine(lines, "screen.create_colony_logistics.smart_clipboard.tooltip.can_learn", entry.canLearnCombo());
        }
        return lines;
    }

    private boolean shouldShowTeachingFeedback(SmartClipboardReport.Entry entry) {
        return !entry.exactComboAlreadyTaught() && !entry.canLearnCombo().isEmpty();
    }

    private void addApprovedSmartTooltipLine(List<Component> lines, String key, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(Component.translatable(key).withStyle(style -> style.withColor(SMART_INFO_LABEL_COLOR))
                    .append(Component.literal(value).withStyle(style -> style.withColor(SMART_INFO_VALUE_COLOR))));
        }
    }

    private void addWrappedApprovedSmartTooltipLine(List<Component> lines, String key, List<String> values) {
        String value = approvedSmartTooltipHutList(values);
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
            String part = takeApprovedSmartTooltipPart(remaining, Math.max(40, available));
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

    private String takeApprovedSmartTooltipPart(String value, int width) {
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

    private int clampScroll(int value, int top, int bottom) {
        return Math.max(0, Math.min(value, maxScroll(top, bottom)));
    }

    private ScrollbarMetrics scrollbarMetrics(int listTop, int listBottom) {
        int maxScroll = maxScroll(listTop, listBottom);
        if (maxScroll <= 0) {
            return null;
        }
        int trackTop = listTop + SCROLLBAR_TRACK_TOP_INSET;
        int trackBottom = listBottom - SCROLLBAR_TRACK_BOTTOM_INSET;
        int trackHeight = trackBottom - trackTop;
        if (trackHeight <= SCROLLBAR_THUMB_HEIGHT) {
            return null;
        }
        int thumbTravel = trackHeight - SCROLLBAR_THUMB_HEIGHT;
        int thumbY = trackTop + scroll * thumbTravel / maxScroll;
        return new ScrollbarMetrics(leftPos + SCROLL_X, trackTop, trackBottom, thumbY, thumbTravel, maxScroll);
    }

    private void setScrollFromThumbY(int thumbY, ScrollbarMetrics metrics) {
        int clampedY = Math.max(metrics.trackTop(), Math.min(thumbY, metrics.maxThumbY()));
        scroll = metrics.thumbTravel() <= 0
                ? 0
                : (clampedY - metrics.trackTop()) * metrics.maxScroll() / metrics.thumbTravel();
    }

    private record ScrollbarMetrics(int x, int trackTop, int trackBottom, int thumbY, int thumbTravel, int maxScroll) {
        private int maxThumbY() {
            return trackBottom - SCROLLBAR_THUMB_HEIGHT;
        }

        private boolean trackHit(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + SCROLLBAR_WIDTH && mouseY >= trackTop && mouseY < trackBottom;
        }

        private boolean thumbHit(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + SCROLLBAR_WIDTH && mouseY >= thumbY && mouseY < thumbY + SCROLLBAR_THUMB_HEIGHT;
        }
    }

    private static <T> void toggle(Set<T> set, T value) {
        if (!set.add(value)) {
            set.remove(value);
        }
    }

    private int valueLineHeight(String value) {
        return value == null || value.isBlank() ? 0 : LINE_HEIGHT;
    }

    private String approvedSmartTooltipHutList(List<String> values) {
        if (values.isEmpty()) {
            return Component.translatable("screen.create_colony_logistics.smart_clipboard.none").getString();
        }
        return String.join(", ", values.stream().map(this::approvedSmartTooltipHutName).distinct().toList());
    }

    private String approvedSmartTooltipHutName(String name) {
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

    private List<SmartClipboardReport.RequestTreeNode> dependencyNodes(SmartClipboardReport.Entry entry) {
        return entry.requestTree().stream()
                .filter(node -> node.depth() > 0)
                .toList();
    }

    private boolean hasExpandedDetails(SmartClipboardReport.Entry entry) {
        return entry.minimumStockRequest()
                || (!entry.minimumStockRequest() && entry.requestingWorkerName().filter(name -> !name.isBlank()).isPresent())
                || !dependencyNodes(entry).isEmpty();
    }

    private List<Integer> visibleDependencyIndexes(SmartClipboardReport.Entry entry, int entryIndex) {
        List<SmartClipboardReport.RequestTreeNode> dependencies = dependencyNodes(entry);
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < dependencies.size(); i++) {
            if (isDependencyVisible(dependencies, entryIndex, i)) {
                visible.add(i);
            }
        }
        return visible;
    }

    private boolean isDependencyVisible(List<SmartClipboardReport.RequestTreeNode> dependencies, int entryIndex, int nodeIndex) {
        int depth = dependencies.get(nodeIndex).depth();
        if (depth <= 1) {
            return true;
        }
        int neededParentDepth = depth - 1;
        for (int i = nodeIndex - 1; i >= 0 && neededParentDepth >= 1; i--) {
            int candidateDepth = dependencies.get(i).depth();
            if (candidateDepth == neededParentDepth) {
                if (!expandedDependencies.contains(dependencyKey(entryIndex, i))) {
                    return false;
                }
                neededParentDepth--;
            }
        }
        return neededParentDepth < 1;
    }

    private boolean hasDependencyChildren(List<SmartClipboardReport.RequestTreeNode> dependencies, int nodeIndex) {
        int depth = dependencies.get(nodeIndex).depth();
        return nodeIndex + 1 < dependencies.size() && dependencies.get(nodeIndex + 1).depth() > depth;
    }

    private boolean toggleDependencyAt(SmartClipboardReport.Entry entry, int entryIndex, double mouseY, int rowY) {
        int y = rowY + EXPANDED_TOP_PADDING;
        y += valueLineHeight(entry.minimumStockRequest()
                ? Component.translatable("screen.create_colony_logistics.smart_clipboard.minimum_stock_request").getString()
                : null);
        if (!entry.minimumStockRequest()) {
            y += valueLineHeight(entry.requestingWorkerName().orElse(null));
        }
        y += 2;
        List<SmartClipboardReport.RequestTreeNode> dependencies = dependencyNodes(entry);
        for (int nodeIndex : visibleDependencyIndexes(entry, entryIndex)) {
            int nodeY = y;
            if (mouseY >= nodeY && mouseY < nodeY + TREE_ROW_HEIGHT && hasDependencyChildren(dependencies, nodeIndex)) {
                toggle(expandedDependencies, dependencyKey(entryIndex, nodeIndex));
                return true;
            }
            y += TREE_ROW_HEIGHT;
        }
        return false;
    }

    private String dependencyKey(int entryIndex, int nodeIndex) {
        return entryIndex + ":" + nodeIndex;
    }

    private List<Integer> filteredEntryIndexes() {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < report.entries().size(); i++) {
            if (!importantOnly || report.entries().get(i).important()) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private int listTop() {
        return listTop(activeTab);
    }

    private int listTop(Tab tab) {
        return topPos + (tab == Tab.SCROLLS ? SCROLL_DETAILS_TOP : CONTENT_LIST_TOP);
    }

    private int listBottom() {
        return listBottom(activeTab);
    }

    private int listBottom(Tab tab) {
        return tab == Tab.SCROLLS
                ? scrollStorageGridTop() - SCROLL_DETAILS_BOTTOM_GAP
                : topPos + CONTENT_LIST_BOTTOM;
    }

    private int scrollStorageGridTop() {
        return topPos + SCROLL_STORAGE_GRID_TOP;
    }

    private int scrollSlotX(int x, int slot) {
        return x + (slot % SCROLL_SLOT_COLUMNS) * SCROLL_SLOT_SIZE;
    }

    private int scrollSlotY(int y, int slot) {
        return y + (slot / SCROLL_SLOT_COLUMNS) * SCROLL_SLOT_SIZE;
    }

    private static int clampSelectedScroll(SmartClipboardReport report, int preferred) {
        if (preferred >= 0 && preferred < report.resourceScrolls().size() && !report.resourceScrolls().get(preferred).isEmpty()) {
            return preferred;
        }
        for (int i = 0; i < report.resourceScrolls().size(); i++) {
            if (!report.resourceScrolls().get(i).isEmpty()) {
                return i;
            }
        }
        return Math.max(0, Math.min(preferred, SmartClipboardScrollStorage.SLOT_COUNT - 1));
    }

    private int nearestSelectedAfterRemoval(int removedSlot) {
        for (int i = removedSlot + 1; i < report.resourceScrolls().size(); i++) {
            if (!report.resourceScrolls().get(i).isEmpty()) {
                return i;
            }
        }
        for (int i = removedSlot - 1; i >= 0; i--) {
            if (!report.resourceScrolls().get(i).isEmpty()) {
                return i;
            }
        }
        return Math.max(0, Math.min(removedSlot, SmartClipboardScrollStorage.SLOT_COUNT - 1));
    }

    private void rememberState() {
        rememberedTab = activeTab;
        rememberedSelectedScroll = selectedScroll;
    }

    private ItemStack displayStack(SmartClipboardReport.Entry entry) {
        List<ItemStack> stacks = entry.displayStacks().isEmpty() ? List.of(entry.requestedStack()) : entry.displayStacks();
        if (stacks.size() == 1) {
            return stacks.getFirst();
        }
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? System.currentTimeMillis() / 50L : minecraft.level.getGameTime();
        int index = (int) ((gameTime / 20L) % stacks.size());
        return stacks.get(index);
    }

    private int importantButtonX() {
        return leftPos + 14;
    }

    private int importantButtonY() {
        return topPos + 14;
    }

    private String treeNodeText(SmartClipboardReport.RequestTreeNode node) {
        ItemStack stack = node.stack();
        String quantity = node.quantityDisplay() == null || node.quantityDisplay().isBlank()
                ? "x" + Math.max(1, node.count())
                : node.quantityDisplay();
        if (!stack.isEmpty()) {
            return stack.getHoverName().getString() + " " + quantity;
        }
        String label = cleanTreeLabel(node.label());
        return label + " " + quantity;
    }

    private String cleanTreeLabel(String label) {
        if (label == null || label.isBlank()) {
            return Component.translatable("screen.create_colony_logistics.smart_clipboard.unknown").getString();
        }
        String value = label.strip();
        value = value.replaceAll("^\\s*\\d+\\s*\\*\\s*", "");
        value = value.replaceAll("(?i)^Recipe:\\[([^]]+)]$", "$1");
        value = value.replaceAll("(?i)Recipe:\\[([^]]+)]", "$1");
        value = value.replaceAll("(?i)\\bcom\\b|\\bminecolonies\\b|\\bcore\\b|\\brequestsystem\\b|\\brequests\\b", " ");
        value = value.replaceAll("[_{}\\[\\]().:]+", " ").trim();
        if (value.isBlank()) {
            return Component.translatable("screen.create_colony_logistics.smart_clipboard.unknown").getString();
        }
        return value;
    }

    private int treeLabelCount(String label, int fallback) {
        if (label != null) {
            String trimmed = label.strip();
            int index = 0;
            while (index < trimmed.length() && Character.isDigit(trimmed.charAt(index))) {
                index++;
            }
            if (index > 0 && trimmed.substring(index).stripLeading().startsWith("*")) {
                try {
                    return Integer.parseInt(trimmed.substring(0, index));
                } catch (NumberFormatException ignored) {
                    // Use the request count below.
                }
            }
        }
        return fallback;
    }

    private String displayRequester(SmartClipboardReport.Entry entry) {
        String building = displayRequester(entry.requestingBuildingName());
        return isUnknownHut(building) ? unknownHut() : building;
    }

    private String displayRequester(String requesterName) {
        if (requesterName == null || requesterName.isBlank()) {
            return unknownHut();
        }
        if (requesterName.contains(": ") && !requesterName.contains("com.minecolonies")) {
            return humanizeRequesterName(requesterName.substring(0, requesterName.indexOf(':')));
        }
        return humanizeRequesterName(requesterName);
    }

    private String humanizeRequesterName(String requesterName) {
        String value = shortenHutName(requesterName);
        return value.equalsIgnoreCase("flower") ? "Florist" : value;
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

    private boolean isArchitectsCutterEntry(SmartClipboardReport.Entry entry) {
        return isDomumOrnamentumEntry(entry)
                && (entry.cutterRecipeId().isPresent()
                || DomumOrnamentumRequestInspector.isMaterializedArchitectsCutterOutput(entry.requestedStack()));
    }

    private enum Tab {
        REQUESTS,
        SCROLLS
    }

    private record ResourceScrollContent(
            String buildingTitle,
            String projectTitle,
            int suppliedPercent,
            int usedPercent,
            List<ResourceLine> resources,
            String error,
            int moduleResourceCount,
            int adaptedResourceCount,
            int inventoryOverlayCount,
            int deliveryOverlayCount,
            int warehouseOverlayCount,
            String resolutionSource
    ) {
        static ResourceScrollContent fromClientSelectedStack(ItemStack scroll) {
            try {
                if (!ColonyId.readFromItemStack(scroll).hasColonyId() || !BuildingId.readFromItemStack(scroll).hasId()) {
                    return error("Resource Scroll is not linked.");
                }
                ResolvedScrollBuilding resolved = resolveBuilderView(scroll);
                if (resolved.building() == null) {
                    return error("Linked building is not available.");
                }
                if (!(resolved.building() instanceof BuildingBuilder.View builder)) {
                    return error("Linked building is not a Builder Hut.");
                }
                BuildingResourcesModuleView module = builder.getModuleViewByType(BuildingResourcesModuleView.class);
                if (module == null) {
                    return error("Linked Builder Hut has no resource module.");
                }
                List<Delivery> deliveries = deliveryRequests(builder);
                Map<String, Integer> warehouseSnapshot = WarehouseSnapshot.readFromItemStack(scroll).snapshot();
                List<BuildingBuilderResource> adapted = new ArrayList<>();
                for (BuildingBuilderResource resource : module.getResources().values()) {
                    BuildingBuilderResource copy = new BuildingBuilderResource(resource.getItemStack().copy(), resource.getAmount(), resource.getAvailable());
                    applyPlayerAndDeliveryAmounts(copy, builder, deliveries);
                    adapted.add(copy);
                }
                adapted.sort(new ResourceComparator(
                        RessourceAvailability.NOT_NEEDED,
                        RessourceAvailability.HAVE_ENOUGH,
                        RessourceAvailability.IN_DELIVERY,
                        RessourceAvailability.NEED_MORE,
                        RessourceAvailability.DONT_HAVE
                ));
                List<ResourceLine> resources = adapted.stream()
                        .map(resource -> ResourceLine.from(resource, warehouseSnapshot))
                        .toList();
                int inventoryOverlayCount = (int) adapted.stream().filter(resource -> resource.getPlayerAmount() > 0).count();
                int deliveryOverlayCount = (int) adapted.stream().filter(resource -> resource.getAmountInDelivery() > 0).count();
                int warehouseOverlayCount = (int) resources.stream()
                        .filter(resource -> resource.deliveryOrWarehouseAmount() > 0)
                        .count() - deliveryOverlayCount;
                int requiredTotal = 0;
                int suppliedTotal = 0;
                for (ResourceLine resource : resources) {
                    requiredTotal += Math.max(0, resource.required());
                    suppliedTotal += Math.min(Math.max(0, resource.available()), Math.max(0, resource.required()));
                }
                int suppliedPercent = requiredTotal <= 0 ? 0 : suppliedTotal * 100 / requiredTotal;
                String project = "";
                if (module.getWorkOrderId() > -1) {
                    IWorkOrderView workOrder = module.getBuildingView().getColony().getWorkOrder(module.getWorkOrderId());
                    if (workOrder != null) {
                        project = workOrder.getDisplayName().getString().replace("\n", "");
                    }
                }
                return new ResourceScrollContent(
                        builderTitle(builder),
                        project,
                        suppliedPercent,
                        module.getProgress(),
                        resources,
                        "",
                        module.getResources().size(),
                        adapted.size(),
                        inventoryOverlayCount,
                        deliveryOverlayCount,
                        Math.max(0, warehouseOverlayCount),
                        resolved.source()
                );
            } catch (RuntimeException ignored) {
                return error("Resource Scroll data is unavailable.");
            }
        }

        private static ResolvedScrollBuilding resolveBuilderView(ItemStack scroll) {
            IBuildingView building = BuildingId.readBuildingViewFromItemStack(scroll);
            if (building != null) {
                return new ResolvedScrollBuilding(building, "client-selected-stack -> BuildingId.readBuildingViewFromItemStack");
            }
            return new ResolvedScrollBuilding(null, "client-selected-stack -> BuildingId.readBuildingViewFromItemStack unresolved");
        }

        private static String builderTitle(BuildingBuilder.View builder) {
            String worker = builder.getWorkerName();
            if (worker != null && !worker.isBlank()) {
                return "Builder's Hut: " + worker.strip();
            }
            return "Builder's Hut";
        }

        private static void applyPlayerAndDeliveryAmounts(BuildingBuilderResource resource, BuildingBuilder.View builder, List<Delivery> deliveries) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.isCreative()) {
                resource.setPlayerAmount(resource.getAmount());
            } else if (minecraft.player != null) {
                resource.setPlayerAmount(InventoryUtils.getItemCountInItemHandler(
                        new InvWrapper(minecraft.player.getInventory()),
                        stack -> !ItemStackUtils.isEmpty(stack)
                                && ItemStackUtils.compareItemStacksIgnoreStackSize(stack, resource.getItemStack()).booleanValue()
                ));
            }
            resource.setAmountInDelivery(0);
            for (Delivery delivery : deliveries) {
                if (ItemStackUtils.compareItemStacksIgnoreStackSize(resource.getItemStack(), delivery.getStack(), false, true)) {
                    resource.setAmountInDelivery(resource.getAmountInDelivery() + delivery.getStack().getCount());
                }
            }
        }

        private static List<Delivery> deliveryRequests(BuildingBuilder.View builder) {
            List<Delivery> deliveries = new ArrayList<>();
            for (Collection<IToken<?>> requests : builder.getOpenRequestsByCitizen().values()) {
                addDeliveryRequests(builder, deliveries, requests);
            }
            return deliveries;
        }

        private static void addDeliveryRequests(BuildingBuilder.View builder, List<Delivery> deliveries, Collection<IToken<?>> tokens) {
            for (IToken<?> token : tokens) {
                IRequest<?> request = builder.getColony().getRequestManager().getRequestForToken(token);
                if (request == null) {
                    continue;
                }
                if (request.getRequest() instanceof Delivery delivery
                        && delivery.getTarget().getInDimensionLocation().equals(builder.getID())) {
                    deliveries.add(delivery);
                }
                if (request.hasChildren()) {
                    addDeliveryRequests(builder, deliveries, request.getChildren());
                }
            }
        }

        private static ResourceScrollContent error(String message) {
            return new ResourceScrollContent("", "", 0, 0, List.of(), message, 0, 0, 0, 0, 0, "unresolved");
        }
    }

    private record ResolvedScrollBuilding(IBuildingView building, String source) {
    }

    private record ResourceLine(ItemStack stack, String name, int missing, int available, int required, int deliveryOrWarehouseAmount, int neededValueColor, int suppliedColor) {
        static ResourceLine from(BuildingBuilderResource resource, Map<String, Integer> warehouseSnapshot) {
            ItemStack stack = resource.getItemStack().copyWithCount(1);
            int available = Math.max(0, resource.getAvailable());
            int required = Math.max(0, resource.getAmount());
            int missing = Math.max(0, required - available);
            int extra = resource.getAmountInDelivery() > 0
                    ? resource.getAmountInDelivery()
                    : warehouseSnapshot.getOrDefault(warehouseSnapshotKey(resource), 0);
            return new ResourceLine(stack, resource.getName(), missing, available, required, extra, neededValueColor(missing), suppliedColor(available, required));
        }

        private static String warehouseSnapshotKey(BuildingBuilderResource resource) {
            ItemStack stack = resource.getItemStack();
            return stack.getDescriptionId() + "-" + stack.getComponentsPatch().hashCode();
        }

        private static int neededValueColor(int missing) {
            return missing > 0 ? RESOURCE_SCROLL_SOFT_RED : MUTED_TEXT;
        }

        private static int suppliedColor(int available, int required) {
            return available >= required ? 0xFF78B86B : RESOURCE_SCROLL_SOFT_RED;
        }
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
