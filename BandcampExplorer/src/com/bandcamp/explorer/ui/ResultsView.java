package com.bandcamp.explorer.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.control.Label;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;

import com.bandcamp.explorer.data.Release;
import com.bandcamp.explorer.data.ReleaseSortOrder;
import com.bandcamp.explorer.data.SearchResult;

/**
 * Controller class for results view component.
 */
class ResultsView extends AnchorPane {

	private static final Logger LOGGER = Logger.getLogger(ResultsView.class.getName());

	private final BandcampExplorerMainForm mainForm;
	private final ReleasePlayerForm releasePlayer;
	private final TabPane tabPane = new TabPane();
	private final ObservableList<Tab> tabs = tabPane.getTabs();
	private final SingleSelectionModel<Tab> selectionModel = tabPane.getSelectionModel();
	private final IntegerProperty numOfUnclosableTabs = new SimpleIntegerProperty();
	private final CombinedResultsTab combinedResultsTab;
	private int tabIndex;



	/**
	 * Class for a custom tab that displays combined results from all currently
	 * opened tabs, omitting any duplicate releases.
	 */
	private class CombinedResultsTab extends Tab {

		private final ReleaseTableView releaseTable = ReleaseTableView.create(mainForm, releasePlayer);
		private final ObservableList<Release> combinedReleases = releaseTable.releases();
		private final BooleanProperty visible = new SimpleBooleanProperty(false);


		/**
		 * Creates a combined results tab.
		 */
		CombinedResultsTab() {
			setContent(releaseTable);
			setClosable(false);

			Label header = new Label("Combined Results");
			header.setStyle("-fx-font-weight: bold");
			setGraphic(header);

			Tooltip tooltip = new Tooltip();
			tooltip.textProperty().bind(Bindings.createStringBinding(
					() -> combinedReleases.size() + " releases total", combinedReleases));
			setTooltip(tooltip);

			visible.addListener((observable, oldValue, newValue) -> {
				if (!newValue.equals(oldValue)) {
					if (newValue) {
						tabs.add(1, this);
						updateReleases();
					}
					else {
						// Select the "normal" tab next to combined results tab (there's always
						// at least one such tab in a tab pane)
						selectionModel.select(2);
						tabs.remove(1);
						combinedReleases.clear();
					}
				}
			});
		}


		/**
		 * Boolean property that indicated whether this tab is visible.
		 * The tab is visible if it's contained by a list of tabs of the parent
		 * tab pane, thus changing this property will trigger the addition/removal
		 * operation depending on new value. If visible, combined results tab 
		 * always resides on the index of 1 in a list of tabs.
		 */
		BooleanProperty visibleProperty() {
			return visible;
		}


		/**
		 * Updates the combined results table by fetching and combining
		 * results from all opened tabs.
		 * Does nothing if combined results tab is not visible at the moment.
		 */
		void updateReleases() {
			if (visible.get()) {
				Set<Release> unique = new HashSet<>();
				doForEachTab(tab -> unique.addAll(getReleasesOnTab(tab)));
				updateCombinedReleases(unique);
			}
		}


		/**
		 * Removes all releases from release table on this tab.
		 * Does nothing if combined results tab is not visible at the moment.
		 */
		void removeReleases() {
			if (visible.get())
				combinedReleases.clear();
		}


		/**
		 * Updates the combined results table, replacing its items by
		 * a specified collection of releases.
		 * 
		 * @param releases new collection of releases to display in a combined results table
		 * @throws NullPointerException if releases is null
		 */
		private void updateCombinedReleases(Collection<Release> releases) {
			List<Release> list = new ArrayList<>(releases);
			list.sort(ReleaseSortOrder.PUBLISH_DATE_DESC.thenComparing(ReleaseSortOrder.ARTIST_AND_TITLE));
			combinedReleases.clear();
			combinedReleases.addAll(list);
		}

	}



	/**
	 * Creates a results view component and provides its initial setup.
	 */
	private ResultsView(BandcampExplorerMainForm mainForm, ReleasePlayerForm releasePlayer) {
		this.mainForm = mainForm;
		this.releasePlayer = releasePlayer;
		this.combinedResultsTab = new CombinedResultsTab();
		
		AnchorPane.setTopAnchor(tabPane, 0.0);
		AnchorPane.setBottomAnchor(tabPane, 0.0);
		AnchorPane.setLeftAnchor(tabPane, 0.0);
		AnchorPane.setRightAnchor(tabPane, 0.0);
		getChildren().add(tabPane);

		// This is a fake tab which serves as a "button" to add new tabs
		Tab addBtn = new Tab("+");
		addBtn.setClosable(false);
		addBtn.setTooltip(new Tooltip("Open a new tab (Ctrl+T)"));
		tabs.add(addBtn);
		selectionModel.selectedItemProperty().addListener((observablem, oldTab, newTab) -> {
			// If user hits "button" we create new result view tab and switch to it
			if (newTab == addBtn)
				addTab();
		});

		// Calculate the minimum number of tabs in a tab pane to prohibit closing, depending
		// on combined results tab visibility. This needs to be done to ensure that tab pane
		// always includes at least one "normal" tab. 
		numOfUnclosableTabs.bind(Bindings.createIntegerBinding(
				() -> combinedResultsTab.visibleProperty().get() ? 3 : 2, combinedResultsTab.visibleProperty()));

		addTab();
	}


	/**
	 * Creates an instance of results view component.
	 * 
	 * @param mainForm reference to app's main form
	 * @param releasePlayer reference to a release player
	 */
	static ResultsView create(BandcampExplorerMainForm mainForm, ReleasePlayerForm releasePlayer) {
		assert mainForm != null;
		assert releasePlayer != null;
		return new ResultsView(mainForm, releasePlayer);
	}


	/**
	 * Adds a new tab to this results view and creates new release table view
	 * as its content to display search result.
	 * Does nothing if tabs are disabled.
	 */
	void addTab() {
		if (tabPane.isDisabled())
			return;

		Tab tab = new Tab("New search (" + ++tabIndex + ")");
		tab.setContent(ReleaseTableView.create(mainForm, releasePlayer));
		tab.setOnCloseRequest(event -> {
			if (isLastTab(tab))
				selectionModel.selectPrevious();
			else
				selectionModel.selectLast();
		});
		tab.setOnClosed(event -> {
			logDeletedSearchResult(tab.getUserData());
			combinedResultsTab.updateReleases();
		});
		// Don't allow to close tabs if there's only one "normal" tab left
		tab.closableProperty().bind(Bindings.size(tabs).greaterThan(numOfUnclosableTabs));

		tabs.add(tab);
		selectionModel.select(tab);
	}


	/**
	 * Returns true if currently selected tab has search result
	 * or if it's a combined results tab.
	 */
	boolean hasSelectedSearchResult() {
		Tab selected = getSelectedTab();
		return selected == combinedResultsTab || selected.getUserData() != null;
	}


	/**
	 * Sets a search result to display in a currently selected result view tab.
	 * If selected tab already contains search result, the old result gets
	 * replaced by a new one.
	 * 
	 * @param result a search result to display
	 * @throws NullPointerException if result is null
	 * @throws IllegalStateException if tabs are disabled
	 */
	void setSearchResult(SearchResult result) {
		if (tabPane.isDisabled())
			throw new IllegalStateException("Tabs must be enabled before setting a search result");

		Tab selected = getSelectedTab();
		if (selected == combinedResultsTab) {
			addTab();
			selected = getSelectedTab();
		}

		// Add new result
		logDeletedSearchResult(selected.getUserData());
		selected.setUserData(result);
		ObservableList<Release> releases = getReleasesOnTab(selected);
		releases.clear();
		result.forEach(releases::add);
		combinedResultsTab.updateReleases();

		// Update tab's header and tooltip accordingly
		selected.setText(result.searchParams().searchQuery());
		Tooltip tooltip = selected.getTooltip();
		if (tooltip == null)
			selected.setTooltip(tooltip = new Tooltip());
		tooltip.setText(result.toString());
	}


	/**
	 * Shows/hides the combined results tab in this results view.
	 * 
	 * @param show if true, the tab get displayed in a tab pane
	 */
	void showCombinedResults(boolean show) {
		combinedResultsTab.visibleProperty().set(show);
	}


	/**
	 * Closes currently selected tab.
	 * Does nothing if selected tab is a combined results tab or selected tab
	 * is the only "normal" tab left in a tab pane or if tabs are disabled.
	 */
	void closeSelectedTab() {
		if (tabPane.isDisabled())
			return;
		Tab tab = getSelectedTab();
		if (!tab.isClosable())
			return;

		// Since there's no native JavaFX support (yet) to close tab programmatically
		// we mimic tab closing behavior by removing tab from a list of tabs and
		// firing appropriate events to let handlers do their job.
		if (tab.getOnCloseRequest() != null) {
			Event closeRequest = new Event(Tab.TAB_CLOSE_REQUEST_EVENT);
			Event.fireEvent(tab, closeRequest);
			if (closeRequest.isConsumed())
				return;
		}
		tabs.remove(tab);
		if (tab.getOnClosed() != null)
			Event.fireEvent(tab, new Event(Tab.CLOSED_EVENT));

		tabPane.requestFocus();
	}


	/**
	 * Switches to the tab next to selected in this results view.
	 * If selected tab is the last tab, then switches to either first "normal" tab
	 * or to combined results tab (if it's visible at the moment).
	 * Does nothing if tabs are disabled.
	 */
	void switchTab() {
		if (tabPane.isDisabled())
			return;
		
		tabPane.requestFocus();
		if (isLastTab(getSelectedTab()))
			selectionModel.select(1);
		else
			selectionModel.selectNext();
	}


	/**
	 * Clears search result from currently selected tab in this results view.
	 * Does nothing if selected tab is a combined results tab.
	 * 
	 * @throws IllegalStateException if tabs are disabled
	 */
	void clearSelectedResult() {
		if (tabPane.isDisabled())
			throw new IllegalStateException("Cannot clear disabled tabs");
		
		Tab selected = getSelectedTab();
		if (selected != combinedResultsTab) {
			clearTab(selected);
			combinedResultsTab.updateReleases();
		}
	}


	/**
	 * Clears all search results from this results view.
	 * 
	 * @throws IllegalStateException if tabs are disabled
	 */
	void clearAllResults() {
		if (tabPane.isDisabled())
			throw new IllegalStateException("Cannot clear disabled tabs");

		doForEachTab(this::clearTab);
		combinedResultsTab.removeReleases();
	}


	/**
	 * Disables/enables all tabs in this results view.
	 * 
	 * @param disable if true, tabs will be disabled
	 */
	void disableTabs(boolean disable) {
		tabPane.setDisable(disable);
	}


	/**
	 * Clears search result from a specified tab.
	 * 
	 * @param tab a tab (cannot be a combined results tab)
	 * @throws NullPointerException if tab is null
	 */
	private void clearTab(Tab tab) {
		assert tab != combinedResultsTab;
		getReleasesOnTab(tab).clear();

		Object searchResult = tab.getUserData();
		if (searchResult instanceof SearchResult) {
			String resultText = searchResult.toString();
			Tooltip tooltip = tab.getTooltip();
			if (tooltip != null)
				tooltip.setText("(DELETED) " + resultText);
			logDeletedSearchResult(resultText);
			tab.setUserData(null);
		}		
	}


	/**
	 * Log the deletion of specified search result.
	 * 
	 * @param searchResult a search result object; the deletion won't be logged
	 *        if this parameter is null or not instance of SearchResult class
	 */
	private void logDeletedSearchResult(Object searchResult) {
		if (searchResult instanceof SearchResult)
			logDeletedSearchResult(searchResult.toString());
	}	


	/**
	 * Logs the deletion of search result.
	 * 
	 * @param resultText text representation of search result
	 */
	private void logDeletedSearchResult(String resultText) {
		LOGGER.info("Search result removed: " + resultText);
	}


	/**
	 * Performs the given action for each "normal" tab in this results view.
	 * 
	 * @param action an action to perform
	 * @throws NullPointerException if action is null
	 */
	private void doForEachTab(Consumer<Tab> action) {
		ListIterator<Tab> itr = tabs.listIterator(combinedResultsTab.visibleProperty().get() ? 2 : 1);
		while (itr.hasNext())
			action.accept(itr.next());
	}


	/**
	 * Gets a list of releases displayed as search result on a specified tab.
	 * 
	 * @param tab a tab
	 */
	private ObservableList<Release> getReleasesOnTab(Tab tab) {
		return ((ReleaseTableView)tab.getContent()).releases();
	}


	/**
	 * Returns currently selected tab.
	 */
	private Tab getSelectedTab() {
		return tabs.stream()
				.filter(Tab::isSelected)
				.findFirst()
				.orElseThrow(AssertionError::new);
	}


	/**
	 * Returns true if given tab is a last tab in this results view.
	 * 
	 * @param tab a tab to check
	 */
	private boolean isLastTab(Tab tab) {
		return tab == tabs.get(tabs.size() - 1);
	}

}
