# Re-start

**An old-school music player, reborn on your phone.** Re-start turns your Android phone into a retro click-wheel music player: scroll with the wheel, press the center to choose, MENU to go back. It plays the music on your phone, and finds you new songs from YouTube Music when you want them.

<p align="center">
  <img src="docs/screenshots/main-menu.png" width="200" alt="Main menu">
  <img src="docs/screenshots/now-playing.png" width="200" alt="Now Playing">
  <img src="docs/screenshots/songs.png" width="200" alt="Songs">
  <img src="docs/screenshots/extras.png" width="200" alt="Extras">
</p>

## Controls

| Do this | In a menu | On Now Playing |
| --- | --- | --- |
| Turn the wheel | Scroll (faster turns go further) | Change the volume |
| Press the center | Select | Show synced lyrics |
| Hold the center | Options for the highlighted item | Options for the song (Up Next, Start Mix, sleep timer…) |
| Press MENU | Go back | Go back |
| Press ⏮ / ⏭ | Previous / next song | Previous / next song |
| Hold ⏮ / ⏭ | | Rewind / fast-forward |

Every step gives a light vibration, with an optional click sound.

## Features

### Now Playing

| Feature | What it does |
| --- | --- |
| Spinning record | The cover is a record that spins while the music plays, and slides away for the next song |
| Record change | A short pause between songs, like putting on another record (can be turned off) |
| Background | Takes the cover's colors and fades from song to song |
| Animations | All of them turn off with one switch in Extras |

### Your music

| Feature | What it does |
| --- | --- |
| Music folder | Plays the songs in one folder of your choice (Music by default) |
| Playlists | Create, rename and reorder them; pin favorites to the main menu, where one press shuffles them |
| Search | Your songs, artists, albums and playlists |
| Lyrics | Synced lyrics from `.lrc` files next to your songs |
| Playback | Shuffle, repeat, Up Next, and a sleep timer that fades the music out |
| Stats | What you play most, and your listening history month by month |
| Delete a song | From your phone and every playlist, from inside the app |
| Backup | Save and restore your playlists, stats and settings to a file |
| In the background | Lock screen and headphone controls; can pause for phone calls, even on silent |

### New music (needs internet)

| Feature | What it does |
| --- | --- |
| **Start Mix** | On any song, like YouTube Music's "Start radio". Songs you have play from your phone; the others stream |
| **For You** | 30 new songs a day, picked from the radios of the songs you play most |
| **Download** | Paste a YouTube or YouTube Music link to save a song, with its cover and synced lyrics (from [LRCLIB](https://lrclib.net)) |
| **Import a playlist** | Into a new or existing playlist. Songs you already have aren't downloaded again; for a song that fails, Re-start can look for the same song elsewhere |

## Install

Download the latest APK from the [Releases](../../releases) page and open it on your phone (Android will ask you to allow installing apps from your browser or file manager).

| Requirement | |
| --- | --- |
| Android version | 7.0 or newer (downloading needs 10 or newer) |
| Phone | 64-bit, which is nearly every phone from the last several years |
| Size | Large: the APK carries its own copy of Python, yt-dlp and ffmpeg for the downloader |

### First run

1. Allow access to your music.
2. Open **Extras → Music folder** and pick the folder your songs are in.
3. Optional: allow notifications (download progress) and the Phone permission (**Extras → Pause for calls**).

## Build it yourself

1. Install [Android Studio](https://developer.android.com/studio) (it includes the Java version Gradle needs).
2. Clone this repository and open the folder in Android Studio.
3. Press **Run** with your phone connected (USB debugging on) or an emulator.

From a terminal:

| Command | What it does |
| --- | --- |
| `./gradlew assembleDebug` | Builds `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew testDebugUnitTest` | Runs the tests |

## How it's built

Kotlin and Jetpack Compose, with [Media3 / ExoPlayer](https://developer.android.com/media/media3) for playback and [Room](https://developer.android.com/training/data-storage/room) for playlists and stats.

| Folder | What's in it |
| --- | --- |
| `app/.../ui/device/` | The player: the click wheel, menus, Now Playing |
| `app/.../playback/` | The background service that plays the music (and streams songs) |
| `app/.../data/` | Finding songs on the phone, playlists and stats (Room), lyrics, mixes, For You |
| `downloader/` | Downloads, playlist imports and YouTube Music lookups, through yt-dlp |

The `downloader` module is separate on purpose: the player itself never needs the internet.

## Disclaimer

Downloading and streaming use [yt-dlp](https://github.com/yt-dlp/yt-dlp), which may go against YouTube's Terms of Service. Use them for music you have the right to listen to and keep. Re-start isn't affiliated with YouTube, Google or Apple.

## Credits

| Project | Used for |
| --- | --- |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) and [youtubedl-android](https://github.com/yausername/youtubedl-android) (with ffmpeg) | Downloading and streaming |
| [LRCLIB](https://lrclib.net) | Synced lyrics |
| [Media3 / ExoPlayer](https://github.com/androidx/media), Jetpack Compose and Room by Google | Playback, the interface, and the database |

## License

[GPL-3.0](LICENSE). You can use, change and share Re-start; if you share a changed version, share its source code under the same license. (The downloader library Re-start includes, youtubedl-android, is GPL-3.0 too, which is why the app as a whole is.)
