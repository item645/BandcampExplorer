package com.bandcamp.explorer.ui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Controller class for event log component.
 */
public class EventLog extends AnchorPane {

	/**
	 * Single instance of event log component
	 */
	private static final EventLog EVENT_LOG = Utils.loadFXMLComponent(
			EventLog.class.getResource("EventLog.fxml"),
			EventLog::new);


	private static final DateTimeFormatter HH_mm_ss = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);
	private static final String LINE_SEPARATOR = System.getProperty("line.separator");

	/**
	 * A handler that transfers messages and exceptions from logger to an
	 * appropriate text area of EventLog
	 */
	private final Handler logHandler;
	{
		logHandler = new Handler() {
			@Override
			public void publish(LogRecord record) {
				// Since this code will be invoked concurrently from search threads,
				// all updates to UI components must be submitted for sequential
				// execution in JavaFX thread
				Platform.runLater(() -> {
					EVENT_LOG.logMessage(getFormatter().format(record));
					Throwable exception = record.getThrown();
					if (exception != null)
						EVENT_LOG.logException(exception);
				});
			}
			@Override public void flush() {}
			@Override public void close() throws SecurityException {}
		};

		// Installing the formatter that adds time stamp and line break to each log message
		logHandler.setFormatter(new Formatter() {
			@Override
			public String format(LogRecord record) {
				return new StringBuilder(HH_mm_ss.format(LocalDateTime.now()))
				.append("   ").append(formatMessage(record)).append(LINE_SEPARATOR).toString();
			}
		});
	}


	private Stage stage;
	@FXML private TextArea eventsTextArea;
	@FXML private TextArea exceptionsTextArea;	


	private EventLog() {}


	/**
	 * Returns a reference to an event log component.
	 */
	public static EventLog getInstance() {
		return EVENT_LOG;
	}


	/**
	 * Returns a log handler that transfers messages and exceptions from logger
	 * to this EventLog.
	 */
	public Handler getLogHandler() {
		return logHandler;
	}


	/**
	 * Shows and brings to front event log window if it was hidden.
	 */
	void show() {
		stage.show();
	}


	/**
	 * Sets the owner window for event log's top stage.
	 * This method is used to set the primary stage as an owner to ensure
	 * that when primary window is closing, event log's window will be automatically
	 * closed as well.
	 * 
	 * @param owner an owner window
	 * @throws NullPointerException if event log's top stage is not yet initialized
	 * @throws IllegalStateException if top stage has already been made visible
	 */
	public void setOwner(Window owner) {
		stage.initOwner(owner);
	}


	/**
	 * Writes the message into the events text area. 
	 * 
	 * @param message a message to log
	 */
	private void logMessage(String message) {
		eventsTextArea.appendText(message);
	}


	/**
	 * Writes stack trace of the specified exception into exceptions
	 * text area, also adding time stamp and separator to the resulting text.
	 * 
	 * @param exception an exception to log
	 */
	private void logException(Throwable exception) {
		StringWriter writer = new StringWriter();
		writer.write(HH_mm_ss.format(LocalDateTime.now()) + "   ");
		exception.printStackTrace(new PrintWriter(writer, true));
		writer.write("--------------------------------\n");
		exceptionsTextArea.appendText(writer.toString());
	}


	/**
	 * Loads a top level stage for event log.
	 */
	private void initStage() {
		stage = new Stage();
		stage.setTitle("Event Log");
		stage.setResizable(false);
		stage.setOnCloseRequest(event -> {
			stage.hide();
			event.consume();
		});
		stage.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
			if (event.getCode() == KeyCode.ESCAPE)
				stage.hide();
		});
		stage.setScene(new Scene(this));
	}


	/**
	 * Initialization method invoked by FXML loader, provides initial setup for components.
	 */
	@FXML
	private void initialize() {
		assert eventsTextArea != null : "fx:id=\"eventsTextArea\" was not injected: check your FXML file 'EventLog.fxml'.";
		assert exceptionsTextArea != null : "fx:id=\"exceptionsTextArea\" was not injected: check your FXML file 'EventLog.fxml'.";

		initStage();
	}

}
