package com.bandcamp.explorer;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bandcamp.explorer.data.SearchEngine;
import com.bandcamp.explorer.ui.BandcampExplorerMainForm;
import com.bandcamp.explorer.ui.ReleasePlayerForm;
import com.bandcamp.explorer.ui.ReleaseTableView;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;


/**
 * Bandcamp Explorer main application class.
 * This class is responsible for loading, initialization and initial
 * setup of application's UI components.
 */
public final class BandcampExplorer extends Application {

	public static void main(String[] args) {
		launch(args);
	}

	/**
	 * A reference to app's top level stage.
	 */
	private Stage primaryStage;

	/**
	 * Executor service employed for performing asynchronous loading
	 * operation during search tasks execution.
	 */
	private final ExecutorService searchExecutor = Executors.newFixedThreadPool(6);

	/**
	 * An instance of SearchEngine class for managing search tasks.
	 */
	private final SearchEngine searchEngine = new SearchEngine(searchExecutor);



	@Override
	public void start(Stage primaryStage) {
		this.primaryStage = primaryStage;
		this.primaryStage.setTitle("Bandcamp Explorer 0.1.1");
		this.primaryStage.setOnCloseRequest(event -> searchExecutor.shutdown());

		try {
			initUI();
		} 
		catch(Exception e) {
			e.printStackTrace();
		}
	}


	/**
	 * Provides loading, initialization and setup of app's UI components.
	 * 
	 * @throws IOException if IO error happened during components load
	 */
	private void initUI() throws IOException {

		// Loading main form
		FXMLLoader mainFormLoader = new FXMLLoader();
		mainFormLoader.setLocation(getResource("ui/BandcampExplorerMainForm.fxml"));
		BorderPane root = (BorderPane)mainFormLoader.load();
		BandcampExplorerMainForm mainForm = mainFormLoader.getController();

		// Loading release table view
		FXMLLoader releaseTableLoader = new FXMLLoader();
		releaseTableLoader.setLocation(getResource("ui/ReleaseTableView.fxml"));
		releaseTableLoader.load();
		ReleaseTableView releaseTable = releaseTableLoader.getController();

		// Loading release player form
		FXMLLoader playerFormLoader = new FXMLLoader();
		playerFormLoader.setLocation(getResource("ui/ReleasePlayerForm.fxml"));
		playerFormLoader.load();
		ReleasePlayerForm releasePlayer = playerFormLoader.getController();

		// Configuration of components
		releasePlayer.setOwner(primaryStage);
		releaseTable.setReleasePlayer(releasePlayer);
		mainForm.setSearchEngine(searchEngine);
		mainForm.setReleaseTableView(releaseTable);

		// When everything's prepared, show app's window
		Scene scene = new Scene(root);
		primaryStage.setScene(scene);
		primaryStage.setMaximized(true);
		primaryStage.show();
	}


	/**
	 * Helper to find resources by name.
	 * 
	 * @param name resource name
	 * @return URL to a resource, if found; null otherwise
	 */
	private URL getResource(String name) {
		Class<?> clazz = getClass();
		URL res = clazz.getResource(name);
		if (res == null)
			res = clazz.getClassLoader().getResource(name);
		return res;
	}

}
