# Voice Assistant — continuity handoff

Last updated: 2026-10-31

## Repository
https://github.com/sokfrut-tech/voice-assistant

## README-recorded scope
An early-stage Russian Android voice assistant:
- Button-based Russian speech recognition.
- Voice command “навигация до <место>” / “маршрут до <место>”.
- Opens a route in Yandex Maps, with Google Maps fallback.
- Speaks a Russian response when a Russian TTS voice is installed.
- README lists future work: smart-response model, offline Vosk recognition for devices without Google services, transliteration and Russian spoken responses.

## Continuation guidance
This repository appears to be an earlier/simpler assistant stage and may overlap with RussSearchOPPO and Jarvis. Before adding features, compare package IDs, source code, release history and user intent to decide whether it remains an independent app or a legacy prototype. Do not merge projects or replace an app without an explicit decision.

## Immediate next steps
1. Inspect latest workflow, branches, commits and APK artifacts.
2. Determine whether the project is still maintained or superseded by RussSearchOPPO/Jarvis.
3. If maintained, run the build and record a tested APK artifact.
4. Keep the README and this handoff synchronized with verified behavior.

## Change record
- 2026-10-31: Added this continuation handoff on a documentation-only branch.
