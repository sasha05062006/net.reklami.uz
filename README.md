# Zevs — WireGuard VPN for Android

Zevs is a focused Android WireGuard client. It imports a provider-supplied .conf file and routes device traffic through that profile.

## Current implementation

- Official WireGuard Android userspace tunnel backend.
- Full-device routing: all apps except Zevs itself are routed through the imported profile.
- The imported profile's DNS settings are preserved. Zevs does not replace DNS with AdGuard or another resolver.
- Imported profile is encrypted with AES-GCM using a non-exportable Android Keystore key.
- Live tunnel RX/TX counters from WireGuard backend statistics.
- Auto-connect after device reboot when enabled in the app.
- Android Always-on VPN callback support; the user can enable Always-on and lockdown in Android VPN settings.
- Futuristic Compose interface with animated tunnel visualization.
- No ad blocker, no DNS interception, no app split-tunneling UI. This is intentionally VPN-only to avoid breaking apps such as ANI X ART.

## Important requirements and limitations

- Zevs does not provide VPN servers or free internet. You must import a valid WireGuard profile from a VPN provider or your own server.
- The provider profile must contain a default route (AllowedIPs = 0.0.0.0/0 or ::/0) for full-tunnel routing. A dual-stack profile should contain both IPv4 and IPv6 default routes if the provider supports IPv6.
- Auto-connect after reboot depends on Android/Samsung background-start restrictions and the device's battery settings. For the most reliable recovery, enable Android's Always-on VPN for Zevs in system VPN settings. Enable Block connections without VPN only if you want strict lockdown and accept that internet access will stop whenever the tunnel is unavailable.
- WireGuard's own userspace backend handles network changes and tunnel socket rebinding; the app does not force DNS rewriting or proxy traffic.
- A debug APK is for testing, not a signed Play Store release.

## Build

GitHub Actions builds app-debug.apk with :app:assembleDebug. Download the Zevs-debug-apk artifact from a successful workflow run.
