package com.bandcamp.explorer.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Consumer;

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
import com.bandcamp.explorer.data.SearchParams;
import com.bandcamp.explorer.data.SearchResult;

/**
 * Controller class for results view component.
 */
public class ResultsView extends AnchorPane {

	private final TabPane tabPane = new TabPane();
	private final ObservableList<Tab> tabs = tabPane.getTabs();
	private final SingleSelectionModel<Tab> selectionModel = tabPane.getSelectionModel();
	private final CombinedResultsTab combinedResultsTab = new CombinedResultsTab();
	private final IntegerProperty numOfUnclosableTabs = new SimpleIntegerProperty();
	private int tabIndex;

	private BandcampExplorerMainForm mainForm;
	private ReleasePlayerForm releasePlayer;


	/**
	 * Class for a custom tab that displays combined results from all currently
	 * opened tabs, omitting any duplicate releases.
	 */
	private class CombinedResultsTab extends Tab {

		private final ReleaseTableView releaseTable = ReleaseTableView.load();
		private final ObservableList<Release> combinedReleases = releaseTable.getReleases();
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
		 * Adds new set of releases to combined results.
		 * Each individual release is added only if it's not present 
		 * in combined results yet.
		 * The addition is not performed if combined results tab is not visible
		 * at the moment.
		 * 
		 * @param releases releases to add
		 * @throws NullPointerException if releases is null
		 */
		void addReleases(Iterable<Release> releases) {
			if (visible.get()) {
				Set<Release> unique = new HashSet<>(combinedReleases);
				releases.forEach(unique::add);
				updateCombinedReleases(unique);
			}
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
			list.sort(ReleaseSortOrder.PUBLISH_DATE_DESC);
			combinedReleases.clear();
			combinedReleases.addAll(list);
		}


		/**
		 * Sets a reference to app's main form component for release
		 * table view on this tab.
		 * 
		 * @param mainForm a main form
		 */
		void setMainForm(BandcampExplorerMainForm mainForm) {
			releaseTable.setMainForm(mainForm);
		}


		/**
		 * Sets the release player for release table view on this tab.
		 * 
		 * @param releasePlayer the release player
		 */
		void setReleasePlayer(ReleasePlayerForm releasePlayer) {
			releaseTable.setReleasePlayer(releasePlayer);
		}

	}



	/**
	 * Creates a results view component and provides its initial setup.
	 */
	private ResultsView() {
		AnchorPane.setTopAnchor(tabPane, 0.0);
		AnchorPane.setBottomAnchor(tabPane, 0.0);
		AnchorPane.setLeftAnchor(tabPane, 0.0);
		AnchorPane.setRightAnchor(tabPane, 0.0);
		getChildren().add(tabPane);

		// This listener sets a main form's reference for this results view and pushes
		// it to all child release tables when results view is added to main form.
		parentProperty().addListener((observable, oldValue, newValue) -> {
			if (mainForm == null) {
				if (newValue instanceof BandcampExplorerMainForm) {
					mainForm = (BandcampExplorerMainForm)newValue;
					combinedResultsTab.setMainForm(mainForm);
					doForEachTab(tab -> getReleaseTableOnTab(tab).setMainForm(mainForm));
				}
				else
					throw new IllegalArgumentException("Invalid container for ResultsView component: " + newValue);
			}
		});

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
	 * Loads a results view component.
	 */
	public static ResultsView load() {
		return new ResultsView();
	}


	/**
	 * Sets the release player for playing selected releases.
	 * 
	 * @param releasePlayer the release player
	 */
	public void setReleasePlayer(ReleasePlayerForm releasePlayer) {
		this.releasePlayer = releasePlayer;
		combinedResultsTab.setReleasePlayer(releasePlayer);
		doForEachTab(tab -> getReleaseTableOnTab(tab).setReleasePlayer(releasePlayer));
	}


	/**
	 * Shows and brings to front player window if it was hidden.
	 */
	void showPlayer() {
		if (releasePlayer != null)
			releasePlayer.show();
	}


	/**
	 * Adds a new tab to this results view and creates new release table view
	 * as its content to display search result.
	 */
	void addTab() {
		Tab tab = new Tab("New search (" + ++tabIndex + ")");
		tab.setOnCloseRequest(event -> {
			if (isLastTab(tab))
				selectionModel.selectPrevious();
			else
				selectionModel.selectLast();
		});
		tab.setOnClosed(event -> combinedResultsTab.updateReleases());
		// Don't allow to close tabs if there's only one "normal" tab left
		tab.closableProperty().bind(Bindings.size(tabs).greaterThan(numOfUnclosableTabs));

		ReleaseTableView releaseTable = ReleaseTableView.load();
		releaseTable.setMainForm(mainForm);
		releaseTable.setReleasePlayer(releasePlayer);
		tab.setContent(releaseTable);

		tabs.add(tab);
		selectionModel.select(tab);
	}


	/**
	 * Sets a search result to display in a currently selected result view tab.
	 * If selected tab already contains search result, the old result gets
	 * replaced by a new one.
	 * 
	 * @param result a search result to display
	 * @throws NullPointerException if result is null
	 */
	void setSearchResult(SearchResult result) {
		Tab selected = getSelectedTab();
		if (selected == combinedResultsTab) {
			addTab();
			selected = getSelectedTab();
		}

		// Add new result
		ObservableList<Release> releases = getReleasesOnTab(selected);
		releases.clear();
		result.forEach(releases::add);
		combinedResultsTab.addReleases(result);

		// Update tab's header and tooltip accordingly
		SearchParams params = result.getSearchParams();
		selected.setText(params.searchQuery());
		StringBuilder tooltipText = new StringBuilder(params.searchType().toString())
		.append(": ").append(params.searchQuery()).append(" (").append(params.pages())
		.append(params.pages() > 1 ? " pages" : " page").append(", ").append(result.size())
		.append(" releases found)");
		selected.setTooltip(new Tooltip(tooltipText.toString()));
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
	 * is the only "normal" tab left in a tab pane.
	 */
	void closeSelectedTab() {
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
		Tab selected = getSelectedTab();
		if (selected.isDisabled())
			return;
		
		tabPane.requestFocus();
		if (isLastTab(selected))
			selectionModel.select(1);
		else
			selectionModel.selectNext();
	}


	/**
	 * Clears search result from currently selected tab in this results view.
	 * Does nothing if selected tab is a combined results tab.
	 */
	void clearSelectedResult() {
		Tab selected = getSelectedTab();
		if (selected != combinedResultsTab) {
			clearTab(selected);
			combinedResultsTab.updateReleases();
		}
	}


	/**
	 * Clears all search results from this results view.
	 */
	void clearAllResults() {
		doForEachTab(this::clearTab);
		combinedResultsTab.removeReleases();
	}


	/**
	 * Disables/enables all tabs in this results view.
	 * 
	 * @param disable if true, tabs will be disabled
	 */
	void disableTabs(boolean disable) {
		combinedResultsTab.setDisable(disable);
		doForEachTab(tab -> tab.setDisable(disable));
	}


	/**
	 * Clears search result from a specified tab.
	 * 
	 * @param tab a tab
	 * @throws NullPointerException if tab is null
	 */
	private void clearTab(Tab tab) {
		getReleasesOnTab(tab).clear();
		Tooltip tooltip = tab.getTooltip();
		if (tooltip != null)
			tooltip.setText("(DELETED) " + tooltip.getText());
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
	 * Returns a release table view that resides on a specified tab.
	 * 
	 * @param tab a tab
	 * @throws NullPointerException if tab is null
	 */
	private ReleaseTableView getReleaseTableOnTab(Tab tab) {
		return (ReleaseTableView)tab.getContent();
	}


	/**
	 * Gets a list of releases displayed as search result on a specified tab.
	 * 
	 * @param tab a tab
	 */
	private ObservableList<Release> getReleasesOnTab(Tab tab) {
		return getReleaseTableOnTab(tab).getReleases();
	}


	/**
	 * Returns currently selected tab.
	 */
	private Tab getSelectedTab() {
		for (Tab t : tabs)
			if (t.isSelected())
				return t;
		throw new AssertionError();
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
