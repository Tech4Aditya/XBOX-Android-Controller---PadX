# PadX

**Turn your Android phone into a wireless Xbox controller for your PC.**

PadX is a two-part system: a Kotlin/Jetpack Compose app that renders an Xbox One style gamepad on your phone, and a small C++ Windows server that exposes a **virtual Xbox 360 controller** through [ViGEmBus](https://github.com/nefarius/ViGEmBus). Games see a real XInput pad, so button prompts, analog sticks, and in-game controller support work without any key remapping.

> No controller? Open the app, open the server, play.

<!-- Add screenshots here -->
<!-- ![PadX controller screen](docs/controller.png) -->

---

## Features

- **Xbox One layout**: LT/LB, RT/RB, two analog sticks with L3/R3, D-pad, A/B/X/Y, Back, Start, and a Guide button
- **Zero-config pairing**: the phone discovers the PC automatically over the local network. Manual IP entry is available as a fallback
- **Low latency**: input is streamed over UDP at roughly 125 Hz in tiny 15-byte packets
- **Multitouch**: sticks and buttons work simultaneously, with haptic feedback on button press
- **Fully customizable layout**: drag any control to move it and resize it with a slider, and the layout persists across launches
- **Failsafe release**: if the phone disconnects, the server releases all inputs after 1.5 seconds so your character does not keep running
- **Native XInput**: appears in Windows as "Xbox 360 Controller" (verify with `joy.cpl`)

---

## How it works

```
┌────────────────────┐     UDP :26760      ┌──────────────────────┐     XInput     ┌──────┐
│  Android app       │  ────────────────►  │  padx_server.exe     │ ─────────────► │ Game │
│  Kotlin + Compose  │   15-byte state     │  C++ / Winsock       │  virtual X360  └──────┘
│                    │  ◄────────────────  │  ViGEmClient         │     pad
└────────────────────┘   discovery reply   └──────────────────────┘
```

1. On launch, the phone broadcasts `CYBERPAD_DISCOVER` to the subnet broadcast address and `255.255.255.255`.
2. The server replies `CYBERPAD_HERE` straight back to the sender.
3. The phone locks onto that IP and streams controller state every ~8 ms.
4. The server validates each packet, drops stale or duplicate ones using a sequence number, and forwards the state to the virtual pad.

### Packet format

All values are little-endian, 15 bytes total.

| Offset | Size | Field     | Notes                                      |
|-------:|-----:|-----------|--------------------------------------------|
| 0      | 1    | `magic`   | Always `0xCB`                              |
| 1      | 2    | `seq`     | Wrapping counter, used to discard stale packets |
| 3      | 2    | `buttons` | XInput `wButtons` bitmask, used as-is      |
| 5      | 1    | `lt`      | Left trigger, 0 or 255 (digital)           |
| 6      | 1    | `rt`      | Right trigger, 0 or 255 (digital)          |
| 7      | 2    | `lx`      | Left stick X, signed 16-bit                |
| 9      | 2    | `ly`      | Left stick Y, signed 16-bit (up is positive) |
| 11     | 2    | `rx`      | Right stick X                              |
| 13     | 2    | `ry`      | Right stick Y                              |

Button bits follow the standard XInput mapping:

| Button | Mask   | Button | Mask   |
|--------|--------|--------|--------|
| D-Up   | 0x0001 | LB     | 0x0100 |
| D-Down | 0x0002 | RB     | 0x0200 |
| D-Left | 0x0004 | Guide  | 0x0400 |
| D-Right| 0x0008 | A      | 0x1000 |
| Start  | 0x0010 | B      | 0x2000 |
| Back   | 0x0020 | X      | 0x4000 |
| L3     | 0x0040 | Y      | 0x8000 |
| R3     | 0x0080 |        |        |

---

## Repository layout

```
padx/
├── server/
│   ├── server.cpp          # Windows UDP server + ViGEm virtual pad
│   └── CMakeLists.txt
└── android/app/src/main/
    ├── AndroidManifest.xml # landscape, INTERNET + Wi-Fi state permissions
    └── java/com/padx/pad/
        ├── MainActivity.kt # connect screen, auto-discovery, fullscreen setup
        ├── Net.kt          # discovery, UDP streaming, shared pad state
        └── Ui.kt           # controls, sticks, layout editor, layout persistence
```

---

## Requirements

**PC**
- Windows 10/11 (x64 or ARM64)
- [ViGEmBus driver](https://github.com/nefarius/ViGEmBus/releases) installed (reboot if prompted)
- To build: CMake 3.15+, Visual Studio 2022 or Build Tools with the **Desktop development with C++** workload (add the ARM64 component if you are on an ARM64 machine), and Git

**Phone**
- Android 8.0 (API 26) or newer
- Same Wi-Fi network as the PC
- To build: Android Studio with the Android SDK matching the project's `compileSdk`

---

## Build and run

### 1. PC server

```powershell
# install the ViGEmBus driver first, then:
cd padx\server
git clone https://github.com/nefarius/ViGEmClient

cd ..
cmake -S server -B build -G "Visual Studio 17 2022" -A ARM64   # use -A x64 on Intel/AMD machines
cmake --build build --config Release
```

Run `build\Release\padx_server.exe`. When Windows Firewall asks, allow access on **Private networks**. The server prints its local IP addresses on startup, which is useful for manual connection.

### 2. Android app

1. In Android Studio create a new **Empty Activity** (Compose) project named `PadX` with the package name **`com.padx.pad`**.
2. Delete the generated `MainActivity.kt`.
3. Copy `AndroidManifest.xml`, `MainActivity.kt`, `Net.kt`, and `Ui.kt` from this repo into the matching locations.
4. If Gradle complains that a dependency needs a newer API level, raise `compileSdk` in `app/build.gradle.kts` to the version it asks for. Leave `minSdk` and `targetSdk` alone.
5. Enable **USB debugging** on your phone (tap Build number 7 times in About phone, then toggle it in Developer options) and press Run. Alternatively use *Build > Build APK(s)* and install the APK manually.

---

## Usage

1. Start `padx_server.exe` on the PC.
2. Open PadX on the phone. It searches for the PC and connects automatically.
3. Press Win+R, type `joy.cpl`, and confirm an **Xbox 360 Controller** appears and reacts to your touches.
4. Launch your game.

If auto-discovery fails, type the PC's IP address (printed by the server) into the app and press **Connect**.

### Customizing the layout

Tap **EDIT LAYOUT** at the bottom of the controller screen.

- **Move**: drag any control
- **Resize**: tap a control, then use the size slider (50% to 200%)
- **Reset**: restore the default Xbox layout
- **Done**: save and return to play mode

Input is disabled while editing. Positions are stored as fractions of the screen, so the layout adapts across screen sizes. Resizing scales a whole cluster, such as the D-pad or the ABXY group, not its individual buttons.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `CMAKE_CXX_COMPILER not set` or `nmake` not found | No C++ compiler installed. Install Visual Studio Build Tools (Desktop development with C++) and use the `-G "Visual Studio 17 2022"` generator |
| `-A ARM64` fails | The ARM64 build tools component is missing. Install it, or fall back to `-A x64` |
| Server says "ViGEmBus driver not found" | Install ViGEmBus and reboot |
| Phone says "No PC found" | Same Wi-Fi? VPN off? Windows network profile set to **Private**? Firewall allowed? Router "client isolation" off? Otherwise enter the IP manually |
| App build fails: dependency requires newer `compileSdk` | Install that SDK platform in SDK Manager and raise `compileSdk` in `app/build.gradle.kts` |
| Game ignores the controller | Check `joy.cpl` first. If the pad shows up there, check the game's input settings or run the game after the server is already running |
| Phone as hotspot, discovery fails | Broadcast is unreliable on hotspots. Use manual IP |

---

## Limitations

- Triggers are digital (fully pressed or released), not analog
- The Guide button may not be exposed to games through the standard XInput API
- Windows only on the PC side
- **No authentication or encryption.** Anyone on the same network who can reach UDP port 26760 can send input to your PC. Use trusted networks only, and close the server when you are done
- ViGEmBus is archived upstream and no longer actively developed. It still works, but it is on borrowed time

## Roadmap ideas

- Analog trigger sliders
- Pairing code or QR code to prevent rogue input on shared networks
- Gyro and motion aiming
- Multiple saved layout profiles
- Per-button resize inside clusters
- Rumble feedback from the game to phone vibration
- Wi-Fi Direct / USB tethering transport option

---

## Tech stack

Kotlin, Jetpack Compose, Kotlin Coroutines, C++17, Winsock2, [ViGEmClient](https://github.com/nefarius/ViGEmClient), CMake.

## Author

Built by Aditya Pandey ([@Tech4Aditya](https://github.com/Tech4Aditya)).

## License
This build has its own license.
Note that ViGEmClient and ViGEmBus have their own licenses.