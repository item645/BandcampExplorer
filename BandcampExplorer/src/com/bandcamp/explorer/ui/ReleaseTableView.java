package com.bandcamp.explorer.ui;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ModifiableObservableListBase;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;

import com.bandcamp.explorer.data.Release;
import com.bandcamp.explorer.data.ReleaseFilters;
import com.bandcamp.explorer.data.SearchType;
import com.bandcamp.explorer.data.Time;
import com.bandcamp.explorer.ui.CellFactory.CellCustomizer;
import com.bandcamp.explorer.util.ExceptionUnchecker;

/**
 * Controller class for release table view component.
 */
class ReleaseTableView extends AnchorPane {

	@FXML private TextField artistFilter;
	@FXML private TextField titleFilter;
	@FXML private TextField tagsFilter;
	@FXML private TextField urlFilter;
	@FXML private DatePicker publishDateFilterFrom;
	@FXML private DatePicker publishDateFilterTo;
	@FXML private DatePicker releaseDateFilterFrom;
	@FXML private DatePicker releaseDateFilterTo;
	@FXML private CheckBox dlTypeFree;
	@FXML private CheckBox dlTypeNameYourPrice;
	@FXML private CheckBox dlTypePaid;
	@FXML private CheckBox dlTypeUnavailable;
	@FXML private Button applyFilter;
	@FXML private Button resetFilter;
	@FXML private Button showPlayer;
	@FXML private Label filteredStatusLabel;
	@FXML private TableView<Release> releaseTableView;
	@FXML private TableColumn<Release, String> artistColumn;
	@FXML private TableColumn<Release, String> titleColumn;
	@FXML private TableColumn<Release, Time> timeColumn;
	@FXML private TableColumn<Release, Release.DownloadType> dlTypeColumn;
	@FXML private TableColumn<Release, LocalDate> releaseDateColumn;
	@FXML private TableColumn<Release, LocalDate> publishDateColumn;
	@FXML private TableColumn<Release, String> tagsColumn;
	@FXML private TableColumn<Release, URI> urlColumn;
	@FXML private TextArea releaseInfo;

	/**
	 * In-memory cache for this component's FXML data. This way we don't need
	 * to open resource stream every time when new table view is created to be
	 * added on ResultsView tab.
	 */
	private static final ByteArrayInputStream FXML_STREAM = ExceptionUnchecker.uncheck(() -> {
		try (InputStream in = new BufferedInputStream(
				ReleaseTableView.class.getResourceAsStream("ReleaseTableView.fxml"))) {
			ByteArrayOutputStream out = new ByteArrayOutputStream(20480);
			for (int b; (b = in.read()) != -1; )
				out.write(b);
			return new ByteArrayInputStream(out.toByteArray());
		}
	});

	private final BandcampExplorerMainForm mainForm;
	private final ReleasePlayerForm releasePlayer;

	private final EnumSet<Release.DownloadType> selectedDownloadTypes = EnumSet.allOf(Release.DownloadType.class);

	private final ObservableList<Release> items = FXCollections.observableArrayList();
	private final FilteredList<Release> filteredItems = new FilteredList<>(items);
	private SortedList<Release> sortedItems = new SortedList<>(filteredItems);

	/**
	 * Special-purpose decorator for items list that features modified clear()
	 * method to deal with the problem of stale Release references in a SortedList.
	 * Reference to this decorator is handed to class clients so they can transparently
	 * call modified version of clear().
	 */
	private final ObservableList<Release> itemsDecorator = new ModifiableObservableListBase<Release>() {

		/** 
		 * {@inheritDoc}
		 * This modified implementation additionally instantiates and installs new instance
		 * of SortedList following each call to clear() on backing items list.
		 * This guarantees that all stale Release references contained by SortedList's internal
		 * array of sorted elements become unreachable as soon as old instance of SortedList
		 * gets discarded, which is essential to release cache to work as it is supposed to.
		 */
		@Override
		public void clear() {
			if (hasListeners()) {
				beginChange();
				nextRemove(0, this);
			}
			items.clear();
			if (hasListeners())
				endChange();

			sortedItems.comparatorProperty().unbind();
			releaseTableView.setItems(sortedItems = new SortedList<>(filteredItems));
			sortedItems.comparatorProperty().bind(releaseTableView.comparatorProperty());
		}

		// Other implemented methods simply delegate to the items list
		@Override public Release get(int index)                       {return items.get(index);}
		@Override public int size()                                   {return items.size();}
		@Override protected void doAdd(int index, Release element)    {items.add(index, element);}
		@Override protected Release doSet(int index, Release element) {return items.set(index, element);}
		@Override protected Release doRemove(int index)               {return items.remove(index);}
	};

	private final CellContextMenu cellContextMenu = new CellContextMenu();
	private TableViewResizeHelper resizeHelper;



	/**
	 * This class provides a context menu for table cells.
	 */
	private class CellContextMenu extends ContextMenu {

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
		 * Creates a context menu instance.
		 */
		CellContextMenu() {
			setOnHidden(event -> showMenuEvent = null);
			setConsumeAutoHidingEvents(false);
			createItems();
		}


		/**
		 * Returns a type-checked reference to cell customizer that adds this menu to a cell.
		 */
		@SuppressWarnings("unchecked")
		<S,T> CellCustomizer<S,T> customizer() {
			// Type cast is safe here because for this customizer type parameters don't matter.
			return (CellCustomizer<S,T>)customizer;
		}


		/**
		 * Creates items for this menu.
		 */
		private void createItems() {
			MenuItem searchArtist = new MenuItem("Search this Artist");
			searchArtist.setOnAction(event -> {
				Release release = getSelectedRelease();
				if (release != null)
					mainForm.searchReleases(release.getArtist(), SearchType.SEARCH, true);
			});

			MenuItem moreFromDomain = new MenuItem("More Releases from this Domain");
			moreFromDomain.setOnAction(event -> {
				Release release = getSelectedRelease();
				if (release != null)
					mainForm.searchReleases(release.getDiscographyURI().toString(), SearchType.DIRECT, true);
			});

			MenuItem viewOnBandcamp = new MenuItem("View on Bandcamp");
			viewOnBandcamp.setOnAction(event -> {
				Release release = getSelectedRelease();
				if (release != null)
					Utils.browse(release.getURI());
			});

			MenuItem viewDiscogOnBandcamp = new MenuItem("View Discography on Bandcamp");
			viewDiscogOnBandcamp.setOnAction(event -> {
				Release release = getSelectedRelease();
				if (release != null)
					Utils.browse(release.getDiscographyURI());
			});

			MenuItem copyText = new MenuItem("Copy Text");
			copyText.setOnAction(event -> {
				TableCell<?,?> cell = getSelectedCell();
				if (cell != null)
					Utils.toClipboardAsString(cell.getItem());
			});

			MenuItem copyReleaseText = new MenuItem("Copy Release as Text");
			copyReleaseText.setOnAction(event -> Utils.toClipboardAsString(getSelectedRelease()));

			MenuItem copyAllReleasesText = new MenuItem("Copy All Releases as Text");
			copyAllReleasesText.setOnAction(event -> {
				// NOTE: we don't use System.lineSeparator() to separate lines here
				// due to a possible bug in JavaFX clipboard implementation.
				// More details: http://stackoverflow.com/questions/18827217/javafx-clipboard-double-newlines
				Utils.toClipboardAsString(sortedItems.stream()
						.map(Release::toString)
						.collect(Collectors.joining("\n")));
			});

			MenuItem playRelease = new MenuItem("Play Release...");
			playRelease.setOnAction(event -> playSelectedRelease());

			getItems().addAll(searchArtist, moreFromDomain, new SeparatorMenuItem(), viewOnBandcamp,
					viewDiscogOnBandcamp, new SeparatorMenuItem(), copyText, copyReleaseText,
					copyAllReleasesText, new SeparatorMenuItem(), playRelease);
		}


		/**
		 * Returns a table cell on which this menu popped up after mouse right-click
		 * event occured.
		 */
		private TableCell<?,?> getSelectedCell() {
			if (showMenuEvent != null) {
				Object source = showMenuEvent.getSource();
				if (source instanceof TableCell)
					return (TableCell<?,?>)source;
			}
			return null;
		}

	}


	/**
	 * Creates an instance of release table view component.
	 * 
	 * @param mainForm reference to app's main form
	 * @param releasePlayer reference to a release player
	 */
	private ReleaseTableView(BandcampExplorerMainForm mainForm, ReleasePlayerForm releasePlayer) {
		this.mainForm = mainForm;
		this.releasePlayer = releasePlayer;
	}


	/**
	 * Creates an instance of release table view component.
	 * 
	 * @param mainForm reference to app's main form
	 * @param releasePlayer reference to a release player
	 */
	static ReleaseTableView create(BandcampExplorerMainForm mainForm, ReleasePlayerForm releasePlayer) {
		assert mainForm != null;
		assert releasePlayer != null;
		FXML_STREAM.reset(); // for BAIS reset is always supported
		return Utils.loadFXMLComponent(FXML_STREAM, () -> new ReleaseTableView(mainForm, releasePlayer));
	}


	/**
	 * Returns an observable list of releases in this table view. 
	 */
	ObservableList<Release> getReleases() {
		return itemsDecorator;
	}


	/**
	 * Prepares a combined filter using conditions provided by filter fields,
	 * composed by logical AND.
	 * 
	 * @return a filter
	 */
	private Predicate<Release> prepareFilter() {
		List<Predicate<Release>> filters = new ArrayList<>();

		String artist = artistFilter.getText().trim();
		if (!artist.isEmpty())
			filters.add(ReleaseFilters.artistContains(artist));

		String title = titleFilter.getText().trim();
		if (!title.isEmpty())
			filters.add(ReleaseFilters.titleContains(title));

		String tags = tagsFilter.getText().trim();
		if (!tags.isEmpty())
			filters.add(ReleaseFilters.byTags(
					Arrays.stream(tags.split(","))
					.map(tag -> tag.trim().toLowerCase(Locale.ENGLISH))
					.collect(Collectors.toSet()),
					null));

		String url = urlFilter.getText().trim();
		if (!url.isEmpty())
			filters.add(ReleaseFilters.urlContains(url));

		filters.add(ReleaseFilters.byDownloadType(selectedDownloadTypes));

		filters.add(ReleaseFilters.byPublishDate(publishDateFilterFrom.getValue(), publishDateFilterTo.getValue()));
		filters.add(ReleaseFilters.byReleaseDate(releaseDateFilterFrom.getValue(), releaseDateFilterTo.getValue()));

		return filters.stream().reduce(ReleaseFilters.any(), (result, filter) -> result.and(filter));
	}


	/**
	 * Prepares the filter and applies it to current list of releases
	 * in this table view.
	 */
	@FXML
	private void applyFilter() {
		filteredItems.setPredicate(prepareFilter());
	}


	/**
	 * Resets all filter fields to their default values and updates
	 * release table view to display all releases.
	 */
	@FXML
	private void resetFilter() {
		artistFilter.clear();
		titleFilter.clear();
		tagsFilter.clear();
		urlFilter.clear();
		dlTypeFree.setSelected(true);
		dlTypeNameYourPrice.setSelected(true);
		dlTypePaid.setSelected(true);
		dlTypeUnavailable.setSelected(true);
		publishDateFilterFrom.setValue(null);
		publishDateFilterTo.setValue(null);
		releaseDateFilterFrom.setValue(null);
		releaseDateFilterTo.setValue(null);

		filteredItems.setPredicate(ReleaseFilters.any());
	}


	/**
	 * Handler for key press on filter fields, applies filter if Enter was
	 * pressed.
	 */
	@FXML
	private void onFilterKeyPress(KeyEvent event) {
		if (event.getCode() == KeyCode.ENTER)
			applyFilter();
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
	 * If double-clicked, opens selected release in player.
	 */
	@FXML
	private void onReleaseTableMouseClick(MouseEvent event) {
		if (event.getClickCount() > 1)
			playSelectedRelease();
	}


	/**
	 * Opens a player window to play currently selected release.
	 */
	private void playSelectedRelease() {
		Release release = getSelectedRelease();
		if (release != null)
			Platform.runLater(() -> releasePlayer.setRelease(release));
	}


	/**
	 * Returns a release currently selected in a table.
	 * If there's no selected release, returns null.
	 */
	private Release getSelectedRelease() {
		return releaseTableView.getSelectionModel().getSelectedItem();
	}


	/**
	 * Shows and brings to front a release player window if it was hidden or iconified.
	 */
	@FXML
	private void showPlayer() {
		releasePlayer.show();
	}


	/**
	 * Initialization method invoked by FXML loader, provides initial setup for components.
	 */
	@FXML
	private void initialize() {
		checkComponents();

		// Mapping dl-type checkboxes to corresponding enum constants and adding
		// listeners, so whenever their values change, selectedDownloadTypes is updated automatically
		Map<Release.DownloadType, CheckBox> dlTypeCheckboxes = new EnumMap<>(Release.DownloadType.class);
		dlTypeCheckboxes.put(Release.DownloadType.FREE, dlTypeFree);
		dlTypeCheckboxes.put(Release.DownloadType.NAME_YOUR_PRICE, dlTypeNameYourPrice);
		dlTypeCheckboxes.put(Release.DownloadType.PAID, dlTypePaid);
		dlTypeCheckboxes.put(Release.DownloadType.UNAVAILABLE, dlTypeUnavailable);
		dlTypeCheckboxes.entrySet().forEach(entry -> {
			Release.DownloadType dlType = entry.getKey();
			CheckBox checkbox = entry.getValue();
			if (!checkbox.isSelected())
				selectedDownloadTypes.remove(dlType);
			checkbox.selectedProperty().addListener((observable, oldValue, newValue) -> {
				if (newValue)
					selectedDownloadTypes.add(dlType);
				else
					selectedDownloadTypes.remove(dlType);
			});
		});

		// Ensure that filter is applied when Enter is pressed on datepicker fields.
		// Regular approach with FXML handlers won't work cause datepicker consumes
		// key events internally.
		publishDateFilterFrom.addEventFilter(KeyEvent.KEY_PRESSED, this::onFilterKeyPress);
		releaseDateFilterFrom.addEventFilter(KeyEvent.KEY_PRESSED, this::onFilterKeyPress);
		publishDateFilterTo.addEventFilter(KeyEvent.KEY_PRESSED, this::onFilterKeyPress);
		releaseDateFilterTo.addEventFilter(KeyEvent.KEY_PRESSED, this::onFilterKeyPress);

		filteredStatusLabel.textProperty().bind(
				Bindings.createStringBinding(
						() -> String.format(
								"Displaying: %1$s of %2$s", filteredItems.size(), items.size()), filteredItems));
		
		releaseTableView.setItems(sortedItems);
		sortedItems.comparatorProperty().bind(releaseTableView.comparatorProperty());

		// Custom cell factories for text and date columns (each custom factory also
		// adds a context menu to cells)
		CellFactory<Release, String> tooltip = new CellFactory<>(
				CellCustomizer.tooltip(), cellContextMenu.customizer());
		CellFactory<Release, LocalDate> centered = new CellFactory<>(
				CellCustomizer.alignment(Pos.CENTER), cellContextMenu.customizer());

		artistColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
		artistColumn.setCellFactory(tooltip);
		artistColumn.setCellValueFactory(cellData -> cellData.getValue().artistProperty());

		titleColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
		titleColumn.setCellFactory(tooltip);
		titleColumn.setCellValueFactory(cellData -> cellData.getValue().titleProperty());

		timeColumn.setCellFactory(new CellFactory<>(
				CellCustomizer.alignment(Pos.CENTER_RIGHT), cellContextMenu.customizer()));
		timeColumn.setCellValueFactory(cellData -> cellData.getValue().timeProperty());

		dlTypeColumn.setCellFactory(new CellFactory<>(cellContextMenu.customizer()));
		dlTypeColumn.setCellValueFactory(cellData -> cellData.getValue().downloadTypeProperty());

		releaseDateColumn.setCellFactory(centered);
		releaseDateColumn.setCellValueFactory(cellData -> {
			// display empty cell instead of LocalDate.MIN
			Release release =  cellData.getValue();
			return release.getReleaseDate().equals(LocalDate.MIN) ? null : release.releaseDateProperty();
		});

		publishDateColumn.setCellFactory(centered);
		publishDateColumn.setCellValueFactory(cellData -> cellData.getValue().publishDateProperty());

		tagsColumn.setCellFactory(tooltip);
		tagsColumn.setCellValueFactory(cellData -> cellData.getValue().tagsStringProperty());

		// For each cell in a release URL column we create a hyperlink which can 
		// be used to open a release page on Bandcamp using the default browser.
		urlColumn.setCellFactory(new CellFactory<>(
				CellCustomizer.cellNode(uri -> {
					if (uri != null) {
						Hyperlink link = new Hyperlink(uri.toString());
						link.setOnAction(event -> Utils.browse(uri));
						link.setStyle("-fx-text-fill: blue;");
						return link;
					}
					else
						return null;
				}),
				cellContextMenu.customizer()
			)
		);
		urlColumn.setCellValueFactory(cellData -> cellData.getValue().uriProperty());

		// Setting a callback to display an information about selected
		// release in text area.
		releaseTableView.getSelectionModel().selectedItemProperty()
		.addListener((observable, oldRelease, newRelease) -> {
			if (newRelease != null) {
				StringBuilder sb = new StringBuilder();
				if (!newRelease.getInformation().isEmpty())
					sb.append(newRelease.getInformation()).append('\n').append('\n');
				if (!newRelease.getCredits().isEmpty())
					sb.append(newRelease.getCredits()).append('\n').append('\n');
				if (!newRelease.getTracks().isEmpty()) {
					sb.append("Tracklist:\n");
					newRelease.getTracks().forEach(track -> sb.append(track).append('\n'));
				}
				releaseInfo.setText(sb.append('\n').toString());
			}
			else
				releaseInfo.clear();

		});

		releaseTableView.setPlaceholder(new Label());

		resizeHelper = new TableViewResizeHelper(releaseTableView);
		resizeHelper.enable();
	}


	/**
	 * Helper method to check if all components defined by FXML file have been injected.
	 */
	private void checkComponents() {
		// copy-pasted from SceneBuilder's "sample controller skeleton" window
		assert publishDateFilterFrom != null : "fx:id=\"publishDateFilterFrom\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert resetFilter != null : "fx:id=\"resetFilter\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert filteredStatusLabel != null : "fx:id=\"filterStatusText\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert publishDateFilterTo != null : "fx:id=\"publishDateFilterTo\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert tagsColumn != null : "fx:id=\"tagsColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert dlTypeColumn != null : "fx:id=\"dlTypeColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert tagsFilter != null : "fx:id=\"tagsFilter\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert dlTypePaid != null : "fx:id=\"dlTypePaid\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert urlFilter != null : "fx:id=\"urlFilter\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert releaseDateFilterTo != null : "fx:id=\"releaseDateFilterTo\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert artistColumn != null : "fx:id=\"artistColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert dlTypeNameYourPrice != null : "fx:id=\"dlTypeNameYourPrice\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert publishDateColumn != null : "fx:id=\"publishDateColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert releaseInfo != null : "fx:id=\"releaseInfo\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert showPlayer != null : "fx:id=\"showPlayer\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert releaseDateFilterFrom != null : "fx:id=\"releaseDateFilterFrom\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert releaseDateColumn != null : "fx:id=\"releaseDateColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert dlTypeUnavailable != null : "fx:id=\"dlTypeUnavailable\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert timeColumn != null : "fx:id=\"timeColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert titleFilter != null : "fx:id=\"titleFilter\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert titleColumn != null : "fx:id=\"titleColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert urlColumn != null : "fx:id=\"urlColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert releaseTableView != null : "fx:id=\"releaseTableView\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert artistFilter != null : "fx:id=\"artistFilter\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert dlTypeFree != null : "fx:id=\"dlTypeFree\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert applyFilter != null : "fx:id=\"applyFilter\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
	}

}
