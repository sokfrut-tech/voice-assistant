# Factory continuation brief — voice-assistant

**Updated:** 2026-11-04 | **Status:** inventory/architecture review needed  
**Canonical portfolio:** Universal-AI-Factory/docs/PROJECT_PORTFOLIO_AND_CONTINUATION_PLAN_2026-11-04.md  
**Branch:** factory/continuation-brief-2026-11-04  
**Open PR:** #15 security toolchain  
**Correlation:** voice-assistant-continuation-20261104

## Mission
Preserve and improve the shared voice-assistant project. The exact current feature scope and relationship to Gemeni/Jarvis must be confirmed from repository source and README before implementation.

## Next actions
1. Inspect README, AGENTS, source tree, build configuration, current main and PR #15.
2. Identify reusable speech, TTS, VAD, wake-word and Android service components without assuming they are production-ready.
3. Map overlap with Gemeni/Jarvis and RussSearchOPPO; avoid duplicate implementations and cross-project breaking changes.
4. Verify build/tests/security workflow on exact current head.
5. Add a clear architecture note and integration contract before extracting shared modules.

## Constraints
No speculative rewrite, no credentials in Git, no production or app publishing without owner approval.

## Resume
Start with read-only repository inventory and current PR/CI refresh. ChatGPT continues directly if external agents are unavailable.


## Documentation handoff PR
- Continuation brief PR: [#16](https://github.com/sokfrut-tech/voice-assistant/pull/16) (draft; not merged).
- Review this brief against current main before code changes.