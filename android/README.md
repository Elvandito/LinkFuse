# LinkFuse

Bond every internet connection on your Android device into one pipe — **no root, no server,
no account, no cloud relay.**

LinkFuse creates a local VPN (`VpnService`) TUN interface, watches Wi-Fi, mobile data and
Ethernet/USB tether with `ConnectivityManager` + `NetworkCallback`, hands the validated
networks to the system via `setUnderlyingNetworks()`, and spreads *new* TCP/UDP flows
across them by weight — with per-flow stickiness so a session never jumps networks
mid-flight.

## What you get

- **One main screen** — per-connection type, live ↓/↑ speeds, combined total, flow share.
- **One persistent notification** — merging / paused / error / no connections, with
  Pause · Resume · Stop actions.
- **One Quick Settings tile** — tap to merge, tap again to pause.
- **First-run walkthrough** — notifications (Android 13+), VPN consent, battery
  optimization, foreground service; one sentence each.

## How the merging works

1. `NetworkMonitor` registers a `NetworkCallback` for Wi-Fi / cellular / Ethernet.
   A link is usable only when it is `NET_CAPABILITY_VALIDATED` and not behind a
   captive portal. Metered links are skipped unless you allow them (and never if
   they're the only option — the internet is never blocked).
2. `MergeVpnService` establishes the TUN (`0.0.0.0/0`), sets DNS from the best link,
   and calls `setUnderlyingNetworks()` in preference order (Ethernet → Wi-Fi → cellular).
3. `TunRouter` reads IPv4 packets off the TUN fd and dispatches by protocol.
4. `UdpRelay` pins each new 5-tuple to a link chosen by `WeightedPicker`, opens a
   `DatagramSocket` that is `protect()`-ed and `network.bindSocket()`-ed, and relays
   both directions.
5. `TcpProxy` is a minimal userspace TCP endpoint (the NetGuard pattern): it answers
   the client handshake and opens a normal socket to the real server on the chosen
   link. Deliberately small — in-order only, fixed window — and anything exotic gets
   a clean RST so apps retry instead of hanging.

## Platform limits (verified against developer.android.com)

- `setUnderlyingNetworks()` (API 22) is **informational/preferential**: the system uses
  it for status-bar icons and capabilities. It does *not* stripe traffic — the app
  forwards packets itself, which is exactly what LinkFuse does. On API 29+ the declared
  networks' capabilities are copied onto the VPN, so the list is kept live.
- Android exposes **no API to split a single TCP connection's bandwidth** across
  interfaces. "Per flow" means per connection; that's the platform ceiling without a
  relay server or kernel MPTCP (which apps can't bind to specific networks).
- Android 14+ requires a declared `foregroundServiceType`; LinkFuse uses `specialUse`
  with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` and the matching permission.
- Only one VPN can run at a time; starting another app's VPN revokes LinkFuse via
  `onRevoke()`, which always closes the TUN.

## Safety

- `onRevoke()` / `onDestroy()` / crash → TUN fd closed → routing returns to the system
  default network. Traffic can never be stranded.
- Zero validated links → the tunnel is torn down and traffic goes direct.
- No logging, no analytics, no telemetry, no network calls of its own except your
  actual traffic on the links you bonded.

## Build

Requires JDK 17 and Android SDK 35.

```bash
cd android
# generate the wrapper once (or just open the folder in Android Studio)
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

### Signed release for sideloading

```bash
./gradlew :app:assembleRelease
# sign with your keystore:
apksigner sign --ks my-release.jks \
  --out linkfuse-release-signed.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

Install: `adb install linkfuse-release-signed.apk` (allow "install unknown apps" for
your shell/transfer app first).

### Play Store

The same release build works on Play. In Play Console, declare the `specialUse`
foreground service use case (already written in the manifest property) under
**App content → Foreground services**. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is a
restricted permission — justify it as "keeps the merge alive while the screen is off".

## Project layout

```
app/src/main/java/dev/linkfuse/app/
├── MainActivity.kt            Compose entry, permission result wiring
├── LinkFuseApp.kt             Application — notification channel bootstrap
├── model/LinkState.kt         UiState, MergeStatus, LinkKind
├── net/NetworkMonitor.kt      ConnectivityManager + NetworkCallback discovery
├── setup/SetupChecker.kt      first-run step checks + intent builders
├── service/
│   ├── MergeController.kt     single source of truth for UI / notification / tile
│   ├── NotificationHub.kt     persistent notification + actions
│   └── MergeTileService.kt    Quick Settings tile
├── vpn/
│   ├── MergeVpnService.kt     VpnService, TUN lifecycle, safety contract
│   ├── TunRouter.kt           packet read loop + sweeper
│   ├── FlowTable.kt           5-tuple stickiness, weighted picker, counters
│   ├── UdpRelay.kt            per-flow UDP balancing
│   ├── TcpProxy.kt            minimal userspace TCP endpoint
│   └── IpPacket.kt            header building + checksums
└── ui/                        Compose screens + theme
```

## License

MIT — do what you want, no warranty.
