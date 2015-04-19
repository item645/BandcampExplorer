package com.bandcamp.explorer.ui;

import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.EventTarget;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
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
import com.bandcamp.explorer.data.Time;
import com.bandcamp.explorer.data.Track;
import com.bandcamp.explorer.ui.CellFactory.CellCustomizer;

/**
 * Controller class for release player form.
 */
public class ReleasePlayerForm extends SplitPane {

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
	@FXML private Button loadReleaseButton;
	@FXML private Button unloadReleaseButton;
	@FXML private Button previousButton;
	@FXML private Button nextButton;
	@FXML private Button playButton;
	@FXML private Button stopButton;
	@FXML private ImageView artworkView;
	@FXML private Hyperlink releaseLink;
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

	private TrackListView trackListView;
	private final AudioPlayer audioPlayer = new AudioPlayer();
	private final ObjectProperty<Release> release = new SimpleObjectProperty<>(null);


	/**
	 * Thin wrapper around tracks table view also encapsulating and providing access
	 * to some associated stuff, like observable items list and list of play buttons 
	 * from the first column.
	 */
	private static class TrackListView {

		private final TableView<Track> tableView;
		private final ObservableList<Track> observableTracks = FXCollections.observableArrayList();
		private final SortedList<Track> sortedTracks = new SortedList<>(observableTracks);

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
		Track getTrack(int index) {
			return observableTracks.get(index);
		}


		/**
		 * Finds the first playable track in this list.
		 * 
		 * @return first playable track; null, if no such track found
		 */
		Track getFirstPlayableTrack() {
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
		Track getPreviousPlayableTrack(Track track) {
			ListIterator<Track> itr = observableTracks.listIterator(track.getNumber() - 1);
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
		Track getNextPlayableTrack(Track track) {
			ListIterator<Track> itr = observableTracks.listIterator(track.getNumber() - 1);
			while (itr.hasNext()) {
				Track t = itr.next();
				if (t != track && t.isPlayable())
					return t;
			}
			return null;
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

		private MediaPlayer player;
		private Track track;
		private Duration duration;
		private ChangeListener<Number> timeSliderValueListener;
		private ChangeListener<Number> volumeSliderValueListener;
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
			player = new MediaPlayer(new Media(track.getFileLink()));
			this.track = track;
			quit = false;

			setStateTransitionHandlers();
			
			highlightCurrentTrack();

			player.setVolume(volumeSlider.getValue() / volumeSlider.getMax());
			player.currentTimeProperty().addListener(observable -> updateTrackProgress());
			nowPlayingInfo.setText(LOADING_TRACK_MSG);

			timeSlider.setDisable(false);
			timeSlider.valueProperty().addListener(
				timeSliderValueListener = (observable, oldValue, newValue) -> {
					if (!quit && !preventTimeSliderSeek)
						player.seek(duration.multiply(newValue.doubleValue() / timeSlider.getMax()));
			});
			volumeSlider.setDisable(false);
			volumeSlider.valueProperty().addListener(
				volumeSliderValueListener = (observable, oldValue, newValue) -> {
					if (!quit)
						player.setVolume(newValue.doubleValue() / volumeSlider.getMax());
			});

			disablePlayerButtons(false);
			playButton.setOnAction(event -> play());
			stopButton.setOnAction(event -> stop());
			prepareSwitchButton(previousButton, trackListView.getPreviousPlayableTrack(track));
			prepareSwitchButton(nextButton, trackListView.getNextPlayableTrack(track));

			currentSecond = -1;
		}


		/**
		 * Returns a track loaded for playback in this audio player.
		 * 
		 * @return a track; null, if there's no track in player
		 */
		Track getTrack() {
			return track;
		}


		/**
		 * Assigns handlers for media player state transition events.
		 */
		private void setStateTransitionHandlers() {
			player.setOnReady(() -> {
				if (!quit) {
					duration = player.getMedia().getDuration();
					updateTrackProgress();
				}
			});
			player.setOnPlaying(() -> {
				playButton.setGraphic(PAUSE_ICON);
				playButton.setOnAction(event -> pause());
				updateTrackListButtons();
			});
			player.setOnPaused(() -> {
				playButton.setGraphic(PLAY_ICON);
				playButton.setOnAction(event -> play());
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
			});
			player.setOnEndOfMedia(() -> {
				if (!quit) {
					Track next = trackListView.getNextPlayableTrack(track);
					if (next != null) {
						updateTrackProgress();
						setTrack(next);
						play();
					}
					else {
						stop();
						updateTrackProgress();
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
			if (timeSliderValueListener != null)
				timeSlider.valueProperty().removeListener(timeSliderValueListener);
			if (volumeSliderValueListener != null)
				volumeSlider.valueProperty().removeListener(volumeSliderValueListener);
			timeSlider.setDisable(true);
			volumeSlider.setDisable(true);
			track = null;
			duration = null;
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

			int thisTrackIndex = track.getNumber() - 1;
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
							setTrack(trackListView.getTrack(otherTrackIndex));
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

			nowPlayingInfo.setText(getNowPlayingInfo(currentTime));

			timeSlider.setDisable(duration.isUnknown());
			if (!timeSlider.isDisabled() && duration.greaterThan(Duration.ZERO) && !timeSlider.isValueChanging()) {
				preventTimeSliderSeek = true; // prevents unnecessary firing of time slider seek listener
				timeSlider.setValue(currentTime.divide(duration.toMillis()).toMillis() * timeSlider.getMax());
				preventTimeSliderSeek = false;
			}
		}


		/**
		 * Builds a string to display in nowPlayingInfo label using current track time.
		 * 
		 * @param currentTime a current track time elsapsed
		 * @return info text
		 */
		private String getNowPlayingInfo(Duration currentTime) {
			return new StringBuilder(track.getArtist()).append(" - ").append(track.getTitle())
					.append(" (").append(formatTime(currentTime)).append('/')
					.append(formatTime(duration)).append(")").toString();
		}


		/**
		 * Converts given duration to a string representation using HH:mm:ss or
		 * mm:ss format, depending on its length.
		 * 
		 * @param duration a duration to convert
		 * @return duration as text
		 */
		private String formatTime(Duration duration) {
			return Time.formatSeconds((int)Math.round(duration.toSeconds()));
		}

	}


	/**
	 * Creates an instance of release player form.
	 */
	private ReleasePlayerForm(Window owner) {
		stage = new Stage();
		stage.initOwner(owner);
		stage.setTitle(NO_RELEASE_TITLE);
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
	 * @throws NullPointerException if owner is null
	 * @throws IllegalStateException if this method has been called more than once
	 */
	public static ReleasePlayerForm create(Window owner) {
		if (!Platform.isFxApplicationThread())
			throw new IllegalStateException("This component can be created only from Java FX Application Thread");
		if (INSTANCE != null)
			throw new IllegalStateException("This component can't be instantiated more than once");
		Objects.requireNonNull(owner);
		
		return (INSTANCE = Utils.loadFXMLComponent(
				ReleasePlayerForm.class.getResource("ReleasePlayerForm.fxml"),
				() -> new ReleasePlayerForm(owner)));
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
		if (this.release.get() != null && this.release.get().equals(release)) {
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
			String artworkLink = release.getArtworkThumbLink();
			artworkView.setImage(artworkLink != null ? new Image(artworkLink, true) : null);

			// Setting release page link
			releaseLink.setText(release.getURI().toString());

			// Setting release info
			releaseInfo.setText(createReleaseInfo(release));

			// Setting the tracklist
			List<Track> tracks = release.getTracks();
			trackListView.clear();
			trackListView.addAll(tracks);
			// Grow list of play buttons to match number of tracks on release so references
			// to new buttons can later be added via trackListView.playPuttons.set()
			// on cells construction
			tracks.forEach(track -> trackListView.playButtons.add(null));
			
			// Prepare the first playable track to play
			Track first = trackListView.getFirstPlayableTrack();
			if (first != null)
				audioPlayer.setTrack(first);

			// Show the window
			stage.setTitle(release.getArtist() + " - " + release.getTitle());
			show();
			stage.requestFocus();
			playButton.requestFocus();
		}
		else {
			loadReleaseButton.requestFocus();
			artworkView.setImage(null);
			releaseLink.setText(null);
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
		LocalDate releaseDate = release.getReleaseDate();
		return new StringBuilder()
		.append(release.getArtist()).append(" - ").append(release.getTitle()).append('\n').append('\n')
		.append("PUBLISHED: ").append(release.getPublishDate()).append('\n')
		.append("RELEASED: ").append(releaseDate.equals(LocalDate.MIN) ? "-" : releaseDate).append('\n')
		.append("DOWNLOAD TYPE: ").append(release.getDownloadType()).append('\n')
		.append("TIME: ").append(release.getTime()).append('\n')
		.append("TAGS: ").append(release.getTagsString()).append('\n')
		.append('\n').append(release.getInformation()).append('\n')
		.append('\n').append(release.getCredits())
		.toString();
	}


	/**
	 * Plays a track selected in track list view.
	 */
	private void playSelectedTrack() {
		Track track = tracksTableView.getSelectionModel().getSelectedItem();
		if (track != null && track.isPlayable()) {
			audioPlayer.setTrack(track);
			audioPlayer.play();
		}
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
	 * Prompts user to enter a URL to a release, loads the release using supplied
	 * URL and sets it to play in this player.
	 * If there's an error loading release, displays message box with information
	 * about error.
	 * If empty URL was supplied or cancel pressed, does nothing.
	 */
	@FXML
	private void loadRelease() {
		Optional<String> url = Dialogs.inputBox("Enter a release URL to load:", "Load Release", stage);
		if (url.isPresent()) {
			String u = url.get().trim();
			if (!u.isEmpty()) {
				try {
					if (!u.startsWith("http://") && !u.startsWith("https://"))
						u = "http://" + u;
					setRelease(Release.forURI(URI.create(u)));
				} 
				catch (Exception e) {
					String errMsg = "Error loading release: " + e;
					LOGGER.log(Level.WARNING, errMsg, e);
					Dialogs.messageBox(errMsg, "Error", stage);
				}
			}
		}
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
	 * Opens release web page on Bandcamp using default web browser.
	 */
	@FXML
	private void openReleasePage() {
		Release rls = release.get();
		if (rls != null)
			Utils.browse(rls.getURI());
	}


	/**
	 * Initialization method invoked by FXML loader, provides initial setup for components.
	 */
	@FXML
	private void initialize() {
		checkComponents();

		trackListView = new TrackListView(tracksTableView);

		// Setting a custom cell factory that creates play/pause button 
		// for each playable track, saves references to these buttons for
		// later access from audio player and also highlights the track
		// that is loaded in audio player
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
						trackListView.playButtons.set(track.getNumber() - 1, button);
						return button;
					}
					else
						return null; // don't create buttons for null or unplayable tracks
				}),
				// Special-purpose customizer that highlights cell's parent table row if it
				// corresponds to currently loaded track
				(cell, track, empty) -> {
					if (track != null)
						highlightTableRow(cell.getTableRow(), track == audioPlayer.getTrack());
				}
			)
		);
		playButtonColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
		playButtonColumn.setSortable(false);

		trackNumberColumn.setCellValueFactory(cellData -> cellData.getValue().numberProperty());

		CellFactory<Track, String> tooltip = new CellFactory<>(CellCustomizer.tooltip());

		artistColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
		artistColumn.setCellFactory(tooltip);
		artistColumn.setCellValueFactory(cellData -> cellData.getValue().artistProperty());

		titleColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
		titleColumn.setCellFactory(tooltip);
		titleColumn.setCellValueFactory(cellData -> cellData.getValue().titleProperty());

		timeColumn.setCellValueFactory(cellData -> cellData.getValue().timeProperty());

		unloadReleaseButton.disableProperty().bind(Bindings.isNull(release));

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
		assert nextButton != null : "fx:id=\"nextButton\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert timeColumn != null : "fx:id=\"timeColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert releaseLink != null : "fx:id=\"releaseLink\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert volumeSlider != null : "fx:id=\"volumeSlider\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert playButtonColumn != null : "fx:id=\"playButtonColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert titleColumn != null : "fx:id=\"titleColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert nowPlayingInfo != null : "fx:id=\"nowPlayingInfo\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert stopButton != null : "fx:id=\"stopButton\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert timeSlider != null : "fx:id=\"timeSlider\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
		assert trackNumberColumn != null : "fx:id=\"trackNumberColumn\" was not injected: check your FXML file 'ReleasePlayerForm.fxml'.";
	}

}
