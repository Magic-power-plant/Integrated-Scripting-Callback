package com.mpp.integratedscripting.client.gui.component.input;

import com.mpp.integratedscripting.api.language.completion.CompletionItem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-logic tests for the completion popup model
 * (docs/LANGUAGE-API-REFACTOR.md Phase 4: the popup logic is tested without a
 * client).
 */
public class CompletionListModelTest {

    private static CompletionItem item(String label) {
        return CompletionItem.builder(label).build();
    }

    private static CompletionItem item(String label, String insertText) {
        return CompletionItem.builder(label).insertText(insertText).build();
    }

    @Test
    public void testFilterByLabel() {
        CompletionListModel model = new CompletionListModel(
                Arrays.asList(item("const"), item("continue"), item("return")));
        model.setFilter("con");
        assertEquals(2, model.getFiltered().size());
        assertEquals("const", model.getSelected().getLabel());

        model.setFilter("ret");
        assertEquals(1, model.getFiltered().size());
        assertEquals("return", model.getSelected().getLabel());
    }

    @Test
    public void testFilterIsCaseInsensitiveAndAlsoMatchesInsertText() {
        CompletionListModel model = new CompletionListModel(
                Arrays.asList(item("Stackable", "stackable()"), item("strength")));
        model.setFilter("STACK");
        assertEquals(1, model.getFiltered().size());
        assertEquals("Stackable", model.getSelected().getLabel());
        assertEquals("STACK", model.getFilterPrefix());
    }

    @Test
    public void testEmptyFilterKeepsEverythingAndEmptyResultIsReported() {
        CompletionListModel model = new CompletionListModel(Arrays.asList(item("a"), item("b")));
        model.setFilter("");
        assertEquals(2, model.getFiltered().size());
        assertFalse(model.isEmpty());

        model.setFilter("zz");
        assertTrue(model.isEmpty());
        assertNull(model.getSelected());
    }

    @Test
    public void testSelectionWrapsAroundLikeArrowKeys() {
        CompletionListModel model = new CompletionListModel(
                Arrays.asList(item("a"), item("b"), item("c")), 2);
        assertEquals(0, model.getSelectedIndex());
        model.moveSelection(-1);
        assertEquals(2, model.getSelectedIndex());
        model.moveSelection(1);
        assertEquals(0, model.getSelectedIndex());
        model.moveSelection(5);
        assertEquals(2, model.getSelectedIndex());
        assertEquals("c", model.getSelected().getLabel());
    }

    @Test
    public void testMovePageClampsAtTheEnds() {
        List<CompletionItem> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(item("item" + i));
        }
        CompletionListModel model = new CompletionListModel(items, 4);
        // A page is visibleRows - 1, i.e. 3 rows.
        model.movePage(1);
        assertEquals(3, model.getSelectedIndex());
        model.movePage(1);
        assertEquals(6, model.getSelectedIndex());
        model.movePage(1);
        assertEquals(9, model.getSelectedIndex());
        model.movePage(1);
        assertEquals(9, model.getSelectedIndex());
        model.movePage(-1);
        assertEquals(6, model.getSelectedIndex());
    }

    @Test
    public void testSelectClampsAndIgnoresAnEmptyList() {
        CompletionListModel model = new CompletionListModel(
                Arrays.asList(item("a"), item("b"), item("c")));
        model.select(2);
        assertEquals(2, model.getSelectedIndex());
        model.select(99);
        assertEquals(2, model.getSelectedIndex());
        model.select(-5);
        assertEquals(0, model.getSelectedIndex());

        CompletionListModel empty = new CompletionListModel(null);
        empty.select(3);
        assertEquals(0, empty.getSelectedIndex());
    }

    @Test
    public void testScrollingWindowKeepsTheSelectionVisible() {
        List<CompletionItem> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(item("item" + i));
        }
        CompletionListModel model = new CompletionListModel(items, 3);
        assertEquals(0, model.getFirstVisibleRow());
        assertEquals(3, model.getDrawnRows());

        model.moveSelection(5);
        assertEquals(5, model.getSelectedIndex());
        assertEquals(3, model.getFirstVisibleRow());
        assertEquals(3, model.getDrawnRows());

        model.moveSelection(4);
        assertEquals(9, model.getSelectedIndex());
        assertEquals(7, model.getFirstVisibleRow());
        assertEquals(3, model.getDrawnRows());
    }

    @Test
    public void testShortListsDrawOnlyTheirRows() {
        CompletionListModel model = new CompletionListModel(Arrays.asList(item("a"), item("b")), 8);
        assertEquals(2, model.getDrawnRows());
        assertEquals(0, model.getFirstVisibleRow());
    }

    @Test
    public void testFilterKeepsTheSelectedCandidateWhenItSurvives() {
        CompletionListModel model = new CompletionListModel(
                Arrays.asList(item("const"), item("continue"), item("convert")));
        model.select(2);
        assertEquals("convert", model.getSelected().getLabel());
        model.setFilter("con");
        // "convert" is still in the narrowed list, so it stays selected.
        assertEquals("convert", model.getSelected().getLabel());
        assertEquals(2, model.getSelectedIndex());
    }

    @Test
    public void testFilterSelectsTheFirstRowWhenTheSelectionIsGone() {
        CompletionListModel model = new CompletionListModel(
                Arrays.asList(item("const"), item("return"), item("reverse")));
        model.select(1);
        assertEquals("return", model.getSelected().getLabel());
        model.setFilter("rev");
        assertEquals("reverse", model.getSelected().getLabel());
        assertEquals(0, model.getSelectedIndex());
    }

    @Test
    public void testNullAndEmptyItemsAreSafe() {
        CompletionListModel empty = new CompletionListModel(null);
        assertTrue(empty.isEmpty());
        assertNull(empty.getSelected());
        assertEquals(0, empty.getDrawnRows());
        empty.moveSelection(1);
        assertEquals(0, empty.getSelectedIndex());
        empty.movePage(1);
        assertEquals(0, empty.getSelectedIndex());

        CompletionListModel withInsertText = new CompletionListModel(
                Arrays.asList(item("label", "inserted")));
        withInsertText.setFilter("in");
        assertEquals(1, withInsertText.getFiltered().size());
    }

}
