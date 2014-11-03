package com.bandcamp.explorer.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Text;
import javafx.util.Callback;

/**
 * Convenient implementation of cell factory callback providing
 * additional capabilities for cells customization.
 *
 * @param <S> type of the TableView generic type
 * @param <T> type of the item contained within the Cell
 */
class CellFactory<S,T> implements Callback<TableColumn<S,T>, TableCell<S,T>> {

	private final Function<T, Node> cellNodeProvider;
	private final List<CellCustomizer<S,T>> cellCustomizers = new ArrayList<>();


	/**
	 * A functional interface that defines callback functions
	 * to be applied on newly created or updated cell to customize
	 * it in some way depending on its content.
	 */
	@FunctionalInterface
	interface CellCustomizer<S,T> {

		/**
		 * Provides customization for specified cell depending on its content.
		 * If cell customizer is installed on cell factory, this method gets 
		 * called every time cell item is updated.
		 * 
		 * @param cell a cell to customize
		 * @param newItem a value of new item contained by cell after update
		 * @param empty indicates whether the cell is "empty", i.e. does not
		 *        contain any domain data
		 */
		void apply(TableCell<S,T> cell, T newItem, boolean empty);


		/**
		 * Creates a customizer that adds specified alignment for cell's content.
		 * 
		 * @param alignment an option that specifies how cell content should be aligned
		 */
		static <S,T> CellCustomizer<S,T> alignment(Pos alignment) {
			return (cell, newItem, empty) -> cell.setAlignment(alignment);
		}


		/**
		 * Creates a customizer that adds a tooltip displaying text 
		 * representation of cell content when cell text is clipped by
		 * parent column's border.
		 */
		static <S,T> CellCustomizer<S,T> tooltip() {
			return (cell, newItem, empty) -> {
				cell.setOnMouseEntered(enterEvent -> {
					// Get a screen point of cell's lower left corner
					Point2D lowerLeftCorner = cell.localToScreen(
							cell.getLayoutBounds().getMinX(), cell.getLayoutBounds().getMaxY());

					// Little hack to determine whether the cell text is clipped.
					// Clipping operation does not change the actual text property of a cell,
					// what really gets changed instead is a labeled text (an instance of
					// com.sun.javafx.scene.control.skin.LabeledText) that is used internally 
					// by a cell skin implementation (com.sun.javafx.scene.control.skin.LabeledSkinBase)
					// to actually display its content as a styled text.
					// LabeledText has the style class "text" and is reachable via node lookup.
					Text displayedText = (Text)cell.lookup(".text");
					String cellText = newItem != null ? newItem.toString() : "";

					// If text is clipped, display the tooltip at cell's lower left corner
					if (displayedText != null && !cellText.isEmpty() && !displayedText.getText().equals(cellText)) {
						Tooltip tooltip = new Tooltip(cell.getText());
						tooltip.show(cell, lowerLeftCorner.getX(), lowerLeftCorner.getY());
						cell.setOnMouseExited(exitEvent -> tooltip.hide());
					}
					else
						cell.setOnMouseExited(null);
				});
			};
		}

	}


	/**
	 * Constructs a new cell factory that renders cells in a default manner,
	 * displaying cell's item value converted to text via item.toString().
	 * 
	 * @param cellCustomizers An array of CellCustomizer callbacks to be invoked
	 *        every time cell item is updated, to customize a cell. If passed array
	 *        is empty (or zero arguments were passed in case of varargs), then this
	 *        factory won't make any additional customizations for cells it creates.
	 * @throws NullPointerException if cell customizers array or any of its elements is null
	 */
	@SafeVarargs
	CellFactory(CellCustomizer<S,T>... cellCustomizers) {
		this(null, cellCustomizers);
	}


	/**
	 * Constructs a new cell factory that allows to replace cells content with an
	 * arbitrary Node objects.
	 * 
	 * @param cellNodeProvider A callback function to return Node objects
	 *        for displaying them as cells content. If non-null, this function
	 *        will be invoked to convert cell item into a Node object for
	 *        displaying it in a cell instead of text. If null, then cell will be
	 *        rendered in a usual manner, displaying cell's item value converted to
	 *        text via item.toString().
	 * @param cellCustomizers An array of CellCustomizer callbacks to be invoked
	 *        every time cell item is updated, to customize a cell. If passed array
	 *        is empty (or zero arguments were passed in case of varargs), then
	 *        this factory won't make any additional customizations for cells it creates.
	 * @throws NullPointerException if cell customizers array or any of its elements is null
	 */
	@SafeVarargs
	CellFactory(Function<T, Node> cellNodeProvider, CellCustomizer<S,T>... cellCustomizers) {
		this.cellNodeProvider = cellNodeProvider;
		for (CellCustomizer<S,T> customizer : cellCustomizers)
			this.cellCustomizers.add(Objects.requireNonNull(customizer));
	}


	/** 
	 * This method gets called to construct a new cell.
	 * 
	 * @param column a column for new cell
	 * @return newly created cell
	 */
	@Override
	public TableCell<S,T> call(TableColumn<S,T> column) {
		return new TableCell<S,T>() {
			@Override
			protected void updateItem(T item, boolean empty) {
				if (item == getItem())
					return;
				super.updateItem(item, empty);
				if (item == null || empty) {
					super.setText(null);
					super.setGraphic(null);
				}
				else {
					super.setText(item.toString());
					if (cellNodeProvider != null) {
						super.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
						super.setGraphic(cellNodeProvider.apply(item));
					}
					else
						super.setGraphic(null);
				}
				cellCustomizers.forEach(customizer -> customizer.apply(this, item, empty));
			}
		};
	}

}
