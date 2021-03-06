package com.bandcamp.explorer.ui;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
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
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import com.bandcamp.explorer.data.Price;
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

	private final Stage primaryStage;
	@FXML private TextField artistFilter;
	@FXML private TextField titleFilter;
	@FXML private TextField tagsFilter;
	@FXML private TextField urlFilter;
	@FXML private TextField minPriceFilter;
	@FXML private TextField maxPriceFilter;
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
	@FXML private TableColumn<Release, Price> priceColumn;
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
	private static final ByteArrayInputStream fxmlStream = ExceptionUnchecker.uncheck(() -> {
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
	private final ObservableList<Release> itemsDecorator = new ItemsDecorator();

	private final ReleaseTableContextMenu releaseTableContextMenu = new ReleaseTableContextMenu();
	private TableViewResizeHelper resizeHelper;


	/**
	 * Special-purpose decorator for items list that features modified clear()
	 * method to deal with the problem of stale Release references in a SortedList.
	 * Reference to this decorator is handed to class clients so they can transparently
	 * call modified version of clear().
	 */
	private class ItemsDecorator extends ModifiableObservableListBase<Release> {

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
	}


	/**
	 * This class provides a context menu for cells in a release table.
	 */
	private class ReleaseTableContextMenu extends CellContextMenu {

		/**
		 * Creates a context menu instance.
		 */
		ReleaseTableContextMenu() {
			LabeledMenuItem searchArtist = new LabeledMenuItem(true);
			LabeledMenuItem moreFromDomain = new LabeledMenuItem(true);

			LabeledMenuItem viewOnBandcamp = new LabeledMenuItem("View on Bandcamp");			
			viewOnBandcamp.setOnAction(event -> ifReleaseSelected(
					release -> Utils.browse(release.uri())));

			LabeledMenuItem viewDiscogOnBandcamp = new LabeledMenuItem("View Discography on Bandcamp");
			viewDiscogOnBandcamp.setOnAction(event -> ifReleaseSelected(
					release -> Utils.browse(release.discographyURI())));

			LabeledMenuItem copyText = new LabeledMenuItem("Copy Text");
			copyText.setOnAction(event -> {
				TableCell<?,?> cell = selectedCell();
				if (cell != null)
					Utils.toClipboardAsString(cell.getItem());
			});

			LabeledMenuItem copyReleaseText = new LabeledMenuItem("Copy Release as Text");
			copyReleaseText.setOnAction(event -> Utils.toClipboardAsString(getSelectedRelease()));

			LabeledMenuItem copyAllReleasesText = new LabeledMenuItem("Copy All Releases as Text");
			// NOTE: we don't use System.lineSeparator() as a delimiter
			// due to a possible bug in JavaFX clipboard implementation.
			// More details: http://stackoverflow.com/questions/18827217/javafx-clipboard-double-newlines
			// (same goes for copyAllURLs)
			copyAllReleasesText.setOnAction(event -> Utils.toClipboardAsString(sortedItems, Release::toString, "\n"));

			LabeledMenuItem copyDiscographyURL = new LabeledMenuItem("Copy Discography URL");
			copyDiscographyURL.setOnAction(event -> ifReleaseSelected(
					release -> Utils.toClipboardAsString(release.discographyURI())));

			LabeledMenuItem copyAllURLs = new LabeledMenuItem("Copy All URLs");
			copyAllURLs.setOnAction(event -> Utils.toClipboardAsString(
					sortedItems, release -> release.uri().toString(), "\n"));

			LabeledMenuItem playRelease = new LabeledMenuItem("Play Release...");
			playRelease.setOnAction(event -> playSelectedRelease());

			ObservableList<MenuItem> menuItems = getItems();

			setOnShowing(windowEvent -> {
				// On menu popup we update "Search..." and "More from..." items text and action
				// using data from selected release
				Release release = getSelectedRelease();
				if (release != null) {
					String artist = release.artist();
					searchArtist.setLabelText(String.format("Search \"%s\"", artist));
					searchArtist.setOnAction(
							actionEvent -> mainForm.searchReleases(artist, SearchType.SEARCH));
					
					URI discographyURI = release.discographyURI();
					moreFromDomain.setLabelText(String.format("More from \"%s\"", discographyURI.getAuthority()));
					moreFromDomain.setOnAction(
							actionEvent -> mainForm.searchReleases(discographyURI.toString(), SearchType.DIRECT));
				}
				else {
					searchArtist.setLabelText(null);
					searchArtist.setOnAction(null);
					moreFromDomain.setLabelText(null);
					moreFromDomain.setOnAction(null);
				}

				// Re-add changed items to let the menu correctly resize itself
				menuItems.set(0, searchArtist);
				menuItems.set(1, moreFromDomain);
			});

			menuItems.addAll(searchArtist, moreFromDomain, new SeparatorMenuItem(), viewOnBandcamp,
					viewDiscogOnBandcamp, new SeparatorMenuItem(), copyText, copyReleaseText,
					copyAllReleasesText, copyDiscographyURL, copyAllURLs, new SeparatorMenuItem(), playRelease);
		}

	}


	/**
	 * Creates an instance of release table view component.
	 * 
	 * @param primaryStage reference to app's primary stage
	 * @param mainForm reference to app's main form
	 * @param releasePlayer reference to a release player
	 */
	private ReleaseTableView(Stage primaryStage, BandcampExplorerMainForm mainForm, ReleasePlayerForm releasePlayer) {
		this.primaryStage = primaryStage;
		this.mainForm = mainForm;
		this.releasePlayer = releasePlayer;
	}


	/**
	 * Creates an instance of release table view component.
	 * 
	 * @param primaryStage reference to app's primary stage
	 * @param mainForm reference to app's main form
	 * @param releasePlayer reference to a release player
	 */
	static ReleaseTableView create(Stage primaryStage, BandcampExplorerMainForm mainForm, 
			ReleasePlayerForm releasePlayer) {
		assert primaryStage != null;
		assert mainForm != null;
		assert releasePlayer != null;
		fxmlStream.reset();
		return Utils.loadFXMLComponent(fxmlStream, () -> new ReleaseTableView(primaryStage, mainForm, releasePlayer));
	}


	/**
	 * Returns an observable list of releases in this table view. 
	 */
	ObservableList<Release> releases() {
		return itemsDecorator;
	}


	/**
	 * Prepares a combined filter using conditions provided by filter fields,
	 * composed by logical AND.
	 * 
	 * @return an Optional containing combined filter; empty Optional if filter can't be
	 *         prepared because some filter fields contain illegal values
	 */
	private Optional<Predicate<Release>> prepareFilter() {
		List<Predicate<Release>> filters = new ArrayList<>();

		Price minPrice, maxPrice;
		try {
			minPrice = readFilterPrice(minPriceFilter, RoundingMode.UP, "Invalid min price");
			maxPrice = readFilterPrice(maxPriceFilter, RoundingMode.DOWN, "Invalid max price");
		}
		catch (IllegalArgumentException e) {
			// Exception already handled in readFilterPrice()
			return Optional.empty();
		}
		if (minPrice != null || maxPrice != null)
			filters.add(ReleaseFilters.byPrice(minPrice, maxPrice));

		String artist = artistFilter.getText().trim();
		if (!artist.isEmpty())
			filters.add(ReleaseFilters.artistContains(artist));

		String title = titleFilter.getText().trim();
		if (!title.isEmpty())
			filters.add(ReleaseFilters.titleContains(title));

		String tags = tagsFilter.getText().trim();
		if (!tags.isEmpty()) {
			filters.add(ReleaseFilters.byTags(Arrays.stream(tags.split(","))
					.map(tag -> tag.trim().toLowerCase(Locale.ENGLISH))
					.filter(tag -> !tag.isEmpty())
					.collect(Collectors.toSet())));
		}

		String url = urlFilter.getText().trim();
		if (!url.isEmpty())
			filters.add(ReleaseFilters.uriContains(url));

		filters.add(ReleaseFilters.byDownloadType(selectedDownloadTypes));

		filters.add(ReleaseFilters.byPublishDate(publishDateFilterFrom.getValue(), publishDateFilterTo.getValue()));
		filters.add(ReleaseFilters.byReleaseDate(releaseDateFilterFrom.getValue(), releaseDateFilterTo.getValue()));

		return Optional.of(filters.stream().reduce(ReleaseFilters.any(), (result, filter) -> result.and(filter)));
	}


	/**
	 * Reads the value of price filter from corresponding text field and
	 * coverts it into an instance of Price.
	 * This methods handles IllegalArgumentException thrown when string value of
	 * price cannot be converted by displaying appropriate message dialog and
	 * selecting field text. Handled exception then gets rethrown from the method.
	 * 
	 * @param field a text field containing the value of price filter
	 * @param roundingMode rounding mode for value scaling
	 * @param invalidPriceTitle a title for error message dialog when filter value is invalid
	 * @return a Price instance; null, if value was not specified
	 * @throws IllegalArgumentException if attempt to parse filter value was unsuccessful
	 */
	private Price readFilterPrice(TextField field, RoundingMode roundingMode, String invalidPriceTitle) {
		String priceText = field.getText().trim();
		try {
			return !priceText.isEmpty() ? Price.parse(priceText, roundingMode) : null;
		}
		catch (IllegalArgumentException e) {
			String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
			Dialogs.messageBox(message, invalidPriceTitle, primaryStage);
			field.requestFocus();
			field.selectAll();
			throw e;
		}
	}


	/**
	 * Prepares the filter and applies it to current list of releases
	 * in this table view.
	 */
	@FXML
	private void applyFilter() {
		prepareFilter().ifPresent(filteredItems::setPredicate);
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
		minPriceFilter.clear();
		maxPriceFilter.clear();
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
		ifReleaseSelected(release -> Platform.runLater(() -> releasePlayer.setRelease(release)));
	}


	/**
	 * Returns a release currently selected in a table.
	 * If there's no selected release, returns null.
	 */
	private Release getSelectedRelease() {
		return releaseTableView.getSelectionModel().getSelectedItem();
	}


	/**
	 * If release table view has selected release, peformes the specified action
	 * on this release, otherwise does nothing.
	 * 
	 * @param action a consumer action, accepting the selected release
	 */
	private void ifReleaseSelected(Consumer<Release> action) {
		Release release = getSelectedRelease();
		if (release != null)
			action.accept(release);
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
								"Displaying: %1$s of %2$s", filteredItems.size(), items.size()),
								filteredItems, items));
		
		releaseTableView.setItems(sortedItems);
		sortedItems.comparatorProperty().bind(releaseTableView.comparatorProperty());

		// Custom cell factories for text and date columns (each custom factory also
		// adds a context menu to cells)
		CellFactory<Release, String> tooltip = new CellFactory<>(
				CellCustomizer.tooltip(), releaseTableContextMenu.customizer());
		CellFactory<Release, LocalDate> centeredDate = new CellFactory<>(
				CellCustomizer.alignment(Pos.CENTER), releaseTableContextMenu.customizer());

		artistColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
		artistColumn.setCellFactory(tooltip);
		artistColumn.setCellValueFactory(cellData -> cellData.getValue().artistProperty());

		titleColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
		titleColumn.setCellFactory(tooltip);
		titleColumn.setCellValueFactory(cellData -> cellData.getValue().titleProperty());

		timeColumn.setCellFactory(new CellFactory<>(
				CellCustomizer.alignment(Pos.CENTER_RIGHT), releaseTableContextMenu.customizer()));
		timeColumn.setCellValueFactory(cellData -> cellData.getValue().timeProperty());

		dlTypeColumn.setCellFactory(new CellFactory<>(releaseTableContextMenu.customizer()));
		dlTypeColumn.setCellValueFactory(cellData -> cellData.getValue().downloadTypeProperty());

		priceColumn.setCellFactory(new CellFactory<>(
				CellCustomizer.alignment(Pos.CENTER_RIGHT), releaseTableContextMenu.customizer()));
		priceColumn.setCellValueFactory(cellData -> cellData.getValue().priceProperty());

		releaseDateColumn.setCellFactory(centeredDate);
		releaseDateColumn.setCellValueFactory(cellData -> cellData.getValue().releaseDateProperty());

		publishDateColumn.setCellFactory(centeredDate);
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
						return link;
					}
					else
						return null;
				}),
				releaseTableContextMenu.customizer()
			)
		);
		urlColumn.setCellValueFactory(cellData -> cellData.getValue().uriProperty());
		// Ignore protocol on sorting
		urlColumn.setComparator(Comparator.comparing(URI::getSchemeSpecificPart));

		// Setting a callback to display an information about selected
		// release in text area.
		releaseTableView.getSelectionModel().selectedItemProperty()
		.addListener((observable, oldRelease, release) -> {
			if (release != null) {
				StringBuilder sb = new StringBuilder();
				release.parentReleaseLink().ifPresent(
						link -> sb.append("From: ").append(link).append('\n').append('\n'));
				release.information().ifPresent(info -> sb.append(info).append('\n').append('\n'));
				release.credits().ifPresent(credits -> sb.append(credits).append('\n').append('\n'));
				if (!release.tracks().isEmpty()) {
					sb.append("Tracklist:\n");
					release.tracks().forEach(track -> sb.append(track).append('\n'));
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
		assert priceColumn != null : "fx:id=\"priceColumn\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
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
		assert maxPriceFilter != null : "fx:id=\"maxPriceFilter\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
		assert minPriceFilter != null : "fx:id=\"minPriceFilter\" was not injected: check your FXML file 'ReleaseTableView.fxml'.";
	}

}
