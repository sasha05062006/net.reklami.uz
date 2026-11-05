# Zevs — Android ad blocker & privacy firewall

Package: `uz.reklami.zevs`  
Repository: `sasha05062006/net.reklami.uz`

Zevs is an Android app project targeting Samsung Galaxy S20+ (Android 13) and newer Android devices.

## Current milestone

- [x] Initial Android/Jetpack Compose project
- [x] Futuristic dark violet dashboard UI
- [x] Russian-language navigation and protection controls (UI prototype only)
- [ ] Real DNS filtering engine
- [ ] Per-app routing rules
- [ ] WireGuard tunnel integration and Poland profile import
- [ ] Traffic counters and app-level statistics
- [ ] Final app icon (waiting for user-provided artwork)

## Important status

The current dashboard is a visual prototype. The switches do **not** yet activate filtering or a VPN tunnel. Zevs will request Android's VPN permission only after the network engine is implemented. No VPN provider or Poland endpoint is bundled; a working tunnel requires a valid server/profile and provider terms must permit its use.

## Build

GitHub Actions builds a debug APK on pushes to `main`. Download it from the workflow run's Artifacts section.

## Planned network architecture

A single Android `VpnService` will own the device VPN slot. DNS filtering and the optional WireGuard tunnel must be composed inside one networking path; starting two unrelated VPN apps simultaneously is not supported by Android. Per-app VPN routing will use Android's allowed/disallowed application APIs where appropriate.

## Branding

Temporary text-based Z mark is used until the owner supplies the final Zevs icon.
