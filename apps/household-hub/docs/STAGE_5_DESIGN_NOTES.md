# Household Hub: Stage 5 Design Notes

**Status:** 📐 Phone design complete, except the morning briefing. Tablet is deferred to a later stage
**Target:** `client` (`:composeApp`, `:core:presentation`, `:core:data`) + the `backend` changes the designs depend on
**Date:** September 2026

---

## 1. What this is

Everything decided while designing the Stage 5 mobile client, in one place: the rules the screens follow, the product decisions behind them, every backend and client change they depend on, and the notes that sat beside each screen on the design canvas.

| Artefact | What it holds |
| :--- | :--- |
| Design canvas — *Household Hub Mobile* ([link](https://claude.ai/code/artifact/c8d66772-c6ea-4c07-90a7-8a4aba45b2b4)) | 84 screens in 20 flows, day theme, 390×844, plus the type proof |
| Palette proof ([link](https://claude.ai/code/artifact/e0af0e37-e774-48a5-b3c2-de096de4d1f6)) | Final tokens, contrast checks, both themes |
| Hearth icon set ([link](https://claude.ai/code/artifact/b87fc4c0-1259-4278-ba6d-71507297affc)) | 31 icons and their drawing rules |
| [`STAGE_5_SECRET_SESSION_LOCKING.md`](STAGE_5_SECRET_SESSION_LOCKING.md) | Approved spec for server-enforced secret chats |
| [`STAGE_5_UI_UX_SPECIFICATION.md`](STAGE_5_UI_UX_SPECIFICATION.md) | The original Stage 5 spec. **Its token table and some flows are out of date** — see §7 |

The artefact links are private to the owner's account.

Where this document and the UI/UX specification disagree, this document is the later decision.

---

## 2. Rules every screen follows

| Rule | What it means in practice |
| :--- | :--- |
| **Dimming means "can't be opened"** | Nothing readable is greyed out. A read-only chat, a built-in agent, a locked secret row all stay at full contrast and state their limit in words. Primary buttons are never disabled; they explain what's missing when tapped. |
| **Waiting has one shape** | Five patterns, one set of beats, and nothing is ever dimmed while it waits. The tapped button keeps its colour and says the verb in progress; a screen arriving empty breathes where the answer lands; a screen refreshing keeps its rows. Nothing is drawn for the first 250ms, so a hub on the LAN shows no loading state at all. §6.21. |
| **Screens scroll** | Any screen whose content can outgrow the viewport scrolls its content region, with header and navigation pinned. Only fixed-shape screens are exempt: the PIN pad, launch, full-screen states. |
| **No raw identifier reaches a person** | `searxng_search` is "Search the web"; `calendar_write` with `action=delete` is "Remove from your calendar". One label map in code, keyed by backend names, feeds every screen. |
| **No UI without a backend behind it** | Suggestion chips and voice input were designed, then dropped, because nothing produces them. Screens that still show unbacked data say so in §5. |
| **Errors live where the problem is** | Under the field, on the attachment, on the message. No dialogs, and nothing typed is ever cleared. |
| **Friction matches damage** | Removing someone means typing their name; deleting yourself means your PIN; removing a calendar event always asks. Each destructive screen lists what goes and what stays. |
| **Tools leave a trail** | Every read leaves a quiet record ("Checked your calendar"). Auto-approve removes the question, never the record. |
| **Gossip is prose, never chrome** | "Liam mentioned he's back by seven" is the whole surface. No cards, badges or citation chips — for gossip, search sources or PDF pages. |
| **Secret Mode is independent of theme** | The achromatic ghost works in both palettes; Secret Mode never changes the theme and the theme never implies it. |
| **Percentages aren't tokens** | Alphas and tints are tuned per palette by perceived lightness, so one value doesn't read 1.5× weaker in daylight. |

---

## 3. Tokens and icons

The palette proof is the source of truth. What changed from the UI/UX specification's table:

| Role | Day | Night | Why |
| :--- | :--- | :--- | :--- |
| Primary | `#3C6E4E` | `#7FB894` | The old greens failed contrast and the selected nav tab didn't read in daylight |
| Secret Mode | `#6B655F` | `#B8B2AC` | Thistle and Plum replaced by an achromatic ghost; `onSecret` deleted |
| Ghost tint / edge / frame | 20 / 69 / 66 % | 14 / 55 / 45 % | Tuned per palette so both land on the same perceived edge |
| Soft outline (chat panel, dividers) | `#EFEBE4` | `#272523` | The day border was too faint next to night's |
| Error | `#A33B2A` | `#E8705C` | New |
| Success | `#3A7455` | `#6FB58E` | New |
| Night surfaces | — | `#191715` / `#211E1B` / `#2A2622` | Three elevation steps |

**Type:** Outfit (display), Inter (text), JetBrains Mono (times, handles, metadata) — the roles are below. **Shape:** 24dp bento cards.

### Space and size

Every padding, gap and dimension comes off a **4dp grid**, with one 2dp half-step for hairline insets. Before this, the canvas had invented 24 gaps, 120 padding combinations and 25 corner radii, and the code had copied them; the icon-and-text gap alone existed at 5, 6, 7, 9 and 10dp. The designs were re-snapped and the code follows them, token for token.

| Space | dp | Size | dp |
| :--- | :--- | :--- | :--- |
| `none` | 0 | `iconSm` (inline with text) | 16 |
| `xxs` (hairline inset) | 2 | `iconMd` (button, composer) | 20 |
| `xs` | 4 | `iconLg` (nav, the drawing grid) | 24 |
| `sm` (icon ↔ text, line ↔ line) | 8 | `iconXl` (empty-state tile) | 28 |
| `md` (inside a card) | 12 | `iconXxl` (on a tile) | 32 |
| `lg` (between blocks, field padding) | 16 | `iconHero` (on a hero tile) | 40 |
| `xl` (screen gutter, card padding) | 20 | `touchTarget` (anything tappable, field height) | 48 |
| `xxl` (between sections) | 24 | `control` (button height, the raised +) | 56 |
| `xxxl` (around a hero) | 32 | `tile` / `tileHero` | 64 / 80 |
| `huge` (full-screen state) | 40 | `readingWidth` (centred prose) | 288 |

**Radii:** 4 / 8 / 12 / 16 / 20 / 24 / 32, plus the stadium pill and the circle. Bento cards stay 24; a list item, a field and a button are all 16; an icon tile reuses the bento radius instead of inventing r22 and r26.

**Off the grid on purpose:** a 1dp border, the 2dp error edge and a shadow's blur. None of them is layout, and rounding them to 4dp would only make them wrong.

**What grew:** a header icon button and the composer's send button, both 44dp, now 48 — they were under the accessible floor. A launch hero tile went 76 → 80. Everything else moved by 2dp or less.

### Type

Same disease, its own pass — and now done. The canvas drew **28 font sizes** (9.5 through 36px, including 12.5, 13.5 and 14.5) in **124 combinations** of family, size, weight, line height and tracking, every one written inline on the element; the components invented their own `sp` on top of that. Nothing was named, so each new screen copied whatever the last one happened to use.

There are now **fifteen roles**. A role carries everything — family, size, weight, line height, tracking — and a screen picks one and writes nothing else. The proof is artboard `0 · The type scale` on the canvas; `HearthTypography` is the same table in code.

| Role | Family | Size | Weight | Line | Tracking | For |
| :--- | :--- | ---: | ---: | ---: | :--- | :--- |
| `hero` | Outfit | 28 | 600 | 32 | -.015em | A full-screen title |
| `title` | Outfit | 21 | 600 | 25 | -.012em | A screen or dialog title in the chrome |
| `heading` | Outfit | 17 | 600 | 22 | -.01em | App bar, card, section |
| `bodyLarge` | Inter | 15 | 400 | 23 | — | Lead prose, and a field's own text |
| `body` | Inter | 14 | 400 | 21 | — | The default |
| `bodyStrong` | Inter | 14 | 600 | 19 | — | A row's name, and every button label |
| `label` | Inter | 13 | 500 | 18 | — | The second line of a row |
| `labelStrong` | Inter | 12 | 600 | 16 | — | Chips, badges, field labels, ON/OFF |
| `caption` | Inter | 12 | 400 | 17 | — | Helper text, and any supporting line |
| `overline` | Inter | 11 | 600 | 13 | .11em | An eyebrow. The string carries the capitals |
| `micro` | Inter | 10 | 600 | 12 | — | Nav labels and counts |
| `monoSm` | Mono | 10 | 500 | 14 | — | Dense metadata: a status line, a latency |
| `mono` | Mono | 12 | 500 | 17 | — | Times, handles, tool records |
| `monoLg` | Mono | 15 | 500 | 21 | .06em | A credential you read back |
| `codeHero` | Mono | 24 | 500 | 26 | .1em | The one code a screen exists to show |

**Glyphs:** an emoji or an avatar's initial is not type — it is sized by the circle it sits in, one step per diameter: **20→10, 32→14, 40→18, 48→22, 56→26, 64→30, 96→36**. That removed 15/17/19/20 on a 40px circle, 18/19/22/23 on a 48px and 25/26/27/30 on a 64px. Circle diameters themselves didn't move.

**Three modifiers, and only inside a role's own line:** `t-em` (600) for emphasis in prose, `t-soft` (500) for its opposite, `t-code` for a handle set in mono.

**Off the scale on purpose:** the masked-PIN row keeps its `.42em` tracking. That is the spread between six dots, not a property of the type — the same reason a 1dp border sits off the 4dp grid. It is the only exception on the canvas.

**What moved:** nav labels no longer thicken when selected — selection reads through colour, and a label that also changes weight shifts the row by a hair as you move between tabs. Prose at 13.5 and 14.5 became 14, small emphasis at 11 and 12.5 became 12, and the eyebrow settled at 11.

### Motion

Time is a scale like space and type, and for the same reason: "waiting" drifts screen by screen otherwise. Four beats, and they are the same everywhere.

| Beat | Value | What it is |
| :--- | :--- | :--- |
| `hold` | 250ms | Nothing is drawn. A hub on the LAN answers well inside it, so a normal call shows no loading state at all |
| `minimumVisible` | 400ms | Once a pattern is on show it stays this long after the answer arrives, so it can never flash |
| `slow` | 8s | Still waiting, longer than usual. The pattern stays and a quiet line joins it; nothing has failed |
| `barCycle` / `breathe` / `wave` | 1150 / 1600 / 1400ms | One pass of the bar, one breath of a skeleton, one pass of the wave across the PIN dots |

The wave also carries its stagger (90ms per dot) and its lift (4dp) — a `Dp`, which is why it lives with the motion rather than the sizes. A row of skeleton blocks staggers the same way, 60ms apart, so six of them read as one thing arriving rather than six lights blinking together.

### In code

A screen reads the scale off the theme, beside the palette — `HearthTheme.spacing.lg`, `HearthTheme.size.iconMd` — with the values held by `HearthSpacing`, `HearthSizes` and `HearthShapes` in `app/theme` and handed down by `HearthTheme` through a composition local. Space doesn't change between day and night, but going through the theme means a later size class (the deferred tablet) can provide its own scale and every screen follows without being touched. Type reaches a screen the same way — `HearthTheme.typography.body` — and can be swapped over a subtree just as space can. Motion arrives the same way, as `HearthTheme.motion`. `DesignSystemTokenTest` fails the build on a raw `dp`, a raw `sp` or a raw duration (`durationMillis = 300`, `300.milliseconds`) written anywhere else in `composeApp/commonMain`, on a `FontWeight` applied by hand at a call site, and on a screen reading `DefaultSpacing` / `DefaultSizes` past the theme. The one exception is the icon set: `HearthIcon` builds its vectors outside composition, and its 24-unit grid is the drawing itself, not a layout decision.

### Hearth icons

24-unit grid, 1.5 stroke with round caps and joins, `currentColor`, 1.85 stroke when active. The sheet draws **31** icons, and its header count now says so. Added this stage: `attach` (paperclip), `biometricUnlock` (viewfinder corners around a keyhole — deliberately no face or finger, see the Secret Mode notes), and `delete` (a backspace key, for the PIN pad) in a "Sign in" group. Removed: `voice`. Agents keep emoji avatars; chrome never uses emoji.

---

## 4. Decisions

| Area | Decision | Why |
| :--- | :--- | :--- |
| Sign-in | Profile picker + 6-digit PIN per member. No email, no username, no password | Two people, no mail server |
| Joining | Admin creates a one-time invite code; the joiner picks their own PIN | The admin never invents or relays a credential |
| PIN recovery | The other member approves with their own PIN; hub access is the last resort | No email reset possible; any member can vouch for any other |
| Admins | One admin. The sole admin can't leave, and the UI says so | There is no promote endpoint |
| Removing a member | Deactivate: keep id, name, colour; erase their private data | Shared facts keep their real source instead of being re-attributed to the admin |
| Changing your PIN | Signs out every other device | You may be changing it because someone saw it |
| Calendar | One calendar per member; the email comes back only here | The CalDAV username is an Apple ID or Google address |
| Agents | Suspended / trashed (7 days) / purged, each with its own chat treatment | Matches the backend's three states |
| Agent tuning | "Straight down the line / Balanced / Full of ideas" → temperature 0.3 / 0.7 / 1.0; `top_p` hidden | Numbers mean nothing to a non-technical person |
| Approvals | Per person, per action. Removing events and replacing notes always ask | Built-in agents can't be edited, and comfort is personal |
| Failed messages | Three cases: never arrived / stream dropped / model failed. Only the first re-sends | Durable execution means a dropped stream is still being answered |
| Long answers | After 60 s, wait quietly; never fail | Giving up on waiting isn't the answer failing |
| Devices | Single device per session for now | Out of scope |
| Offline | Signed out → offline screen. Signed in → cached reading, writes disabled, not queued | A queued question gets a stale answer hours later |
| Secret chats | Server-enforced lock, 10-minute sliding window, PIN to unlock | The old lock accepted any string |
| Unlock scope | One PIN unlocks every secret chat; switching a chat to secret gives a token for that chat only | Unlocking one at a time adds friction without adding safety |
| Secret titles | Titles and previews are withheld by the server until unlocked | Hiding them in the app alone is spoofable |
| Secret memory | Secret turns write no memories; switching a chat to secret is not retroactive | Secret means off the record, from that point on |
| Privacy phrases | "Don't tell…" keeps that message private and offers to make the whole chat secret | The backend already detects it |
| Biometrics | Phase 2, "face or fingerprint", biometric only, hub PIN as the fallback, invalidated when enrolment changes | Partners often know each other's phone passcode |
| Search | Titles and agent names, on the phone, for the MVP. Full-text after the MVP | No backend work, works offline |
| Chat previews | New `last_message_preview` on the session list | Rows need to be recognisable when scrolling |
| PDFs | Attached to a chat. The file is never kept, the extracted text is. No page limit | Agreed when the document feature was designed |
| Notes | Read, share (.md), delete. No editing in the app | You ask the agent to change a note |
| Memories | Remove only, no editing | Kept simple; `UpdateMemoryUseCase` stays unused |
| Web search | Each step shows its own sources, from a summary saved with the tool part: a search opens to its results, a page read names its page. A closed fold says what its steps did (#40) | Doesn't depend on the model remembering to cite, and survives reopening the chat |
| Agent in a chat | The header pill names the agent, with no chevron. Changing agent means starting a new chat | A session is bound to one agent and there is no endpoint to switch |
| Platforms | Android and iOS phones, portrait. Tablet in a later stage | Both of you need it on your own phone |
| Dropped | Suggestion chips, voice input, the 150-page PDF limit | Nothing backs them, or they measured the wrong thing |

---

## 5. Backend and client work the designs depend on

Items marked **⛔** break a designed flow until they're done.

### 5.1 Authentication and members

- [x] ⛔ **PIN replaces password.** `users.hashed_pin`, hashed like the password today. Add a per-member failed-attempt counter with backoff — `LoginUseCase` has timing defence but no attempt limit, and a PIN without a lockout is the weakest part of the system. *Done in slice 1: five free tries, then 30 s doubling to 15 min (`pin_lockout.py`), guesses for one member checked one at a time; 401 carries `attempts_left`, 429 carries `retry_after_seconds`. Username, email and password are gone; `POST /users` is removed until invites replace it.*
- [x] ⛔ **Public profile list** for the picker: id, name, colour; active members only. It reveals names to anyone who can reach the hub — acceptable on LAN + Tailscale, but deliberate. *`GET /auth/members`.*
- [x] ⛔ **Invites.** `Invite` record (code, invited_name, is_admin, expires_at, used_at); `POST /invites` (admin) and `POST /invites/{code}/redeem` (public). `is_admin` comes from the stored invite, never the payload. Single use, short expiry, same lockout as PIN entry. `create_member` takes the PIN from the joiner. *Done in slice 2: `POST /invites`, `GET /invites/{code}` and `POST /invites/{code}/redeem`; six characters from an alphabet without look-alikes, fifteen minutes, single use claimed in one conditional write, and one hub-wide guard on guessing.*
- [x] **PIN reset.** `PinReset` record (code, target, approver, expires_at, used_at); approve endpoint checks the approver's own PIN; redeem endpoint. *Done in slice 2, plus `hub reset-pin` from the server itself.*
- [x] **Sign out elsewhere on PIN change.** `token_version` on the user, bumped on change and checked on every request — the JWT is stateless and can't be revoked as issued. *Done in slice 2: every token records `ver`, and `POST /auth/refresh` re-issues one that is still accepted.*
- [x] **Deactivate instead of delete.** `is_active=false` (field and login check exist). Erase sessions, personal memories, personal space, calendar credential, documents and PIN hash. Shared facts keep their source; agents pass to the admin. Context assembly labels facts from inactive members as past; the picker excludes them; name uniqueness is scoped to active members. *Done in slice 2, for removal and for leaving.*
- [x] Reword `SoleAdminDeletionException` — it tells you to promote another admin, which isn't possible. *Done in slice 2; it now answers 409 with `sole_admin`.*
- [x] Default `avatar_color` to a Hygge swatch instead of `#4F46E5`. *`#3C6E4E`, the first swatch on the first-run picker.*

### 5.2 Secret chats — see [`STAGE_5_SECRET_SESSION_LOCKING.md`](STAGE_5_SECRET_SESSION_LOCKING.md)

- [ ] ⛔ `POST /auth/unlock-secret`, 423 on secret reads, rate limit (§4.1–4.2).
- [ ] ⛔ Session-scoped token when a chat is made secret or created secret (§4.1.1).
- [ ] ⛔ Session list withholds `title` and `last_message_preview` for secret chats while locked (§4.3).
- [ ] ⛔ Skip memory extraction on secret turns, plus a one-off cleanup (§4.4).
- [ ] ⛔ Client: replace the unlock stub, keep the token in memory only, drive the idle timer from user input only, clear on background / device lock / sign-out, unlock bar and Lock now in Chats.
- [ ] **Surface `suggestSecretMode`.** The client parses it from the stream's done event and drops it.
- [ ] Privacy phrases are English regexes only; Spanish or Catalan never trigger.

### 5.3 Chats and messages

- [ ] ⛔ `last_message_preview` on `SessionRead`, truncated server-side.
- [ ] ⛔ **Classify stream failures by when they happen** in `ChatSessionViewModel`: before the first delta = not delivered; after = delivered. Keep the partial answer; put the status on the assistant turn. Today a dropped stream discards the half-answer and marks your question failed.
- [ ] ⛔ The 60-second polling limit ends the active wait, not the turn — it must not surface as an error.
- [x] ⛔ **Handle `ToolExecuting` and `ToolResult`.** Done in #33/#34: every tool part is drawn where it ran.
- [x] **Tool steps say what they found, or why not (#40).** Each tool part carries a `summary`; see §6.7.
- [ ] ⛔ **`tool_call_id` on `ToolApprovalProposal`.** `approveTool` needs it and the event doesn't carry it, so approval can't work as wired.
- [ ] Milestone ids on `ChatStreamEvent.Done`, for the transient publish notice and its Undo (`DELETE /gossip/{id}`).
- [ ] Title search on the phone, over the loaded list, excluding secret chats.

### 5.4 Tools and approvals

- [ ] ⛔ **Per-person, per-(tool, action) auto-approve** stored per user and checked in `process_chat_turn`, replacing the single `auto_approve_writes` boolean. Remove-event and replace-note are excluded entirely.
- [ ] Approval card labels come from the action, not the tool.

### 5.5 Agents

- [ ] ⛔ **Distinguishable handle conflicts.** Raise the unused `SlugConflictException` as 409 with `handle_taken` or `handle_in_trash`; for the trash case include agent_id, whether it's yours, the owner's name and days remaining. Today both are 400 with an English sentence.
- [ ] `min_length=1` after stripping on `name` and `system_prompt`, on create and update.
- Trash, restore (410 after 7 days) and purge already work server-side — UI only.

### 5.6 Memory and household sharing

- [ ] ⛔ `MemoryAuditViewModel.loadAudit()` never loads `userMilestones` — wire it to `GET /gossip/audit`, or "Shared with the household" renders empty.

### 5.7 Calendar

- [ ] ⛔ **`CalendarViewModel` doesn't exist.** Schedule and both dashboards depend on it.
- [ ] Build Google's per-account CalDAV URL in the client and show it read-only.

### 5.8 PDFs

- [ ] ⛔ `POST /sessions/{id}/documents`: parse, store the sections against the session, discard the file, return id + metadata.
- [ ] ⛔ `pdf_reader(document_id, pages or section)` reads from that store — today the tool always fails.
- [ ] ⛔ Tell the agent each turn which documents are attached (name, pages, headings, unreadable pages).
- [ ] The message's `metadata_json` carries the document id, for the card.
- [ ] `DELETE /sessions/{id}/documents/{doc_id}`; documents removed with their session.
- [ ] Remove the page limit everywhere it's defined (router, reader, use case, tool schema, unused setting).
- [ ] Detect password-protected PDFs (`doc.needs_pass`) with their own error code.
- [ ] Return the pages that had no text, so partly scanned PDFs warn.

### 5.9 Notes

- [ ] ⛔ `agent_id` and `source_session_id` on `app_documents`, set by `document_writer`.
- [ ] ⛔ **Bug:** `SaveDocumentUseCase` with `action=create` on an existing title silently replaces that note. Create should pick a new title, or fail so the agent appends.

### 5.10 Offline

- [ ] **A client database doesn't exist.** `:core:data` stores only the auth token. Offline reading needs a cache for sessions, messages, members, spaces and events, with a written-at time per record for the "as of" banner. Until then, signed-in offline is an empty app with a retry.

### 5.11 Documentation drift

- [ ] `STAGE_5_UI_UX_SPECIFICATION.md`: token table (old greens, Thistle/Plum secret accent), the in-chat agent dropdown that "allows switching on the fly" (sessions are bound to one agent; the pill has no chevron — §4), and the tablet layout, now deferred.
- [ ] `STAGE_2_BASELINE.md`: says PDF chunks are stored and uploads go to 50 MB; nothing is stored and the limit is 25 MB.

---

## 6. Notes by flow

The notes that sat beside each group of screens on the canvas.

### 6.1 Onboarding

*Screens:* 0 · System splash, 1 · Splash — reaching the hub, 2a · First run — no members, 2b · Who’s here — hub ready, 2c · PIN, PIN · forgotten, PIN · the other member approves, Hub unreachable, Hub error — HTTP 500, Something else answered — web page, Not at that address — 404, Something went wrong

#### Launch — where the app goes first

- **Signed in on this phone** (a token is stored) → Home straight away. There is no hub call, so it works offline. A token the hub no longer accepts is handled later: slice 2 needs 401 handling anyway, because changing a PIN signs out other devices.
- **Signed out** → the "Reaching the hub" frame is the splash: tile, name, "Reaching your hub…", the hub address and a loading bar, while GET /auth/status runs. Then straight to 2a First run (no members) or 2b Who's here (members). There is no "hub is ready" frame in between.
- **Any failure** → the offline layout, worded for what came back. Every failure retries by itself every 10 seconds.

| What came back | Title | Chip |
| :--- | :--- | :--- |
| Nothing (no route) | Can't reach your hub | No route |
| An HTTP error | Your hub isn't answering properly | HTTP 500 |
| A web page instead of JSON | Something else answered | Web page |
| 404 | Your hub isn't at that address | Not found |
| Anything else | Something went wrong reaching your hub | Error |

The offline card no longer shows a Tailscale status row. Android can tell a VPN is on, not that it is Tailscale. It keeps the hub address, the chip and "Retrying in".

Each screen has its own ViewModel; the planned AuthViewModel was not built.

On the canvas: 1 · Splash — reaching the hub, Hub unreachable, and one artboard per other failure — Hub error — HTTP 500, Something else answered — web page, Not at that address — 404, Something went wrong.

#### 0 · System splash

The platform's static splash — canvas colour, launch tile centred — shows only until the app draws. The animated waiting happens in Compose, so it works the same on iOS, whose launch screen can't stay up while code runs.

The tile moves up slightly when the launch frame takes over. Accepted.

#### What's left on the form

- name 1–128, shown on the profile picker
- a 6-digit PIN, hashed like the password was (bcrypt handles short inputs)
- colour, from the Hygge palette

Email, username and password are all gone — three fields for a two-person house.

#### Profile picker + PIN

Streaming-service shape: tap a face, enter a PIN. Nothing to type an identifier for.

GET /auth/status already returns member_count; this needs a sibling returning id, name and colour so the picker renders before anyone authenticates. That endpoint reveals member names to anyone who can reach the hub — acceptable on a LAN + Tailscale-only service, but a deliberate choice.

#### What the credential becomes

Email, username and password all go; one PIN per member replaces them. The JWT is already keyed on user.id, so tokens never depended on any of them.

The client already has UnlockSecretSessionUseCase(sessionId, pinOrPassword) for unlocking secret sessions — same credential, so a locked secret chat and the lock screen ask for the same six digits. The backend has no PIN at all yet.

#### A PIN needs a lockout to be sound

6 digits is a million combinations; 4 is ten thousand. Neither survives unlimited guessing, and LoginUseCase has timing-attack defence but NO attempt counter today.

Add per-member failed-attempt tracking with a backoff — the screen shows “2 attempts left before a 30-second wait”. With that, a PIN is fine for a house on a private network; without it, it is the weakest part of the system.

Hash it exactly like the password (bcrypt handles short inputs); never store digits.

#### Avatar colour needs a new default

The backend defaults avatar_color to #4F46E5 — an indigo from no palette we use. The swatches here are Hygge values. One-line change, and no rows exist yet to migrate.

The offline screen is not an error page — it's the likely first experience. A hub that isn't running, and a phone away from home without Tailscale, both land here before any credential is typed. It says what came back, since the phone can't tell which of the two it was (see Launch above).

#### PIN recovery without email

No mail server means no reset link. So recovery is social: the OTHER member approves it with their own PIN and reads out a code.

Deliberately not “the admin resets it” — that strands the admin. Any member can vouch for any other, which works in a two-person house in both directions.

Last resort is hub access: whoever can reach the server can reset any PIN. That is the true backstop and it cannot be lost with a phone.

NEW BACKEND WORK: a PinReset record (code, target user, approver, expires_at, used_at), an approve endpoint that checks the approver's own PIN, and a redeem endpoint.

### 6.2 Invite

*Screens:* Invite 1 · admin generates, Invite 2 · joiner enters code, Invite 3 · joiner sets up

#### Invite registration — how member 2 joins

Admin names the person and gets a one-time code → the joiner enters it from the sign-in screen → they pick their own name confirmation, PIN and colour. The admin never invents or transmits a credential.

#### The way in was missing

A new member opening the app hits “Who's here?” — and they are not on it. The sign-in screen now carries “I have an invite code” instead of a sentence explaining that admins add people, which stated the rule without offering the door.

Code entry sits between generation and setup: six segmented boxes rather than one text field, so a mistyped character is obvious, with Paste for when it was sent over chat.

#### This does not exist yet

Today POST /users has an admin type the new member's username, email AND password, then hand them over verbally. There is no invite entity, no code, no expiry.

Needs: an Invite record (code, invited_name, is_admin, expires_at, used_at), POST /invites (admin-only) and POST /invites/{code}/redeem (public). create_member then takes the PIN from the joiner instead of the caller.

#### Two rules for the redeem endpoint

It is public — it must be, since the joiner has no token yet. So:

- is_admin comes from the stored invite, NEVER from the redeem payload. Otherwise anyone with a code grants themselves admin.
- Single use and short expiry, enforced server-side; the 15-minute countdown is a UI convenience, not the control.

Same lockout applies as the PIN screen — a 6-character code is guessable without one.

### 6.3 Daily loop

*Screens:* 3 · Household, 4 · New Chat · hero (+), 5 · Conversation, 8 · Schedule, 6 · Chats, 7 · My Space, Household · first day, nothing set up

#### Core daily loop — mobile portrait, 390×844

Household → hero (+) → Conversation → Chats → My Space.

RULE — SCREENS SCROLL. Any screen whose content can outgrow the viewport scrolls its content region, with header and nav pinned. Devices vary in height, lists grow, text scales with system font size. Only fixed-shape screens are exempt: the PIN pad, launch, and full-screen states. Apply it by default; never clip.

#### Ships now — backed by Stage 4 ViewModels

- Conversation, New Chat, Chats — ChatSessionViewModel
- Secret Mode toggle + tool approval — already streamed
- Hub status line — DashboardViewModel.serverStatus

#### Needs backend — not buildable yet

- Briefing cards — no endpoint generates them
- Both calendars — no CalendarViewModel exists
- Publish notice — needs milestone ids on ChatStreamEvent.Done

Inline tool approval. Decline / Edit / Approve are 46px tall — above the 44px floor. The card scrolls away with history rather than blocking the thread.

The transient publish notice. Appears only when THAT turn published something of yours; Undo calls DELETE /gossip/{id}. Self-dismisses, leaves no trace — a confirmation of your own action, never a view of the bus.

Gossip, in prose only. "Liam mentioned he's back by seven" is the whole surface. No card, no badge, no citation chip.

#### Schedule — the tab that pointed at nothing

Colour is the member (Emma green, Liam copper), and a split bar means both. That reuses the avatar colour chosen at first run, which is why the colour picker is not decoration.

Needs CalendarViewModel, which does not exist — same dependency as both dashboards.

### 6.4 Message states

*Screens:* Message · never reached the hub, Message · stream dropped, reconnecting, Message · still working after 60s, Message · answer failed

#### Three ways a message goes wrong, not two

1. NEVER REACHED THE HUB — the question itself failed. Only this case re-sends, so only this one shows Retry on the user's bubble.

2. REACHED IT, STREAM DROPPED — Stage 3's durable execution keeps generating after the client disconnects, so the answer is still being written. The partial text STAYS, and the indicator sits where the text stopped, because that is where the eye is. The client reconnects and fetches the rest with the same polling the 409 recovery already uses.

3. MODEL ERRORED OR TIMED OUT (502/504) — the question arrived, the answer did not. Try again regenerates the ANSWER and never re-asks.

While an answer is still being written the composer waits: the session lock would return 409 on a second send anyway, so the UI says so instead of letting it fail.

#### After 60 seconds, wait quietly — never fail

SessionRepositoryImpl stops polling after 60s and throws DomainException. But with durable execution, giving up on WAITING is not the answer failing — a long answer with tool calls may still be landing.

So the spinner stops (motion implies something is about to happen) and the line becomes a statement: still working, it will appear here. The composer says you can leave, since the hub keeps the turn and the next time the conversation loads it simply fetches the finished answer.

Code change: the 60s exhaustion must not surface as an error or as case 3. It ends the ACTIVE wait, not the turn. Only a real 502/504 from the model reaches the Try again card.

Single device only for now — the same session open on a second device is out of scope.

#### What the code does today — needs fixing in ChatSessionViewModel

Any stream failure sets streamingMessage = null (the partial answer is thrown away) and marks the USER message FAILED_OFFLINE or FAILED_ERROR. So a dropped stream tells you your question failed when it arrived, discards the half-answer you were reading, and invites a duplicate send.

The fix is to decide by WHEN it failed: before the first Delta means not delivered (case 1); after it means delivered (case 2 or 3). The status belongs on the assistant turn, not the user's, in the last two.

### 6.5 Tools

*Screens:* Tools · reading, then a record, Tools · edit before approving, Tools · approving a removal, Tools · approved and declined, Tools · a tool that failed

#### Tools leave a trail

READ tools (calendar, search, PDFs) run without asking. While running: a spinner line in the answer's place — Searching the web… When done, it stays as a quiet record above the answer — Checked your calendar — so you can always see where an answer came from. Same attribution idea as the gossip bus, applied to tools.

WRITE tools ask first: Decline / Edit / Approve. Edit opens the card in place with the proposed values; changed fields are marked, and the button becomes Approve with changes. approveTool already accepts modifiedArguments, so this is UI only.

After a decision the card COLLAPSES into a one-line record — Added to your calendar · Dinner together, Sat 20:30, or Not added. A decided card should not keep its buttons.

A tool that fails says what broke and offers the one fix — here, Reconnect calendar — and states that nothing was written.

#### Two things to fix in code first

1. ~~ChatSessionViewModel drops ToolExecuting and ToolResult (else -> {}).~~ Done in #33/#34: every tool part is drawn in order, running and done.

2. approveTool(toolCallId, …) needs a call id, but the ToolApprovalProposal event carries only tool, arguments and message. The client has nothing to pass, so approval cannot work as wired. The event contract needs a tool_call_id before any of this is built.

### 6.6 Auto-approve

*Screens:* Tools · when agents act, Tools · auto-approve, ticked, Tools · auto-approved from then on

#### Every tool and every action, in one place

Just looking (never ask): Read calendar, Search the web, Read PDFs. Calendar: Add / Change / Remove events.  Notes: Create / Add to / Replace.

Approval is a per-person comfort setting, not per agent — it has to cover built-in agents, which can't be edited, and it's about how much YOU want to be asked.

Destructive actions — Remove events, Replace notes — always ask and can't be switched off. They can't be undone from the app, which is exactly when a confirmation earns its interruption.

The comfort decision happens where you feel it: the Add card carries “Auto-approve adding events from now on”, and ticking it is what flips that switch here. The Remove card has no such option.

The card LABEL comes from the action, not the tool: calendar_write with action=delete reads “Remove from your calendar” in the error tone, never “Add to your calendar”.

BACKEND: auto_approve_writes is one boolean for both write tools and all their actions. This needs a stored per-user preference per (tool, action), checked in process_chat_turn instead of the flag, with delete and replace excluded from it entirely.

WAYS IN: Profile → Agents → When agents act (your personal settings), and a link at the top of the agent catalogue (where you manage agents). The catalogue had no entry point of its own until now either — Profile → Agents → Your agents is its door.

#### The auto-approve lifecycle

1. TICK — the checkbox gets its own row on the card, under the details and above the buttons, worded by action and time: Auto-approve adding events from now on. Ticked, the card border turns green so the choice is visible before you commit to it.

2. CONFIRM ONCE — after approving, one line says Adding events is now automatic, with Undo right there. It only appears the time you switch it on.

3. FROM THEN ON — no card at all. The write still leaves a record, tagged auto, so nothing happens invisibly. Auto-approve removes the question, never the trail.

Undo, the settings screen and the checkbox all move the same per-person, per-action setting. Remove and Replace cards never show the checkbox.

### 6.7 Web search

*Screens:* Web search · sources, opened, Web search · service down, Research · a page it couldn't read, and the Explanatory steps row

#### Where the answer came from (#40)

Each step shows its own sources, so nothing is listed twice when an answer searches four times:

- A search says what it searched for and how much came back — *Searched the web for “dinner Gràcia Thursday” · 5 results ⌄* — and opens in place to its results: each title a link, its site underneath. No card.
- A page read names its page — *Read Menu and opening hours · lapubilla.cat* — the title a link. The Read lines are the sources the answer rests on.
- A failed step keeps its tool's own icon, in red, and says why when that is known: *Couldn't read scmp.com · it blocks automated reading*, *Couldn't search the web · the hub's search service isn't answering*.

None of it depends on the model citing: it comes from the tools' own results. The answer can still name sources in prose; the spec rules out citation chips. Never an id or a tool name.

#### A closed fold says what its steps did

A finished answer folds each run of two or more steps behind a label built from them: one phrase per kind in the order it first ran, repeats counted (*Searched the web twice, read 3 pages*), a write named by its outcome (*added Dinner together*), at most two kinds then *and N more*, and *· N failed* last, in red. When nothing worked, the label is the failure itself. Thinking and looking through the sources are neither named nor counted, and a single step isn't folded at all. A screen reader still hears how many steps a fold holds.

#### Backend and client

Every tool part the hub saves carries a `summary` beside `{type, tool, success}` (`summarize_tool`, `app/domain/use_cases/chat/tool_summary.py`): a search's query, count, and each result's title and URL; a page read's title and URL; a written event's title; and a failure's reason. The same summary rides on the live `tool_result` event, so the line is the same while it is written and when the chat is reopened; secret chats save it under the same rules as the rest of the answer. It never carries snippets, ids or error text, and only `http(s)` links get through.

A failure's reason is a code — `service_unavailable`, `blocked`, `forbidden`, `too_large`, `not_a_page`, `unreadable`, `not_found`, `unknown` — set where the cause is known (the page reader, the SearXNG connector) and put into words on the phone. The exception's message is written for the model and never reaches the screen.

Search failures still don't fail the turn: `execute_tool` returns them to the model as a failed result, which is why the agent can explain. On the phone, `toolStep` decides each step's icon, words and results from its summary, `ToolStepLine` draws it, and `stepsLabel` builds a fold's label.

### 6.8 PDFs

*Screens:* PDF · attached, being read, PDF · asked and answered, PDF · what was kept, PDF · a scan it can't read, PDF · partly scanned, PDF · password-protected

#### PDFs belong to the chat they’re attached in

The paperclip opens the phone’s file picker, filtered to PDFs. Reading starts the moment you pick, so problems show up before you’ve written your question; you can keep typing meanwhile, and Send waits for the text if you’re faster.

The document rides on your message. Each read leaves a record (“Read pages 3–6”), like calendar reads, and the answer cites pages in prose — the spec rules out citation chips.

The paperclip only appears for agents allowed to read PDFs. The Coordinator doesn’t get one.

#### The file is never kept — the text is, with the chat

Tapping the document shows what was kept: title, pages, and the sections the agent can read. That list is the parser’s own structure, so it doubles as a glass box — this is literally what the Researcher sees.

The original can’t be reopened, and the sheet says so. Remove from this chat deletes the extracted text; deleting the chat does the same.

Secret chats can take attachments (reading changes nothing), but the extracted text is locked like the messages: 423 without the unlock.

#### Every problem shows on the attachment, before sending

Failures (red, nothing can be read):
- No page has any text — a scan (ScannedPdfException, 422): drawn.
- Password-protected: drawn. Today this falls into “Unexpected error parsing PDF”, or passes as a scan.
- Over 25 MB (413): “Too big to read — the limit is 25 MB.”
- Over 30 s to read (422): “Took too long to read. A smaller file will work.”
- Empty, or won’t open as a PDF (400/422): “This file couldn’t be opened as a PDF.”

Warning (copper, still attached): some pages are scans — drawn. The chip names the pages it can’t read, and the agent is told too, so it can say “page 6 is a scan” instead of answering from nothing.

NO PAGE LIMIT. The old 150-page cap is dropped: once the text is stored and read a few pages at a time, length costs nothing extra. 25 MB and 30 s are the real limits. The message box keeps working in every case; ✕ removes the attachment.

#### Backend: nothing connects a PDF to a chat yet

POST /documents/pdf parses and returns sections, page numbers and citations, then keeps nothing. The pdf_reader tool always fails (“use the dedicated endpoint”). The client has no PDF code. The Stage 2 baseline says structured chunks are stored, and that uploads go to 50 MB; neither is true (25 MB).

Needed:
- POST /sessions/{id}/documents — parse, store the sections against the session, discard the file, return id + metadata.
- pdf_reader(document_id, pages or section) reads from that store, a few pages at a time.
- Each turn tells the agent which documents are attached (name, pages, headings, unreadable pages).
- The message’s metadata_json carries the document id, for the card.
- DELETE /sessions/{id}/documents/{doc_id}; removed with the session.
- Drop max_pages: the router’s Query(150, le=300), the defaults in the reader, use case and tool schema, and the unused MAX_PDF_PAGES setting.
- Check doc.needs_pass before reading and raise its own error (code: password_protected).
- Return the pages that had no text, so a partly scanned PDF can warn instead of passing silently.

### 6.9 Offline

*Screens:* Offline · signed in, Offline · conversation

#### The offline rule

SIGNED OUT + hub unreachable → the offline screen. You cannot authenticate without the server, so there is nothing to show.

SIGNED IN + hub unreachable → the app still opens. Cached data is readable; anything that needs the hub is disabled rather than left to fail.

The banner leads with WHEN the data is from, not that you are offline — for a schedule, staleness is the fact that matters.

The agent pill greys in the offline conversation on purpose — the agent list can't load without the hub, so the picker genuinely can't open. That's the dimming rule applied correctly, not an exception to it.

#### This needs a client database that does not exist

:core:data/local/ holds StoredSessionLocalDataSource and its per-platform implementations — nothing else. No SQLDelight, Room or DataStore in any build file. The SQLite DB is the BACKEND's; the client keeps only the auth token and the member it belongs to, which is what lets the profile draw itself offline and nothing more.

So offline reading is net-new work: a cache for sessions, messages, members, spaces and events, plus a written-at timestamp per record to drive the banner.

Until then the honest behaviour when signed in and offline is an empty app with a retry — worth knowing before these screens get built.

#### Why writes are disabled, not queued

A queued calendar write is fine. A queued question to an agent is not — answering "what's left this week?" hours later produces a stale reply to a question nobody is still asking, and streaming cannot be replayed.

So the composer is disabled outright and says why. If you would rather queue, it should queue TOOL ACTIONS only, never conversation turns.

### 6.10 Profile

*Screens:* Profile, What I know about you, Shared with the household

#### Profile — reached from the avatar on any dashboard

A hub plus one content screen. Everything the spec piled onto Profile is here, but the memory audit gets its own page because it is the only part with real content in it.

#### Decisions baked in

- Two separate rows, because they are two different things: memories an agent wrote about you (AgentMemory) and facts published to the household (GossipMilestone). Same screen would conflate them.
- “Add a member” appears for admins only — it opens the invite flow.
- “Change PIN” replaces password; no email row anywhere.
- Sign out is outlined in error red, not filled — destructive but not the page's purpose.

#### The audit list

Each row is one AgentMemory: the sentence, which agent wrote it, when, and a revoke ✕. The filter maps to MemoryAuditUiState.selectedScope — PERSONAL vs HOUSEHOLD.

NO CONFIDENCE INDICATOR, deliberately. reflect_turn.py drops anything scoring below MEMORY_REFLECTION_CONFIDENCE_THRESHOLD (0.70) instead of storing it, and dedup only ever raises a score (max of old and new). So everything that reaches this screen already cleared the bar, and the extraction prompt reserves ≥0.7 for “clear, factual, or explicit user statements”.

An earlier draft showed an “unsure” chip. It pointed at memories the system had judged explicit — misleading in the direction that gets accurate memories deleted.

The closing line still matters: removing a memory does not delete the conversation, and users will assume it does unless told.

#### Still missing in code

MemoryAuditViewModel.loadAudit() populates memories but never userMilestones — the “Shared with the household” screen would render empty. It needs wiring to GET /gossip/audit.

Decided: memories can be removed, not edited. UpdateMemoryUseCase exists but stays unused in the UI.

### 6.11 Notes

*Screens:* Notes · written by agents, Notes · reading one

#### Notes: what agents write down

Created, added to or replaced by document_writer — each one approved first (Tools band). Until now there was no way to read one afterwards.

Reached from My Space, which now scrolls. The list shows title, which agent, and when. A note shows its source chat, how often it changed (version), the text, and two actions: Share (the .md export, through the phone’s share sheet) and Delete.

Read-only by design for the MVP: you ask the agent to change a note, you don’t edit it yourself. Secret chats can’t write notes at all.

#### Backend: notes don’t know where they came from

app_documents stores user, title, content and version — no agent and no session. Both the list and the “From your chat” link need agent_id and source_session_id, set by document_writer.

Bug: SaveDocumentUseCase with action=create on a title that already exists silently REPLACES that note’s content. The approval card says “Create note”, so you could approve what looks like a new note and lose an old one. Create should pick a new title, or fail and let the agent append.

Existing and ready: GET /documents, GET /documents/{id}, GET /documents/{id}/export, DELETE /documents/{id}.

### 6.12 Members & account

*Screens:* Members · admin view, Remove a member, Delete my account, Change PIN

#### One admin, anyone can see the list

list_members only needs get_current_user, so every member sees Members. Invite and Remove are admin-only; Reset PIN is available to everyone, because PIN recovery is social — any member vouches for any other. This is where that flow starts from the approver's side.

THE SOLE ADMIN CAN'T LEAVE. DeleteMemberUseCase raises SoleAdminDeletionException, and there is no promote endpoint, so Profile states the limit instead of offering a button that fails. The backend error also needs rewording: it says “Promote another member to admin first”, which points at a capability that does not exist.

#### Removing someone deactivates, not deletes

Today the user row is deleted and CASCADE takes everything with it, while shared facts and household memories are reassigned to the admin — which silently re-attributes what the removed person said to someone else. The attributed bus exists to prevent exactly that.

New model: keep id, name and colour with is_active=false (the field and the login check already exist). Explicitly erase sessions, personal memories, personal space, calendar credential, documents and the PIN hash. Shared facts KEEP their original source. Agents still pass to the admin — that is ownership, not attribution; an agent needs a living owner to be editable.

THREE CONSEQUENCES TO BUILD: context assembly labels facts from inactive members as past (“Liam, who's no longer in the household, mentioned…”) so agents stop planning around him; the profile picker excludes inactive members; name uniqueness is scoped to active members.

#### Changing the PIN signs out everywhere else

You might be changing it because someone saw it, so the old sessions have to die. The JWT is keyed on user.id and stateless, so it can't be revoked as issued — this needs a token_version on the user, bumped on change and checked on every request.

Removal and self-deletion both confirm with friction proportional to the damage: removing someone else means typing their name, deleting yourself means your PIN. Each lists what is erased and what stays, in two separate boxes, before either button is live.

### 6.13 Agents

*Screens:* Agents · the catalog, Agents · new, Agents · edit, suspend, delete, Agents · trash

#### Three states, not two — spec in STAGE_1_BASELINE.md §162–174

SUSPENDED (is_active=false): manual, stays in the catalogue, chats go read-only. TRASHED (deleted_at set): hidden from the catalogue, GET /agents/trash, 7 days. PURGED: gone, slug freed, sessions archived with agent_id=null.

BUILT-INS ARE NOT DIMMED. They are fully usable — you chat with them constantly; only editing is blocked. Dimming would say “unavailable”, the same mistake the read-only Chats row made. The signal is the ABSENCE of the chevron, plus a lock on the “built in” chip.

#### Tuning, in words instead of numbers

temperature 0.7 / top_p 0.9 means nothing to anyone who has not read an inference doc, so the form asks HOW IT ANSWERS and offers three: Straight down the line, Balanced, Full of ideas — mapping to temperature 0.3 / 0.7 / 1.0.

top_p is NOT exposed. Tuning both at once is usually a mistake; it stays at 0.9. model_alias is a footnote rather than a picker while qwen3:14b is the only model on the hub.

Other bindings: slug is create-only (absent from AgentUpdate), so the handle locks after saving; tool_permissions is a 5-item allowlist shown in plain language — “Search the web”, not searxng_search.

BOTH FORMS SCROLL. Header and the primary action are pinned; only the fields move, with a fade at the lower edge so it is clear there is more. That keeps Save (edit) and Create always reachable rather than at the end of a long scroll — and on edit it keeps Move to trash pinned too, which is worth watching: a destructive control that is always under your thumb.

#### One human label per capability, everywhere

Permissions (what an agent MAY do): calendar_read → Read calendar · calendar_write → Add events · searxng_search → Search the web · pdf_reader → Read PDFs · document_writer → Write notes

Actions (what it is ABOUT to do, on the approval card): create_event → Add to your calendar

Two vocabularies, one rule: no raw identifier ever reaches a person. Keep both maps in one place in code, keyed by the backend names, so the catalogue, both forms and the approval card cannot drift.

#### Edit only exists for your own agents

update_agent.py and soft_delete_agent.py both reject is_builtin, so Home Coordinator and Academic Researcher have no edit screen at all — the catalogue should not offer one.

Suspend and delete live here rather than in the catalogue, because both are consequences of editing an agent you own. is_active is a field on AgentUpdate, so the toggle is the same save.

Delete is outlined rather than filled, separated at the bottom, and says where the agent goes and what survives — seven days in the trash, conversations kept. A destructive action that explains itself gets misfired less than one guarded by a dialog.

#### What the trash screen has to get right

- Restore returns 410 Gone after 7 days, so a countdown can expire while the screen is open. The row has to handle failing, not just disappear.
- Purging frees the slug — worth saying, because it is the reason to purge early rather than wait.
- Conversations are NOT cascade-deleted. They archive: readable, not repliable. That is the reassurance that makes deleting an agent feel safe, and it is currently invisible.
- Trashed AND suspended both return 400 on posting to existing sessions, so the Chats list needs a read-only state for both.

All of it already works server-side — 12 tests in test_agents.py. This is purely missing UI.

### 6.14 Agent form errors

*Screens:* New agent · required fields missing, New agent · handle already used, New agent · handle held by your trash, New agent · the hub didn't answer

#### How the form fails

Errors appear when you tap Create, never while you’re still typing. The form scrolls to the first one; each field says what’s wrong directly under itself, with a red edge on the field. No dialogs, and nothing you typed is ever cleared.

Create is never disabled. A dimmed button can’t tell you what’s missing — and dimming is reserved for things that can’t be opened.

“What it’s for” is the only optional field, and now says so on the form itself.

#### The cases not drawn

- Someone else’s deleted agent holds the handle: “[Name]’s deleted agent still holds this handle. It frees up in 4 days — or change the handle.” No buttons, even for an admin who technically could purge it: this form shouldn’t destroy someone else’s agent.
- No handle can be made from the name (all emoji, non-Latin script): the handle stays empty and asks for one.
- Typing an invalid handle can’t happen: the field lowercases and turns spaces into dashes as you type.

The edit form has no handle, and names are allowed to repeat, so only “missing” and “didn’t answer” apply there.

#### Backend: the client can’t tell these apart yet

create_agent.py raises InvalidOperationException for both conflicts, so both come back as 400 with an English sentence. The app would have to string-match “currently in trash” to know which screen to show.

Fix: raise the unused SlugConflictException as 409 with a code — handle_taken or handle_in_trash — and, for the trash case, agent_id, whether it’s yours, the owner’s name and days_remaining. That’s what the Restore / Delete buttons and the other-member copy need.

Also: name and system_prompt accept empty strings (no min_length). Add min_length=1 after stripping, on create and update.

### 6.15 Agent unavailable

*Screens:* Chats · agent unavailable, Conversation · read only, Chats · read-only row options, Conversation · agent purged

#### When the agent isn't there

Trashed and suspended both return 400 on posting to an existing session, so both get one treatment: the history reads normally, the composer is replaced by an explanation and a way out.

The Chats row matters as much as the screen — you should learn a thread is read-only before tapping it, not after typing into it.

Deliberately NOT the offline treatment. Offline is environmental and the fix is to wait; this is a state you chose and the fix is a button. Same visual weight would teach the wrong response.

Copy avoids naming which state it is — “paused” covers suspended and trashed. Purged agents land here too, but their conversations can never be resumed, so that variant needs different copy: no “bring it back”, just a note that the agent is gone.

#### Dimming said the wrong thing — fixed

The first read-only row greyed everything out, which is the language for “you can't have this”. The thread opens and reads perfectly — only replying is blocked — so it now looks as available as any other row, with one copper “Read only” chip carrying the single limit.

Rejected: a pause marker on the avatar. Truer, since it is the AGENT that is paused rather than the thread, but a 17px dot is easy to miss and has to be learned. This state comes up rarely enough that it should announce itself.

General rule worth keeping: reserve dimming for things that cannot be opened. Anything readable stays at full contrast and states its limit in words.

#### Purged is a different promise

A purged agent leaves agent_id=null, is_archived=true — so the header has no agent to name. It shows the conversation title instead, with an Archived badge. There is no “bring it back”, because there is nothing to bring.

An inline note in the stream says when the agent went, so the history stops making sense at a known point rather than trailing off.

The one action is forward: start a new conversation. Everything else here is past tense.

### 6.16 Search & secret titles

*Screens:* Chats · unlock secret chats, Chats · secret titles unlocked, Chats · title search, Chats · no matching titles

#### Search & secret titles

Starts from 6 · Chats in the daily loop, which now shows the locked state: a bar at the top of the list, and secret rows that say only “Private conversation”, the agent and the date.

Unlock → PIN → the same list with real titles and previews. Search is separate and never shows secret chats, locked or not.

#### Two ways in, one unlock

- The bar’s Unlock opens the PIN screen worded for all secret chats, then returns to the list. For “which one was it?”.
- Tapping a locked row opens the existing per-chat PIN screen (Secret Mode band), then that chat. For when you already know.

Either way the unlock covers every secret chat for the window. The bar only appears when you have secret chats. Once unlocked it offers Lock now, which throws the unlock token away and hides the titles immediately. No countdown: the timer slides with every touch, so a number would only ever read 10:00.

#### Backend: titles are held back, not hidden

GET /sessions returns title and last_message_preview = null for secret sessions unless the request carries a valid unlock token. The app renders that as “Private conversation”, and drops what it holds when the chats lock again.

reflect_turn keeps titling secret chats. The title was never the leak: the messages are already stored in plain text. The leak was showing it without the PIN.

Rejected: hiding titles in the app while the server always sends them. Anyone with the login token would see them.

Spec: STAGE_5_SECRET_SESSION_LOCKING.md §4.3.

#### Search is titles only for the MVP

Filters on the phone as you type, over the list GET /sessions already returns in full. It matches the title and the agent’s name; no backend work, and it works offline over whatever is loaded.

The footnote says what it can’t do, so a miss isn’t mistaken for a lost chat. Full-text search (SQLite FTS5, matched line shown, opens at the message) comes after the MVP.

#### Decided: the preview line gets a field

Every Chats row shows the last message under the title, but SessionRead had nothing to supply it. It gains last_message_preview for all sessions: the latest message, truncated on the server.

For secret sessions it is held back exactly like the title — null while locked, real once unlocked.

### 6.17 Empty states

*Screens:* Empty · no chats yet, Empty · nothing remembered, Empty · nothing shared, Empty · trash, Empty · no notes

#### Empty states

One pattern everywhere: a soft tile with the screen’s own Hearth icon, a plain title, and one line on what will appear here and how.

Only Chats gets a button. It’s the one empty screen where you’re expected to act; the others fill themselves as you talk.

Empty Chats has no search and no secret bar — both arrive with the first chat. Memory keeps its tabs at zero so the two scopes are learnable before they fill. Shared keeps its Secret Mode card, because that promise is true (milestones are dropped for secret turns).

#### Decided: secret chats write no memories

reflect_turn.py used to drop milestones for secret turns but still extract memories, only forcing them to personal. They showed up unlocked in What I know about you, and personal memories are loaded into every chat, so a secret surprise could surface in an ordinary one.

Now memory extraction is skipped for secret turns exactly like milestones; the title step still runs. Which is why the empty memory screen can promise “Nothing said in Secret Mode is ever written down.”

Spec: STAGE_5_SECRET_SESSION_LOCKING.md §4.4.

### 6.18 Calendar

*Screens:* Calendar · pick provider, Calendar · credentials + test, Calendar · Google credentials, Calendar · auth rejected

#### Calendar sync — pick a provider, prove the credentials, then save

ConfigureCalendarUseCase encrypts the secret with Fernet, calls test_connection, and only persists if it succeeds. So the button really does verify — “Nothing is saved until the hub can reach your calendar” is literally true, not reassurance.

#### The app-password wall

Neither provider accepts a normal account password over CalDAV. This is where almost everyone fails, so it is called out BEFORE the field and again as the first explanation when auth is rejected.

Apple: 2FA required, password shown hyphenated (abcd-efgh-ijkl-mnop). Google: 2-Step must ALREADY be on or the App Passwords page does not exist — a dead end the user has to fix elsewhere before returning. Google shows the password space-separated.

#### One calendar per member — resolved

credential_repo.save() replaces rather than appends, and Profile now matches: a single row showing the connected account, its sync time, and Change — which replaces rather than adds.

The copy says so out loud (“One calendar per member”) so nobody goes looking for an Add button that would silently overwrite what they already had.

If a second calendar is ever wanted, the backend needs a real one-to-many first; the UI is the easy half.

#### This is where the email comes back

Removed from account creation, required here — the CalDAV username IS an Apple ID or Google address. Exactly the split you asked for: no address to make an account, an address to reach a calendar.

It is stored on the credential (CalendarCredential.username), not on the User, so the account still has no email field.

#### Google's URL is per-account

Apple has one host for everyone (caldav.icloud.com), so that field is a fixed preset. Google gives each account its own path:

apidata.googleusercontent.com/caldav/v2/{address}/events

So the client builds it from what was typed and shows the result read-only — asking someone to paste that by hand is how you get a support conversation. The backend accepts any https URL (SSRF-guarded), so construction is the client's job.

#### Worth knowing before you build on it

Google treats CalDAV basic auth as legacy and steers everything toward OAuth. App passwords work today but are the path Google has been narrowing for years.

For a two-person homelab that is an acceptable bet — if it breaks, you reconnect. Worth knowing it is a bet rather than assuming parity with Apple, which shows no sign of removing app-specific passwords.

### 6.19 Secret Mode

*Screens:* Secret ON · Copenhagen Day, Secret ON · Midnight Espresso, Secret · locked, needs PIN, Secret · fingerprint unlock

SECRET MODE IS INDEPENDENT OF THEME. Same session, same state, both palettes — Secret Mode never switches the theme, and the theme never implies Secret Mode.

Dimming the screen for privacy was considered and rejected: it overrides the user's own theme setting, and it announces the mode to anyone glancing over your shoulder — the opposite of what the achromatic ghost is for.

Ghost alphas differ per palette — Day tint 20 / edge 69 / frame 66, Night 14 / 55 / 45 — solved so both land on the same perceived edge. A shared alpha reads ~1.5× weaker in daylight.

#### Locking is now server-enforced — see STAGE_5_SECRET_SESSION_LOCKING.md

What was there before: unlockSecretSession accepted any non-blank string, the locked set was in-memory so nothing survived a restart, and GET /sessions/{id} served secret messages to any authenticated owner. The lock was decoration.

What replaces it: POST /auth/unlock-secret verifies the PIN with the same hasher login uses and returns a scoped secret_read token, 10 minutes, held by the device. Session reads return 423 Locked without it — not 401, so the client's token-refresh logic doesn't fire on a lock.

No unlocked_until column on the server: that would outlive the device and unlock the tablet when you unlocked your phone.

#### The idle timer's one rule

It resets on USER INPUT ONLY — tap, type, send. Never on SSE deltas, health polls or background sync. With no absolute cap, that rule is the only thing stopping a wall-mounted tablet nobody has touched from staying unlocked forever.

Re-lock also fires immediately on background, device lock and sign-out. Those are the real “I walked away” signals; the timer is just the fallback when none of them does.

Expiry is quiet: no modal, the next tap lands here, and a stream in flight finishes first.

#### Biometrics is phase 2, and only a shortcut

A face or fingerprint authenticates to the phone, never to the hub, so it cannot produce the secret_read token directly. It releases a device-bound secret from Keystore/Keychain which the client exchanges for one.

ONE WORDING FOR EVERY PHONE: “face or fingerprint”, and the biometricUnlock icon (viewfinder corners around a keyhole) — no finger, no face. Android can’t tell you which method BiometricPrompt will use, so anything specific would be wrong somewhere. The system sheet on top of this screen names the exact method anyway.

BIOMETRIC ONLY, NO PHONE-PASSCODE FALLBACK: partners often know each other’s passcode. The fallback is the hub PIN. The key is invalidated if a new face or finger is enrolled.

Off on shared devices, always — BiometricPrompt returns success, not identity. Not available on Web/Wasm.

### 6.20 Privacy phrases

*Screens:* Privacy phrase · offer to go secret, Privacy phrase · accepted

#### Saying “don’t tell” makes that message secret

process_chat_turn matches phrases like “keep this between us” and “don’t tell”. That one turn is then treated as secret: nothing is shared with the household, nothing is remembered, and write tools are locked for it. The line under the bubble says so, in the Secret Mode grey.

The server also returns suggest_secret_mode, so the chat offers to go fully secret. “Not now” leaves it as it is; the offer comes back only on the next phrase, never as a nag.

Accepting flips the header to ON, gives the screen the ghost frame, and marks the point with “Secret from here on”. Anything shared before that point stays shared — the switch doesn’t reach back.

#### Backend and client gaps

- The client parses suggestSecretMode from the stream’s done event and then drops it — no ViewModel reads it.
- The phrases are English regexes only. Spanish or Catalan (“no se lo digas”, “entre nosotros”) never trigger.
- Switching to secret locks the chat you’re in. Under the locking spec the next read needs an unlock token, so the app would bounce you to the PIN screen mid-conversation. Fix: PATCH /secret (and creating a secret chat) returns a secret_read token scoped to that one session — it only grants what you could already read a second ago. Not the all-chats token: that would let anyone holding the phone make a chat secret and read every other one.

### 6.21 Waiting has one shape

*Screens:* every screen that calls the hub

#### Five patterns, and why there are five and not one

A spinner in the middle of the screen says only "something is happening". Which thing, and where the answer will appear, is what a person actually needs to know — so the pattern follows the shape of the wait.

| # | Pattern | Where |
| :-- | :--- | :--- |
| 1 | The tapped button keeps full colour and shadow, its label becomes the verb in progress, a 4dp bar runs along its bottom edge | The eight button actions: invite code, join, create an invite, approve a PIN, a new PIN, changing a PIN, removing a member, leaving |
| 2 | The screen arrives empty: real chrome draws at once, breathing blocks where the answer lands | Members, Profile, Forgotten PIN |
| 3 | The screen refreshes: rows stay put, a full-bleed bar under the header | Members, coming back to it |
| 4 | One card reloads in place | Add a member, asking for a second code |
| 5 | The PIN pad's six dots wave | The PIN pad, which has no submit button to put a bar on |

Pattern 5 exists only because pattern 1 has nowhere to attach: the pad submits on the sixth digit, so there is no button. The dots are the only thing on screen that belongs to the action.

#### Four beats, identical everywhere

Nothing for 250ms, then the pattern, held at least 400ms; at 8 seconds a quiet caption joins it; then the Unreachable / Failed wording each screen already has.

The first beat is the one that matters most and is the easiest to get wrong. A hub on the LAN answers in well under 250ms, so **the common case is that no loading state appears at all** — the screen simply updates. A loading state that flashes for two frames is worse than none, which is what the 400ms floor is for at the other end.

Nothing is ever dimmed while it waits (§2). A working button keeps its colour, its shadow and its place in the accessibility tree; it is not `enabled = false` wearing a different name.

#### An animated component must be able to stand still

Compose UI tests synchronise on idleness, and **an infinite animation never lets a composition go idle** — `waitForIdle`, `waitUntil` and every `onNode…` under one time out rather than failing with something you can read.

So motion is switched off by construction rather than driven by hand. `HearthMotion` carries `animate` beside its durations; `StillMotion` is the same scale with `animate = false` and the time-gated beats collapsed to zero. Every animated component reads `HearthTheme.motion.animate` and draws its **resting frame** when it is false: a full-opacity skeleton block, a bar frozen at a fixed fraction, dots at rest.

Tests reach it through one composable, `StillTheme`, and never compose `HearthTheme` directly — `StillMotionInTestsTest` fails the build otherwise. That guard exists because the first version of this relied on `TestApp` alone, and the tests that most needed the off switch turn out not to use `TestApp`: every `every_previewed_state_draws` mounts its screen's `Content` on a bare theme. The first busy state added to a preview provider would have hung a whole screen test rather than failing it. The theme's own tests are the one exception, since proving `HearthTheme` hands out `DefaultMotion` means composing the real thing.

The rule that follows: a component that cannot stand still cannot be tested, so it does not ship. Nothing asserts on the motion itself — no test names a duration, an easing or a frame. The only timing test is `waitPhase`, which is arithmetic over two thresholds with no clock in it. The motion is checked by eye, once, on a real device against a real hub.

---

## 7. Still open

| Item | State |
| :--- | :--- |
| **Morning briefing** | Next to design. The Household dashboard and "Your day" in My Space show text no endpoint produces |
| **Tablet split layout** | Deferred to a later stage. Stage 5 ships phone portrait only |
| **Night theme per screen** | Tokens are proven in both themes; only the Secret Mode pair is drawn at night |
| **Full-text search** | After the MVP: SQLite FTS5, matched line shown, opens at the message |
| **Biometrics** | Phase 2, per the locking spec §7 |
| **Several devices on one session** | Out of scope |
| **Google calendar via OAuth** | App passwords work today, but Google has been narrowing that path |
