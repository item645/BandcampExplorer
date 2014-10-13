package com.bandcamp.explorer.ui;

import java.util.ListIterator;
import java.util.function.Consumer;

import com.bandcamp.explorer.data.Release;
import com.bandcamp.explorer.data.SearchParams;
import com.bandcamp.explorer.data.SearchResult;

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;

/**
 * Controller class for results view component.
 */
public class ResultsView extends AnchorPane {

	private ReleasePlayerForm releasePlayer;

	private final TabPane tabPane = new TabPane();
	private final ObservableList<Tab> tabs = tabPane.getTabs();
	private int tabIndex;


	/**
	 * Creates a results view component and provides its initial setup.
	 */
	private ResultsView() {
		AnchorPane.setTopAnchor(tabPane, 0.0);
		AnchorPane.setBottomAnchor(tabPane, 0.0);
		AnchorPane.setLeftAnchor(tabPane, 0.0);
		AnchorPane.setRightAnchor(tabPane, 0.0);
		getChildren().add(tabPane);

		// This is a fake tab which serves as a "button" to add new tabs
		Tab addBtn = new Tab("+");
		addBtn.setClosable(false);
		tabs.add(addBtn);
		tabPane.getSelectionModel().selectedItemProperty().addListener((observablem, oldTab, newTab) -> {
			// If user hits "button" we create new result view tab and switch to it
			if (newTab == addBtn)
				addResultView();
		});

		addResultView();
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
		doForEachTab(tab -> getReleaseTableOnTab(tab).setReleasePlayer(releasePlayer));
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

		// add new result
		ObservableList<Release> releases = getReleasesOnTab(selected);
		releases.clear();
		result.forEach(releases::add);

		// update tab's header and tooltip accordingly
		SearchParams params = result.getSearchParams();
		selected.setText(params.searchQuery());
		StringBuilder tooltipText = new StringBuilder(params.searchType().toString())
		.append(": ").append(params.searchQuery()).append(" (").append(params.pages())
		.append(params.pages() > 1 ? " pages" : " page").append(", ").append(result.size())
		.append(" releases found)");
		selected.setTooltip(new Tooltip(tooltipText.toString()));
	}


	/**
	 * Clears search result from currently selected tab in this results view.
	 */
	void clearSelectedResult() {
		clearTab(getSelectedTab());
	}


	/**
	 * Clears all search results from this results view.
	 */
	void clearAllResults() {
		doForEachTab(this::clearTab);
	}


	/**
	 * Disables/enables all tabs in this results view.
	 * 
	 * @param disable if true, tabs will be disabled
	 */
	void disableTabs(boolean disable) {
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
	 * Performs the given action for each tab in this results view.
	 * 
	 * @param action an action to perform
	 * @throws NullPointerException if action is null
	 */
	private void doForEachTab(Consumer<Tab> action) {
		ListIterator<Tab> itr = tabs.listIterator(1);
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
	 * Adds a new tab to this results view and loads a release table view
	 * as its content to display search result.
	 */
	private void addResultView() {
		Tab tab = new Tab("New search (" + ++tabIndex + ")");
		// don't allow to close tabs if there's only two tabs left (one normal and one fake "button" tab)
		tab.closableProperty().bind(Bindings.size(tabs).greaterThan(2));
		tab.setOnCloseRequest(event -> tabPane.getSelectionModel().selectLast());

		ReleaseTableView releaseTable = ReleaseTableView.load();
		releaseTable.setReleasePlayer(releasePlayer);
		tab.setContent(releaseTable);

		tabs.add(tab);
		tabPane.getSelectionModel().select(tab);
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

}
