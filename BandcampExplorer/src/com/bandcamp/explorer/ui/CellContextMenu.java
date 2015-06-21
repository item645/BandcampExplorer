package com.bandcamp.explorer.ui;

import com.bandcamp.explorer.ui.CellFactory.CellCustomizer;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Special-purpose context menu implementation designed to be used only with
 * {@link javafx.scene.control.TableCell} or its subclasses.
 * The instance of this context menu should be attached to table cells only through
 * the use of cell customizer, supplied by {@link #customizer()} method.
 * In addition to standard context menu functionality, this implementation allows
 * its clients to obtain a reference to a table cell on which this menu has popped up. 
 */
class CellContextMenu extends ContextMenu {

	/**
	 * Holds a reference to a right-click mouse event that caused this menu to pop up
	 */
	private MouseEvent showMenuEvent;

	/**
	 * Cell customizer that attaches this context menu to table cells.
	 */
	private final CellCustomizer<?,?> customizer = (cell, newItem, empty) -> {
		cell.setContextMenu(this);
		cell.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
			if (event.getButton() == MouseButton.SECONDARY)
				showMenuEvent = event;
		});
	};


	/**
	 * Creates a CellContextMenu instance.
	 */
	CellContextMenu() {
		init();
	}


	/**
	 * Creates a CellContextMenu instance initialized with the given items
	 * 
	 * @param items items for this menu
	 */
	CellContextMenu(MenuItem... items) {
		super(items);
		init();
	}


	/**
	 * Shared initialization code for constructors.
	 */
	private void init() {
		setOnHidden(event -> showMenuEvent = null);
		setConsumeAutoHidingEvents(false);
	}


	/**
	 * Returns a cell customizer that adds this menu to a cell.
	 */
	@SuppressWarnings("unchecked")
	final <S,T> CellCustomizer<S,T> customizer() {
		// Type cast is safe here because for this customizer type parameters don't matter.
		return (CellCustomizer<S,T>)customizer;
	}


	/**
	 * Returns a table cell on which this menu popped up after mouse right-click
	 * event occured.
	 * In case it is not possible to obtain a table cell reference, returns null.
	 */
	final TableCell<?,?> getSelectedCell() {
		if (showMenuEvent != null) {
			Object source = showMenuEvent.getSource();
			if (source instanceof TableCell)
				return (TableCell<?,?>)source;
		}
		return null;
	}

}
