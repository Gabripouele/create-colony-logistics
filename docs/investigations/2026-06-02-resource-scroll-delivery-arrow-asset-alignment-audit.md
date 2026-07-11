# Resource Scroll Delivery Arrow Asset And Alignment Audit

Date: 2026-06-02

## Scope

This audit investigated the incoming / delivery indicator currently rendered in Create-Colony-Logistics Smart Resource Scroll rows.

No code, assets, UI layout, tooltip formatting, version, release jar, or backup-folder changes were made in this pass.

## Current Rendering Path

The indicator is rendered in:

- `com.createcolonylogistics.client.SmartClipboardScreen`
  - `renderSelectedScrollFromClientStack(GuiGraphics, ItemStack, int, int)`
  - `drawNeededLine(GuiGraphics, ResourceLine, int, int, int)`
  - `drawResourceAvailabilityIndicator(GuiGraphics, ResourceLine, int, int, int)`
  - `drawDeliveryGlyph(GuiGraphics, int, int, int)`
  - `drawWarehouseGlyph(GuiGraphics, int, int, int)`
  - `ResourceLine.from(BuildingBuilderResource, Map<String, Integer>)`

Code path:

1. `render(...)` enables a scissor region for the list:
   `graphics.enableScissor(leftPos + LIST_X, listTop, leftPos + LIST_X + LIST_WIDTH, listBottom)`.
2. Scroll-tab content calls `renderScrollDetails(...)`.
3. The selected scroll calls `renderSelectedScrollFromClientStack(...)`.
4. Each row renders item/name/needed/supplied lines.
5. `drawNeededLine(...)` returns the pixel x-coordinate after the "Needed: <missing>" text.
6. `drawResourceAvailabilityIndicator(...)` draws either the delivery glyph or warehouse glyph when `resource.deliveryOrWarehouseAmount() > 0`.
7. `ResourceLine.from(...)` sets `deliveryIndicator = resource.getAmountInDelivery() > 0`, so delivery takes precedence over warehouse.

## Asset / Source Used

The current green delivery arrow does not use a texture asset.

- Resource namespace: none.
- Texture path: none.
- Repo asset file: none.
- Dependency asset file: none.
- Atlas usage: none.
- UV coordinates: none.
- Render method: dynamically drawn `GuiGraphics.fill(...)` rectangles.
- Source: our mod code, not copied texture data.

The delivery glyph is drawn by:

```java
private void drawDeliveryGlyph(GuiGraphics graphics, int x, int y, int color) {
    graphics.fill(x, y + 3, x + 7, y + 5, color);
    graphics.fill(x + 5, y + 1, x + 7, y + 7, color);
    graphics.fill(x + 7, y + 2, x + 9, y + 6, color);
}
```

The green color is selected in `drawResourceAvailabilityIndicator(...)`:

```java
int color = resource.deliveryIndicator() ? 0xFF4F8F5A : 0xFF6D84A8;
```

Factory Gauges assets are not referenced. No Factory Gauges jar was found by the local Gradle-cache jar-name search for `*factory*.jar` or `*gauges*.jar`.

MineColonies assets are not reused by the current Create-Colony-Logistics indicator. The current implementation approximates the idea with primitives.

Our mod GUI textures currently present under `src/main/resources/assets/create_colony_logistics/textures/gui` are:

- `smart_clipboard_gui.png`
- `eletron_overlay.png`

Neither is used for the delivery arrow.

## Current Coordinates And Dimensions

For each Smart Resource Scroll row:

- Row origin: `x = leftPos + LIST_X`, `y = rowY`.
- Row height: `RESOURCE_ROW_HEIGHT = 36`.
- Item icon: `graphics.renderItem(resource.stack(), x, y)`.
- Name: `x + 22`, `y + 1`.
- Needed line: `x + 22`, `y + 11`.
- Supplied line: `x + 22`, `y + 21`.
- Separator: `y + RESOURCE_SEPARATOR_Y`, where `RESOURCE_SEPARATOR_Y = 33`.

Indicator call:

```java
int neededEndX = drawNeededLine(graphics, resource, x + 22, y + 11, textWidth);
drawResourceAvailabilityIndicator(graphics, resource, neededEndX, y + 11, x + LIST_WIDTH);
```

Inside `drawResourceAvailabilityIndicator(...)`:

- Draw skipped if `resource.deliveryOrWarehouseAmount() <= 0`.
- Draw skipped if `x + 16 >= lineRight`.
- `glyphX = Math.min(x, lineRight - 34)`.
- Delivery glyph call: `drawDeliveryGlyph(graphics, glyphX, y + 2, color)`.
- Amount text x: `glyphX + 11`.
- Amount text y: same `y` passed to indicator, which is row `y + 11`.

Therefore, for a row at `rowY`, the delivery glyph receives base `glyphY = rowY + 13`.

The three rectangles occupy:

- Body: `x..x+7`, `rowY+16..rowY+18`.
- Center vertical block: `x+5..x+7`, `rowY+14..rowY+20`.
- Right block / arrow head: `x+7..x+9`, `rowY+15..rowY+19`.

Effective glyph footprint:

- Width: 9 px.
- Height: 6 px.
- Top: `rowY + 14`.
- Bottom: `rowY + 20`.

There is no texture size assumption, no UV start, no UV width/height, and no sprite atlas.

## Clipping / Scissor

The active scissor region is the whole Smart Clipboard list:

- Left: `leftPos + LIST_X`
- Right: `leftPos + LIST_X + LIST_WIDTH`
- Top: `listTop`
- Bottom: `listBottom`

The row itself is not separately clipped. With a row height of 36 px, a glyph from `rowY + 14` to `rowY + 20` does not clip against the row top, row separator at `rowY + 33`, or list scissor during normal visible rows.

The indicator also reserves space by clamping `glyphX` to `lineRight - 34`, then placing amount text at `glyphX + 11`. That makes overlap with the amount text unlikely unless the amount itself is long and truncated.

## Why The Tip Appears Missing

Most likely cause: the current "arrow" is not a texture and does not draw a pointed tip.

The rightmost arrow-head rectangle is:

```java
graphics.fill(x + 7, y + 2, x + 9, y + 6, color);
```

That creates a blunt 2 px wide rectangular end, not a triangular or single-pixel arrow tip. The result can read like a clipped or missing tip even when no clipping is occurring.

Less likely causes:

- Wrong UV width: not applicable.
- Wrong render width: not applicable to a texture; primitive width is 9 px by construction.
- Wrong source texture dimensions: not applicable.
- Atlas/sprite cropping: not applicable.
- Scissor clipping: unlikely, because the glyph is inside row and list bounds.
- Overlap with amount text: unlikely; amount starts 2 px after the glyph footprint.
- Render order: unlikely; glyph is drawn before the amount text, but the amount starts to the right.
- Texture with no visible tip: not applicable.

## Why The Indicator Appears Too Low

Likely cause: the glyph is intentionally offset down by `+2` relative to the Needed line y-coordinate.

The Needed text and amount text are drawn at `rowY + 11`. The delivery glyph is called at `rowY + 13`, then its own internal top begins at `glyphY + 1`, so the visible glyph begins at `rowY + 14`.

That places the glyph's visual center around `rowY + 17`, while the text line starts at `rowY + 11`. Since Minecraft font line height is about 9 px, the glyph sits visually low against the text line and nearer the lower part of the row.

Moving only the glyph call 2 px upward, from `drawDeliveryGlyph(..., y + 2, ...)` to `drawDeliveryGlyph(..., y, ...)`, would make the visible glyph occupy approximately `rowY + 12..rowY + 18`, which should better align with the Needed amount line without moving the amount text.

## MineColonies Reference Behavior

MineColonies `WindowResourceList` uses the BlockUI XML:

- Class: `com.minecolonies.core.client.gui.WindowResourceList`
- Window XML: `minecolonies:gui/windowresourcescroll.xml`
- Jar path: `assets/minecolonies/gui/windowresourcescroll.xml`

Relevant XML entries:

```xml
<image id="indeliveryicon" source="minecolonies:textures/gui/citizen/delivery_16x16.png" size="16 16" pos="120 12" visible="false"/>
<image id="inWarehouseIcon" source="minecolonies:textures/gui/citizen/crate_16x16.png" size="16 16" pos="120 12" visible="false"/>
<text id="indeliveryamount" size="30 9" pos="132 18" textalign="MIDDLE" color="green"/>
<text id="inWarehouseAmount" size="30 9" pos="132 18" textalign="MIDDLE" color="yellow"/>
```

MineColonies asset paths:

- `assets/minecolonies/textures/gui/citizen/delivery_16x16.png`
- `assets/minecolonies/textures/gui/citizen/crate_16x16.png`

MineColonies bytecode confirms:

- It initially hides `indeliveryicon` and `inWarehouseIcon`.
- If `BuildingBuilderResource.getAmountInDelivery() > 0`, it shows `indeliveryicon` and writes the delivery amount.
- Else if the warehouse snapshot has an amount, it shows `inWarehouseIcon` and writes the warehouse amount.
- Delivery amount takes precedence over warehouse amount.

Current Create-Colony-Logistics behavior matches the delivery-before-warehouse precedence, but it does not reuse MineColonies' icon assets or BlockUI placement. It uses an inline primitive approximation near the Needed line.

## Is A 2 px Upward Adjustment Safe?

Likely yes, for the glyph only.

Moving the glyph 2 px upward should:

- better align the glyph with the Needed line and amount text;
- avoid the row separator because the glyph would move farther from `rowY + 33`;
- avoid the row top because the glyph would still start around `rowY + 12`;
- remain stable across GUI scale because these are integer GUI coordinates;
- not require changing row height, item icon alignment, text alignment, scroll slots, or scrollbar behavior.

The amount text should probably stay at `rowY + 11`. Moving both icon and amount would change text baseline alignment and is a larger UI change than needed.

## Smallest Safe Future Patch

Audit-only recommendation, not applied:

1. Change only the delivery and warehouse glyph y argument in `drawResourceAvailabilityIndicator(...)`:
   - from `drawDeliveryGlyph(graphics, glyphX, y + 2, color)`
   - to `drawDeliveryGlyph(graphics, glyphX, y, color)`
   - and similarly for `drawWarehouseGlyph(...)` if visual parity is desired.
2. Improve the arrow tip by adding a 1 px tip rectangle or by reshaping the primitive head:
   - for example, add a narrow final tip at `x + 9` after checking the desired 10 px footprint;
   - keep amount text at `glyphX + 11` if the glyph remains no wider than 10 px.
3. Alternatively, if exact MineColonies parity is preferred, replace the primitive with `minecolonies:textures/gui/citizen/delivery_16x16.png` and `crate_16x16.png`, but that is a larger change because it alters footprint, vertical placement, and asset dependency behavior.

## Risks Of Future Patch

- Adding a pointed tip can increase glyph width and reduce spacing before the amount text.
- Moving only delivery but not warehouse can make the two indicators inconsistent.
- Switching to MineColonies assets would change the visual style and size more than the observed 2 px alignment issue.
- A wider glyph could collide with long needed counts or long incoming counts in narrow rows.
- If future rows are partly clipped by scrolling, moving up is still safe for normal rows but should be checked at list boundaries.

## No-Change Confirmation

No code was modified.
No assets were modified.
No UI layout was changed.
No tooltip formatting was changed.
The locked Smart Info tooltip formatting was not modified.
No version bump was made.
No release jar was built.
No backup copy was created.

