package com.bandcamp.explorer.ui;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import com.bandcamp.explorer.data.Release;
import com.bandcamp.explorer.data.SearchType;
import com.bandcamp.explorer.data.Time;
import com.bandcamp.explorer.data.Track;
import com.bandcamp.explorer.ui.CellFactory.CellCustomizer;

/**
 * Controller class for release player form.
 */
class ReleasePlayerForm extends SplitPane {

	private static final Logger LOGGER = Logger.getLogger(ReleasePlayerForm.class.getName());

	private static final String NO_RELEASE_TITLE = "[No Release]";

	// Icons for player control buttons
	private static final Image PLAY_ICON_IMAGE   = loadIcon("player_play.png");
	private static final Image PAUSE_ICON_IMAGE  = loadIcon("player_pause.png");
	private static final ImageView PLAY_ICON     = new ImageView(PLAY_ICON_IMAGE);
	private static final ImageView PAUSE_ICON    = new ImageView(PAUSE_ICON_IMAGE);
	private static final ImageView STOP_ICON     = new ImageView(loadIcon("player_stop.png"));
	private static final ImageView PREVIOUS_ICON = new ImageView(loadIcon("player_previous.png"));
	private static final ImageView NEXT_ICON     = new ImageView(loadIcon("player_next.png"));

	private static ReleasePlayerForm INSTANCE;

	private final Stage stage;
	@FXML private MenuButton moreActionsMenu;
	@FXML private Button loadReleaseButton;
	@FXML private Button unloadReleaseButton;
	@FXML private Button previousButton;
	@FXML private Button nextButton;
	@FXML private Button playButton;
	@FXML private Button stopButton;
	@FXML private ImageView artworkView;
	@FXML private Hyperlink releaseLink;
	@FXML private Hyperlink discogLink;
	@FXML private Hyperlink downloadLink;
	@FXML private TextArea releaseInfo;
	@FXML private Label nowPlayingInfo;
	@FXML private Slider timeSlider;
	@FXML private Slider volumeSlider;
	@FXML private Label volumeLevel;
	@FXML private TableView<Track> tracksTableView;
	@FXML private TableColumn<Track, Track> playButtonColumn;
	@FXML private TableColumn<Track, Number> trackNumberColumn;
	@FXML private TableColumn<Track, String> artistColumn;
	@FXML private TableColumn<Track, String> titleColumn;
	@FXML private TableColumn<Track, Time> timeColumn;

	private final BandcampExplorerMainForm mainForm;
	private TrackListView trackListView;
	private final TrackListContextMenu trackListContextMenu = new TrackListContextMenu();
	private final AudioPlayer audioPlayer = new AudioPlayer();
	private final ReleaseProperty release = new ReleaseProperty();
	private final ReleaseUrlLoader releaseUrlLoader = new ReleaseUrlLoader();


	/**
	 * Property wrapper for Release object, provides few additional helper methods mostly
	 * inspired by {@link java.util.Optional} functionality.
	 */
	private static class ReleaseProperty extends SimpleObjectProperty<Release> {

		/**
		 * Returns true if release is set (i.e. non-null).
		 */
		boolean isPresent() {
			return get() != null;
		}


		/**
		 * If release is present, peformes the specified action on it,
		 * otherwise does nothing.
		 * 
		 * @param action a consumer action, accepting the release object
		 */
		void ifPresent(Consumer<Release> action) {
			Release release = get();
			if (release != null)
				action.accept(release);
		}


		/**
		 * If release is present, extracts and returns some value from release object, 
		 * otherwise returns the default value.
		 * 
		 * @param extractor extraction function which takes release object as parameter
		 *        and returns desired value
		 * @param defaultVal default value, returned in case the release is not set
		 * @return extracted value or default
		 */
		<T> T extractOrElse(Function<Release, T> extractor, T defaultVal) {
			Release release = get();
			return release != null ? extractor.apply(release) : defaultVal;
		}


		/**
		 * Returns true if this release is equal to the specified release object. 
		 * 
		 * @param otherRelease release object for comparison 
		 */
		boolean valueEquals(Release otherRelease) {
			return Objects.equals(get(), otherRelease);
		}

	}


	/**
	 * A loader that performs asynchronous loading of the release from the specified URL 
	 * and sets it for playback in audio player.
	 */
	private class ReleaseUrlLoader {

		private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
			Thread t = new Thread(task);
			t.setDaemon(true);
			return t;
		});


		/**
		 * Prompts user to enter a URL to a release, loads the release using supplied
		 * URL and sets it to play in audio player.
		 * If there's an error loading release, displays message box with information
		 * about error.
		 * If empty URL was supplied or cancel pressed, does nothing.
		 * In case supplied URL is malformed, does nothing visually but records an 
		 * error message in event log. 
		 */
		void loadFromDialog() {
			load(Dialogs.inputBox("Enter a release URL to load:", "Load Release", stage));
		}


		/**
		 * Obtains release URL from system clipboard, loads the release using supplied
		 * URL and sets it to play in audio player.
		 * If there's an error loading release, displays message box with information
		 * about error.
		 * If clipboard does not contain text data, does nothing.
		 * In case supplied URL is malformed, does nothing visually but records an 
		 * error message in event log. 
		 */
		void loadFromClipboard() {
			load(Utils.getClipboardString());
		}


		/**
		 * Asynchronously loads the release if supplied Optional contains an URL.
		 */
		private void load(Optional<String> maybeURL) {
			maybeURL.map(this::prepareURL).ifPresent(url -> executor.submit(createReleaseLoaderTask(url)));
		}


		/**
		 * Prepares an URL, lowercasing and adding protocol if necessary.
		 */
		private String prepareURL(String url) {
			assert url != null;
			url = url.trim().toLowerCase(Locale.ROOT);
			if (!url.isEmpty()) {
				if (!url.startsWith("https://") && !url.startsWith("http://"))
					url = "http://" + url;
				return url;
			}
			else {
				return null;
			}
		}


		/**
		 * Creates new JavaFX task to load the release from specified URL.
		 */
		private Task<Release> createReleaseLoaderTask(String url) {
			Task<Release> task = new Task<Release>() {
				@Override
				protected Release call() throws Exception {
					return Release.forURI(new URL(url).toURI());
				}
			};
			task.setOnSucceeded(event -> setRelease(task.getValue()));
			task.setOnFailed(event -> handleException(task.getException()));
			return task;
		}


		/**
		 * Handles exception that can possibly occur during release load.
		 * For exceptions caused by malformed URL records a warning with error 
		 * message in the event log. For other exceptions additionally displays
		 * a message box with error message.
		 */
		private void handleException(Throwable exception) {
			if (exception instanceof ExecutionException)
				exception = exception.getCause();
			if (exception instanceof MalformedURLException || exception instanceof URISyntaxException) {
				LOGGER.warning("Invalid release URL: " + exception.getMessage());
			}
			else {
				String errMsg;
				if (exception != null) {
					errMsg = "Error loading release: " + exception.getMessage();
					LOGGER.log(Level.WARNING, errMsg, exception);
				}
				else {
					errMsg = "Error loading release";
					LOGGER.log(Level.WARNING, errMsg);
				}
				Dialogs.messageBox(errMsg, "Error", stage);
			}
		}

	}


	/**
	 * Thin wrapper around tracks table view also encapsulating and providing access
	 * to some associated stuff, like observable items list and list of play buttons 
	 * from the first column.
	 */
	private static class TrackListView {

		private final TableView<Track> tableView;
		private final ObservableList<Track> observableTracks = FXCollections.observableArrayList();
		
		/**
		 * Reference to a sorted list wrapper around table view items
		 */
		final SortedList<Track> sortedTracks = new SortedList<>(observableTracks);

		/**
		 * Holds a list of play buttons from table's first column
		 */
		final List<TrackListButton> playButtons = new ArrayList<>();


		/**
		 * Creates a wrapper around specified table view.
		 * 
		 * @param tableView tracks table view
		 */
		TrackListView(TableView<Track> tableView) {
			this.tableView = tableView;
			this.tableView.setPlaceholder(new Label());
			this.tableView.setItems(sortedTracks);
			sortedTracks.comparatorProperty().bind(tableView.comparatorProperty());
		}


		/**
		 * Returns a track at the specified position in a track list view.
		 * 
		 * @param index zero-based index of the position
		 * @return a track
		 * @throws IndexOutOfBoundsException if index is out of range
		 */
		Track track(int index) {
			return observableTracks.get(index);
		}


		/**
		 * Finds the first playable track in this list.
		 * 
		 * @return first playable track; null, if no such track found
		 */
		Track firstPlayableTrack() {
			for (Track t : observableTracks)
				if (t.isPlayable())
					return t;
			return null;
		}


		/**
		 * Returns the closest playable track previous to
		 * the given track.
		 * 
		 * @param track a given track to search from
		 * @return closest playable track; null if no such track found
		 * @throws NullPointerException if track is null
		 */
		Track previousPlayableTrack(Track track) {
			ListIterator<Track> itr = observableTracks.listIterator(track.number() - 1);
			while (itr.hasPrevious()) {
				Track t = itr.previous();
				if (t != track && t.isPlayable())
					return t;
			}
			return null;
		}


		/**
		 * Returns the closest playable track next to
		 * the given track.
		 * 
		 * @param track a given track to search from
		 * @return closest playable track; null if no such track found
		 * @throws NullPointerException if track is null
		 */
		Track nextPlayableTrack(Track track) {
			ListIterator<Track> itr = observableTracks.listIterator(track.number() - 1);
			while (itr.hasNext()) {
				Track t = itr.next();
				if (t != track && t.isPlayable())
					return t;
			}
			return null;
		}


		/**
		 * Returns the track currently selected in a tracklist table view.
		 * If there is no track selected, returns null.
		 */
		Track selectedTrack() {
			return tableView.getSelectionModel().getSelectedItem();
		}


		/**
		 * If tracklist view has selected track, peformes the specified action
		 * on this track, otherwise does nothing.
		 * 
		 * @param action a consumer action, accepting the selected track
		 */
		void ifTrackSelected(Consumer<Track> action) {
			Track track = selectedTrack();
			if (track != null)
				action.accept(track);
		}


		/**
		 * Removes all items from this track list view.
		 */
		void clear() {
			observableTracks.clear();
			playButtons.clear();
		}


		/**
		 * Adds a collection of tracks as items to this track list view.
		 * 
		 * @param tracks a collection of tracks
		 * @throws NullPointerException if the specified collection is null
		 */
		void addAll(Collection<Track> tracks) {
			observableTracks.addAll(tracks);
		}

	}


	/**
	 * Implements a context menu for tracklist table cells.
	 */
	private class TrackListContextMenu extends CellContextMenu {

		/**
		 * Creates a context menu instance.
		 */
		TrackListContextMenu() {
			LabeledMenuItem play = new LabeledMenuItem("Play");
			LabeledMenuItem pause = new LabeledMenuItem("Pause");
			LabeledMenuItem stop = new LabeledMenuItem("Stop");
			LabeledMenuItem previous = new LabeledMenuItem("Previous");
			LabeledMenuItem next = new LabeledMenuItem("Next");

			pause.setOnAction(event -> audioPlayer.pause());
			stop.setOnAction(event -> audioPlayer.stop());

			LabeledMenuItem searchArtist = new LabeledMenuItem(true);

			LabeledMenuItem viewOnBandcamp = new LabeledMenuItem("View on Bandcamp");
			viewOnBandcamp.setOnAction(event -> trackListView
					.ifTrackSelected(track -> track.link().ifPresent(Utils::browse)));

			LabeledMenuItem copyText = new LabeledMenuItem("Copy Text");
			copyText.setOnAction(event -> {
				TableCell<?,?> cell = selectedCell();
				if (cell != null)
					Utils.toClipboardAsString(cell.getItem());
			});

			LabeledMenuItem copyTrackText = new LabeledMenuItem("Copy Track as Text");
			copyTrackText.setOnAction(event -> trackListView
					.ifTrackSelected(Utils::toClipboardAsString));

			LabeledMenuItem copyAllTracksText = new LabeledMenuItem("Copy All Tracks as Text");
			copyAllTracksText.setOnAction(event -> Utils.toClipboardAsString(
					trackListView.sortedTracks, Track::toString, "\n"));

			LabeledMenuItem copyArtistTitle = new LabeledMenuItem("Copy Artist and Title");
			copyArtistTitle.setOnAction(event -> trackListView
					.ifTrackSelected(track -> Utils.toClipboardAsString(track.artist() + " - " + track.title())));

			LabeledMenuItem copyTrackURL = new LabeledMenuItem("Copy Track URL");
			copyTrackURL.setOnAction(event -> trackListView
					.ifTrackSelected(track -> track.link().ifPresent(Utils::toClipboardAsString)));

			LabeledMenuItem copyAudioURL = new LabeledMenuItem("Copy Audio URL");
			copyAudioURL.setOnAction(event -> trackListView
					.ifTrackSelected(track -> track.fileLink().ifPresent(Utils::toClipboardAsString)));

			ObservableList<MenuItem> menuItems = getItems();

			setOnShowing(windowEvent -> {
				// Update items on menu popup
				Track track = trackListView.selectedTrack();
				if (track != null) {
					if (track.isPlayable()) {
						if (audioPlayer.isPlayingTrack(track)) {
							play.setDisable(true);
							pause.setDisable(false);
							stop.setDisable(false);
						}
						else {
							play.setDisable(false);
							play.setOnAction(actionEvent -> Platform.runLater(() -> {
								audioPlayer.setTrack(track);
								audioPlayer.play();
							}));
							pause.setDisable(true);
							stop.setDisable(!audioPlayer.isPausedTrack(track));
						}
						copyAudioURL.setDisable(false);
					}
					else {
						play.setDisable(true);
						pause.setDisable(true);
						stop.setDisable(true);
						copyAudioURL.setDisable(true);
					}

					updatePrevNextItem(previous, trackListView.previousPlayableTrack(track));
					updatePrevNextItem(next, trackListView.nextPlayableTrack(track));

					String artist = track.artist();
					searchArtist.setLabelText(String.format("Search \"%s\"", artist));
					searchArtist.setOnAction(
							actionEvent -> mainForm.searchReleases(artist, SearchType.SEARCH));

					viewOnBandcamp.setDisable(!track.link().isPresent());
				}
				else {
					searchArtist.setLabelText(null);
					searchArtist.setOnAction(null);
				}

				searchArtist.setDisable(mainForm.isRunningSearch());
				// Re-add item to trigger menu resizing
				menuItems.set(menuItems.indexOf(searchArtist), searchArtist);
			});

			menuItems.addAll(play, pause, stop, previous, next, new SeparatorMenuItem(),
					searchArtist, new SeparatorMenuItem(), viewOnBandcamp, new SeparatorMenuItem(),
					copyText, copyTrackText, copyAllTracksText, copyArtistTitle, copyTrackURL, copyAudioURL);
		}


		/**
		 * Sets up previous/next menu item for playing specified track.
		 * 
		 * @param menuItem previous or next menu item
		 * @param track a track to play when item is selected
		 */
		private void updatePrevNextItem(LabeledMenuItem menuItem, Track track) {
			if (track == null)
				menuItem.setDisable(true);
			else {
				menuItem.setDisable(false);
				menuItem.setOnAction(event -> Platform.runLater(() -> {
					boolean wasPlaying = audioPlayer.isPlaying();
					audioPlayer.setTrack(track);
					if (wasPlaying)
						audioPlayer.play();
				}));
			}
		}

	}


	/**
	 * Class for control buttons in a track list view, allowing for playing/pausing
	 * individual tracks.
	 */
	private static class TrackListButton extends Button {

		private final ImageView playIcon;
		private final ImageView pauseIcon;

		{
			// New image view object is instantiated for each button because
			// it is not allowed to reuse the same node within a scene graph
			playIcon = new ImageView(PLAY_ICON_IMAGE);
			playIcon.setFitWidth(12);
			playIcon.setFitHeight(12);
			pauseIcon = new ImageView(PAUSE_ICON_IMAGE);
			pauseIcon.setFitWidth(12);
			pauseIcon.setFitHeight(12);

			setMinSize(20, 20);
			setMaxSize(20, 20);
			setPrefSize(20, 20);
			setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		}


		/**
		 * Sets a play icon for this button.
		 */
		void setPlayIcon() {
			setGraphic(playIcon);
		}


		/**
		 * Sets a pause icon for this button.
		 */
		void setPauseIcon() {
			setGraphic(pauseIcon);
		}

	}


	/**
	 * AudioPlayer is used to actually play audio tracks on release.
	 * This class employs JavaFX MediaPlayer to play audio and works with associated
	 * controls in enclosing class to provide abilities for full-fledged playback,
	 * such as playing, stopping, pausing, skipping to next or previous track,
	 * adjusting track's position and changing sound volume level.
	 */
	private class AudioPlayer {

		private static final String LOADING_TRACK_MSG = "Loading track...";

		private final InvalidationListener timeListener = observable -> updateTrackProgress();
		private ChangeListener<Number> timeSliderValueListener;
		private ChangeListener<Number> volumeSliderValueListener;
		private MediaPlayer player;
		private Track track;
		private Duration duration;
		private String durationText;
		private long currentSecond = -1;
		private boolean preventTimeSliderSeek;
		private boolean quit = true;


		/**
		 * Sets up a track to play in this audio player.
		 * This method triggers a load of track's audio data and prepares
		 * associated controls and underlying media player for playback.
		 * 
		 * @param track a track to play
		 * @throws NullPointerException is track is null
		 * @throws IllegalArgumentException if track is not playable
		 */
		void setTrack(Track track) {
			if (this.track == Objects.requireNonNull(track))
				return;
			if (!track.isPlayable())
				throw new IllegalArgumentException("Track is not playable: " + track);

			if (player != null)
				quit();
			player = new MediaPlayer(track.fileLink().map(Media::new).get());
			this.track = track;
			quit = false;

			setStateTransitionHandlers();

			highlightCurrentTrack();

			player.setVolume(volumeSlider.getValue() / volumeSlider.getMax());
			player.currentTimeProperty().addListener(timeListener);
			nowPlayingInfo.setText(LOADING_TRACK_MSG);

			if (timeSliderValueListener == null) {
				timeSliderValueListener = (observable, oldValue, newValue) -> {
					if (!quit && !preventTimeSliderSeek)
						player.seek(duration.multiply(newValue.doubleValue() / timeSlider.getMax()));
				};
				timeSlider.valueProperty().addListener(timeSliderValueListener);
			}
			if (volumeSliderValueListener == null) {
				volumeSliderValueListener = (observable, oldValue, newValue) -> {
					if (!quit)
						player.setVolume(newValue.doubleValue() / volumeSlider.getMax());
				};
				volumeSlider.valueProperty().addListener(volumeSliderValueListener);
			}
			volumeSlider.setDisable(false);

			disablePlayerButtons(false);
			playButton.setOnAction(event -> play());
			stopButton.setOnAction(event -> stop());
			prepareSwitchButton(previousButton, trackListView.previousPlayableTrack(track));
			prepareSwitchButton(nextButton, trackListView.nextPlayableTrack(track));

			currentSecond = -1;
		}


		/**
		 * Returns a track loaded for playback in this audio player.
		 * 
		 * @return a track; null, if there's no track in player
		 */
		Track track() {
			return track;
		}


		/**
		 * Assigns handlers for media player state transition events.
		 */
		private void setStateTransitionHandlers() {
			player.setOnReady(() -> {
				if (!quit) {
					duration = player.getMedia().getDuration();
					durationText = formatTime(duration);
					updateTrackProgress();
				}
			});
			player.setOnPlaying(() -> {
				playButton.setGraphic(PAUSE_ICON);
				playButton.setOnAction(event -> pause());
				timeSlider.setDisable(false);
				updateTrackListButtons();
			});
			player.setOnPaused(() -> {
				playButton.setGraphic(PLAY_ICON);
				playButton.setOnAction(event -> play());
				timeSlider.setDisable(false);
				updateTrackListButtons();
			});
			player.setOnStopped(() -> {
				playButton.setGraphic(PLAY_ICON);
				playButton.setOnAction(event -> play());
				updateTrackProgress();
				updateTrackListButtons();
			});
			player.setOnStalled(() -> {
				nowPlayingInfo.setText(LOADING_TRACK_MSG);
				timeSlider.setDisable(true);
			});
			player.setOnEndOfMedia(() -> {
				if (!quit) {
					Track next = trackListView.nextPlayableTrack(track);
					if (next != null) {
						updateTrackProgress();
						setTrack(next);
						play();
					}
					else {
						stop();
						// Contrary to what documentation says, a call to player.seek() while
						// player is in STOPPED state DOES have a side effect of changing the
						// value of player's internal flag variable isUpdateTimeEnabled to true,
						// which allows for automatic updating of currentTime property (and triggering
						// corresponding notifications) on resuming playback later on after stopping
						// when player has reached end of media.
						// Here we're doing a call to player.seek() to trigger the aforementioned effect
						// as a workaround for the bug with time slider and now playing info label not
						// reflecting track progress when playback has been started after reaching 
						// the end of media for current track. The bug is caused by the fact that player
						// stops updating currentTime property on playback due to isUpdateTimeEnabled
						// flag being set to false after track ended.
						player.seek(Duration.ZERO);
					}
				}
			});
			player.setOnError(() -> {
				if (!quit) {
					MediaException e = player.getError();
					if (e != null) {
						LOGGER.log(Level.SEVERE, "Audio Player Error: " + e.getMessage(), e);
						Dialogs.messageBox(e.getMessage(), "Audio Player Error", stage);
					}
					quit();
				}
			});
		}


		/**
		 * Checks if player is currently playing audio.
		 */
		boolean isPlaying() {
			return player != null && player.getStatus() == Status.PLAYING;
		}


		/**
		 * Checks if player is currently playing specified track.
		 * 
		 * @param track a track to check for playback
		 */
		boolean isPlayingTrack(Track track) {
			return this.track == track && isPlaying();
		}


		/**
		 * Checks if player is currently holding specified track on pause.
		 * 
		 * @param track a track to check for pause
		 */
		boolean isPausedTrack(Track track) {
			return this.track == track && player != null && player.getStatus() == Status.PAUSED;
		}


		/**
		 * Starts playback for current track.
		 */
		void play() {
			if (player != null) {
				switch (player.getStatus()) {
				case UNKNOWN: case PAUSED: case READY: case STOPPED:
					player.play();
				default:
				}
			}
		}


		/**
		 * Pauses currently playing track.
		 */
		void pause() {
			if (isPlaying())
				player.pause();
		}


		/**
		 * Stop playback for current track.
		 */
		void stop() {
			if (player != null) {
				switch (player.getStatus()) {
				case PLAYING: case PAUSED:
					player.stop();
				default:
				}
			}
		}


		/**
		 * Quits this player, unloading media for current track, disabling controls
		 * and resetting its state values to default.
		 */
		void quit() {
			quit = true;
			if (player != null) {
				player.dispose();
				player = null;
			}
			timeSlider.setDisable(true);
			volumeSlider.setDisable(true);
			track = null;
			duration = null;
			durationText = null;
			currentSecond = -1;
			playButton.setGraphic(PLAY_ICON);
			nowPlayingInfo.setText("");
			disablePlayerButtons(true);
		}


		/**
		 * Disables/enables player's control buttons
		 */
		private void disablePlayerButtons(boolean disable) {
			playButton.setDisable(disable);
			stopButton.setDisable(disable);
			previousButton.setDisable(disable);
			nextButton.setDisable(disable);
		}


		/**
		 * Sets up previous/next button for playing specified track.
		 * 
		 * @param button previous or next button
		 * @param track a track to play when button is pressed
		 */
		private void prepareSwitchButton(Button button, Track track) {
			if (track == null)
				button.setDisable(true);
			else {
				button.setDisable(false);
				button.setOnAction(event -> {
					boolean wasPlaying = isPlaying();
					setTrack(track);
					if (wasPlaying)
						play();
				});
			}
		}


		/**
		 * Highlights the currently loaded track by setting bold font
		 * style for corresponding table row in a track list view.
		 */
		private void highlightCurrentTrack() {
			trackListView.playButtons.forEach(button -> {
				if (button != null) {
					Parent parent = button.getParent();
					if (parent instanceof TableCell) {
						TableRow<?> row = ((TableCell<?,?>)parent).getTableRow();
						highlightTableRow(row, row.getItem() == track);
					}
				}
			});
		}


		/**
		 * Updates play buttons in a track list view so that they conform to
		 * audio player current state.
		 */
		private void updateTrackListButtons() {
			if (quit)
				return;

			int thisTrackIndex = track.number() - 1;
			for (int i = 0; i < trackListView.playButtons.size(); i++) {
				TrackListButton button = trackListView.playButtons.get(i);
				if (button != null) {
					if (i == thisTrackIndex) {
						// index corresponding to current track
						if (isPlaying()) {
							button.setPauseIcon();
							button.setOnAction(event -> pause());
						}
						else {
							button.setPlayIcon();
							button.setOnAction(event -> play());
						}
					}
					else {
						button.setPlayIcon();
						int otherTrackIndex = i;
						button.setOnAction(event -> {
							// no need for isPlayable() check here because buttons are 
							// created only for playable tracks 
							setTrack(trackListView.track(otherTrackIndex));
							play();
						});
					}
				}
			}
		}


		/**
		 * Updates information about current track's progress.
		 * This includes updating of time slider to reflect current track time as
		 * well as updating nowPlayingInfo label with artist/title and 
		 * elapsed/total track time.
		 */
		private void updateTrackProgress() {
			if (quit)
				return;

			Duration currentTime = player.getCurrentTime();
			long second = currentTime.equals(player.getStopTime()) 
					? Math.round(currentTime.toSeconds()) : (long)currentTime.toSeconds();
			if (second == currentSecond)
				// prevents updating more than once in a second
				return;
			else
				currentSecond = second;

			nowPlayingInfo.setText(getNowPlayingInfo(track, currentTime, durationText));

			timeSlider.setDisable(duration == null || duration.isUnknown());
			if (!timeSlider.isDisabled() && duration.greaterThan(Duration.ZERO) && !timeSlider.isValueChanging()) {
				preventTimeSliderSeek = true; // prevents unnecessary firing of time slider seek listener
				timeSlider.setValue(currentTime.divide(duration.toMillis()).toMillis() * timeSlider.getMax());
				preventTimeSliderSeek = false;
			}
		}


		/**
		 * Builds a string to display in nowPlayingInfo label using current track,
		 * current time and total time.
		 * 
		 * @param track a track
		 * @param currentTime a current track time elsapsed
		 * @param totalTimeText text representation of total duration of a track
		 * @return info text
		 */
		private String getNowPlayingInfo(Track track, Duration currentTime, String totalTimeText) {
			if (track != null && currentTime != null && totalTimeText != null)
				return new StringBuilder(track.artist()).append(" - ").append(track.title())
						.append(" (").append(formatTime(currentTime)).append('/')
						.append(totalTimeText).append(')').toString();
			else
				return "";
		}


		/**
		 * Converts given duration to a string representation using HH:mm:ss or
		 * mm:ss format, depending on its length.
		 * 
		 * @param duration a duration to convert
		 * @return duration as text
		 */
		private String formatTime(Duration duration) {
			return Time.formatSeconds(!duration.equals(Duration.INDEFINITE) && !duration.equals(Duration.UNKNOWN)
					? (int)Math.round(duration.toSeconds())
					: 0);
		}

	}



	/**
	 * Creates an instance of release player form.
	 */
	private ReleasePlayerForm(Window owner, BandcampExplorerMainForm mainForm) {
		this.mainForm = mainForm;

		Scene scene = new Scene(this);
		scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());

		stage = new Stage();
		stage.initOwner(owner);
		stage.setTitle(NO_RELEASE_TITLE);
		stage.setResizable(false);
		stage.setOnCloseRequest(event -> {
			stage.hide();
			event.consume();
		});
		stage.addEventFilter(KeyEvent.KEY_PRESSED, createHotkeyFilter());

		stage.setScene(scene);
	}


	/**
	 * Creates a filter for player form hotkeys.
	 */
	private EventHandler<KeyEvent> createHotkeyFilter() {
		KeyCombination CTRL_C = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
		KeyCombination CTRL_V = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);
		return keyEvent -> {
			if (keyEvent.getCode() == KeyCode.B) {
				focusAndFire(nextButton);
			}
			else if (keyEvent.getCode() == KeyCode.Z) {
				focusAndFire(previousButton);
			}
			else if (keyEvent.getCode() == KeyCode.X) {
				focusAndFire(playButton);
			}
			else if (keyEvent.getCode() == KeyCode.C && !CTRL_C.match(keyEvent)) {
				focusAndFire(playButton);
			}
			else if (keyEvent.getCode() == KeyCode.V) {
				if (CTRL_V.match(keyEvent)) {
					releaseUrlLoader.loadFromClipboard();
					keyEvent.consume();
				}
				else {
					focusAndFire(stopButton);
				}
			}
			else if (keyEvent.getCode() == KeyCode.ESCAPE) {
				stage.hide();
			}
		};
	}


	/**
	 * Requests focus and invokes action installed for the specified button.
	 * Does notthing is button is disabled.
	 */
	private static void focusAndFire(Button button) {
		if (!button.isDisabled()) {
			button.requestFocus();
			button.fire();
		}
	}


	/**
	 * Loads an icon image for player control button.
	 * 
	 * @param name icon name
	 * @return icon image; null, if icon with such name is not found
	 */
	private static Image loadIcon(String name) {
		URL url = ReleasePlayerForm.class.getResource(name);
		return url != null ? new Image(url.toString()) : null;
	}


	/**
	 * Creates an instance of release player form component.
	 * 
	 * @param owner the owner of player form window
	 * @param mainForm reference to app's main form
	 * @throws NullPointerException if owner is null
	 * @throws IllegalStateException if this method has been called more than once
	 *         or if it is called from the thread other than JavaFX Application Thread
	 */
	static ReleasePlayerForm create(Window owner, BandcampExplorerMainForm mainForm) {
		assert INSTANCE == null;
		assert owner != null;
		assert mainForm != null;
		
		return (INSTANCE = Utils.loadFXMLComponent(
				ReleasePlayerForm.class.getResource("ReleasePlayerForm.fxml"),
				() -> new ReleasePlayerForm(owner, mainForm)));
	}


	/**
	 * Shows and brings to front player window if it was hidden.
	 */
	void show() {
		stage.show();
	}


	/**
	 * Sets a release to play in this player.
	 * 
	 * If there was no release in player, sets the release, loads
	 * all necessary stuff, prepares the player to play first playable
	 * track on this release (if any) and brings player's window to front.
	 * 
	 * If there's another release loaded in a player, interrupts the playback,
	 * unloads release data and sets a new one as described above.
	 * 
	 * If attempted to set the same release object that is already set
	 * for this player, does nothing except bringing player's window to front.
	 * 
	 * If null release is set, unloads the current release (if any), interrupts
	 * the playback.
	 * 
	 * @param release a release
	 */
	void setRelease(Release release) {
		if (this.release.isPresent() && this.release.valueEquals(release)) {
			// For same release just bring window to front
			show();
			return;
		}
		else {
			this.release.set(release);
			audioPlayer.quit();
		}

		if (release != null) {
			// Loading artwork
			artworkView.setImage(release.artworkLink().map(link -> new Image(link, true)).orElse(null));
			
			// Setting release info
			releaseInfo.setText(createReleaseInfo(release));

			// Setting the tracklist
			List<Track> tracks = release.tracks();
			trackListView.clear();
			trackListView.addAll(tracks);
			// Grow list of play buttons to match number of tracks on release so references
			// to new buttons can later be added via trackListView.playPuttons.set()
			// on cells construction
			tracks.forEach(track -> trackListView.playButtons.add(null));

			// Prepare the first playable track to play
			Track first = trackListView.firstPlayableTrack();
			if (first != null)
				audioPlayer.setTrack(first);

			// Show the window
			stage.setTitle(release.artist() + " - " + release.title());
			show();
			stage.requestFocus();
			playButton.requestFocus();
		}
		else {
			loadReleaseButton.requestFocus();
			artworkView.setImage(null);
			releaseInfo.clear();
			trackListView.clear();
			stage.setTitle(NO_RELEASE_TITLE);
		}
	}


	/**
	 * Creates a string with information about release to place into text area
	 * on a player form.
	 * 
	 * @param release a release to create info for
	 */
	private static String createReleaseInfo(Release release) {
		StringBuilder info = new StringBuilder();

		info.append(release.artist()).append(" - ").append(release.title()).append('\n').append('\n');
		release.parentReleaseLink().ifPresent(link -> info.append("FROM: ").append(link).append('\n'));
		info.append("PUBLISHED: ").append(release.publishDate()).append('\n');
		info.append("RELEASED: ").append(release.releaseDate().map(LocalDate::toString).orElse("-")).append('\n');
		info.append("DOWNLOAD TYPE: ").append(release.downloadType()).append('\n');
		info.append("PRICE: ").append(release.price()).append('\n');
		info.append("TIME: ").append(release.time()).append('\n');
		info.append("TAGS: ").append(release.tagsString()).append('\n');
		release.information().ifPresent(information -> info.append('\n').append(information).append('\n'));
		release.credits().ifPresent(credits -> info.append('\n').append(credits));

		return info.toString();
	}


	/**
	 * Plays a track selected in track list view.
	 */
	private void playSelectedTrack() {
		trackListView.ifTrackSelected(track -> {
			if (track.isPlayable()) {
				audioPlayer.setTrack(track);
				audioPlayer.play();
			}
		});
	}


	/**
	 * Handler for key press on table view.
	 * If Enter was pressed, plays selected track.
	 */
	@FXML
	private void onTracksTableKeyPress(KeyEvent event) {
		if (event.getCode() == KeyCode.ENTER)
			playSelectedTrack();
	}


	/**
	 * Handler for mouse click on a table view.
	 * If double-clicked, plays selected track.
	 */
	@FXML
	private void onTracksTableMouseClick(MouseEvent event) {
		if (event.getClickCount() > 1)
			playSelectedTrack();
	}


	/**
	 * Loads the release from URL supplied via dialog.
	 */
	@FXML
	private void loadReleaseFromDialog() {
		releaseUrlLoader.loadFromDialog();
	}


	/**
	 * Unloads currently loaded release from the player.
	 */
	@FXML
	private void unloadRelease() {
		setRelease(null);
	}


	/**
	 * Handles Enter key press event directed to button by invoking the action 
	 * installed on that button.
	 * If event's target is not a button or target button is disabled or does not
	 * have action installed, then does nothing.
	 */
	@FXML
	private void runButtonActionOnEnter(KeyEvent event) {
		if (event.getCode() == KeyCode.ENTER) {
			EventTarget target = event.getTarget();
			if (target instanceof Button)
				((Button)target).fire();
		}
	}


	/**
	 * Opens release web page on Bandcamp.
	 */
	@FXML
	private void openReleasePage() {
		release.ifPresent(release -> Utils.browse(release.uri()));
	}


	/**
	 * Opens a Bandcamp discography page on this release's parent domain.
	 */
	@FXML
	private void openDiscographyPage() {
		release.ifPresent(release -> Utils.browse(release.discographyURI()));
	}


	/**
	 * Opens the download web page on Bandcamp if free download link is available
	 * for current release.
	 * If there's no free download link, does nothing.
	 */
	@FXML
	private void openDownloadPage() {
		release.ifPresent(release -> release.downloadLink().ifPresent(Utils::browse));
	}


	/**
	 * Initialization method invoked by FXML loader, provides initial setup for components.
	 */
	@FXML
	private void initialize() {
		checkComponents();
		initMenuButtons();
		initLinks();
		initPlayerControls();
		initTrackListView();		
	}


	/**
	 * Initialization for menu buttons.
	 */
	private void initMenuButtons() {
		unloadReleaseButton.disableProperty().bind(release.isNull());
		moreActionsMenu.disableProperty().bind(release.isNull());

		LabeledMenuItem searchArtist = new LabeledMenuItem(true);
		LabeledMenuItem moreFromDomain = new LabeledMenuItem(true);

		LabeledMenuItem copyReleaseText = new LabeledMenuItem("Copy Release as Text");
		copyReleaseText.setOnAction(event -> release.ifPresent(Utils::toClipboardAsString));

		LabeledMenuItem copyURL = new LabeledMenuItem("Copy URL");
		copyURL.setOnAction(event -> release.ifPresent(release -> Utils.toClipboardAsString(release.uri())));

		LabeledMenuItem copyDiscographyURL = new LabeledMenuItem("Copy Discography URL");
		copyDiscographyURL.setOnAction(event -> release.ifPresent(
				release -> Utils.toClipboardAsString(release.discographyURI())));

		ObservableList<MenuItem> menuItems = moreActionsMenu.getItems();

		moreActionsMenu.showingProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue) {
				release.ifPresent(release -> {
					boolean isRunningSearch = mainForm.isRunningSearch();

					String artist = release.artist();
					searchArtist.setLabelText(String.format("Search \"%s\"", artist));
					searchArtist.setOnAction(
							actionEvent -> mainForm.searchReleases(artist, SearchType.SEARCH));
					searchArtist.setDisable(isRunningSearch);

					URI discogURI = release.discographyURI();
					moreFromDomain.setLabelText(String.format("More from \"%s\"", discogURI.getAuthority()));
					moreFromDomain.setOnAction(
							actionEvent -> mainForm.searchReleases(discogURI.toString(), SearchType.DIRECT));
					moreFromDomain.setDisable(isRunningSearch);
				});
				// Re-add changed items to let the menu correctly resize itself
				menuItems.set(0, searchArtist);
				menuItems.set(1, moreFromDomain);
			}
		});

		menuItems.addAll(searchArtist, moreFromDomain, new SeparatorMenuItem(),
				copyReleaseText, copyURL, copyDiscographyURL);
	}


	/**
	 * Initialization for hyperlinks.
	 */
	private void initLinks() {
		addLinkTooltip(releaseLink, release -> release.uri().toString());
		addLinkTooltip(discogLink, release -> release.discographyURI().toString());

		releaseLink.disableProperty().bind(release.isNull());
		discogLink.disableProperty().bind(release.isNull());

		downloadLink.visibleProperty().bind(
				Bindings.createBooleanBinding(
						() -> release.isPresent() && release.get().downloadLink().isPresent(), release));

		// Prevent links from being styled as "visited"
		BooleanProperty alwaysFalse = new SimpleBooleanProperty(false);
		releaseLink.visitedProperty().bind(alwaysFalse);
		discogLink.visitedProperty().bind(alwaysFalse);
		downloadLink.visitedProperty().bind(alwaysFalse);				
	}


	/**
	 * Helper for adding tooltips to hyperlinks.
	 */
	private void addLinkTooltip(Hyperlink link, Function<Release, String> textExtractor) {
		Tooltip tooltip = new Tooltip();
		tooltip.textProperty().bind(
				Bindings.createStringBinding(
						() -> release.extractOrElse(textExtractor, ""), release));
		link.setTooltip(tooltip);
	}


	private void initPlayerControls() {
		playButton.setGraphic(PLAY_ICON);
		stopButton.setGraphic(STOP_ICON);
		previousButton.setGraphic(PREVIOUS_ICON);
		nextButton.setGraphic(NEXT_ICON);

		// Make the volume label display volume percentage
		volumeLevel.textProperty().bind(
				Bindings.createStringBinding(
						() -> Math.round(volumeSlider.getValue()) + "%", volumeSlider.valueProperty()));
	}


	/**
	 * Performs initial setup of track list view table and its columns.
	 */
	private void initTrackListView() {
		trackListView = new TrackListView(tracksTableView);

		// Setting a custom cell factory that creates play/pause button 
		// for each playable track, saves references to these buttons for
		// later access from audio player, highlights the track that is
		// loaded in audio player and also adds a context menu for each cell
		playButtonColumn.setCellFactory(new CellFactory<>(
				CellCustomizer.cellNode(track -> {
					if (track != null && track.isPlayable()) {
						// Since this factory will be invoked not only during table initial
						// fill but also on any column sort actions, we need to adjust our
						// newly created buttons to conform with player's current state
						TrackListButton button = new TrackListButton();
						if (audioPlayer.isPlayingTrack(track))
							button.setPauseIcon();
						else
							button.setPlayIcon();
						button.setOnAction(event -> {
							if (audioPlayer.isPlayingTrack(track)) {
								button.setPauseIcon();
								audioPlayer.pause();
							}
							else {
								button.setPlayIcon();
								audioPlayer.setTrack(track);
								audioPlayer.play();
							}
						});
						// Saving button references so they could be later accessed from audio player's
						// state transition handlers
						trackListView.playButtons.set(track.number() - 1, button);
						return button;
					}
					else
						return null; // don't create buttons for null or unplayable tracks
				}),
				// Special-purpose customizer that highlights cell's parent table row if it
				// corresponds to currently loaded track
				(cell, track, empty) -> {
					if (track != null)
						highlightTableRow(cell.getTableRow(), track == audioPlayer.track());
				},
				// Also, add a customizer for context menu
				trackListContextMenu.customizer()
			)
		);
		playButtonColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
		playButtonColumn.setSortable(false);

		trackNumberColumn.setCellFactory(new CellFactory<>(trackListContextMenu.customizer()));
		trackNumberColumn.setCellValueFactory(cellData -> cellData.getValue().numberProperty());

		CellFactory<Track, String> tooltip = new CellFactory<>(
				CellCustomizer.tooltip(), trackListContextMenu.customizer());

		artistColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
		artistColumn.setCellFactory(tooltip);
		artistColumn.setCellValueFactory(cellData -> cellData.getValue().artistProperty());

		titleColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
		titleColumn.setCellFactory(tooltip);
		titleColumn.setCellValueFactory(cellData -> cellData.getValue().titleProperty());

		timeColumn.setCellFactory(new CellFactory<>(trackListContextMenu.customizer()));
		timeColumn.setCellValueFactory(cellData -> cellData.getValue().timeProperty());
	}


	/**
	 * Highlights the specified table row by applying a bold font style for its contents.
	 * 
	 * @param row a row to highlight
	 * @param highlight if true, row gets highlighted, otherwise sets style back to normal
	 */
	private static void highlightTableRow(TableRow<?> row, boolean highlight) {
		row.setStyle(highlight ? "-fx-font-weight: bold" : "-fx-font-weight: normal");
	}


	/**
	 * Helper method to check if all components defined by FXML file have been injected.
	 */
	private void checkComponents() {
		// copy-pasted from SceneBuilder's "sample controller skeleton" window
		assert volumeLevel != null : "fx:id=\"volumeLevel\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert artistColumn != null : "fx:id=\"artistColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert tracksTableView != null : "fx:id=\"tracksTableView\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert previousButton != null : "fx:id=\"previousButton\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert releaseInfo != null : "fx:id=\"releaseInfo\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert artworkView != null : "fx:id=\"artworkView\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert playButton != null : "fx:id=\"playButton\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert loadReleaseButton != null : "fx:id=\"loadReleaseButton\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert unloadReleaseButton != null : "fx:id=\"unloadReleaseButton\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert moreActionsMenu != null : "fx:id=\"moreActionsMenu\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert nextButton != null : "fx:id=\"nextButton\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert timeColumn != null : "fx:id=\"timeColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert releaseLink != null : "fx:id=\"releaseLink\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert discogLink != null : "fx:id=\"discogLink\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert downloadLink != null : "fx:id=\"downloadLink\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert volumeSlider != null : "fx:id=\"volumeSlider\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert playButtonColumn != null : "fx:id=\"playButtonColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert titleColumn != null : "fx:id=\"titleColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert nowPlayingInfo != null : "fx:id=\"nowPlayingInfo\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert stopButton != null : "fx:id=\"stopButton\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert timeSlider != null : "fx:id=\"timeSlider\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert trackNumberColumn != null : "fx:id=\"trackNumberColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
	}

}
