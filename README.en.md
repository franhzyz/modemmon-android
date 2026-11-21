# ModemMon — modem and router monitor for OpenWrt

[Русский](README.md) | **English**

Android app for aiming the antenna, monitoring the modem and managing an
OpenWrt router (tested with Foxconn T99W175, Snapdragon X55, QMI, OrangePi Zero3).

## Screenshots

| Signal monitor | RSRP (antenna aiming) |
|---|---|
| ![Monitor](docs/screenshots/monitor.png) | ![RSRP](docs/screenshots/rsrp.png) |

## Features

- Signal monitor: SINR/RSRP/RSRQ/RSSI, graphs, peak signal
- **Parking-sensor sound** (sound setting): while aiming the antenna, the beep
  gets faster the closer you get to the peak + vibration (enable in app options)
- One-tap modem switching: primary ↔ backup channel
- Speed test (Yandex.Internetometer) from the router over the selected channel
- Forkop: status, dashboard, section test, diagnostics, section/link editor
- SMS · USSD · AT commands, cells/bands
- Network: interfaces, Wi-Fi, routes, DHCP, DNS, firewall
- VPN (WireGuard/AmneziaWG), services, UCI config, terminal, files, Wake-on-LAN

## Requirements

The app uses two independent parts:

- **Modem engine** — `medvemodem`, `luci-app-medvemodem` and related packages
  from [Medvedolog/medvemodem](https://github.com/Medvedolog/medvemodem).
  They provide the `medvemodem` ubus object and channel control.
- **Our telemetry package** — `modem-telemetry`. It serves
  `/cgi-bin/modem-telemetry` (schema v2) and is attached to
  [this repository's releases](https://github.com/franhzyz/modemmon-android/releases).
  It is not included in the Medvedolog archives.

## Installing packages on the router

The script [`openwrt/install-modemmon.sh`](openwrt/install-modemmon.sh) installs
Medvedolog's packages and detects the architecture and OpenWrt version itself:

```sh
sh install-modemmon.sh
```

Then install our telemetry package:

```sh
apk add --allow-untrusted ./modem-telemetry-2.6.1-r0.apk
```

`modem-telemetry` is built as `arch=noarch`: the same APK works on
aarch64_cortex-a53, aarch64_generic and x86_64, on OpenWrt 24.10 and 25.12.
Architecture-named archives are only aliases containing the same APK.

## Installing the app

Download the APK from [releases](https://github.com/franhzyz/modemmon-android/releases)
and install it on your phone. Alpha/test build.

## Languages

The app is bilingual: Russian and English. Switch in Options → "Language".

## Feedback

The app is under development — **send feedback and feature requests**:
what you would like to see, what is inconvenient, what to add.
Use [Issues](https://github.com/franhzyz/modemmon-android/issues) —
frequently requested items go into upcoming versions.

## Building from source

```sh
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`
