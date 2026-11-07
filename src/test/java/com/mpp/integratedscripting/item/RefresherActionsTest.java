package com.mpp.integratedscripting.item;

import org.cyclops.integrateddynamics.api.part.IPartType;
import org.cyclops.integrateddynamics.core.part.panel.PartTypePanel;
import org.cyclops.integrateddynamics.core.part.panel.PartTypePanelVariableDriven;
import org.cyclops.integrateddynamics.part.PartTypePanelDisplay;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the "is this part a Display Panel?" decision.
 *
 * <p>A network refresh re-evaluates every Display Panel it visits; this check
 * decides which parts need that extra recompute step. The checks use mocks, so
 * no part is actually registered with Integrated Dynamics.</p>
 */
public class RefresherActionsTest {

    @Test
    public void variableDrivenPanelIsADisplay() {
        PartTypePanelVariableDriven<?, ?> panel = mock(PartTypePanelVariableDriven.class);

        assertTrue(RefresherActions.isDisplayPanel(panel));
    }

    @Test
    public void displayPanelImplementationIsADisplay() {
        PartTypePanelDisplay panel = mock(PartTypePanelDisplay.class);

        assertTrue(RefresherActions.isDisplayPanel(panel));
    }

    @Test
    public void otherPanelsAreNotDisplays() {
        PartTypePanel<?, ?> panel = mock(PartTypePanel.class);

        assertFalse(RefresherActions.isDisplayPanel(panel));
    }

    @Test
    public void plainPartAndNullAreNotDisplays() {
        assertFalse(RefresherActions.isDisplayPanel(mock(IPartType.class)));
        assertFalse(RefresherActions.isDisplayPanel(null));
    }

}
