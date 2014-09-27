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
 * Utility class providing custom factories to create TableView cells.
 */
class CellFactories {

	private CellFactories() {};


	/**
	 * Returns a wrapper around specified cell factory adding an
	 * ability to set content alignment.
	 * 
	 * @param cellFactory cell factory to be wrapped
	 * @param alignment an option that specifies how cell content should be aligned
	 * @return cell factory with added alignment
	 * @throws NullPointerException if cellFactory is null
	 */
	static <S,T> Callback<TableColumn<S,T>, TableCell<S,T>> alignedCellFactory(
			Callback<TableColumn<S,T>, TableCell<S,T>> cellFactory, Pos alignment) {
		return column -> {
			TableCell<S,T> cell = cellFactory.call(column);
			cell.setAlignment(alignment);
			return cell;
		};
	}


	/**
	 * Returns a wrapper around specified cell factory that adds
	 * a tooltip displaying text representation of cell's content.
	 * 
	 * @param cellFactory cell factory to be wrapped
	 * @return cell factory that adds tooltips to created cells
	 * @throws NullPointerException if cellFactory is null
	 */
	static <S,T> Callback<TableColumn<S,T>, TableCell<S,T>> tooltipCellFactory(
			Callback<TableColumn<S,T>, TableCell<S,T>> cellFactory) {
		// TODO find a way to hide tooltip when cell value is null or empty
		return column -> {
			TableCell<S,T> cell = cellFactory.call(column);
			Tooltip tooltip = new Tooltip();
			tooltip.textProperty().bind(cell.textProperty());
			cell.setTooltip(tooltip);
			return cell;
		};
	}


	/**
	 * Returns a cell factory for creating cells whose content type is URI,
	 * displaying the value of URI as clickable hyperlink. If hyperlink
	 * is clicked, its underlying URI will be used to launch the
	 * default browser and open a web page corresponding to this URI.
	 * 
	 * @return cell factory for URI cells that renders cell's content as hyperlink
	 */
	static <S> Callback<TableColumn<S, URI>, TableCell<S, URI>> hyperlinkCellFactory() {
		return nodeCellFactory(uri -> {
			Hyperlink link = new Hyperlink(uri.toString());
			link.setOnAction(event -> Utils.browse(uri));
			link.setStyle("-fx-text-fill: blue;");
			return link;
		});
	}


	/**
	 * Returns a cell factory for creating cells to display an arbitrary
	 * Node object instead of text. Nodes to display as cells content are
	 * obtained using supplied cell node provider function.
	 * Note that node will not be rendered in a cell if cell's content is null.
	 * 
	 * @param cellNodeProvider a callback function to return Node objects
	 * 		  for displaying them as cells content
	 * @return cell factory that creates cells with arbitrary nodes
	 */
	static <S,T> Callback<TableColumn<S,T>, TableCell<S,T>> nodeCellFactory(
			Function<T, Node> cellNodeProvider) {
		return column -> {
			return new TableCell<S,T>() {
				@Override
				protected void updateItem(T item, boolean empty) {
					super.updateItem(item, empty);
					if (item == null || empty) {
						super.setText(null);
						super.setGraphic(null);
					}
					else {
						super.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
						super.setGraphic(cellNodeProvider.apply(item));
					}
				}
			};
		};
	}

}
