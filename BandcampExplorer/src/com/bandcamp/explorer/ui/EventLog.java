package com.bandcamp.explorer.ui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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

	private static EventLog EVENT_LOG;

	private static final DateTimeFormatter HH_mm_ss = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);

	/**
	 * A handler that transfers messages and exceptions from logger to an
	 * appropriate text area of EventLog
	 */
	private final Handler logHandler = createHandler();


	/**
	 * Instantiates and sets up the log handler.
	 */
	private static Handler createHandler() {
		Handler handler = new Handler() {
			@Override
			public void publish(LogRecord record) {
				// Since this code will be invoked concurrently from different threads,
				// all updates to UI components must be submitted for sequential
				// execution in JavaFX thread
				String messageText = getFormatter().format(record);
				String exceptionText = exceptionToText(record.getThrown());
				Platform.runLater(() -> {
					EVENT_LOG.logMessage(messageText);
					if (exceptionText != null)
						EVENT_LOG.logException(exceptionText);
				});
			}
			@Override public void flush() {}
			@Override public void close() throws SecurityException {}
		};

		// Installing the formatter that adds time stamp and line break to each log message
		handler.setFormatter(new Formatter() {
			@Override
			public String format(LogRecord record) {
				return new StringBuilder(HH_mm_ss.format(LocalDateTime.now()))
				.append("   ").append(formatMessage(record)).append(System.lineSeparator()).toString();
			}
		});

		return handler;
	}


	/**
	 * Converts the specified exception into a text, composed of current time stamp,
	 * exception stack trace and separator.
	 * 
	 * @param exception an exception to convert
	 * @return exception text; null, if passed exception object is null
	 */
	private static String exceptionToText(Throwable exception) {
		if (exception != null) {
			StringWriter output = new StringWriter();
			output.write(HH_mm_ss.format(LocalDateTime.now()) + "   ");
			exception.printStackTrace(new PrintWriter(output, true));
			output.write("--------------------------------" + System.lineSeparator());
			return output.toString();
		}
		else
			return null;
	}


	private final Stage stage;
	@FXML private TextArea eventsTextArea;
	@FXML private TextArea exceptionsTextArea;	


	/**
	 * Creates an instance of event log.
	 */
	private EventLog(Window owner) {
		Scene scene = new Scene(this);
		scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
		
		stage = new Stage();
		stage.initOwner(owner);
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
		stage.setScene(scene);
	}


	/**
	 * Creates an instance of the event log component.
	 * 
	 * @param owner the owner of event log window
	 * @throws NullPointerException if owner is null
	 * @throws IllegalStateException if this method has been called more than once
	 *         or if it is called from the thread other than JavaFX Application Thread
	 */
	public static EventLog create(Window owner) {
		if (!Platform.isFxApplicationThread())
			throw new IllegalStateException("This component can be created only from JavaFX Application Thread");
		if (EVENT_LOG != null)
			throw new IllegalStateException("This component can't be instantiated more than once");
		Objects.requireNonNull(owner);
		
		return (EVENT_LOG = Utils.loadFXMLComponent(
				EventLog.class.getResource("EventLog.fxml"),
				() -> new EventLog(owner)));
	}


	/**
	 * Returns a log handler that transfers messages and exceptions from logger
	 * to this EventLog.
	 */
	public Handler logHandler() {
		return logHandler;
	}


	/**
	 * Shows and brings to front event log window if it was hidden.
	 */
	void show() {
		stage.show();
	}


	/**
	 * Clears all content from events and exceptions text areas and logs
	 * message about that.
	 */
	@FXML
	private void clear() {
		eventsTextArea.clear();
		exceptionsTextArea.clear();
		Logger.getLogger(EventLog.class.getName()).fine("Event Log cleared");
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
	 * Writes exception text into the exceptions text area.
	 * 
	 * @param exceptionText exception text to log
	 */
	private void logException(String exceptionText) {
		exceptionsTextArea.appendText(exceptionText);
	}


	/**
	 * Initialization method invoked by FXML loader.
	 */
	@FXML
	private void initialize() {
		assert eventsTextArea != null : "fx:id=\"eventsTextArea\" was not injected: check your FXML file 'EventLog.fxml'.";
		assert exceptionsTextArea != null : "fx:id=\"exceptionsTextArea\" was not injected: check your FXML file 'EventLog.fxml'.";
	}

}
