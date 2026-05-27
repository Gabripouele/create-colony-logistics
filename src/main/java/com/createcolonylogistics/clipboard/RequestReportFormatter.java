package com.createcolonylogistics.clipboard;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class RequestReportFormatter {
    private RequestReportFormatter() {
    }

    public static List<Component> format(RequestAnalysisService.AnalysisResult result) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.header", result.colonyName()).withStyle(ChatFormatting.GOLD));

        if (result.reportedCount() == 0) {
            lines.add(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.no_requests").withStyle(ChatFormatting.GRAY));
            return lines;
        }

        result.groupedEntries().forEach((requester, entries) -> {
            lines.add(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.group", requester, entries.size()).withStyle(ChatFormatting.YELLOW));
            for (RequestAnalysisService.RequestReportEntry entry : entries) {
                lines.add(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.item_line", entry.requestedItemName(), entry.requestedCount()).withStyle(ChatFormatting.WHITE));
                lines.add(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.stock", stockText(entry.warehouseStock())).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.do_block", entry.domumBlockId().toString()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.cutter_recipe",
                        entry.cutterRecipe().<Object>map(Object::toString).orElse(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.unknown"))).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.taught", yesNo(entry.exactComboTaught())).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.known_by", listOrNone(entry.knownBy())).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.can_learn", listOrNone(entry.canLearn())).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.request_meta",
                        entry.requestToken(),
                        entry.requesterPosition().<Object>map(RequestReportFormatter::posText).orElse(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.unknown")),
                        entry.workerName().<Object>map(name -> name).orElse(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.unknown"))).withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.fingerprint", entry.exactComboFingerprint()).withStyle(ChatFormatting.DARK_GRAY));
            }
        });

        if (result.capped()) {
            lines.add(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.capped").withStyle(ChatFormatting.RED));
        }

        return lines;
    }

    private static Object stockText(int stock) {
        return stock < 0 ? Component.translatable("item.create_colony_logistics.smart_colony_clipboard.unknown") : Integer.toString(stock);
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(value
                ? "item.create_colony_logistics.smart_colony_clipboard.yes"
                : "item.create_colony_logistics.smart_colony_clipboard.no");
    }

    private static Object listOrNone(List<String> values) {
        return values.isEmpty() ? Component.translatable("item.create_colony_logistics.smart_colony_clipboard.none") : String.join(", ", values);
    }

    private static String posText(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
