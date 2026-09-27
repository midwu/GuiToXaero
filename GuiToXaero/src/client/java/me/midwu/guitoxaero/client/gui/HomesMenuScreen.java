package me.midwu.guitoxaero.client.gui;

import me.midwu.guitoxaero.client.home.HomeEntry;
import me.midwu.guitoxaero.client.home.HomeStorage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Shows every home that has been scanned for the current server: name,
 * dimension, coordinates, and a "Go" button that runs {@code /home <name>}.
 * Doesn't touch Xaero at all - this is purely a convenience list on top of
 * whatever {@link HomeStorage} already has saved.
 */
public class HomesMenuScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 70;
    private static final int LIST_MARGIN = 20;
    private static final int GO_BUTTON_WIDTH = 50;

    private final List<HomeEntry> allHomes;
    private List<HomeEntry> filtered = new ArrayList<>();
    private String dimensionFilter = "all";
    private int scrollOffset = 0;

    private TextFieldWidget searchBox;
    private final List<ButtonWidget> rowButtons = new ArrayList<>();

    public HomesMenuScreen() {
        super(Text.literal("GuiToXaero - Homes"));
        String serverKey = HomeStorage.currentServerKey(net.minecraft.client.MinecraftClient.getInstance());
        this.allHomes = HomeStorage.get().getHomes(serverKey);
    }

    @Override
    protected void init() {
        searchBox = new TextFieldWidget(textRenderer, width / 2 - 100, 20, 200, 20, Text.literal("Search"));
        searchBox.setChangedListener(s -> applyFilter());
        addDrawableChild(searchBox);
        setInitialFocus(searchBox);

        int filterY = 44;
        int filterWidth = 74;
        int startX = width / 2 - (filterWidth * 4 + 6) / 2;
        String[] ids = {"all", "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
        String[] labels = {"All", "Overworld", "Nether", "End"};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(labels[i]), b -> setDimensionFilter(id))
                    .dimensions(startX + i * (filterWidth + 2), filterY, filterWidth, 18).build());
        }

        int closeWidth = 80;
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(width - closeWidth - 10, height - 26, closeWidth, 20).build());

        applyFilter();
    }

    private void setDimensionFilter(String dim) {
        this.dimensionFilter = dim;
        this.scrollOffset = 0;
        applyFilter();
    }

    private void applyFilter() {
        String query = searchBox == null ? "" : searchBox.getText().trim().toLowerCase(Locale.ROOT);
        filtered = allHomes.stream()
                .filter(h -> dimensionFilter.equals("all") || h.dimensionId().equals(dimensionFilter))
                .filter(h -> query.isEmpty() || h.name().toLowerCase(Locale.ROOT).contains(query))
                .collect(Collectors.toList());
        rebuildRowButtons();
    }

    private int visibleRowCount() {
        return Math.max(1, (height - LIST_TOP - LIST_MARGIN) / ROW_HEIGHT);
    }

    private void rebuildRowButtons() {
        for (ButtonWidget b : rowButtons) remove(b);
        rowButtons.clear();

        int maxScroll = Math.max(0, filtered.size() - visibleRowCount());
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int visible = visibleRowCount();
        for (int i = 0; i < visible; i++) {
            int index = scrollOffset + i;
            if (index >= filtered.size()) break;
            HomeEntry home = filtered.get(index);
            int rowY = LIST_TOP + i * ROW_HEIGHT;
            ButtonWidget go = ButtonWidget.builder(Text.literal("Go"), b -> teleportTo(home))
                    .dimensions(width / 2 + 150, rowY, GO_BUTTON_WIDTH, ROW_HEIGHT - 2)
                    .build();
            rowButtons.add(go);
            addDrawableChild(go);
        }
    }

    /** Runs "/home &lt;name&gt;" - only ever called from the player's own click on this button. */
    private void teleportTo(HomeEntry home) {
        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player == null || player.networkHandler == null) return;
        player.networkHandler.sendChatCommand("home " + home.name());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!filtered.isEmpty()) {
            scrollOffset -= (int) Math.signum(verticalAmount);
            rebuildRowButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);

        if (allHomes.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("No homes scanned yet."), width / 2, LIST_TOP + 10, 0xAAAAAA);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Open your server's home menu and press the GuiToXaero scan key."),
                    width / 2, LIST_TOP + 24, 0xAAAAAA);
            return;
        }

        int visible = visibleRowCount();
        for (int i = 0; i < visible; i++) {
            int index = scrollOffset + i;
            if (index >= filtered.size()) break;
            HomeEntry home = filtered.get(index);
            int rowY = LIST_TOP + i * ROW_HEIGHT;

            if ((index & 1) == 0) {
                context.fill(width / 2 - 150, rowY - 1, width / 2 + 145, rowY + ROW_HEIGHT - 3, 0x22FFFFFF);
            }

            String dimLabel = shortDimLabel(home.dimensionId());
            Text line = Text.literal(home.name())
                    .formatted(Formatting.WHITE)
                    .append(Text.literal("  [" + dimLabel + "]").formatted(Formatting.GRAY))
                    .append(Text.literal("  " + home.coordsString()).formatted(Formatting.DARK_GRAY));
            context.drawTextWithShadow(textRenderer, line, width / 2 - 148, rowY + 5, 0xFFFFFF);
        }

        if (filtered.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("No homes match this filter."), width / 2, LIST_TOP + 10, 0xAAAAAA);
        }
    }

    private static String shortDimLabel(String dimensionId) {
        return switch (dimensionId) {
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> "Overworld";
        };
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
