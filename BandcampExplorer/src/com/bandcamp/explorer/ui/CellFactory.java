package com.bandcamp.explorer.ui;

import java.net.URI;
import java.util.function.Function;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
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
	private final CellCustomizer<S,T> cellCustomizer;
	
	
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
	}
	
	
	/**
	 * Constructs a new cell factory.
	 * 
	 * @param cellNodeProvider A callback function to return Node objects
	 *        for displaying them as cells content. If non-null, this function
	 *        will be invoked to convert cell item into a Node object for
	 *        displaying it in a cell instead of text. If null, then cell will be rendered 
	 *        in a usual manner, displaying cell's item value converted to text 
	 *        via item.toString() 
	 * @param cellCustomizer An instance of CellCustomizer to be invoked every time
	 *        cell item is updated, to customize a cell. If null, then this factory
	 *        won't make any additional customizations for cells it creates
	 */
	CellFactory(Function<T, Node> cellNodeProvider, CellCustomizer<S,T> cellCustomizer) {
		this.cellNodeProvider = cellNodeProvider;
		this.cellCustomizer = cellCustomizer;
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
				if (cellCustomizer != null)
					cellCustomizer.apply(this, item, empty);
			}
		};
	}

	
	/**
	 * Creates a cell factory that customizes cells by adding specified
	 * alignment for cell's content.
	 * 
	 * @param alignment an option that specifies how cell content should be aligned
	 * @return cell factory
	 */
	static <S,T> CellFactory<S,T> aligned(Pos alignment) {
		return new CellFactory<>(null, (cell, newItem, empty) -> cell.setAlignment(alignment));
	}
	
	
	/**
	 * Creates cell customizer that adds a tooltip displaying
	 * text representation of cell's content.
	 * 
	 * @return cell customizer
	 */
	private static <S,T> CellCustomizer<S,T> tooltipper() {
		return (cell, newItem, empty) -> {
			if (newItem == null || empty) {
				// remove tooltip (if any) when cell is empty
				cell.setTooltip(null);
			}
			else {
				if (cell.getTooltip() == null) {
					Tooltip tooltip = new Tooltip();
					tooltip.textProperty().bind(cell.textProperty());
					cell.setTooltip(tooltip);
				}
			}
		};
	}

	
	/**
	 * Creates a cell factory that customizes cells by adding a tooltip
	 * displaying text representation of cell's content.
	 * Tooltip is not displayed when cell is empty.
	 * 
	 * @return cell factory
	 */
	static <S,T> CellFactory<S,T> tooltip() {
		return new CellFactory<>(null, tooltipper());
	}
	
	
	
	/**
	 * Creates a cell factory for cells whose content type is URI,
	 * displaying the value of URI as clickable hyperlink. If hyperlink
	 * is clicked, its underlying URI will be used to launch the
	 * default browser and open a web page corresponding to this URI.
	 * Additionally, this factory adds a tooltip, displaying text representation
	 * of URI.
	 * 
	 * @return cell factory
	 */
	static <S> CellFactory<S, URI> tooltippedHyperlink() {
		return new CellFactory<>(uri -> {
			Hyperlink link = new Hyperlink(uri.toString());
			link.setOnAction(event -> Utils.browse(uri));
			link.setStyle("-fx-text-fill: blue;");
			return link;
		}, tooltipper());
	}
		
}
