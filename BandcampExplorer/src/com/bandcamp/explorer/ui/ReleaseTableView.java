package com.bandcamp.explorer.ui;

import java.net.URI;
import java.time.LocalDate;

import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import com.bandcamp.explorer.data.Release;

/**
 * Controller class for release table view component.
 */
public class ReleaseTableView {

	@FXML private SplitPane rootPane;
	@FXML private TableView<Release> releaseTableView;
	@FXML private TableColumn<Release, String> artistColumn;
	@FXML private TableColumn<Release, String> titleColumn;
	@FXML private TableColumn<Release, Release.DownloadType> dlTypeColumn;
	@FXML private TableColumn<Release, LocalDate> releaseDateColumn;
	@FXML private TableColumn<Release, LocalDate> publishDateColumn;
	@FXML private TableColumn<Release, String> tagsColumn;
	@FXML private TableColumn<Release, URI> urlColumn;
	@FXML private TextArea releaseInfo;

	private final ObservableList<Release> tableData = FXCollections.observableArrayList();
	private final SortedList<Release> sortedTableData = new SortedList<>(tableData);

	private ReleasePlayerForm releasePlayer;

	@SuppressWarnings("unused")
	private ResizeHelper resizeHelper;


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
	 */
	private class ResizeHelper {

		private final ObservableList<TableColumn<Release,?>> columns;
		private final DoubleProperty tableWidth;
		private final DoubleProperty[] columnWidths;
		private ScrollBar tableVBar;
		private BooleanProperty tableVBarVisible;
		private boolean preventUpdate;


		ResizeHelper() {
			tableWidth = new SimpleDoubleProperty(releaseTableView.getWidth());
			tableWidth.bind(releaseTableView.widthProperty());
			tableWidth.addListener(this::onChangeWidth);

			columns = releaseTableView.getColumns();

			columnWidths = new DoubleProperty[columns.size()];
			for (int i = 0; i < columnWidths.length; i++) {
				TableColumn<Release,?> col = columns.get(i);
				columnWidths[i] = new SimpleDoubleProperty(col.getWidth());
				columnWidths[i].bind(col.widthProperty());
				columnWidths[i].addListener(this::onChangeWidth);
			}
		}


		/**
		 * Obtains an instance of TableView's internal vertical scrollbar and
		 * adds a listener to track its visibility.
		 */
		private void initTableVBar() {
			for (Node node : releaseTableView.lookupAll(".scroll-bar")) {
				if (node instanceof ScrollBar) {
					ScrollBar sb = (ScrollBar)node;
					if (sb.getOrientation() == Orientation.VERTICAL) {
						tableVBar = sb;
						break;
					}
				}
			}
			if (tableVBar != null) {
				tableVBarVisible = new SimpleBooleanProperty(tableVBar.isVisible());
				tableVBarVisible.bind(tableVBar.visibleProperty());
				tableVBarVisible.addListener(this::onTableVBarVisible);
			}
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
			updateLastColumnWidth(newValue ? tableVBar.getWidth() + 2 : 2);
		}


		/** 
		 * Called when table or column width changes.
		 * Updates last column's width according to changed width, also taking into
		 * account vertical scrollbar visibility to properly calculate padding.
		 */
		private void onChangeWidth(Observable o) {
			if (tableVBar == null)
				initTableVBar();
			updateLastColumnWidth(tableVBar != null && tableVBar.isVisible() ? tableVBar.getWidth() + 2 : 2);
		}


		/**
		 * Updates last column's width to ensure that total width of all columns matches
		 * table's width.
		 * 
		 * @param padding denotes a size of extra space to put between right border
		 *        of last column and left side of a table during resize   
		 */
		private void updateLastColumnWidth(double padding) {
			if (preventUpdate || columns.size() == 0)
				return;
			// prevents recursive update on last column width change
			preventUpdate = true;

			double columnWidthTotal = 0.0;
			for (int i = 0; i < columnWidths.length; i++)
				columnWidthTotal += columnWidths[i].get();
			TableColumn<Release,?> lastColumn = columns.get(columns.size() - 1);
			lastColumn.setPrefWidth(lastColumn.getWidth() + (tableWidth.get() - columnWidthTotal) - padding);

			preventUpdate = false;
		}

	}


	/**
	 * Returns a root node of ReleaseTableView component.
	 */
	Node getRoot() {
		return rootPane;
	}


	/**
	 * Returns underlying TableView.
	 */
	TableView<Release> getTable() {
		return releaseTableView;
	}


	/**
	 * Returns an observable list of items of underlying TableView 
	 */
	ObservableList<Release> getItems() {
		return tableData;
	}


	/**
	 * Sets the release player for playing selected releases.
	 * 
	 * @param releasePlayer the release player
	 */
	public void setReleasePlayer(ReleasePlayerForm releasePlayer) {
		this.releasePlayer = releasePlayer;
	}


	/**
	 * Opens a player window to play currently selected release.
	 */
	public void playSelectedRelease() {
		if (releasePlayer != null) {
			Release release = releaseTableView.getSelectionModel().getSelectedItem();
			if (release != null)
				releasePlayer.setRelease(release);
		}
	}


	/**
	 * Helper method to check if all components defined by FXML file have been injected.
	 */
	private void checkComponents() {
		// copy-pasted from SceneBuilder's "sample controller skeleton" window
		assert artistColumn != null : "fx:id=\"artistColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert tagsColumn != null : "fx:id=\"tagsColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert publishDateColumn != null : "fx:id=\"publishDateColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert titleColumn != null : "fx:id=\"titleColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert urlColumn != null : "fx:id=\"urlColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert rootPane != null : "fx:id=\"rootPane\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert releaseTableView != null : "fx:id=\"releaseTableView\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert dlTypeColumn != null : "fx:id=\"dlTypeColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert releaseInfo != null : "fx:id=\"releaseInfo\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert releaseDateColumn != null : "fx:id=\"releaseDateColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
	}


	/**
	 * Initialization method invoked by FXML loader, provides initial setup for components.
	 */
	@FXML
	private void initialize() {
		checkComponents();

		releaseTableView.setItems(sortedTableData);
		sortedTableData.comparatorProperty().bind(releaseTableView.comparatorProperty());

		CellFactory<Release, String> tooltipFactory = CellFactory.tooltip();
		
		artistColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
		artistColumn.setCellFactory(tooltipFactory);
		artistColumn.setCellValueFactory(cellData -> cellData.getValue().artistProperty());

		titleColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
		titleColumn.setCellFactory(tooltipFactory);
		titleColumn.setCellValueFactory(cellData -> cellData.getValue().titleProperty());

		dlTypeColumn.setCellValueFactory(cellData -> cellData.getValue().downloadTypeProperty());

		releaseDateColumn.setCellFactory(CellFactory.aligned(Pos.CENTER));
		releaseDateColumn.setCellValueFactory(cellData -> {
			// display empty cell instead of LocalDate.MIN
			Release release =  cellData.getValue();
			return release.getReleaseDate().equals(LocalDate.MIN) ? null : release.releaseDateProperty();
		});

		publishDateColumn.setCellFactory(CellFactory.aligned(Pos.CENTER));
		publishDateColumn.setCellValueFactory(cellData -> cellData.getValue().publishDateProperty());

		tagsColumn.setCellFactory(tooltipFactory);
		tagsColumn.setCellValueFactory(cellData -> cellData.getValue().tagsStringProperty());

		urlColumn.setCellFactory(CellFactory.tooltippedHyperlink());
		urlColumn.setCellValueFactory(cellData -> cellData.getValue().uriProperty());

		// Setting a callback to display an information about selected
		// release in text area.
		releaseTableView.getSelectionModel().selectedItemProperty()
		.addListener((observable, oldRelease, newRelease) -> {
			if (newRelease != null) {
				StringBuilder sb = new StringBuilder();
				if (!newRelease.getInformation().isEmpty())
					sb.append(newRelease.getInformation()).append('\n').append('\n');
				if (!newRelease.getTracks().isEmpty()) {
					sb.append("Tracklist:\n");
					newRelease.getTracks().forEach(track -> sb.append(track).append('\n'));
				}
				releaseInfo.setText(sb.append('\n').toString());
			}
			else
				releaseInfo.clear();

		});

		resizeHelper = new ResizeHelper();
	}


	/**
	 * Handler for key press on table view.
	 * If Enter was pressed, opens selected release in player.
	 */
	@FXML
	private void onReleaseTableKeyPress(KeyEvent event) {
		if (event.getCode() == KeyCode.ENTER)
			playSelectedRelease();
	}


	/**
	 * Handler for mouse click on a table view.
	 * If double-clicked , opens selected release in player.
	 */
	@FXML
	private void onReleaseTableMouseClick(MouseEvent event) {
		if (event.getClickCount() > 1)
			playSelectedRelease();
	}

}
