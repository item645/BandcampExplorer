package com.bandcamp.explorer;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bandcamp.explorer.ui.BandcampExplorerMainForm;
import com.bandcamp.explorer.ui.ReleasePlayerForm;
import com.bandcamp.explorer.ui.ResultsView;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;


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
	 * operations during search tasks execution.
	 */
	private final ExecutorService searchExecutor = Executors.newFixedThreadPool(6);



	@Override
	public void start(Stage primaryStage) {
		this.primaryStage = primaryStage;
		this.primaryStage.setTitle("Bandcamp Explorer 0.2.0");
		this.primaryStage.setOnCloseRequest(event -> searchExecutor.shutdown());

		try {
			initUI();
		} 
		catch(Throwable e) {
			e.printStackTrace();
		}
	}


	/**
	 * Provides loading, initialization and setup of app's UI components.
	 * 
	 * @throws IOException if IO error happened during components load
	 */
	private void initUI() {	
		// Loading components
		BandcampExplorerMainForm mainForm = BandcampExplorerMainForm.load();
		ResultsView resultsView = ResultsView.load();
		ReleasePlayerForm releasePlayer = ReleasePlayerForm.load();

		// Configuration
		releasePlayer.setOwner(primaryStage);
		resultsView.setReleasePlayer(releasePlayer);
		mainForm.setResultsView(resultsView);
		mainForm.setSearchExecutor(searchExecutor);

		// When everything's prepared, show app's window
		Scene scene = new Scene(mainForm);
		primaryStage.setScene(scene);
		primaryStage.setMaximized(true);
		primaryStage.show();
	}
	
}
