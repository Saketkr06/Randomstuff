# Sony TV Remote

This app turns your Android phone into a remote control for your Sony television. It can press remote buttons, switch the television on and off, open apps, type text, play a YouTube video, and start screen mirroring.

## Step 1: Prepare the television (one time)

The app talks to the television over your home Wi-Fi network. The television must allow this first.

1. On the television, open Settings, then Network & Internet, then Home network setup, then IP control.
2. Set Authentication to "Normal and Pre-Shared Key".
3. Set a Pre-Shared Key. The app starts with the key `0000`. You can use any key, but type the same one in the app.
4. Turn on "Simple IP control".
5. In the same area, turn on "Remote start" so the phone can wake the television from standby.

Menu names differ a little between models, so look for these words if the path above is not exact.

## Step 2: Build the app file

The app file is built for you by GitHub, so you do not need Android Studio.

1. This folder already sits inside the Randomstuff repository on GitHub. The build instructions are in `.github/workflows/build-sony-tv-remote.yml` at the top of the repository.
2. Open the repository on GitHub, click "Actions", choose "Build Sony TV Remote app", and click "Run workflow". Wait about five minutes. It also runs by itself whenever files in this folder change.
3. Open the finished run and download the file called `sony-tv-remote` from the Artifacts section. Unzip it to get `app-debug.apk`.

## Step 3: Install on your phone

1. Copy `app-debug.apk` to your phone and open it.
2. Allow "Install unknown apps" when the phone asks.
3. Open "Sony TV Remote". Keep the phone on the same Wi-Fi network as the television.

## Using the app

- **Find TV** searches your Wi-Fi network and lets you pick the television. You can also type its address by hand.
- **Arrow buttons, OK, Back, Home, Input, volume, play, pause** work like the real remote.
- **Apps** shows the apps installed on the television and opens the one you pick.
- **Send text to TV** types into a search box that is open on the television.
- **Play YouTube on TV** takes a YouTube link and plays it on the television.
- **Cast phone screen** opens the phone's own screen mirroring list (Smart View or Cast). Choose your television there.
- **Share to TV:** in the YouTube app, a browser, or a chat, tap Share on a YouTube link and choose "Sony TV Remote". The video starts on the television. This works for YouTube links only.
- **Phone volume buttons** change the television volume while the app is open on screen.

## If something does not work

- Nothing happens: check that the pre-shared key in the app matches the one on the television.
- "TV refused access": the key is wrong or IP control is off.
- YouTube does not start: make sure the phone and television are on the same Wi-Fi, then try Find TV again.
- If the build fails, open the failed run on GitHub and send the error text to Claude.
