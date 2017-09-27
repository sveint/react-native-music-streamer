# react-native-music-streamer

A react-native audio streaming module **(for Android only)**, using [ExoPlayer](https://github.com/google/ExoPlayer).

## Features

- Music playback using foreground service
- Fast playback start due to using ExoPlayer
- Configurable notification
- Autoupdate current title from stream (Shoutcast/Icecast compatible format)
- Stops when headphone unplugs and for incoming calls

## Installation

`npm install react-native-music-streamer --save`

Then run the following command to link to your Android project

`react-native link react-native-music-streamer`

## Usage

### Basic

```javascript
import RNMusicStreamer from 'react-native-music-streamer';

// Prepare player
RNMusicStreamer.prepare(
  'http://someurl', // stream url
  {
    title: str // "title" for notification (optional)
    album: str, // "album" for notification (optional)
    artist: str, // "artist" for notification (optional)
    metadataFromStream: bool, // If true, title will be set from stream metadata. Updates every 10s. 'title' property will be used as placeholder. (optional)
    artwork: obj/str, // artwork for notification. Either a url as string, or a react-native format {uri: ...} object for local image. (optional)
  }
)
RNMusicStreamer.play()
RNMusicStreamer.pause()
RNMusicStreamer.stop()
RNMusicStreamer.seekToTime(16) //seconds
RNMusicStreamer.duration().then(duration => {
  console.log(duration) //seconds
})
RNMusicStreamer.currentTime().then(currentTime => {
  console.log(currentTime) //seconds
})

// Player Status:
// - PLAYING
// - PAUSED
// - STOPPED
// - FINISHED
// - BUFFERING
// - ERROR
RNMusicStreamer.status().then(status => {
  console.log(status)
})

RNMusicStreamer.getCurrentUrl().then(url => {
  console.log(url)
})

```

### Status Change Observer
The status changes a lot upon preparation, so you should consider some debounce mechanism.

```javascript
const {
  DeviceEventEmitter
} = 'react-native'

// Player Status:
// - PLAYING
// - PAUSED
// - STOPPED
// - FINISHED
// - BUFFERING
// - ERROR
DeviceEventEmitter.addListener(
  'RNMusicStreamerStatusChanged',
  status => console.log('Status changed: ', status)
)

```

### Metadata Change Observer

Called every 10 seconds if using `{metadataFromStream: true}` in `prepare()`. Lets you use the metadata in your application.

```javascript
const {
  DeviceEventEmitter
} = 'react-native'

DeviceEventEmitter.addListener(
  'RNMusicStreamerMetadataChanged',
  metadata => console.log('Metadata changed: ', metadata)
)

```

## TODO

- Code cleanup
- Add bandwith usage data
- Better handling of service lifecycle (it now lives as long as the module)

## Credits

This project is built upon [react-native-audio-streamer](https://github.com/indiecastfm/react-native-audio-streamer) (MIT), adding more functionality for Android.

Icons by [Font Awesome](http://fontawesome.io/).

