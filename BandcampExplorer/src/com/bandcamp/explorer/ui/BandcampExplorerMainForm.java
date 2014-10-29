package com.bandcamp.explorer.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;

import com.bandcamp.explorer.data.SearchParams;
import com.bandcamp.explorer.data.SearchResult;
import com.bandcamp.explorer.data.SearchTask;
import com.bandcamp.explorer.data.SearchType;
import com.bandcamp.explorer.util.ExceptionUnchecker;

/**
 * Controller class for application's main form.
 */
public class BandcampExplorerMainForm extends BorderPane {

	@FXML private ComboBox<SearchType> searchType;
	@FXML private ComboBox<Integer> page;
	@FXML private TextField searchQuery;
	@FXML private CheckBox showCombinedResults;
	@FXML private Text statusText;
	@FXML private Button findReleases;
	@FXML private Button cancelSearch;
	@FXML private Button clearSelected;
	@FXML private Button clearAll;
	@FXML private ProgressBar progressBar;

	/**
	 * A component for displaying search results
	 */
	private ResultsView resultsView;

	/**
	 * An executor service to be passed to created search tasks
	 */
	private ExecutorService searchExecutor;

	/**
	 * A reference to currently running search task
	 */
	private SearchTask runningTask; // doesn't need to be volatile since accessed only from JavaFX thread



	private BandcampExplorerMainForm() {}


	/**
	 * Loads a main form component.
	 */
	public static BandcampExplorerMainForm load() {
		return Utils.loadFXMLComponent(
				BandcampExplorerMainForm.class.getResource("BandcampExplorerMainForm.fxml"),
				BandcampExplorerMainForm::new);
	}


	/**
	 * Sets a ResultsView component to use with this form.
	 * 
	 * @param resultsView a results view
	 * @throws NullPointerException if resultsView is null
	 */
	public void setResultsView(ResultsView resultsView) {
		setCenter(this.resultsView = Objects.requireNonNull(resultsView));
	}


	/**
	 * Sets an executor service to be used by search tasks for performing
	 * asynchronous loading operations.
	 * 
	 * @param searchExecutor an executor service
	 * @throws NullPointerException if searchExecutor is null
	 */
	public void setSearchExecutor(ExecutorService searchExecutor) {
		this.searchExecutor = Objects.requireNonNull(searchExecutor);
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
			resultsView.addTab();

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

		String query = searchQuery.getCharacters().toString().trim();
		if (query.isEmpty())
			return;

		SearchTask task = new SearchTask(
				new SearchParams.Builder(query, searchType.getValue()).pages(page.getValue()).build(),
				searchExecutor);

		statusText.textProperty().bind(task.messageProperty());
		task.setRequestingDataMessage("Requesting data...");
		task.setLoadingReleasesMessage("Loading releases...");

		task.setOnRunning(event -> {
			runningTask = task;
			disableControlsOnSearch(true);
			initProgressBar(task.progressProperty());
		});

		task.setOnSucceeded(event -> {
			runningTask = null;

			SearchResult result = ExceptionUnchecker.uncheck(() -> task.get());
			resultsView.setSearchResult(result);

			writeStatusBar(new StringBuilder(Integer.toString(result.size()))
			.append(" releases found during last search. Search time: ")
			.append(Duration.between(task.getStartTime(), Instant.now()).getSeconds())
			.append('s').toString());

			disableControlsOnSearch(false);
		});

		task.setOnCancelled(event -> {
			runningTask = null;
			writeStatusBar("Search cancelled");
			disableControlsOnSearch(false);
			resetProgressBar();
		});

		task.setOnFailed(event -> {
			runningTask = null;

			Throwable ex = task.getException();
			if (ex instanceof ExecutionException)
				ex = ex.getCause();
			if (ex != null) {
				writeStatusBar("Error: " + ex);
				ex.printStackTrace();
			}
			else
				writeStatusBar("Unknown error");

			disableControlsOnSearch(false);
			resetProgressBar();
		});

		new Thread(task).start();
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
		cancelSearch.setDisable(!disable);
		clearSelected.setDisable(disable);
		clearAll.setDisable(disable);

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
	 * Initializes progress bar binding its progress property to a specified
	 * observable.
	 * 
	 * @param observable observable that progress bar should be bound to
	 */
	private void initProgressBar(ObservableValue<? extends Number> observable) {
		resetProgressBar();
		progressBar.progressProperty().bind(observable);
	}


	/**
	 * Resets progress bar unbinding its progress property and setting
	 * progress value to 0.
	 */
	private void resetProgressBar() {
		progressBar.progressProperty().unbind();
		progressBar.setProgress(0);
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
		assert cancelSearch != null : "fx:id=\"cancelSearch\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert progressBar != null : "fx:id=\"progressBar\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert statusText != null : "fx:id=\"statusText\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert searchQuery != null : "fx:id=\"searchQuery\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert findReleases != null : "fx:id=\"findReleases\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert page != null : "fx:id=\"page\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert clearSelected != null : "fx:id=\"clearSelected\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert clearAll != null : "fx:id=\"clearAll\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
		assert showCombinedResults != null : "fx:id=\"showCombinedResults\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
	}
}
