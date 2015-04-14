package com.bandcamp.explorer.ui;

import java.util.Objects;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.ListBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * This helper class attempts to solve well-known "extra column" problem in TableView
 * without having to stick to using CONSTRAINED_RESIZE_POLICY (this policy has its
 * downsides since it won't let you use horizontal scrollbar and it also resizes all columns
 * at once, which is rather cumbersome).
 * 
 * The basic idea here is to track changes in widths of table and individual columns 
 * and resize only the last column so that total width of all columns matches
 * that of a table. When table is narrowed or other columns are resized to the
 * point that last column has finally shrinked to its minimum width, resizing
 * is no longer performed for last column and horizontal scrollbar now appears and
 * can be used instead. In oppposite scenario, when table is stretched and horizontal
 * scrollbar disappears, last column starts automatically resizing again, thus guaranteeing
 * that extra column never visually appears on the screen.
 * 
 * <p>Limitations:<br>
 * - This class is intended to be used only for table view with
 * UNCONSTRAINED_RESIZE_POLICY. Using it with CONSTRAINED_RESIZE_POLICY will lead to
 * unexpected behaviour.<br>
 * - The resizing won't be performed if current last column is non-resizable or have fixed
 * width (i.e. its max width equals to min width).<br>
 */
class TableViewResizeHelper {

	private final InvalidationListener onChangeWidth = this::onChangeWidth;
	private final ChangeListener<Boolean> onTableVBarVisible = this::onTableVBarVisible;
	private final TableView<?> tableView;
	private final ObservableList<? extends TableColumn<?,?>> visibleColumns;
	private final ListProperty<DoubleProperty> columnWidths;
	private ScrollBar tableVBar;
	private boolean preventUpdate;
	private boolean enabled;


	/**
	 * Creates resize helper for the specified table view.
	 * 
	 * @param tableView a table view to apply this resize helper to
	 * @throws NullPointerException if tableView is null
	 */
	TableViewResizeHelper(TableView<?> tableView) {
		this.tableView = Objects.requireNonNull(tableView);
		visibleColumns = tableView.getVisibleLeafColumns();

		columnWidths = new SimpleListProperty<>();
	}


	/**
	 * Enables this helper for a table view.
	 */
	void enable() {
		if (enabled)
			return;

		// Adding a listener to track table view width change
		tableView.widthProperty().addListener(onChangeWidth);

		// Create a binding between a list of visible columns and list property
		// that tracks a width of each visible column.
		columnWidths.bind(new ListBinding<DoubleProperty>() {
			{super.bind(visibleColumns);}

			@Override
			protected ObservableList<DoubleProperty> computeValue() {
				// Whenever a list of visible columns gets changed (for example, when
				// user manually changes column ordering or hides columns using table menu),
				// columnWidths list property is updated to reflect those changes
				ObservableList<DoubleProperty> widths = FXCollections.observableArrayList();
				visibleColumns.forEach(column -> {
					DoubleProperty width = new SimpleDoubleProperty(column.getWidth());
					width.bind(column.widthProperty());
					width.addListener(onChangeWidth);
					widths.add(width);
				});
				return widths;
			}

			@Override
			public void dispose() {
				super.unbind(visibleColumns);
			}
		});
		columnWidths.addListener(onChangeWidth);

		enabled = true;
	}


	/**
	 * Disables this helper for a table view.
	 * After this method is called, table view will use its current column 
	 * resize policy to resize columns.
	 */
	void disable() {
		if (!enabled)
			return;

		tableView.widthProperty().removeListener(onChangeWidth);

		columnWidths.unbind();
		columnWidths.get().forEach(width -> width.removeListener(onChangeWidth));
		columnWidths.removeListener(onChangeWidth);

		if (tableVBar != null) {
			tableVBar.visibleProperty().removeListener(onTableVBarVisible);
			tableVBar = null;
		}

		enabled = false;
	}


	/**
	 * Obtains an instance of TableView's internal vertical scrollbar and
	 * adds a listener to track its visibility.
	 */
	private void initTableVBar() {
		for (Node node : tableView.lookupAll(".scroll-bar")) {
			if (node instanceof ScrollBar) {
				ScrollBar sb = (ScrollBar)node;
				if (sb.getOrientation() == Orientation.VERTICAL) {
					tableVBar = sb;
					break;
				}
			}
		}
		if (tableVBar != null)
			tableVBar.visibleProperty().addListener(onTableVBarVisible);
	}


	/**
	 * Called when vertical scrollbar changes its visibility (which is a result 
	 * of table's fill or height change). Updates last column's width using padding
	 * value based on scrollbar's visibility.
	 * The reason we do this is that vertical scrollbar takes some extra space
	 * at table's right side which means we should update last column's width
	 * accordingly (if scrollbar is visible then padding value must be larger).
	 */
	private void onTableVBarVisible(Observable observable, Boolean oldValue, Boolean newValue) {
		// for some reason this works properly only with delayed update
		Platform.runLater(() -> updateLastColumnWidth(newValue ? tableVBar.getWidth() : 2));
	}


	/** 
	 * Called when table or column width changes.
	 * Updates last column's width according to changed width, also taking into
	 * account vertical scrollbar visibility to properly calculate padding.
	 */
	private void onChangeWidth(Observable o) {
		if (tableVBar == null)
			initTableVBar();
		updateLastColumnWidth(tableVBar != null && tableVBar.isVisible() ? tableVBar.getWidth() : 2);
	}


	/**
	 * Updates last column's width to ensure that total width of all columns matches
	 * table's width.
	 * 
	 * @param padding denotes a size of extra space to put between right border
	 *        of last column and left side of a table during resize   
	 */
	private void updateLastColumnWidth(double padding) {
		if (preventUpdate || visibleColumns.size() == 0)
			return;
		// prevents recursive update on last column width change
		preventUpdate = true;

		double columnWidthTotal = 0.0;
		for (int i = 0; i < columnWidths.size(); i++)
			columnWidthTotal += columnWidths.get(i).get();
		TableColumn<?,?> lastColumn = visibleColumns.get(visibleColumns.size() - 1);
		lastColumn.setPrefWidth(lastColumn.getWidth() + (tableView.getWidth() - columnWidthTotal) - padding);

		preventUpdate = false;
	}

}
