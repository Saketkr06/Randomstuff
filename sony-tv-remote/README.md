# Sony TV Remote

This app turns your Android phone into a remote control for your Sony television. It can press remote buttons, switch the television on and off, open apps, type text, play a YouTube video, and start screen mirroring.

## Step 1: Prepare the television (one time)

The app talks to the television over your home Wi-Fi network. The television must allow this first.

1. On the television, press the Quick Settings button (spanner icon), open Settings, then Network & Internet, then Home network setup (some models call it Local network setup), then IP control.
2. Turn IP control on.
3. Set Authentication to the choice that includes the Pre-Shared Key ("Pre-Shared Key" or "Normal and Pre-Shared Key").
4. Choose Pre-Shared Key and enter a key. The app starts with the key `0000`. You can use any key, but type the same one in the app.
5. Go back to Network & Internet, open Remote start, and choose "On (Powered on by apps)" so the phone can wake the television from standby.

You do not need "Simple IP control". This app uses Sony's other network control methods.

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

## Control the television from anywhere (Tailscale)

Tailscale is a free app that links your own devices over the internet, privately and securely, as if they were on one Wi-Fi network. With it, the remote works when you are away from home.

1. Install Tailscale from the Play Store on the television and sign in.
2. Install Tailscale on your phone and sign in with the same account. Keep it turned on.
3. While still at home on the television's Wi-Fi, open this app, tap "Find TV", and pick your television. This saves what the app needs for YouTube later.
4. In the Tailscale app on the television, find the television's Tailscale address. It starts with `100.`.
5. Away from home, open this app and choose "Tailscale (away from home)" in the pop-up. Type the television's Tailscale address into the address box. The app remembers it separately from your home Wi-Fi address.

What to expect:
- The remote buttons, volume, apps and typing work while the television is on.
- You cannot switch the television on from outside, because it drops off Tailscale when it is off or in standby.
- Sony's network control may refuse connections that arrive over Tailscale on some models. This has not been tested. If the buttons do nothing, the fix is to run Tailscale on an always-on device at home instead.

## Using the app

- **Connection choice:** when the app opens, a pop-up asks whether to connect over the same Wi-Fi (at home) or over Tailscale (away from home). Each choice remembers its own television address. Tap the small button under the title to change it later. A YouTube link shared to the app skips the pop-up and uses the last choice.
- **Find TV** searches your Wi-Fi network and lets you pick the television. You can also type its address by hand.
- **Arrow buttons, OK, Back, Home, Input, volume, play, pause** work like the real remote.
- **Apps** shows the apps installed on the television and opens the one you pick.
- **Send text to TV** types into a search box that is open on the television.
- **Play YouTube on TV** takes a YouTube link and plays it on the television.
- **Cast phone screen** opens the phone's own screen mirroring list (Smart View or Cast). Choose your television there.
- **Share to TV:** in the YouTube app, a browser, or a chat, tap Share on a YouTube link and choose "Sony TV Remote". The video starts on the television. This works for YouTube links only.
- **Phone volume buttons** change the television volume while the app is open on screen.
- **Television status:** the coloured line under the title shows whether the television is on, in standby, or off, and what it is playing. It updates every few seconds while the app is open. For a channel or a video input it shows the name. For YouTube started from this app it says YouTube. For other apps, such as Netflix, Sony's network control does not say what they play, so it shows "Home screen or an app".

## If something does not work

- Nothing happens: check that the pre-shared key in the app matches the one on the television.
- "TV refused access": the key is wrong or IP control is off.
- YouTube does not start: make sure the phone and television are on the same Wi-Fi, then try Find TV again.
- If the build fails, open the failed run on GitHub and send the error text to Claude.
