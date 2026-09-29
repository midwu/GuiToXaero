package me.midwu.guitoxaero.client.gui;

import me.midwu.guitoxaero.client.home.HomeEntry;
import me.midwu.guitoxaero.client.home.HomeStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lists scanned homes with search, dimension filter and a "Go" button (/home <name>). */
public class HomesMenuScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 70;
    private static final int LIST_MARGIN = 40;

    private final List<HomeEntry> allHomes;
    private List<HomeEntry> filtered = new ArrayList<>();
    private String dimensionFilter = "all";
    private int scrollOffset = 0;

    private EditBox searchBox;
    private final List<Button> rowButtons = new ArrayList<>();

    public HomesMenuScreen() {
        super(Component.literal("GuiToXaero - Homes"));
        this.allHomes = HomeStorage.get().getHomes(HomeStorage.currentServerKey(Minecraft.getInstance()));
    }

    @Override
    protected void init() {
        rowButtons.clear();

        searchBox = new EditBox(this.font, width / 2 - 100, 20, 200, 20, Component.literal("Search"));
        searchBox.setResponder(s -> applyFilter());
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        String[] ids = {"all", "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
        String[] labels = {"All", "Overworld", "Nether", "End"};
        int w = 74;
        int startX = width / 2 - (w * 4 + 6) / 2;
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> setDimensionFilter(id))
                    .pos(startX + i * (w + 2), 44).size(w, 18).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .pos(width - 90, height - 26).size(80, 20).build());

        applyFilter();
    }

    private void setDimensionFilter(String dim) {
        dimensionFilter = dim;
        scrollOffset = 0;
        applyFilter();
    }

    private void applyFilter() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        filtered = new ArrayList<>();
        for (HomeEntry h : allHomes) {
            if (!dimensionFilter.equals("all") && !h.dimensionId().equals(dimensionFilter)) continue;
            if (!query.isEmpty() && !h.name().toLowerCase(Locale.ROOT).contains(query)) continue;
            filtered.add(h);
        }
        rebuildRowButtons();
    }

    private int visibleRows() {
        return Math.max(1, (height - LIST_TOP - LIST_MARGIN) / ROW_HEIGHT);
    }

    private void rebuildRowButtons() {
        for (Button b : rowButtons) removeWidget(b);
        rowButtons.clear();

        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, filtered.size() - visibleRows())));

        for (int i = 0; i < visibleRows(); i++) {
            int index = scrollOffset + i;
            if (index >= filtered.size()) break;
            HomeEntry home = filtered.get(index);
            Button go = Button.builder(Component.literal("Go"), b -> teleportTo(home))
                    .pos(width / 2 + 150, LIST_TOP + i * ROW_HEIGHT).size(50, ROW_HEIGHT - 2).build();
            rowButtons.add(go);
            addRenderableWidget(go);
        }
    }

    /** Runs "/home <name>" - only ever from the player's own click on this button. */
    private void teleportTo(HomeEntry home) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.connection == null) return;
        player.connection.sendCommand("home " + home.name());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!filtered.isEmpty()) {
            scrollOffset -= (int) Math.signum(scrollY);
            rebuildRowButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(this.font, this.title, width / 2, 8, 0xFFFFFFFF);

        if (allHomes.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal("No homes scanned yet."), width / 2, LIST_TOP + 10, 0xFFAAAAAA);
            g.drawCenteredString(this.font, Component.literal("Open the homes GUI and press the GuiToXaero scan key."),
                    width / 2, LIST_TOP + 24, 0xFFAAAAAA);
            return;
        }

        for (int i = 0; i < visibleRows(); i++) {
            int index = scrollOffset + i;
            if (index >= filtered.size()) break;
            HomeEntry home = filtered.get(index);
            int y = LIST_TOP + i * ROW_HEIGHT;
            if ((index & 1) == 0) g.fill(width / 2 - 150, y - 1, width / 2 + 145, y + ROW_HEIGHT - 3, 0x22FFFFFF);
            String text = home.name() + "  [" + shortDim(home.dimensionId()) + "]  " + home.coordsString();
            g.drawString(this.font, Component.literal(text), width / 2 - 148, y + 5, 0xFFFFFFFF, true);
        }
        if (filtered.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal("No homes match this filter."), width / 2, LIST_TOP + 10, 0xFFAAAAAA);
        }
    }

    private static String shortDim(String id) {
        return switch (id) {
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> "Overworld";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
