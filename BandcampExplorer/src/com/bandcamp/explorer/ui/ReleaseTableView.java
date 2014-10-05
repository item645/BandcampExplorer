package com.bandcamp.explorer.ui;

import java.net.URI;
import java.time.LocalDate;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
	private TableViewResizeHelper resizeHelper;


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

		resizeHelper = new TableViewResizeHelper(releaseTableView);
		resizeHelper.enable();
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
