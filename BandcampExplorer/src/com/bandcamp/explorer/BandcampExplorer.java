package com.bandcamp.explorer;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import com.bandcamp.explorer.ui.BandcampExplorerMainForm;
import com.bandcamp.explorer.ui.Dialogs;
import com.bandcamp.explorer.ui.EventLog;


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
	private static final String APP_TITLE = "Bandcamp Explorer 0.3.6";

	/**
	 * A reference to app's top level stage.
	 */
	private Stage primaryStage;

	/**
	 * Executor service employed for performing various asynchronous operations
	 */
	private final ExecutorService executorService = Executors.newFixedThreadPool(7);

	/**
	 * Host services for the application.
	 */
	private static HostServices hostServices;


	/**
	 * Returns the instance of HostServices.
	 * This method can be called only from Java FX Application Thread.
	 */
	public static HostServices hostServices() {
		if (!Platform.isFxApplicationThread())
			throw new IllegalStateException("This method can be called only from Java FX Application Thread");
		return hostServices;
	}


	@Override
	public void start(Stage primaryStage) {
		this.primaryStage = primaryStage;
		this.primaryStage.setTitle(APP_TITLE);
		this.primaryStage.setOnCloseRequest(event -> executorService.shutdownNow());

		hostServices = getHostServices();

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
		configureLogging(eventLog.logHandler());
		BandcampExplorerMainForm mainForm = BandcampExplorerMainForm.create(
				primaryStage, eventLog, executorService);

		// Configuring scene and displaying app window
		Scene scene = new Scene(mainForm);
		scene.getStylesheets().add(getClass().getResource("ui/style.css").toExternalForm());
		primaryStage.setScene(scene);
		primaryStage.setMaximized(true);
		primaryStage.show();

		LOGGER.info(APP_TITLE + " started");
	}


	/**
	 * Sets up application-wide logging.
	 */
	private void configureLogging(Handler eventLogHandler) {
		LOGGER.setUseParentHandlers(false);
		LOGGER.setLevel(getLoggingLevel());
		LOGGER.addHandler(eventLogHandler);

		// Make sure to log any uncaught exceptions in JavaFX thread
		Thread.currentThread().setUncaughtExceptionHandler((thread, exception) -> {
			LOGGER.log(Level.SEVERE, "Unexpected error occurred in thread \"" + thread.getName() + "\"", exception);
			Dialogs.messageBox("Unexpected Error: " + exception.getMessage() + 
					"\n\nSee Event Log for details (Ctrl+E)", "Error", primaryStage);
		});
	}


	/**
	 * Returns the default logging level to use for all loggers in the application.
	 * If valid level name was specified in the application command line parameters,
	 * this level will be used. Otherwise {@link Level#ALL} will be returned.
	 */
	private Level getLoggingLevel() {
		Level defaultLevel = Level.ALL;
		return getParameters().getNamed().entrySet().stream()
				.filter(e -> e.getKey().toLowerCase(Locale.ROOT).equals("log_level"))
				.findFirst()
				.map(Map.Entry::getValue)
				.map(name -> name.toUpperCase(Locale.ROOT))
				.map(name -> {
					try {
						return Level.parse(name);
					}
					catch (IllegalArgumentException e) {
						return defaultLevel;
					}
				})
				.orElse(defaultLevel);
	}

}
