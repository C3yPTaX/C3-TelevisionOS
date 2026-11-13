# C3-TelevisionOS

**Your TV. Your Rules.**

A lightweight, minimal and performance-focused TV interface for the **Xiaomi TV F Pro 75 (2026)** running **Fire OS 8**.

> **Status:** Planning / feasibility validation. This project initially targets a custom launcher and integrations **on top of Fire OS**, not a replacement firmware or full custom operating system.

## Vision

Create a clean, responsive and distraction-free Smart TV experience, visually inspired by Apple tvOS without copying its assets. Show only what is useful: entertainment apps, connected devices and essential controls.

**Principles**
- Performance first: minimal memory usage and no unnecessary services created by this project.
- Minimal UI: no ads, recommendations or unsolicited content *inside C3 Home*.
- Remote-first: excellent navigation with the stock TV remote and clear focus states.
- Modular: load features on demand, avoid always-running components.
- Preserve compatibility: use official apps and existing Fire OS services.
- Reversible: retain a verified path back to the stock launcher/settings.
- Measure, don't guess: benchmark startup time, memory, input latency and stability.

## Target device

| Item | Details |
| --- | --- |
| TV | Xiaomi TV F Pro 75 (2026) |
| Model | L75MB-FPEU |
| Firmware observed | Fire OS 8.1.8.5 |
| CPU / GPU | Quad-core Cortex-A55 / Mali-G52 MC1 |
| Memory | 2 GB RAM |
| Storage | 32 GB |

Hardware details, Android API level, permissions and firmware restrictions must be verified on-device during Phase 1.

## Scope: v1.0

### 1. C3 Home

A minimal, dark and Apple-inspired launcher, **if a custom home screen is feasible and beneficial**.

- Clean grid of selected applications and devices
- Remote-friendly focus and navigation
- Lightweight transitions and fast startup
- Optional quick access to last-used applications
- No advertising or external recommendations in our UI

### 2. Entertainment

Expose **only** the following official apps:

- Netflix
- Prime Video
- Twitch
- YouTube
- RTL+
- Disney+

Launch installed apps directly. Preserve each provider's own login/session management where supported; C3-TelevisionOS will not store provider passwords or bypass DRM.

### 3. C3 Desktop

View and optionally control existing Windows sessions on the TV.

- Select gaming PC or laptop
- Watch running apps and game clients (including Metin2)
- Optional Bluetooth keyboard/mouse interaction
- Low-latency streaming, targeting 1080p initially and 4K if feasible
- Prefer **Sunshine (Windows host) + Moonlight (TV client)** for the first prototype
- Launch streaming only on demand; do not permanently connect

Streaming performance, decoding support and input functionality must be tested on the actual TV.

### 4. C3 Smart Inputs

- Quickly select HDMI sources
- A dedicated PlayStation tile
- Investigate HDMI-CEC detection and automatic switching to the console input
- Reliable path back to C3 Home

Automatic PlayStation recognition and input switching depend on firmware/hardware permissions and are **not guaranteed**.

### 5. C3 Remote Power

- Wake a configured Windows PC using Wake-on-LAN
- Display connection state where possible
- Open C3 Desktop after wake
- Optional authenticated shutdown via a narrowly scoped PC-side component

Features requiring a Windows agent must be explicitly opt-in and protected from unauthorized network use.

### 6. C3 Control Center

A compact settings menu focused on:

- Picture: supported brightness, contrast, display modes, HDR
- Sound: volume and audio output
- Connectivity: Wi-Fi, Bluetooth and HDMI
- Devices: saved PCs/laptops
- System: information, restart and return to stock Fire OS

Where direct platform access is restricted, deep-link to existing Fire OS settings rather than claiming to replace them.

## Out of scope for v1.0

- Separate media library (C3 Media)
- Creator/OBS mode
- Smart-home dashboards or widgets
- Weather/news feeds
- Custom streaming providers or app store
- Replacing or flashing the device firmware
- Bypassing DRM, app authentication or platform security

## Proposed stack

- Kotlin and Android SDK compatible with the device's Fire OS API level
- TV remote / D-pad navigation and Android intent-based app launches
- ADB for on-device testing and diagnostics
- Moonlight + Sunshine for initial desktop streaming
- HDMI-CEC and Wake-on-LAN **where supported**
- GitHub issues, pull requests and versioned releases

The final implementation and libraries are chosen **after feasibility testing**, not assumed in advance.

## Roadmap

### Phase 1 — Feasibility and baseline

1. Confirm ADB connectivity, exact Android API level, firmware and installed application package names.
2. Determine permitted methods for launching a custom home screen and handling the Home key.
3. Test official streaming-app launches and access to system settings.
4. Investigate HDMI input control/CEC permissions and Moonlight compatibility.
5. Measure stock launch responsiveness, memory use and available resources.
6. Document risks, rollback procedure and **go/no-go decisions** for each module.

**Deliverable:** Feasibility report, measured baseline, technical architecture, risk register and verified recovery path. **Do not disable system packages or flash firmware.**

### Phase 2 — C3 Home MVP

Create the smallest installable APK with working remote navigation and six streaming app shortcuts. Test launcher behavior on the real TV.

### Phase 3 — C3 Desktop

Prototype Moonlight/Sunshine on PC and laptop. Verify session continuity, latency, video quality and keyboard/mouse input.

### Phase 4 — Inputs and power

Add HDMI/PlayStation workflows where technically feasible, Wake-on-LAN, and optional secured power controls.

### Phase 5 — Control Center and optimization

Add essential settings, benchmark against the Phase 1 baseline, test idle resource usage, app switching, stability and rollback.

## Definition of done: v1.0

- Minimal interface opens and works reliably with the stock remote
- All six requested streaming apps can be launched; sessions remain controlled by providers
- Windows desktop streaming works for a supported PC and laptop configuration
- HDMI sources remain accessible; PlayStation automation implemented only if validated
- Remote Power works for configured/supported devices
- Essential settings remain reachable, directly or through system settings
- Measured and acceptable startup, resource use and navigation responsiveness
- Safe, documented return to the stock launcher and TV behavior

## Development workflow

Development is incremental and requirement-driven. Implement one tested module at a time. Record any platform limitations explicitly; do not silently replace requirements with unsafe hacks. Use feature branches and pull requests, keep the default branch stable, and validate changes on-device before considering them complete.

---
**C3-TelevisionOS** — Fast. Minimal. Personal.
