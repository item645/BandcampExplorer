package com.bandcamp.explorer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import com.bandcamp.explorer.ui.BandcampExplorerMainForm;
import com.bandcamp.explorer.ui.Dialogs;
import com.bandcamp.explorer.ui.EventLog;
import com.bandcamp.explorer.ui.ReleasePlayerForm;


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
	 * Parent logger for the application.
	 */
	private static final Logger LOGGER = Logger.getLogger(BandcampExplorer.class.getPackage().getName());

	/**
	 * App title with current version number
	 */
	private static final String APP_TITLE = "Bandcamp Explorer 0.3.2";

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
		this.primaryStage.setTitle(APP_TITLE);
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
	 */
	private void initUI() {
		// Loading and configuring components
		configureLogging();
		BandcampExplorerMainForm mainForm = BandcampExplorerMainForm.getInstance();
		mainForm.setSearchExecutor(searchExecutor);
		ReleasePlayerForm.getInstance().setOwner(primaryStage);
		EventLog.getInstance().setOwner(primaryStage);

		// When everything's prepared, show app's window
		primaryStage.setScene(new Scene(mainForm));
		primaryStage.setMaximized(true);
		primaryStage.show();

		LOGGER.info(APP_TITLE + " started");
	}


	/**
	 * Sets up application-wide logging.
	 */
	private void configureLogging() {
		LOGGER.setUseParentHandlers(false);
		LOGGER.setLevel(Level.ALL);
		LOGGER.addHandler(EventLog.getInstance().getLogHandler());

		// Make sure to log any uncaught exceptions in JavaFX thread
		Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
			LOGGER.log(Level.SEVERE, "Unexpected error occured in thread \"" + thread.getName() + "\"", exception);
			Dialogs.messageBox("Unexpected Error: " + exception.getMessage() + 
					"\n\nSee Event Log for details (Ctrl+E)", "Error", primaryStage);
		});
	}

}
