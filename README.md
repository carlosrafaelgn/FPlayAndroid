FPlayAndroid
============

FPlay for Android is a simple and straightforward music player, with equalizer and bass boost.

Is is remotely based on my old J2ME player FPlay, also available on GitHub: https://github.com/carlosrafaelgn/FPlay

The main goal of this project is to provide the users with a fully functional music player based on lists and folders. The player itself does not consume too much memory.

Moreover, this source code can be used as an example on how to do a few common tasks in Android:
- Extract ID3v1 and ID3v2 information from MP3 and AAC files without android.media.MediaMetadataRetriever
- Reduce the latency between consecutive tracks using setNextMediaPlayer()
- Apply audio effects to music (equalizer and bass boost)
- Use android.media.audiofx.Visualizer and getFft()\
- Use keyboard for navigation and control
- Handle different screen sizes and densities
- Handle physical media buttons as well as Bluetooth commands
- Send track information via A2DP Bluetooth (using com.android.music.playbackcomplete, com.android.music.playstatechanged and com.android.music.metachanged intents)
- Use custom fonts as vector icons instead of bitmap files
- Create custom menus
- Create custom views
- Create custom ListView with support for multiple selection and item reordering

The player is still in a "beta stage", so there may be unknown bugs yet ;)

----

Free third-party resources used in the application:

Folder/Disc icons: http://www.24psd.com/ubuntu+icon+pack

Phone icon: http://www.psdgraphics.com/graphics/photoshop-recreation-of-google-nexus-one-smartphone-download-psd

SD card icon: http://artofapogee.blogspot.com.br/2010/02/sd-card-icon.html

The font used to draw the scalable icons, icons.ttf, was created using IcoMoon App, by Keyamoon, available at: http://icomoon.io/app

Some of the scalable icons were created by me and some came from the IcoMoon Free icon set, by Keyamoon, licensed under the Creative Commons License 3.0.

----

This projected is licensed under the terms of the FreeBSD License. See LICENSE.txt for more details.
