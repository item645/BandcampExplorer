package com.bandcamp.explorer.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import com.bandcamp.explorer.data.SearchParams;
import com.bandcamp.explorer.data.SearchResult;
import com.bandcamp.explorer.data.SearchTask;
import com.bandcamp.explorer.data.SearchType;
import com.bandcamp.explorer.util.ExceptionUnchecker;


/**
 * Controller class for application's main form.
 */
public class BandcampExplorerMainForm extends BorderPane {

	private static final Logger LOGGER = Logger.getLogger(BandcampExplorerMainForm.class.getName());

	private static BandcampExplorerMainForm INSTANCE;

	@FXML private ComboBox<SearchType> searchType;
	@FXML private ComboBox<Integer> page;
	@FXML private TextField searchQuery;
	@FXML private CheckBox showCombinedResults;
	@FXML private Text statusText;
	@FXML private Button findReleases;
	@FXML private Button clearSelected;
	@FXML private Button clearAll;

	private final ResultsView resultsView;
	private final ReleasePlayerForm releasePlayer;
	private final EventLog eventLog;
	private final ProgressBarDialog progressBar;
	private ExecutorService executorService;

	/**
	 * Holds a reference to currently running search task.
	 */
	private SearchTask runningTask; // doesn't need to be volatile since accessed only from JavaFX thread.


	/**
	 * Creates an instance of main form.
	 */
	private BandcampExplorerMainForm(Stage primaryStage, ReleasePlayerForm releasePlayer, EventLog eventLog) {
		this.releasePlayer = releasePlayer;
		this.eventLog = eventLog;
		this.progressBar = ProgressBarDialog.create(primaryStage);

		setCenter(this.resultsView = ResultsView.create(this, releasePlayer));

		KeyCombination CTRL_E   = new KeyCodeCombination(KeyCode.E,   KeyCombination.SHORTCUT_DOWN);
		KeyCombination CTRL_P   = new KeyCodeCombination(KeyCode.P,   KeyCombination.SHORTCUT_DOWN);
		KeyCombination CTRL_T   = new KeyCodeCombination(KeyCode.T,   KeyCombination.SHORTCUT_DOWN);
		KeyCombination CTRL_W   = new KeyCodeCombination(KeyCode.W,   KeyCombination.SHORTCUT_DOWN);
		KeyCombination CTRL_TAB = new KeyCodeCombination(KeyCode.TAB, KeyCombination.SHORTCUT_DOWN);

		// top level filter for hotkeys
		EventHandler<KeyEvent> hotkeyFilter = event -> {
			if (CTRL_E.match(event)) {
				this.eventLog.show();
				event.consume();
			}
			else if (CTRL_P.match(event)) {
				this.releasePlayer.show();
				event.consume();
			}
			else if (CTRL_T.match(event)) {
				this.resultsView.addTab();
				searchQuery.requestFocus();
				event.consume();
			}
			else if (CTRL_W.match(event)) {
				this.resultsView.closeSelectedTab();
				event.consume();
			}
			else if (CTRL_TAB.match(event)) {
				this.resultsView.switchTab();
				event.consume();
			}
		};
		this.addEventFilter(KeyEvent.KEY_PRESSED, hotkeyFilter);
		progressBar.addEventFilter(KeyEvent.KEY_PRESSED, hotkeyFilter);
	}


	/**
	 * Creates an instance of main form.
	 * 
	 * @param primaryStage reference to app's primary stage
	 * @param releasePlayer reference to a release player
	 * @param eventLog reference to event log
	 * 
	 * @throws NullPointerException if any of passed parameters is null
	 * @throws IllegalStateException if this method has been called more than once
	 */
	public static BandcampExplorerMainForm create(
			Stage primaryStage,
			ReleasePlayerForm releasePlayer,
			EventLog eventLog) {
		if (!Platform.isFxApplicationThread())
			throw new IllegalStateException("This component can be created only from Java FX Application Thread");
		if (INSTANCE != null)
			throw new IllegalStateException("This component can't be instantiated more than once");
		Objects.requireNonNull(primaryStage);
		Objects.requireNonNull(releasePlayer);
		Objects.requireNonNull(eventLog);

		return (INSTANCE = Utils.loadFXMLComponent(
				BandcampExplorerMainForm.class.getResource("BandcampExplorerMainForm.fxml"),
				() -> new BandcampExplorerMainForm(primaryStage, releasePlayer, eventLog)));
	}


	/**
	 * Sets an executor service to be used for performing various
	 * asynchronous operations.
	 * 
	 * @param executorService an executor service
	 * @throws NullPointerException if executorService is null
	 */
	public void setExecutorService(ExecutorService executorService) {
		this.executorService = Objects.requireNonNull(executorService);
		progressBar.setExecutor(executorService);
	}


	/**
	 * Puts specified query and search type as values into appropriate form fields and
	 * runs a search using these values. After search is finished, passes the result
	 * to a release view for display.
	 * 
	 * @param query search query
	 * @param type search type
	 * @param newResultTab if true, new tab will be opened in a results view component
	 *        to hold the result of this search, otherwise currently selected tab will
	 *        be used
	 * @throws NullPointerException if search query or search type is null
	 * @throws IllegalStateException if there's search task running now
	 */
	void searchReleases(String query, SearchType type, boolean newResultTab) {
		checkForRunningTask();

		searchQuery.setText(Objects.requireNonNull(query));
		searchType.setValue(Objects.requireNonNull(type));
		if (newResultTab)
			Platform.runLater(() -> resultsView.addTab());

		searchReleases();
	}


	/**
	 * Runs a search using parameters provided by fields. After search is finished,
	 * passes the result to a release view for display.
	 * 
	 * @throws IllegalStateException if there's search task running now
	 */
	@FXML
	private void searchReleases() {
		checkForRunningTask();

		String query = searchQuery.getText().trim();
		if (query.isEmpty())
			return;

		SearchParams params = new SearchParams.Builder(
				query, searchType.getValue()).pages(page.getValue()).build();

		SearchTask task = new SearchTask(
				params,
				executorService,
				"Requesting data...",
				// this one is invoked for every release so we don't want to use heavyweight String.format() here
				(processed, total) -> new StringBuilder("Loading releases... (")
				                     .append(processed)
				                     .append('/')
				                     .append(total)
				                     .append(')')
				                     .toString()
				);

		progressBar.initStatusText(task.messageProperty());
		progressBar.setOnCancel(this::cancelSearch);

		task.setOnRunning(event -> {
			runningTask = task;
			disableControlsOnSearch(true);
			progressBar.initProgress(task.progressProperty());
			progressBar.show();

			String report = String.format(" search: %1$s: %2$s (%3$d %4$s)",
					params.searchType(),
					params.searchQuery(),
					params.pages(),
					params.pages() > 1 ? "pages" : "page");
			writeStatusBar("Running" + report);
			LOGGER.info("Starting" + report);
		});
		task.setOnSucceeded(event -> {
			runningTask = null;

			disableControlsOnSearch(false);
			progressBar.hideAndReset(500);
			
			SearchResult result = ExceptionUnchecker.uncheck(() -> task.get());
			resultsView.setSearchResult(result);

			int loaded = result.size();
			int failed = result.getFailedCount();
			int total = loaded + failed;
			String report = String.format("Search finished: %1$d %2$s found%3$s. Search time: %4$ds.",
					total,
					total == 1 ? "release" : "releases",
					failed > 0 ? String.format(", %1$d failed to load", failed) : "",
					Duration.between(task.getStartTime(), Instant.now()).getSeconds());
			writeStatusBar(report);
			LOGGER.info(report);
		});
		task.setOnCancelled(event -> {
			runningTask = null;

			String msg = "Search cancelled";
			writeStatusBar(msg);
			LOGGER.info(msg);

			disableControlsOnSearch(false);
			progressBar.hideAndReset(0);
		});
		task.setOnFailed(event -> {
			runningTask = null;

			Throwable ex = task.getException();
			if (ex instanceof ExecutionException)
				ex = ex.getCause();
			if (ex != null) {
				writeStatusBar("Search failed: " + ex);
				LOGGER.log(Level.WARNING, "Search failed: " + ex.getMessage(), ex);
			}
			else {
				String errMsg = "Search failed: Unknown error";
				writeStatusBar(errMsg);
				LOGGER.warning(errMsg);
			}

			disableControlsOnSearch(false);
			progressBar.hideAndReset(0);
		});

		executorService.execute(task);
	}


	/**
	 * Helper to check whether the search task is running at the moment.
	 * Throws IllegalStateException if it is.
	 */
	private void checkForRunningTask() {
		if (runningTask != null)
			throw new IllegalStateException("Can't start a new search when there is a search task running");
	}


	/**
	 * Handler for key press on search query field, runs a search 
	 * if Enter was pressed.
	 */
	@FXML 
	private void onSearchQueryKeyPress(KeyEvent event) {
		if (event.getCode() == KeyCode.ENTER)
			searchReleases();
	}


	/**
	 * Cancels currently running search task.
	 */
	@FXML
	private void cancelSearch() {
		if (runningTask != null)
			runningTask.cancel(false);
	}


	/**
	 * Helper to disable/enable some controls when search is running.
	 */
	private void disableControlsOnSearch(boolean disable) {
		findReleases.setDisable(disable);
		clearSelected.setDisable(disable);
		clearAll.setDisable(disable);
		showCombinedResults.setDisable(disable);

		resultsView.disableTabs(disable);
	}


	/**
	 * Writes text into a status bar.
	 * 
	 * @param text text to write
	 */
	private void writeStatusBar(String text) {
		statusText.textProperty().unbind();
		statusText.setText(text);
	}


	/**
	 * Clears search result from currently selected tab in a results view.
	 */
	@FXML
	private void clearSelectedResult() {
		resultsView.clearSelectedResult();
		writeStatusBar("");
	}


	/**
	 * Clears all search results from results view.
	 */
	@FXML
	private void clearAllResults() {
		resultsView.clearAllResults();
		writeStatusBar("");
	}


	/**
	 * Handler invoked when search type combobox changes its value.
	 * Disables pages combobox if search type set to DIRECT.
	 */
	@FXML
	private void onSearchTypeChange() {
		if (searchType.getValue() == SearchType.DIRECT) {
			page.setValue(1);
			page.setDisable(true);
		}
		else
			page.setDisable(false);
	}


	/**
	 * Shows/hides the combined results tab in results view component
	 * depending on current state of showCombinedResults checkbox.
	 */
	@FXML
	private void onShowCombinedResultsChange() {
		resultsView.showCombinedResults(showCombinedResults.isSelected());
	}


	/**
	 * Initialization method invoked by FXML loader, provides initial setup for components.
	 */
	@FXML
	private void initialize() {  	
		checkComponents();

		searchType.setItems(FXCollections.observableArrayList(SearchType.values()));
		searchType.setValue(SearchType.SEARCH);

		ObservableList<Integer> pageNums = FXCollections.observableArrayList();
		for (int i = 1; i <= 10; i++)
			pageNums.add(i);
		page.setItems(pageNums);
		page.setValue(1);
	}


	/**
	 * Helper method to check if all components defined by FXML file have been injected.
	 */
	private void checkComponents() {
		// copy-pasted from SceneBuilder's "sample controller skeleton" window
		assert searchType != null : "fx:id=\"searchType\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert statusText != null : "fx:id=\"statusText\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert searchQuery != null : "fx:id=\"searchQuery\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert findReleases != null : "fx:id=\"findReleases\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert page != null : "fx:id=\"page\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert clearSelected != null : "fx:id=\"clearSelected\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert clearAll != null : "fx:id=\"clearAll\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert showCombinedResults != null : "fx:id=\"showCombinedResults\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
	}

}
