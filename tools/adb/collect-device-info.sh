#!/usr/bin/env bash
# C3-TelevisionOS – Phase 1: device information collector
#
# READ-ONLY: this script only queries the TV. It does not install, start,
# disable or change anything on the device.
#
# Usage (Linux/macOS, or Git Bash / WSL on Windows):
#   adb connect <TV-IP>:5555
#   ./tools/adb/collect-device-info.sh [output-file]
#
# Review the output before sharing it publicly: it may contain device
# identifiers such as network names or Bluetooth device names.

set -u

OUT="${1:-c3-device-info-$(date +%Y%m%d-%H%M%S).txt}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH" >&2
  exit 1
fi

if [ "$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)" -ne 1 ]; then
  echo "Exactly one connected device is required (see 'adb devices')." >&2
  exit 1
fi

section() {
  printf '\n===== %s =====\n' "$1"
}

run() {
  # $1: title, rest: command executed via 'adb shell'
  local title="$1"; shift
  section "$title"
  printf '$ adb shell %s\n' "$*"
  adb shell "$@" 2>&1
}

{
  section "Collected"
  date -u +"%Y-%m-%dT%H:%M:%SZ"

  # --- 1. System / build -------------------------------------------------
  for p in ro.build.version.sdk ro.build.version.release ro.build.display.id \
           ro.build.version.name ro.build.mktg.fireos ro.build.version.fireos \
           ro.product.model ro.product.device ro.product.manufacturer \
           ro.board.platform ro.hardware ro.product.cpu.abilist \
           ro.config.low_ram dalvik.vm.heapsize dalvik.vm.heapgrowthlimit; do
    printf '%s=' "$p"; adb shell getprop "$p"
  done

  # --- 2. Hardware / memory ---------------------------------------------
  run "CPU" cat /proc/cpuinfo
  run "Memory" cat /proc/meminfo
  run "Memory per process (summary)" dumpsys meminfo
  run "Storage" df -h /data
  run "Screen size / density" 'wm size; wm density'
  run "Display modes / HDR" "dumpsys display | grep -iE 'mode|hdr|refresh|supportedModes' | head -n 80"
  run "System features" pm list features

  # --- 3. Video decoders (Moonlight) ------------------------------------
  run "Media codec files" 'ls /vendor/etc/ /odm/etc/ 2>/dev/null | grep -i media_codecs'
  run "Media codecs (vendor)" 'cat /vendor/etc/media_codecs*.xml 2>/dev/null'

  # --- 4. Launcher / home -----------------------------------------------
  run "Default HOME activity" cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
  run "All HOME activities" cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
  run "Current foreground activity" "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'"

  # --- 5. Installed apps --------------------------------------------------
  run "Third-party packages" pm list packages -3
  run "All packages" pm list packages
  run "LEANBACK launcher activities" cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER

  section "Candidate streaming / Moonlight packages (versions)"
  for pkg in $(adb shell pm list packages | tr -d '\r' | sed 's/^package://' \
      | grep -iE 'netflix|avod|firebat|primevideo|youtube|twitch|disney|rtl|tvnow|limelight|moonlight'); do
    printf '\n--- %s\n' "$pkg"
    adb shell dumpsys package "$pkg" | grep -E 'versionName|versionCode|targetSdk|minSdk' | head -n 4
    adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER "$pkg"
  done

  # --- 6. HDMI / TV inputs / CEC ----------------------------------------
  run "TV inputs" dumpsys tv_input
  run "HDMI-CEC" dumpsys hdmi_control
  run "Passthrough handlers" cmd package query-activities --brief -a android.intent.action.VIEW -d content://android.media.tv/passthrough/test

  # --- 7. Settings deep links -------------------------------------------
  for a in android.settings.SETTINGS android.settings.WIFI_SETTINGS \
           android.settings.BLUETOOTH_SETTINGS android.settings.SOUND_SETTINGS \
           android.settings.DISPLAY_SETTINGS android.settings.ACCESSIBILITY_SETTINGS \
           android.settings.DEVICE_INFO_SETTINGS android.settings.INPUT_METHOD_SETTINGS; do
    run "Resolve $a" cmd package query-activities --brief -a "$a"
  done
  run "Amazon settings package activities (exported)" "dumpsys package com.amazon.tv.settings.v2 | grep -E 'com.amazon.tv.settings.v2/[^ ]+' -o | sort -u | head -n 150"

  # --- 8. Accessibility (home redirect feasibility) ---------------------
  run "Enabled accessibility services" settings get secure enabled_accessibility_services
  run "Accessibility enabled" settings get secure accessibility_enabled
  run "Accessibility state" "dumpsys accessibility | head -n 60"

  # --- 9. Performance-related settings (read only) ----------------------
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do
    printf '%s=' "$k"; adb shell settings get global "$k"
  done

  # --- 10. Moonlight (only if installed) --------------------------------
  run "Moonlight exported activities" "dumpsys package com.limelight | grep -iE 'ShortcutTrampoline|exported|Activity' | head -n 60"
} > "$OUT"

echo "Done. Output written to: $OUT"
echo "Please review it for private data before sharing."
