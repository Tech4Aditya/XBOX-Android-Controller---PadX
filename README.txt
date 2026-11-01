PadX - phone as Xbox controller
PC:      server/  (C++, ViGEmBus)       Phone: android/app/src/main (Kotlin + Compose)
1) Install ViGEmBus driver on the PC (reboot if asked).
2) Build server: git clone https://github.com/nefarius/ViGEmClient server/ViGEmClient
   cmake -S server -B build && cmake --build build --config Release
3) Run padx_server.exe, allow Windows Firewall on PRIVATE networks.
4) Android Studio: new "Empty Activity" (Compose) project, package com.padx.pad,
   copy the 3 .kt files + manifest over. Build, install.
5) Phone + PC on same Wi-Fi. Open app -> auto-connects. Launch Watch Dogs.
