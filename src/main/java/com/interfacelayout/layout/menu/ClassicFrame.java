package com.interfacelayout.layout.menu;

import com.interfacelayout.InterfaceLayoutConfig;
import com.interfacelayout.layout.orbs.OrbController;
import com.interfacelayout.util.WidgetGeometry;
import com.interfacelayout.util.WidgetStateStore;
import java.awt.Point;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetType;

/**
 * Draws the Resizable Classic side-panel frame (tapered stone rows and side
 * columns) around the Resizable Modern side panel. Modern keeps its native
 * behaviour of closing the panel when the active tab is clicked; when closed,
 * the two stone rows stack in the bottom-right corner.
 */
@Slf4j
@Singleton
public class ClassicFrame
{
    private String lastReport = "";
    private boolean framed, slotted;
    private boolean lastOpen;
    private Widget logoutRow;
    // Modern's own highlight, learned the first time it is seen, so it can be put back when the frame is removed.
    private int modernSelected = -1;
    private boolean modernTiling;

    /** Hands any stone carrying a Classic highlight back to Modern's own highlight. */
    public void restore()
    {
        // The bottom stone container has no children of its own, so everything in it is the logout stone.
        if (logoutRow != null) { logoutRow.deleteAllChildren(); logoutRow = null; }
        for (int index = 0; index < MenuStoneRegistry.MODERN.length; index++)
        {
            Widget stone = index == 10 ? null : client.getWidget(MenuStoneRegistry.MODERN[index][0]);
            if (stone == null || !classicHighlight(stone.getSpriteId())) continue;
            stone.setSpriteId(modernSelected);
            if (modernSelected != -1) stone.setSpriteTiling(modernTiling);
        }
    }
    // Measurements taken from ToplevelOsrsStretch.SIDE_MENU (Resizable Classic).
    private static final int FRAME_WIDTH = 241, ROW_HEIGHT = 37, PANEL_HEIGHT = 261;
    private static final int TOP_ROW_SPRITE = 1173, BOTTOM_ROW_SPRITE = 1174;
    private static final int LEFT_COLUMN_SPRITE = 1175, RIGHT_COLUMN_SPRITE = 1176;
    private static final int BACKGROUND_SPRITE = 897;
    private static final int SLOT_WIDTH = 33, CORNER_WIDTH = 38, CORNER_ICON_INSET = 4;
    private static final int STONE_HEIGHT = 36, LOGOUT_ICON_SPRITE = 907, LOGOUT_PANEL = 10;
    // Read from ToplevelPreEoc.STONE10's op listener: [914, event_op, 1131, 10].
    private static final int SIDEBUTTON_OP_SCRIPT = 914, EVENT_OP = -2147483644, LOGOUT_OP_SOUND = 1131;
    private static final int[] MODERN_LOGOUT = {InterfaceID.ToplevelPreEoc.STONE10, InterfaceID.ToplevelPreEoc.ICON10};
    private static final int SELECTED_TOP_LEFT = 1026, SELECTED_TOP_RIGHT = 1027;
    private static final int SELECTED_BOTTOM_LEFT = 1028, SELECTED_BOTTOM_RIGHT = 1029, SELECTED_MIDDLE = 1030;
    // Modern's panel is the 190x261 contents plus a 7px border on every side.
    private static final int CONTENTS_X = 25, CONTENTS_Y = 37, MODERN_BORDER = 7;

    @Inject private Client client;
    @Inject private InterfaceLayoutConfig config;
    @Inject private OrbController orbs;
    @Inject private WidgetStateStore states;
    @Inject private MenuStoneBackgrounds backgrounds;

    public void apply()
    {
        framed = false; slotted = false;
        if (!config.classicFrame()) { report("off"); return; }
        if (!orbs.isLoggedIn() || orbs.isFixedMode() || orbs.isClassicResizable()) { report("skipped: not logged in to Resizable Modern"); return; }
        if (config.menuLayout() != MenuStoneController.Layout.VANILLA_HORIZONTAL || config.menuOverlayButtons())
        { report("skipped: needs Native rows with overlay buttons off"); return; }

        Widget container = client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_CONTAINER);
        Widget topStones = client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_MOVABLE);
        Widget bottomStones = client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_STATIC);
        Widget topRow = client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_BACKGROUND);
        Widget bottomRow = client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_STATIC_BACKGROUND);
        if (container == null || container.getParent() == null || topStones == null || bottomStones == null
            || topRow == null || bottomRow == null)
        {
            report("skipped: missing widget container=" + (container != null) + " topStones=" + (topStones != null)
                + " bottomStones=" + (bottomStones != null) + " topRow=" + (topRow != null) + " bottomRow=" + (bottomRow != null));
            return;
        }
        if (topRow.isHidden() || bottomRow.isHidden())
        {
            report("skipped: row hidden top=" + topRow.isHidden() + " (self " + topRow.isSelfHidden() + ") bottom="
                + bottomRow.isHidden() + " (self " + bottomRow.isSelfHidden() + ")");
            return;
        }

        Rectangle game = WidgetGeometry.bounds(container.getParent());
        boolean open = open(container);
        lastOpen = open;
        boolean modal = modal();
        int selected = selected(open, modal);
        int x = game.x + game.width - FRAME_WIDTH;
        int bottomY = game.y + game.height - ROW_HEIGHT;
        int topY = bottomY - ROW_HEIGHT - (open ? PANEL_HEIGHT : 0);

        // The static row is drawn first, so the panel backdrop and columns go there to sit under everything else.
        int lower = InterfaceID.ToplevelPreEoc.SIDE_STATIC_BACKGROUND, upper = InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_BACKGROUND;
        // The backdrop runs 10px under each row. Collapsed, that leaves a 20px strip behind the seam between the rows.
        if (open || config.classicFrameFill())
            backgrounds.addArt(lower, BACKGROUND_SPRITE, true, new Rectangle(x + 20, topY + 27, 200, bottomY + 10 - (topY + 27)));
        if (open)
        {
            backgrounds.addArt(lower, LEFT_COLUMN_SPRITE, false, new Rectangle(x + 2, topY + ROW_HEIGHT, 26, PANEL_HEIGHT));
            backgrounds.addArt(lower, RIGHT_COLUMN_SPRITE, false, new Rectangle(x + 212, topY + ROW_HEIGHT, 26, PANEL_HEIGHT));
        }
        backgrounds.addArt(lower, BOTTOM_ROW_SPRITE, false, new Rectangle(x, bottomY, FRAME_WIDTH, ROW_HEIGHT));
        backgrounds.addArt(upper, TOP_ROW_SPRITE, false, new Rectangle(x, topY, FRAME_WIDTH, ROW_HEIGHT));

        // Classic's stone containers are the full 241x37 row, with the stones at the top edge.
        widen(topStones); widen(bottomStones);
        place(topStones, x, topY);
        place(bottomStones, x, bottomY);

        for (int index = 0; index < MenuStoneRegistry.MODERN.length; index++)
        {
            if (index == 10) continue;
            Widget stone = client.getWidget(MenuStoneRegistry.MODERN[index][0]);
            Widget icon = client.getWidget(MenuStoneRegistry.MODERN[index][1]);
            if (stone == null || stone.getParent() != (index < 7 ? topStones : bottomStones)) continue;
            int slot = slot(index);
            boolean corner = slot == 0 || slot == 6;
            int stoneX = slot == 0 ? 0 : CORNER_WIDTH + SLOT_WIDTH * (slot - 1);
            int iconY = icon == null ? 0 : icon.getRelativeY() - stone.getRelativeY();
            states.capture(stone);
            stone.setOriginalWidth(corner ? CORNER_WIDTH : SLOT_WIDTH);
            stone.setWidthMode(WidgetSizeMode.ABSOLUTE);
            // The highlight follows the open panel itself, not whatever sprite the stone last carried, so tabs
            // opened by hotkey are highlighted and nothing stale is ever restored.
            int current = stone.getSpriteId();
            if (current != -1 && !classicHighlight(current)) { modernSelected = current; modernTiling = stone.getSpriteTiling(); }
            if (index == selected)
            {
                // Classic tiles the middle highlight across the stone and draws the corner ones once.
                stone.setSpriteId(selectedSprite(index < 7, slot));
                stone.setSpriteTiling(!corner);
            }
            else stone.setSpriteId(-1);
            states.position(stone, stoneX, 0);
            stone.revalidate();
            if (icon == null || icon.getParent() != stone.getParent()) continue;
            int iconX = slot == 0 ? CORNER_ICON_INSET : slot == 6 ? FRAME_WIDTH - CORNER_ICON_INSET - SLOT_WIDTH : stoneX;
            states.position(icon, iconX + (SLOT_WIDTH - icon.getWidth()) / 2, iconY);
            icon.revalidate();
        }

        // Modern keeps logout as an X by the minimap. Classic has a door stone in the fourth bottom slot, so one is
        // drawn there and wired to the same client script, with the same arguments, as Modern's own logout button.
        int logoutX = CORNER_WIDTH + SLOT_WIDTH * 2;
        if (selected == LOGOUT_PANEL)
        {
            Widget highlight = bottomStones.createChild(-1, WidgetType.GRAPHIC);
            highlight.setSpriteId(SELECTED_MIDDLE);
            highlight.setSpriteTiling(true);
            highlight.setOriginalX(logoutX); highlight.setOriginalY(0);
            highlight.setOriginalWidth(SLOT_WIDTH); highlight.setOriginalHeight(STONE_HEIGHT);
            highlight.revalidate();
        }
        // The game blanks the stones while a bank, shop or trade inventory replaces the side panel; so does the door.
        if (!modal)
        {
            Widget door = bottomStones.createChild(-1, WidgetType.GRAPHIC);
            door.setSpriteId(LOGOUT_ICON_SPRITE);
            door.setOriginalX(logoutX); door.setOriginalY(0);
            door.setOriginalWidth(SLOT_WIDTH); door.setOriginalHeight(STONE_HEIGHT);
            door.setAction(0, "Logout");
            door.setHasListener(true);
            door.setOnOpListener(SIDEBUTTON_OP_SCRIPT, EVENT_OP, LOGOUT_OP_SOUND, LOGOUT_PANEL);
            door.revalidate();
        }
        logoutRow = bottomStones;
        // The door replaces Modern's X by the minimap, so the X and its click area are hidden directly.
        for (int component : MODERN_LOGOUT) states.hide(client.getWidget(component), true);

        slotted = true;
        report("applied open=" + open + " panel=" + client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)
            + " containerHidden=" + container.isHidden() + " game=" + game + " frameX=" + x + " topY=" + topY
            + " topStones=" + WidgetGeometry.bounds(topStones) + " bottomStones=" + WidgetGeometry.bounds(bottomStones)
            + " container=" + WidgetGeometry.bounds(container) + " topRow=" + WidgetGeometry.bounds(topRow)
            + " bottomRow=" + WidgetGeometry.bounds(bottomRow));
        if (!open) return;
        Widget[] border = container.getDynamicChildren();
        if (border != null)
            for (Widget piece : border)
                if (piece != null && piece.getType() == WidgetType.GRAPHIC) states.hide(piece, true);
        states.hide(client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_BACKGROUND), true);
        place(container, x + CONTENTS_X - MODERN_BORDER, topY + CONTENTS_Y - MODERN_BORDER);
        framed = true;
    }

    /** True when the game has redrawn Modern's own panel border or backdrop since the frame was last applied. */
    public boolean stale()
    {
        if (!slotted) return false;
        Widget container = client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_CONTAINER);
        if (container == null) return false;
        boolean open = open(container);
        if (open != lastOpen) return true;
        for (int component : MODERN_LOGOUT)
        {
            Widget logout = client.getWidget(component);
            if (logout != null && !logout.isSelfHidden()) return true;
        }
        int selected = selected(open, modal());
        for (int index = 0; index < MenuStoneRegistry.MODERN.length; index++)
        {
            Widget stone = index == 10 ? null : client.getWidget(MenuStoneRegistry.MODERN[index][0]);
            if (stone != null && stone.getSpriteId() != (index == selected ? selectedSprite(index < 7, slot(index)) : -1))
                return true;
        }
        if (!framed || !open) return false;
        Widget backdrop = client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_BACKGROUND);
        if (backdrop != null && !backdrop.isSelfHidden()) return true;
        Widget[] border = container.getDynamicChildren();
        if (border == null) return false;
        for (Widget piece : border)
            if (piece != null && piece.getType() == WidgetType.GRAPHIC && !piece.isSelfHidden()) return true;
        return false;
    }

    /** Open when a tab is selected, or when a bank, shop or trade inventory has taken over the side panel. */
    private boolean open(Widget container)
    {
        return !container.isHidden() && (client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL) != -1 || modal());
    }

    private boolean modal()
    {
        Widget modal = client.getWidget(InterfaceID.ToplevelPreEoc.SIDEMODAL);
        if (modal == null || modal.isHidden()) return false;
        Widget[] contents = modal.getNestedChildren();
        return contents != null && contents.length > 0;
    }

    private int selected(boolean open, boolean modal)
    {
        return open && !modal ? client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL) : -1;
    }

    private static boolean classicHighlight(int sprite)
    {
        return sprite >= SELECTED_TOP_LEFT && sprite <= SELECTED_MIDDLE;
    }

    private void widen(Widget row)
    {
        states.capture(row);
        row.setOriginalWidth(FRAME_WIDTH); row.setOriginalHeight(ROW_HEIGHT);
        row.setWidthMode(WidgetSizeMode.ABSOLUTE); row.setHeightMode(WidgetSizeMode.ABSOLUTE);
    }

    /** Classic orders the bottom row Clan, Friends, Account; Modern has Account before Friends. */
    private static int slot(int index)
    {
        return index == 8 ? 2 : index == 9 ? 1 : index % 7;
    }

    private static int selectedSprite(boolean top, int slot)
    {
        if (slot == 0) return top ? SELECTED_TOP_LEFT : SELECTED_BOTTOM_LEFT;
        if (slot == 6) return top ? SELECTED_TOP_RIGHT : SELECTED_BOTTOM_RIGHT;
        return SELECTED_MIDDLE;
    }

    private void report(String message)
    {
        if (message.equals(lastReport)) return;
        lastReport = message;
        log.info("[ClassicFrame] {}", message);
    }

    private void place(Widget widget, int canvasX, int canvasY)
    {
        Point parent = WidgetGeometry.location(widget.getParent());
        states.position(widget, canvasX - parent.x, canvasY - parent.y);
        widget.revalidate();
    }
}
