package com.mpp.integratedscripting.client.gui.component.input;

import com.mpp.integratedscripting.api.language.completion.CompletionItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The selection and scrolling state of the completion popup, without any GUI
 * code so it can be unit tested.
 *
 * <p>The editor asks the completion service for candidates once per request, then
 * narrows them locally as the user keeps typing: {@link #setFilter(String)}
 * applies the longer prefix, {@link #moveSelection(int)} moves through the
 * filtered list, and {@link #getFirstVisibleRow()} keeps the selected row inside
 * the fixed-height popup.</p>
 *
 * <p>The behaviour follows CodeMirror's completion dialog:</p>
 * <ul>
 *   <li>narrowing the list keeps the selected candidate when it survives the new
 *   filter (identity, not index), otherwise the first row is selected;</li>
 *   <li>ArrowDown/ArrowUp wrap around at the ends;</li>
 *   <li>PageDown/PageUp move by a page and clamp at the ends;</li>
 *   <li>a click selects one row by index.</li>
 * </ul>
 *
 * @author rubensworks
 */
public final class CompletionListModel {

    /** Rows a popup shows at most; the list scrolls inside this window. */
    public static final int DEFAULT_VISIBLE_ROWS = 8;

    private final List<CompletionItem> items;
    private final int visibleRows;
    private List<CompletionItem> filtered;
    private String filterPrefix = "";
    private int selectedIndex;

    /**
     * @param items The candidates, as returned by the completion service.
     */
    public CompletionListModel(List<CompletionItem> items) {
        this(items, DEFAULT_VISIBLE_ROWS);
    }

    /**
     * @param items The candidates, as returned by the completion service.
     * @param visibleRows The maximum number of rows to show at once.
     */
    public CompletionListModel(List<CompletionItem> items, int visibleRows) {
        this.items = items == null
                ? Collections.<CompletionItem>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(items));
        this.visibleRows = Math.max(1, visibleRows);
        this.filtered = new ArrayList<>(this.items);
    }

    /**
     * @return The unfiltered candidates.
     */
    public List<CompletionItem> getItems() {
        return items;
    }

    /**
     * @return The candidates matching the current prefix.
     */
    public List<CompletionItem> getFiltered() {
        return Collections.unmodifiableList(filtered);
    }

    /**
     * @return The prefix the current filter was built from; never {@code null}.
     */
    public String getFilterPrefix() {
        return filterPrefix;
    }

    /**
     * Narrow the list to the labels that start with the given prefix, ignoring
     * case. The selected candidate is kept when it is still part of the filtered
     * list (like CodeMirror's completion dialog); otherwise the first row is
     * selected.
     * @param prefix The typed prefix; {@code null} or empty keeps everything.
     */
    public void setFilter(@Nullable String prefix) {
        CompletionItem selected = getSelected();
        this.filterPrefix = prefix == null ? "" : prefix;
        if (this.filterPrefix.isEmpty()) {
            this.filtered = new ArrayList<>(this.items);
        } else {
            List<CompletionItem> matches = new ArrayList<>();
            for (CompletionItem item : this.items) {
                if (matchesPrefix(item, this.filterPrefix)) {
                    matches.add(item);
                }
            }
            this.filtered = matches;
        }
        int preserved = -1;
        if (selected != null) {
            for (int i = 0; i < this.filtered.size(); i++) {
                if (this.filtered.get(i) == selected) {
                    preserved = i;
                    break;
                }
            }
        }
        this.selectedIndex = preserved >= 0 ? preserved : 0;
    }

    private static boolean matchesPrefix(CompletionItem item, String prefix) {
        return item.getLabel().regionMatches(true, 0, prefix, 0, prefix.length())
                || item.getInsertText().regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /**
     * @return Whether there is nothing to show.
     */
    public boolean isEmpty() {
        return this.filtered.isEmpty();
    }

    /**
     * @return The number of rows the popup may show at once.
     */
    public int getVisibleRows() {
        return visibleRows;
    }

    /**
     * @return The index of the selected row inside the filtered list.
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Move the selection, wrapping around at the first and last row, like
     * CodeMirror's ArrowUp/ArrowDown completion keys.
     * @param delta Negative to move up, positive to move down.
     */
    public void moveSelection(int delta) {
        int size = this.filtered.size();
        if (size == 0) {
            this.selectedIndex = 0;
            return;
        }
        int next = (this.selectedIndex + delta) % size;
        if (next < 0) {
            next += size;
        }
        this.selectedIndex = next;
    }

    /**
     * Move the selection by one page, clamping at the ends (CodeMirror's
     * PageUp/PageDown completion keys).
     * @param direction -1 for a page up, 1 for a page down.
     */
    public void movePage(int direction) {
        int size = this.filtered.size();
        if (size == 0) {
            this.selectedIndex = 0;
            return;
        }
        int step = Math.max(2, this.visibleRows - 1);
        int next = this.selectedIndex + (direction < 0 ? -step : step);
        this.selectedIndex = Math.max(0, Math.min(size - 1, next));
    }

    /**
     * Select one row, clamped into the filtered list. Used by the popup's mouse
     * handling.
     * @param index The filtered row index.
     */
    public void select(int index) {
        if (this.filtered.isEmpty()) {
            this.selectedIndex = 0;
            return;
        }
        this.selectedIndex = Math.max(0, Math.min(this.filtered.size() - 1, index));
    }

    /**
     * @return The selected candidate, or {@code null} when the list is empty.
     */
    @Nullable
    public CompletionItem getSelected() {
        if (this.filtered.isEmpty()) {
            return null;
        }
        return this.filtered.get(Math.max(0, Math.min(this.filtered.size() - 1, this.selectedIndex)));
    }

    /**
     * @return The index of the first row to draw, so the selection stays visible.
     */
    public int getFirstVisibleRow() {
        if (this.selectedIndex < this.visibleRows) {
            return 0;
        }
        return this.selectedIndex - this.visibleRows + 1;
    }

    /**
     * @return The number of rows to draw.
     */
    public int getDrawnRows() {
        int first = getFirstVisibleRow();
        return Math.max(0, Math.min(this.visibleRows, this.filtered.size() - first));
    }

}
