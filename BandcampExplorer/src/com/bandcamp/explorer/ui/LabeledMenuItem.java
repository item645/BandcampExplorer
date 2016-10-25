package com.bandcamp.explorer.ui;

import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;

/**
 * Menu item implementation that uses internal label (set as a value of 
 * {@link #graphicProperty()}) to dislay text.
 * This allows for additional styling of menu items, in particular - to set a limit
 * on menu width through CSS.
 * Also, this implementation supports setting a content tooltip for menu item. If text displayed
 * in this menu item is clipped due to the width limitation, tooltip with full (non-clipped) text
 * will be shown on the right side when item is selected by mouse.
 */
class LabeledMenuItem extends MenuItem {

	private final Label label;


	/**
	 * Constructs new labeled menu item with empty text and no tooltip.
	 */
	LabeledMenuItem() {
		this(null, false);
	}


	/**
	 * Constructs new labeled menu item with the specified text and no tooltip.
	 * 
	 * @param text a text to display in menu item
	 */
	LabeledMenuItem(String text) {
		this(text, false);
	}


	/**
	 * Constructs new labeled menu item with empty text and allows to add a
	 * content tooltip to it.
	 * 
	 * @param addContentTooltip indicates whether a content tooltip should be added
	 *        to this menu item
	 */
	LabeledMenuItem(boolean addContentTooltip) {
		this(null, addContentTooltip);
	}


	/**
	 * Constructs new labeled menu item with the specified text and allows to
	 * add a content tooltip to it.
	 * 
	 * @param text a text to display in menu item
	 * @param addContentTooltip indicates whether a content tooltip should be added
	 *        to this menu item
	 */
	LabeledMenuItem(String text, boolean addContentTooltip) {
		setGraphic(label = new Label(text));
		if (addContentTooltip)
			// Set a content tooltip to display on the right upper corner of the label
			Utils.setContentTooltip(label, 
					() -> label.getLayoutBounds().getMaxX(), () -> label.getLayoutBounds().getMinY());
	}


	/**
	 * Sets a text for this menu item.
	 * Text will be displayed using internal label.
	 * 
	 * @param text a text to display
	 */
	final void setLabelText(String text) {
		label.setText(text);
	}

}
