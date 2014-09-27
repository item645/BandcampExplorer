package com.bandcamp.explorer.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;

import com.bandcamp.explorer.data.Release;
import com.bandcamp.explorer.data.ReleaseFilters;
import com.bandcamp.explorer.data.ReleaseSortOrder;
import com.bandcamp.explorer.data.SearchEngine;
import com.bandcamp.explorer.data.SearchParams;
import com.bandcamp.explorer.data.SearchTask;
import com.bandcamp.explorer.data.SearchType;

/**
 * Controller class for application's main form.
 */
public class BandcampExplorerMainForm {

	@FXML private BorderPane rootPane;
	@FXML private ComboBox<SearchType> searchType;
    @FXML private ComboBox<Integer> page;
    @FXML private TextField searchQuery;
    @FXML private CheckBox combineSearchResults;
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
    @FXML private Text statusText;
    @FXML private Button findReleases;
    @FXML private Button cancelSearch;
    @FXML private Button clearResults;
    @FXML private Button applyFilter;
    @FXML private Button resetFilter;
    @FXML private Button playSelected;
    @FXML private ProgressBar progressBar;
    
    private final EnumSet<Release.DownloadType> selectedDownloadTypes = EnumSet.allOf(Release.DownloadType.class);
    
    private static final Comparator<Release> DEFAULT_SORT_ORDER = ReleaseSortOrder.PUBLISH_DATE_DESC;
    
    private SearchEngine searchEngine;
    private ReleaseTableView releaseTableView;
    
    
    /**
     * Sets a release table view component to use with this form.
     * 
     * @param tableView a table view
     * @throws NullPointerException if tableView is null
     */
    public void setReleaseTableView(ReleaseTableView tableView) {
    	this.releaseTableView = Objects.requireNonNull(tableView);
    	rootPane.setCenter(tableView.getRoot());
    }
    
    
    /**
     * Sets a search engine to use with this form.
     * 
     * @param searchEngine a search engine
     * @throws NullPointerException if searchEngine is null
     * @throws IllegalStateException if search engine currently used by this form 
     * 		   has a task running now
     */
    public void setSearchEngine(SearchEngine searchEngine) {
    	Objects.requireNonNull(searchEngine);
    	if (this.searchEngine != null && this.searchEngine.isTaskRunning())
    		throw new IllegalStateException("Cannot replace search engine instance while search task is still running");
    	this.searchEngine = searchEngine;
    }
    
    
	/**
	 * Helper method to check if all components defined by FXML file have been injected.
	 */
	private void checkComponents() {
		// copy-pasted from SceneBuilder's "sample controller skeleton" window
		assert publishDateFilterFrom != null : "fx:id=\"publishDateFilterFrom\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert resetFilter != null : "fx:id=\"resetFilter\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert publishDateFilterTo != null : "fx:id=\"publishDateFilterTo\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert clearResults != null : "fx:id=\"clearResults\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert combineSearchResults != null : "fx:id=\"combineSearchResults\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert progressBar != null : "fx:id=\"progressBar\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert searchQuery != null : "fx:id=\"searchQuery\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert tagsFilter != null : "fx:id=\"tagsFilter\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert dlTypePaid != null : "fx:id=\"dlTypePaid\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert urlFilter != null : "fx:id=\"urlFilter\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert releaseDateFilterTo != null : "fx:id=\"releaseDateFilterTo\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert dlTypeNameYourPrice != null : "fx:id=\"dlTypeNameYourPrice\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert searchType != null : "fx:id=\"searchType\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert rootPane != null : "fx:id=\"rootPane\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert releaseDateFilterFrom != null : "fx:id=\"releaseDateFilterFrom\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert dlTypeUnavailable != null : "fx:id=\"dlTypeUnavailable\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert playSelected != null : "fx:id=\"playSelected\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert titleFilter != null : "fx:id=\"titleFilter\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert cancelSearch != null : "fx:id=\"cancelSearch\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert statusText != null : "fx:id=\"statusText\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert findReleases != null : "fx:id=\"findReleases\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert artistFilter != null : "fx:id=\"artistFilter\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert page != null : "fx:id=\"page\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert dlTypeFree != null : "fx:id=\"dlTypeFree\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
        assert applyFilter != null : "fx:id=\"applyFilter\" was not injected: check your FXML file 'BandcampExplorerMainForm.fxml'.";
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
    }
    
    
    /**
     * Prepares a combined filter using conditions provided by filter fields,
     * composed by logical AND.
     * 
     * @return a filter
     */
    private Predicate<Release> prepareFilter() {
    	List<Predicate<Release>> filters = new ArrayList<>();
    	
    	String artist = artistFilter.getCharacters().toString().trim();
    	if (!artist.isEmpty())
    		filters.add(ReleaseFilters.artistContains(artist));
    	
    	String title = titleFilter.getCharacters().toString().trim();
    	if (!title.isEmpty())
    		filters.add(ReleaseFilters.titleContains(title));
    	
    	String tags = tagsFilter.getCharacters().toString().trim();
    	if (!tags.isEmpty()) {
    		List<String> tagsList = Arrays.asList(tags.split(","));
    		tagsList.replaceAll(tag -> tag.trim().toLowerCase(Locale.ENGLISH));
    		filters.add(ReleaseFilters.byTags(tagsList, null));
    	}
    	
    	String url = urlFilter.getCharacters().toString().trim();
    	if (!url.isEmpty())
    		filters.add(ReleaseFilters.urlContains(url));
    	
    	filters.add(ReleaseFilters.byDownloadType(selectedDownloadTypes));
    	
    	filters.add(ReleaseFilters.byPublishDate(publishDateFilterFrom.getValue(), publishDateFilterTo.getValue()));
    	filters.add(ReleaseFilters.byReleaseDate(releaseDateFilterFrom.getValue(), releaseDateFilterTo.getValue()));
    	
    	return filters.stream().reduce(ReleaseFilters.any(), (result, filter) -> result.and(filter));
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
     * Runs a search using parameters provided by fields. After search is finished,
     * filters the results according to values in filter field and updates table view.
     */
    @FXML
    private void searchReleases() {
    	if (searchEngine.isTaskRunning())
    		return;
    	
    	Predicate<Release> filter = prepareFilter();
    	SearchParams params = 
    			new SearchParams.Builder(searchQuery.getCharacters().toString().trim(), searchType.getValue())
    	.pages(page.getValue()).combineResults(combineSearchResults.isSelected()).build();
    	
    	SearchTask task = searchEngine.newTaskFor(params);
    	
    	statusText.textProperty().bind(task.messageProperty());
    	task.setRequestingDataMessage("Requesting data...");
    	task.setLoadingReleasesMessage("Loading releases...");
    	
    	task.setOnRunning(event -> {
    		disableButtonsOnSearch(true);
    		initProgressBar(task.progressProperty());
    	});
    	task.setOnSucceeded(event -> {
    		reportSearchFinish(task);
    		updateTableItems(filter);
    		disableButtonsOnSearch(false);
    	});
    	task.setOnCancelled(event -> {
    		writeStatusBar("Search cancelled");
    		disableButtonsOnSearch(false);
    		resetProgressBar();
    	});
    	task.setOnFailed(event -> {
    		Throwable ex = task.getException();
    		String msg;
    		if (ex != null)
    			msg = ex instanceof ExecutionException ? ex.getCause().toString() : ex.toString();
    		else
    			msg = "";
    		writeStatusBar(msg.isEmpty() ? "Unknown error" : "Error: " + msg);
    		disableButtonsOnSearch(false);
    		resetProgressBar();
    	});
    	
    	new Thread(task).start();
    }
    
    
    /**
     * Cancels currently running search task.
     */
    @FXML
    private void cancelSearch() {
    	searchEngine.cancelTask();
    }
    
    
    /**
     * Helper to disable/enable some buttons when search is running.
     */
    private void disableButtonsOnSearch(boolean disable) {
        findReleases.setDisable(disable);
        cancelSearch.setDisable(!disable);
        clearResults.setDisable(disable);
        applyFilter.setDisable(disable);
        resetFilter.setDisable(disable);
    }
    
    
    /**
     * Writes a summary about finished search into a status bar. 
     * 
     * @param finishedTask a search task in SUCCEEDED state
     */
    private void reportSearchFinish(SearchTask finishedTask) {
    	try {
			StringBuilder report = new StringBuilder(Integer.toString(finishedTask.get().size()))
			.append(" releases found during last search (")
			.append(searchEngine.getResultsCount())
			.append(" releases total). Time: ")
			.append(Duration.between(finishedTask.getStartTime(), Instant.now()).getSeconds())
			.append("s");
			writeStatusBar(report.toString());
		}
    	catch (InterruptedException | ExecutionException whatever) {
			// should not happen 'cause we report only on succeeded task 
			whatever.printStackTrace();
		}
    }
    
    
    /**
     * Writes text into a status bar
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
     * Updates release table view by fetching current results from search
     * engine and filtering them with specified filter.
     * 
     * @param filter a filter
     * @throws NullPointerException is filter is null
     */
    private void updateTableItems(Predicate<Release> filter) {
    	ObservableList<Release> tableItems = releaseTableView.getItems();
    	tableItems.clear();
    	searchEngine.getResults(DEFAULT_SORT_ORDER).forEach(release -> {
    		if (filter.test(release))
    			tableItems.add(release);
    	});
    }
    
    
    /**
     * Clears all search results from search engine and view table.
     */
    @FXML
    private void clearResults() {
    	releaseTableView.getItems().clear();
    	searchEngine.clearResults();
    	writeStatusBar("");
    }
    
    
    /**
     * Opens a player window to play currently selected release.
     */
    @FXML
    private void playSelectedRelease() {
    	releaseTableView.playSelectedRelease();
    }
    
    
    /**
     * Prepares filter using filter fields and applies it to current search,
     * updating release table view correspondingly.
     */
    @FXML
    private void applyFilter() {
    	updateTableItems(prepareFilter());
    }
    
    
    /**
     * Resets all filter field to their default values and updates
     * release table view to display all current search results.
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
    	
    	releaseTableView.getItems().clear();
    	releaseTableView.getItems().addAll(searchEngine.getResults(DEFAULT_SORT_ORDER));
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
    
}
