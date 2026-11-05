# Zevs — VPN + DNS AdBlock for Android

**Application ID:** `uz.reklami.zevs`  
**Target device:** Samsung Galaxy S20+ / Android 13 and newer  
**Repository:** `sasha05062006/net.reklami.uz`

Zevs is an Android privacy app with a Russian-language dark dashboard. It uses Android's single VPN slot and the official WireGuard Android tunnel library.

## Implemented in the current source

- Jetpack Compose dashboard and Russian UI.
- Import and validation of a WireGuard `.conf` profile from Android's document picker.
- Save the imported profile in app-private storage.
- WireGuard userspace tunnel start/stop using `com.wireguard.android:tunnel`.
- AdGuard DNS (94.140.14.14 / 94.140.15.15) can be applied to the WireGuard profile for DNS-based ad/tracker filtering.
- Per-app split tunneling: if one or more apps are selected, only those packages are included in the VPN; if none are selected, all apps except Zevs use the tunnel.
- Separate DNS-only protection mode, which leaves non-DNS traffic direct and uses DNS-over-TLS upstreams.
- DNS-only mode includes a small built-in domain denylist.

## Important limitations — not a finished production release yet

- The DNS-only mode currently supports IPv4 UDP DNS; IPv6 and TCP DNS handling are not implemented.
- The built-in denylist is small. User-editable allow/deny lists and automatic filter-list updates are not yet implemented.
- When WireGuard is active, ad blocking is provided by the selected AdGuard DNS resolver; the custom DNS-only denylist is not injected into the WireGuard tunnel.
- Traffic counters and app-level usage statistics are still placeholders.
- Autostart / always-on recovery and a full reconnect supervisor are not yet implemented.
- The app has not yet been verified on a physical Samsung Galaxy S20+.
- GitHub Actions produces a **debug APK** for installation/testing. It is not a signed Play Store release.

A valid WireGuard profile and a VPN provider/server are required. Zevs does not include or provide a Poland VPN server.

## Build

GitHub Actions builds `app-debug.apk` on pushes to `main` and pull requests. Download the `Zevs-debug-apk` artifact from the successful workflow run.

## Network architecture

Android allows one active VPN service per user profile. Zevs therefore uses the WireGuard backend for full VPN mode, and a separate DNS-only VPN mode when WireGuard is not connected. Do not start another VPN app at the same time.


CI verification commit: this branch is used to validate the debug APK build before release.
