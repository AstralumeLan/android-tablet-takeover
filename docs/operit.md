# Delegating on-device work to Operit (optional)

Operit (`com.ai.assistance.operit`) is an on-device AI agent app with an external HTTP API (default port 8094,
bearer token). `host/operit.sh` sends it a task and streams the reply (SSE), so multi-step UI work happens on the tablet
instead of shipping screenshots over a slow relay.

- `host/operit.sh [-t seconds] [-c chat_id] [-r] "message"` — token is read from `$OPERIT_TOKEN_FILE`, never printed.
- **Fresh chat bug/workaround**: the API ignores `create_new_chat` unless its `FloatingChatService` is running, so the
  message silently lands in the previous chat with its whole history (huge token counts). The script first sends a tiny
  priming request (`show_floating=true, initial_mode=BALL, stop_after=true`) to obtain a new `chat_id`, then sends the
  real task to it with `show_floating=false`.
- Give Operit a skill that sets hard limits (never pay/order/send, stop at captchas, step caps, JSON output) and tells it
  to read the screen through the takeover app's `DUMP` broadcast. Example: `operit/assist-skill/SKILL.md`.
- A fast/cheap model with low "thinking" is plenty for UI steps; screenshots to a vision model are the main cost driver.
- Verify anything important yourself over adb — the reply is another model's summary.
