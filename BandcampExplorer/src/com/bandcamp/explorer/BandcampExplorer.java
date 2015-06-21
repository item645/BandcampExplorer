package com.bandcamp.explorer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Handler;
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
	private static final String APP_TITLE = "Bandcamp Explorer 0.3.4";

	/**
	 * A reference to app's top level stage.
	 */
	private Stage primaryStage;

	/**
	 * Executor service employed for performing various asynchronous operations
	 */
	private final ExecutorService executorService = Executors.newFixedThreadPool(7);



	@Override
	public void start(Stage primaryStage) {
		this.primaryStage = primaryStage;
		this.primaryStage.setTitle(APP_TITLE);
		this.primaryStage.setOnCloseRequest(event -> executorService.shutdownNow());

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
		EventLog eventLog = EventLog.create(primaryStage);
		configureLogging(eventLog.getLogHandler());
		ReleasePlayerForm releasePlayer = ReleasePlayerForm.create(primaryStage);
		BandcampExplorerMainForm mainForm = BandcampExplorerMainForm.create(primaryStage, releasePlayer, eventLog);
		mainForm.setExecutorService(executorService);

		// When everything's prepared, show app's window
		primaryStage.setScene(new Scene(mainForm));
		primaryStage.setMaximized(true);
		primaryStage.show();

		LOGGER.info(APP_TITLE + " started");
	}


	/**
	 * Sets up application-wide logging.
	 */
	private void configureLogging(Handler eventLogHandler) {
		LOGGER.setUseParentHandlers(false);
		LOGGER.setLevel(Level.ALL);
		LOGGER.addHandler(eventLogHandler);

		// Make sure to log any uncaught exceptions in JavaFX thread
		Thread.currentThread().setUncaughtExceptionHandler((thread, exception) -> {
			LOGGER.log(Level.SEVERE, "Unexpected error occurred in thread \"" + thread.getName() + "\"", exception);
			Dialogs.messageBox("Unexpected Error: " + exception.getMessage() + 
					"\n\nSee Event Log for details (Ctrl+E)", "Error", primaryStage);
		});
	}

}
