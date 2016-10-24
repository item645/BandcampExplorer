package com.bandcamp.explorer.ui;

import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;

/**
 * Menu item implementation that uses internal label (set as a value of 
 * {@link #graphicProperty()}) to dislay text.
 * This allows for additional styling of menu items, in particular - to set a limit
 * on menu width through CSS.
 * Also, if text displayed in this menu item is clipped due to the width limitation,
 * a tooltip with full (non-clipped) text will be shown on the right side when item
 * is selected by mouse.
 */
class LabeledMenuItem extends MenuItem {

	private final Label label;


	/**
	 * Constructs a labeled menu item with empty text.
	 */
	LabeledMenuItem() {
		label = new Label();
		init();
	}


	/**
	 * Constructs a labeled menu item with the specified text.
	 * 
	 * @param text a text to display in menu item
	 */
	LabeledMenuItem(String text) {
		label = new Label(text);
		init();
	}


	/**
	 * Shared initialization code for constructors.
	 */
	private void init() {
		setGraphic(label);
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
