# Household Hub: Memory

**Status:** ✅ Built in slice 5 (20 October 2026)
**Target:** `backend` (`memories`, `chat`, `integrations`, `bootstrap`) + `client` (`:core:*`, `:composeApp`)
**Date:** October 2026

This is the spec for Stage 5 slice 5. It replaces the per-turn reflection, the gossip milestones and the
"Profile, memory and sharing" slice as first planned. It was agreed decision by decision on
12 October 2026, with measurements taken on the live hub; §11 holds those numbers. The screens and
the questions left open were settled on 13 and 14 October; §8 and §12 record them.

---

## 1. Why this exists

Issue #133 began as "calendar events are saved as memories". Reading the logs and the database showed
the cause is the shape of the memory system, not the calendar.

1. **Writing is blind and append-only.** `ReflectTurnUseCase` never shows the model what is already
   stored. It can add, but it cannot correct, merge or retire. Its only duplicate check is identical text.
2. **It runs after every turn.** Each run is a model call that reasons for 12 to 17 seconds on the one
   model slot, and the member's next message waits behind it (#81). Turning reasoning off makes it fast
   and wrong (#81's findings), so the call cannot be made cheap where it is.
3. **It reads the assistant's words as facts.** In the test chat of #133, two of three writes came from
   what the agent said, not from the member. An agent that claims something untrue (#64) would have it
   remembered.
4. **Two stores hold the same thing.** Every chat that wrote a household memory also wrote a gossip
   milestone. Of 40 milestones on the hub, 36 were schedule items.
5. **Reading is by recency.** The prompt takes the newest 20 personal memories, 10 household memories
   and 15 milestones, relevant or not. With 50 personal memories stored, 30 never reached an agent.
   Every write also changes the system prompt, so the next call re-reads all of it (#79's cache is undone).

---

## 2. Writing memories

Nothing is written after a turn any more. There are two ways a memory is made.

### 2.1 `remember` and `forget`: when the member asks

A tool the agent calls during the turn, for "remember that…" and "forget that…".

- It is a write tool: it pauses on the approval card (Decline / Edit / Approve) like any other, and is
  locked in a secret turn.
- The card shows the closest stored memory (§6), so the member sees what would be added to or replaced.
- **It never replaces another member's memory.** When the closest memory was said by someone else,
  approving saves the new one as a household memory, keeps theirs, and raises a proposal for them
  (§6.1). The card says "Liam said" over the stored memory and "This is kept too. Liam will be asked
  which one is right." Replacing the member's own memory still reads "This replaces".
- `remember` can be set to run without asking, like adding an event. `forget` always asks, like
  removing one, and has no Undo; once it runs it leaves a step line ("Forgot a memory · …").
- The write changes the system prompt, so the next call re-reads it once, about 2 seconds. That is
  accepted: it only follows something the member asked for. No per-chat snapshot of memories is kept.
- The same model sometimes says it did something without calling the tool (#64). Reflection (§2.2)
  covers that: it reads what the member said and saves it anyway.

### 2.2 Reflection: after the chat goes quiet

One model call per idle chat, in place of one per turn. It can reason as long as it needs, because
nobody is waiting on the slot.

**The sweep.** A loop inside the hub, started with the app like `periodic_trash_purger`
(`app/bootstrap/app_factory.py`), waking about once a minute. No cron job: the loop can see whether a
turn is running, and the hub is a single process. For each chat with messages newer than its marker,
it reflects when all of these hold:

| Condition | Why |
| :--- | :--- |
| The chat's newest message is older than the idle time (10 minutes, a setting) | The member has probably stopped |
| Ollama reports the chat model in memory (`GET /api/ps`) | Never load the model just to reflect; the desktop keeps it 15 minutes |
| No turn is running, anywhere on the hub | Never start in front of a live answer |
| No approval card is waiting in the chat | The turn is paused, not finished; the chat is skipped until the card is answered |

A chat that fails a condition is tried again on a later pass. A chat left unreflected by a restart or a
skipped pass is picked up the next time the model is in memory.

**A turn always wins.** Whether a turn is running is known only per chat today
(`presentation/api/session_lock.py`). It becomes a hub-wide port in `domain`, set while any turn runs.
A turn that starts while reflection is running cancels it; the marker is not moved, so a later pass
redoes the work. Nobody waits behind reflection.

**Which model.** The chat's own agent model, resolved through `ResolveAgentModelUseCase` as a turn does;
the sweep checks that this model is in memory. Every agent runs the same model today, so there is no
separate reflection setting.

**Not keeping the model longer.** `GET /api/ps` returns each loaded model's `expires_at`. A reflection
call passes `keep_alive` as the time left until then, so the model unloads when it would have anyway.
To be confirmed against the hub's Ollama version when the sweep is built.

**The marker.** Each chat stores the last message that was reflected and when. A chat the member comes
back to reflects only the new part. It also stores what the last run did, for the screens (§8).

**What reflection may save.**

- Only what the member states. The assistant's text is context for understanding it, never a source.
- Nothing from a chat that is secret now, and nothing from a message that tripped a privacy phrase
  (`privacy_trigger_detected` in its metadata). Whether a chat was secret when each message was sent
  is not stored yet, so a chat switched to secret part-way is not reflected at all. Keeping what came
  before, as the locking spec's §4.4 asks, is slice 6.
- Dates are written as absolute dates, as today.

**What it does with each fact** is §6: save it, or raise a proposal.

**Later trigger.** When the chat connection becomes a WebSocket (#165), a socket that
closes and stays closed can trigger the same job sooner. The sweep stays as the safety net.

### 2.3 Chat title

Reflection used to write the title on the first turn. Now the title is the start of the member's first
message, cut with "…" when long, so a new chat is recognisable at once. The first reflection replaces
it with a proper one. Chats cannot be renamed today (the only session `PATCH` is `/{id}/secret`); when
renaming arrives, it adds a flag that stops the replacement.

---

## 3. One store

Gossip milestones and household memories were written together and read together. They become one.

### 3.1 `agent_memories`

| Column | Status | Purpose |
| :--- | :--- | :--- |
| `id`, `user_id`, `agent_id` | kept | Who it is about or who said it, and which agent recorded it (empty for a manual entry) |
| `scope` | kept | `personal` or `household` |
| `category` | kept | `preference`, `fact`, `milestone`, `health`, `project` |
| `content` | kept | The statement |
| `confidence` | kept for now | Not shown to the model or the member; dropping it is a separate decision |
| `source_session_id` | kept | The chat it came from |
| `is_active`, `created_at`, `updated_at` | kept | |
| `expires_at` | **new** | Optional. An expired memory is left out of the prompt and of recall |
| `origin` | **new** | `reflection`, `tool` or `manual`: how it got there |
| `relevant_for` | **new** | A short list of when this memory matters, written by the model when it saves (§5.2) |
| `event_id` | **new** | Optional. The calendar event this memory is about (§7) |
| replaced-by and retired-at | **new** | Set when a memory is retired in favour of another, or removed by the member, so the history can be read and undone |
| `moved_from` | **new** | Optional. For an event memory, the event's time before it last moved, for the "Moved in your calendar · was …" line (§7) |
| `event_starts_at` | **new** | Optional. For an event memory, the event's start it was last matched to, so the periodic check can tell it moved (§7; added in PR 7) |
| `time_from_event` | **new** | Optional. `timed` or `all_day` once the hub rebuilt the sentence from its event: the content is then the title, and each reader adds the time in its own zone (§7; added in PR 7) |

A memory is never deleted by the system, only made inactive. Removing one with ✕ retires it, and Undo
brings it back until the member leaves the page.

### 3.2 What happens to the gossip bus

The bus was a behaviour: something one member says reaches every agent, with the speaker's name. That
stays. A household-scope memory is the bus.

| Bus feature | Where it lives now |
| :--- | :--- |
| Shared with every agent | Household-scope memories |
| Who said it | The memory's own member and agent, shown in the prompt line |
| Expiry | `expires_at` |
| Audit and take back | The member's own household memories, made inactive |
| Manual publish | Adding a household memory, `origin = manual` |
| Secret Mode | Unchanged: nothing from a secret turn is stored |

Removed: the `gossip_milestones` table, the `/gossip` endpoints, the milestone categories, and notes
addressed to one member (the entity allowed them; no query ever delivered one). The name copies on a
milestone go too: if an agent is purged, a memory it recorded reads "an agent".

### 3.3 Starting empty

The hub is a development environment. The migration clears `agent_memories`, `conversation_sessions`
and `chat_messages`, and drops `gossip_milestones`; nothing is carried over. Clearing the chats means no
old chat waits to be reflected without a marker.

The reflection PR clears memories, vectors, proposals and chats once more, so the new reflection is
tested from a clean start rather than on what per-turn reflection wrote in the meantime.

### 3.4 `conversation_sessions`

Gains the reflection marker (last reflected message, when) and a small record of what the last run
added, proposed and retired.

### 3.5 `memory_proposals`

A pending change to a stored memory (§6.1).

| Column | Purpose |
| :--- | :--- |
| `id` | |
| `memory_id` | The stored memory it would change |
| `content` | The new statement, as read in the chat |
| `relation` | What the model named (§6). Kept for the logs; never shown |
| `answerer_id` | The member who said the stored memory. Only they can answer |
| `source_session_id`, `source_user_id` | The chat it was read in, and whose chat that is |
| `state` | `pending`, then `kept` (Keep stored), `both` (Keep both), `replaced` (Use new) or `dropped` |
| `created_at`, `answered_at` | |

A proposal is dropped, not answered, when its stored memory is removed or retired, or when a newer
proposal about the same memory takes its place.

---

## 4. Vectors

Memories are matched by meaning, so each one is embedded with `bge-m3`, which the hub already runs on
the server's own Ollama for ranking what a turn reads. `IEmbedder`
(`app/domain/repositories/embedder.py`) is reused.

### 4.1 `memory_vectors`

An ordinary table, so it has a model, a migration and a foreign key like any other.

| Column | Purpose |
| :--- | :--- |
| `memory_id` | Foreign key; the vector goes when the memory does |
| `kind` | `content` or `relevant_for`: a memory has one of each |
| `model` | The embedding model that made it. Vectors from two models cannot be compared, so a change of `EMBEDDING_MODEL` shows which rows to redo |
| `vector` | 1,024 numbers as packed 32-bit floats, about 4 KB |

The vector stays out of the domain entity: it is a detail of how the data layer searches.

### 4.2 Search

Ranking uses `vec_distance_cosine` from the `sqlite-vec` extension, called on that ordinary table. The
extension is loaded on each database connection by a hook on the engine, with SQL-level extension
loading left switched off.

- This keeps the speed of the extension without its `vec0` virtual table, which has no foreign keys and
  sits outside the ORM.
- If the extension fails to load, the same rows are ranked in Python. It is an accelerator, not a
  requirement.
- `sqlite-vec` is pre-v1 and scans every row; the stable release has no approximate index. At household
  size that is not a limit (§11.3).

### 4.3 When a vector is made

- On write, and again when `content` or `relevant_for` changes.
- If the embedder is down, the memory is saved without one, and the sweep fills it in later.

---

## 5. Reading memories

Two parts of one mechanism. The fixed block decides what an agent always knows; recall brings in what
did not fit, when a message relates to it.

### 5.1 The fixed block

A block in the system prompt, filled up to a token budget (a setting, not a row count; about 1,000
tokens to start, half of today's memory lines).

It holds the member's own personal memories and the household ones, never another member's personal
ones. In a secret chat, personal ones only. Recall (§5.2) follows the same rule.

1. Preferences and health first, personal and household alike. Recall cannot find these: "answer
   briefly" resembles no message.
2. Then every other memory, newest first, until the budget is full.

Retired and expired memories never go in. A household line says who said it. The confidence figure is
not printed. The block only changes when a memory is written, so the cached prompt holds from one
message to the next.

While the store is small, everything fits and the agent knows all of it. As it grows, older facts fall
out of the block and are reached by recall.

### 5.2 Recall

Once per message the member sends, while that turn's prompt is assembled. Not on tool rounds, not when
a paused turn resumes, not on regenerate: those reuse what the message already carries.

- **The question** is the member's message plus the previous exchange. A follow-up like "what should I
  get her?" carries no meaning alone.
- **It searches** memories that are not in the fixed block; in a secret chat, personal ones only.
- **It scores** a memory by the better of its two vectors: the statement and its `relevant_for` list.
  The list is what makes a dinner question find "is vegetarian" (§11.4).
- **It attaches** the best matches above a cut-off, up to a small maximum, to the message, and saves
  them with it in `chat_messages.metadata_json["recalled"]`: each memory's id and its text exactly as
  attached. Later turns replay the same text, so the cache holds. A memory already attached to an
  earlier message in the window is not attached again.
- **It stays silent** when nothing passes the cut-off, which is the normal case.
- **It gives up** when the embedder has to load (6.3 seconds, measured): a wait cap skips recall for
  that message.

The cut-off is a setting and starts at 0.60, with at most 3 memories attached (§11.4: wanted matches
scored 0.63 to 0.69, noise up to 0.54). Similarity scores are only comparable within one
question, so the real value has to come from the hub's own chats. Recall therefore logs its scores from
the first day, including for memories that are already in the block, where it attaches nothing.

Not built: a `recall` tool the agent chooses to call (it would depend on the model, as #64 shows), a
model call that rewrites the question first (a wait before every answer), and a reranker (Ollama's API
documents no rerank endpoint).

---

## 6. Comparing on write

Every new fact, from `remember` or from reflection, is compared with what is stored.

1. The closest stored memories are fetched by similarity, with a low bar.
2. The score never decides alone. A contradiction scores as high as a rewording (§11.5), so a rule like
   "above 0.9 is a duplicate" would throw corrections away. The model reads the new fact beside its
   neighbours and names the relation.

| Relation | Meaning | In reflection |
| :--- | :--- | :--- |
| same | Already stored, in other words or another language | Nothing is saved |
| different | A separate fact | Saved |
| more detail | Agrees and adds something | Proposal |
| changed | The stored one is out of date | Proposal |
| contradicts | Both cannot be true | Proposal |
| unclear | The model cannot tell | Proposal |

In `remember` the member is already looking at an approval card, so the relation and the closest
memory are shown there and the member decides; no separate proposal is raised.

A **proposal** is a pending change to a stored memory. The stored memory stays active until the member
answers. This is the guard against a background step quietly losing information, which is the reason
another memory system (Mem0) reportedly stopped updating automatically.

When a memory is replaced, the old one is made inactive and records what replaced it and when.

### 6.1 Proposals

The relation decides whether a fact is saved, dropped or proposed, and it is logged. It is not shown:
every proposal is the same card, whichever of the four relations raised it.

- **Three answers.** Keep stored (the proposal is closed), Keep both (the new statement is saved beside
  the stored one) and Use new (it replaces the stored one). Use new is the suggested answer.
- **Only the speaker answers.** A proposal against a memory another member said goes to that member,
  and counts only in their "to check". Everyone else sees it read-only, with who will choose.
- **Where it came from.** The card shows when and in whose chat the statement was read. The chat's
  title is shown only to the chat's owner; anyone else sees the owner's name ("Emma's chat").
- **No editing.** To word it differently, the member tells an agent to remember it; that card has Edit.
- **No expiry.** A proposal stays until it is answered or dropped (§3.5).
- **A count only.** No notification.

---

## 7. Calendar events as memories

Events are memories. This reverses #133's first reading ("the calendar is the source of truth, so none
of this needs remembering"): what an agent knows about the member's plans is part of knowing them, and
a shared event is how another member's agent can plan around it. The fault was that the memories went
stale and repeated, and that is what this section fixes.

**Which events.** Those that come up in a chat: created, changed or mentioned. Events that exist only
in the calendar app are not turned into memories; an agent reads those with its calendar tool.

**The link.** A memory made from an event the hub touched keeps that event's id in `event_id` and
expires when the event ends. Reflection takes the id from the chat's saved tool steps, so an event
created and removed in the same chat leaves nothing. A mentioned event with no id known is a dated
memory with an expiry and no link.

**Removed events are named.** An event the chat touched that is gone, whether removed in the chat or no
longer in the calendar, is listed for the model as removed (`[r1] Dinner · Sun 25 Oct 2026, 20:00`),
with its last title and start from the chat's steps, so it doesn't save the event as a plain dated
memory. A fact the model tags with one is dropped and logged (`outcome=dropped_removed_event`).

**Scope.** Personal or household, decided per event like any other memory.

**Staying true.**

| The event is changed… | What happens to the memory |
| :--- | :--- |
| Through the hub (`UpdateCalendarEventUseCase`, `DeleteCalendarEventUseCase`) | Updated or retired at once. No proposal: the member already made the change |
| Outside the hub, in a calendar app | A periodic check finds it |

**The periodic check.** The hub keeps no copy of the calendar and has no sync: events are read live from
Apple or Google. So a pass asks the calendar for the event of each linked, unexpired memory
(`ICalendarConnector.get_event`). Gone means retire; moved means update. It needs no model, so it runs
on its own schedule, every 30 minutes, apart from the reflection sweep. A calendar that cannot be
reached changes nothing: a Google sign-in that expired, for instance while the OAuth app is in Testing
(seven days), stops updates until the member reconnects, and retires nothing.

**When it moves.** The sentence is rebuilt from the event's fields, with no model call, so it is right
at once, and `moved_from` keeps the time it had. The hub keeps every time in UTC (Anton, 19 October), so
the rebuilt sentence stores only the title with `event_starts_at`: the phone adds the time in its own
zone, and a prompt in the zone the phone sent with the message. The member sees the new sentence with "Moved in your
calendar · was Sat 20:00". A removed event's memory is retired silently.

**Repeating events.** One memory for the series, expiring when the series ends, or never.

---

## 8. What the member sees

Designed on the canvas's "Memory (slice 5)" page, 13 and 14 October 2026. The design notes (§6.22)
hold the wording, colours and every state; this section is what the hub must serve.

- **The memory page**, from Profile's Memories row. Yours and Household tabs; each row is the sentence,
  who said it, which agent, when, an "until" date for event memories, and ✕ on the member's own. ✕ is
  one tap with Undo in place. Household rows by others have no ✕.
- **Changes to check**, from a band at the top of the memory page and a count on Profile's row. One
  card per proposal the member can answer, then the ones waiting for someone else, read-only.
- **In a chat**, a quiet line after the last message looked over: "Remembered 2 · 1 to check · 10:52".
  It opens "Remembered from this chat": changes to check first, then what the look-over saved, each with
  ✕ and answers in place. A look-over that saved nothing shows "Looked over · 10:52 · nothing new", which
  opens nothing.
- **On the approval card**, `remember` shows the closest memory and whether it is replaced or kept
  (§2.1).

Two things the old design had that no longer apply:

- **The publish notice and its Undo.** No turn publishes anything now, so there is nothing to confirm
  at the end of a turn.
- **Two separate lists** for memories and shared facts. They are one store; a shared fact is a
  household memory.

"Nothing said in Secret Mode is ever written down" stays true, and is delivered by this slice.

---

## 9. Logging

Thorough on purpose. Reflection logs nothing today, which is why #133 had to be read out of the
database. Every problem in this slice should be readable from Loki without guessing.

Every line carries the member and chat ids, in the form the tool-call lines already use (`_log_call`
in `process_chat_turn.py`). Content from a secret turn is never logged.

Lines carry ids, scores, relations, outcomes and counts. **Memory text is off by default**: a hub setting
(`MEMORY_LOG_TEXT`) adds it, cut to 500 characters, while tuning. That is the only way to check a
dropped fact, which is stored nowhere else.

| Area | What is logged |
| :--- | :--- |
| Sweep | For each chat it considers: reflected, or skipped and why (not idle yet, model not in memory, a turn running, nothing new, secret) |
| Reflection run | Messages covered, duration, tokens; for each fact found, its neighbours with scores, the relation the model chose, and the outcome (saved, proposal, dropped) with the memory id |
| `remember` / `forget` | The closest stored memory and its score, beside the existing tool line |
| Embedding and search | Whether `sqlite-vec` loaded at startup, each fallback to Python or to keywords, failures, durations, how many missing vectors a pass filled in |
| Prompt block | Budget used against the limit, how many memories went in, which were left out |
| Recall | Per message: the top scores, what was attached, the cut-off in force, how long it took, and why when it attached nothing (below cut-off, embedder loading, wait cap) |
| Calendar link | A memory updated or retired because the hub changed its event; for the periodic check, how many were checked, changed and retired, and any calendar that could not be reached |
| Proposals | Created, answered (which answer, by whom), dropped and why |
| Title | When reflection replaces the automatic one |

A Grafana dashboard for these is not part of this spec; which panels it shows is a separate choice.

---

## 10. Test expectations

Behaviour the slice's tests must pin. Names are for orientation; each PR writes its tests first.

| Behaviour | Assertion |
| :--- | :--- |
| No reflection per turn | Finishing a turn starts no model call |
| Sweep waits for idle | A chat younger than the idle time is not reflected |
| Sweep respects the model | With the chat model not in memory, nothing is reflected and the reason is logged |
| Sweep respects turns | With a turn running anywhere on the hub, nothing is reflected; a turn that starts mid-reflection cancels it and the marker does not move |
| Sweep skips a paused chat | A chat with an unanswered approval card is not reflected |
| Model kept no longer | A reflection call's `keep_alive` is the time left on the loaded model |
| Marker | A second pass over an unchanged chat does nothing; a returned-to chat reflects only the new messages |
| Member's words only | A fact that appears only in the assistant's answer is not saved |
| Secret writes nothing | A secret chat, or a message with a privacy trigger, creates and changes no memory (moved here from the locking spec's §8) |
| Secret still titled | A secret chat still gets its title (moved here from the locking spec's §8) |
| Title | A new chat's title is the start of the first message with "…"; reflection replaces it |
| `remember` | The fact is stored only after approval, and is refused in a secret turn |
| `remember` on another's memory | Approving keeps their memory, saves the new one as household, and raises a proposal for them |
| Same fact | A rewording, or the same fact in another language, adds no row |
| Changed fact | A changed or contradicting fact creates a proposal and leaves the stored memory active |
| Replacement | Use new makes the old memory inactive and records what replaced it; Keep both keeps both active |
| Only the speaker answers | Another member cannot answer a proposal, and it is not in their count |
| Source chat | A proposal shows its chat's title to the chat's owner only |
| Remove and Undo | ✕ retires a memory; Undo makes it active again |
| Fixed block | Preferences and health are present whatever their age; the block never exceeds its budget; expired and inactive memories are absent |
| Recall | A related memory outside the block is attached and replayed unchanged on the next turn; an unrelated message attaches nothing |
| Recall gives up | A slow embedder skips recall and the turn still answers |
| Search fallback | With the extension unavailable, search returns the same order from Python |
| Event link | An event created in a chat is remembered once with its id and expiry; created and removed in the same chat, or gone from the calendar, it is named as removed and a fact tagged with it is not saved |
| Event changed in the hub | Removing the event retires its memory; moving it rebuilds the sentence and records `moved_from` |
| Event changed outside | The periodic check retires a memory whose event is gone, and changes nothing when the calendar is unreachable |
| One store | No `/gossip` route exists; a household memory carries who said it into the prompt line |
| Log text | With the setting off, no log line carries memory text |
| Boundaries | The AST boundary tests pass: no vector or SQL type reaches `domain` |

---

## 11. Measurements

Taken on 12 October 2026 against the live hub: the chat model `qwen3.8-rvn` on the desktop's Ollama,
`bge-m3` on the server's. Sentences in the similarity tests are invented.

### 11.1 The prompt cache (#133's test chat, 11 October)

| Call | Prompt tokens read | Time |
| :--- | :--- | :--- |
| After no memory write | 154 to 446, cache restored | about 0.3 s |
| After a memory write | 4,104 and 4,669, the whole prompt | 2.1 s and 2.4 s |

On the second re-read 1,708 tokens still matched, but Ollama had no checkpoint that early, so it read
everything again. A write costs the whole prompt, not the part after the memory list. The memory and
milestone lines took about 2,000 of 3,842 tokens (inferred from where the cached prefix ended).

### 11.2 Per-turn reflection

| | |
| :--- | :--- |
| Prompt | 483 to 556 tokens |
| Generated | 589 to 823 tokens for a small JSON object; the rest is reasoning (#81) |
| Time on the slot | 12.4 to 17.4 s |
| A message sent meanwhile | waited about 7 s |

### 11.3 Search time

One question against vectors of 1,024 numbers, top 10, in a container from the hub's base image on the
server. All three returned the same top 10.

| Memories | Plain Python | `vec0` table | Ordinary table + `vec_distance_cosine` |
| :--- | :--- | :--- | :--- |
| 500 | 107 ms | 1.3 ms | 1.4 ms |
| 5,000 | 1,081 ms | 10.7 ms | 14.9 ms |
| 20,000 | not run | 43 ms | 61 ms |

Also verified: `sqlite-vec` 0.1.9 loads in the hub's Linux image (Python 3.14.8, SQLite 3.46.1) and on
the Windows development machine (Python 3.14.4); a connect hook loads it on every pooled connection
with SQLAlchemy 2.1.4 and aiosqlite 0.22.1; SQL-level `load_extension` stays refused afterwards.
`bge-m3` takes 23 ms per message when loaded and 6.3 s when it has to load; it unloads after 30
minutes idle.

### 11.4 Recall: with and without `relevant_for`

Ten invented memories, best wanted memory against best unwanted one.

| Message | Plain: wanted / wrong | With `relevant_for`: wanted / wrong |
| :--- | :--- | :--- |
| "What should I cook for dinner tonight?" | 0.46 / 0.44 | 0.69 / 0.47 |
| The same question in Spanish | 0.42 / 0.45 | 0.66 / 0.47 |
| "Can you suggest a running plan for this week?" | 0.51 / 0.48 | 0.69 / 0.54 |
| "Remind me what I still need from the hardware shop" | 0.58 / 0.48 | 0.69 / 0.49 |
| "What should I get her for her birthday?" | 0.39 / 0.42 | 0.63 / 0.46 |
| "and for the weekend?", after a dinner question | 0.36 / 0.56 | 0.51 / 0.56 |
| Four unrelated messages, highest score | 0.50 | 0.52 |

Plain, wanted memories (0.39 to 0.58) overlap the noise (up to 0.50), so no cut-off works. With the
list, five of six sit at 0.63 to 0.69 and everything else at 0.54 or below. Adding the previous exchange
to the question fixed the pronoun case without the list: the three memories about the person came top
at 0.58 to 0.63 against 0.43.

Limits: the lists were written knowing the questions, so the gain is optimistic; ten memories and ten
messages; the vague follow-up still fails.

### 11.5 Same, changed, opposite

Against a stored "Sam is vegetarian and does not eat fish."

| New fact | Kind | Similarity |
| :--- | :--- | :--- |
| The same sentence in Spanish | same | 0.979 |
| "Sam has been vegetarian since 2019 and does not eat fish." | more detail | 0.927 |
| "Sam is not vegetarian." | opposite | 0.923 |
| "Sam follows a vegetarian diet." | same | 0.914 |
| "Sam is vegan now." | changed | 0.854 |
| "Robin is vegetarian and does not eat fish." | another person | 0.754 |
| "Sam started eating fish again." | changed | 0.715 |
| "Sam works from home on Mondays and Fridays." | other topic | 0.631 |
| "The household has a cat called Miso." | unrelated | 0.453 |

Similarity finds what is about the same thing and cannot tell agreement from contradiction. Given the
same pairs to classify, the chat model matched the expected relation 25 times out of 26 over two runs,
and named the opposite correctly both times. Its one miss called a related allergy "more detail", which
under §6 becomes a proposal to reject. Thirteen clean pairs is a small sample: published benchmarks put
implied changes much lower.

---

## 12. Questions settled

Left open on 12 October; answered by Anton on 13 and 14 October.

| Question | Answer |
| :--- | :--- |
| Where proposals live | A band on the memory page opens Changes to check; in a chat, they are answered in the sheet (§8) |
| Editing a proposal | No. Keep stored, Keep both or Use new (§6.1) |
| Expiry of a proposal | None (§6.1) |
| Count or notification | A count only |
| "Shared with the household" | Gone as a screen: the Household tab |
| The last reflection in a chat | A quiet line after the last message looked over, and its sheet (§8) |
| Defaults | Block about 1,000 tokens; recall cut-off 0.60; at most 3 attached. All settings |
| Renaming chats | Not possible today; no flag until it is (§2.3) |
| Keeping the model loaded | `keep_alive` set to the time left (§2.2) |
| Event memories | Checked every 30 minutes; one memory per series; a moved sentence rebuilt from the event (§7) |
| Memory text in logs | Off by default, behind a setting (§9) |
| Per-message secrecy | Slice 6. Until then a chat that is secret now is skipped (§2.2) |
| `remember` / `forget` without asking | `remember` can be; `forget` always asks (§2.1) |

**How it ships.** Each PR writes its tests first and changes something that can be checked on the phone.

1. **One store.** The migration (§3), proposals table and wipe; `/gossip` goes. (#182)
2. **Memory page.** The list, tabs, ✕ with Undo, and its states. (#188)
3. **Reading.** Vectors (§4), the fixed block and recall (§5). (#189)
4. **Reflection.** The sweep (§2.2) and comparing on write (§6), after another wipe (§3.3). (#191)
5. **Changes to check and the chat sheet** (§6.1, §8). (#197)
6. **`remember` / `forget`** (§2.1). (#210)
7. **Calendar links** (§7). (#224)
8. **Wrap-up.** The remember and forget cards show an event memory's day and time; the docs record what was built.

---

## 13. Out of scope

- **Per-message secrecy.** Slice 6 stores whether each message was sent in secret, so a chat switched
  to secret part-way keeps what came before (locking spec §4.4).
- **WebSocket transport** for chat. That is #165; this slice works over the existing server-sent
  events.
- **The dashboard.** Its household section reads household memories in place of milestones, with no
  redesign.
- **A reranker or an entailment model.** Both would be another model to host.
- **Reasoning switches** on background calls (#81, closed as not planned). Moving reflection off the
  turn removes the wait without them.

## 14. References

- Issues: #133 (the trigger, closed in favour of this slice), #81 (reasoning on background calls),
  #64 (claimed tool calls), #79 (the prompt cache), #53 (work after a turn), #165 (WebSocket transport).
- [A-MEM: Agentic Memory for LLM Agents](https://arxiv.org/pdf/2502.12110) — a note on when a memory
  matters, embedded with it.
- [Mem0: how it works](https://docs.mem0.ai/core-concepts/how-it-works.md) — search before the model
  call; automatic writes are add-only.
- [Letta: agent memory](https://docs.letta.com/guides/agents/memory) — a core block always in the
  prompt, the rest searched.
- [Graphiti temporal model](https://mintlify.com/getzep/graphiti/concepts/temporal-model) — invalidate,
  never overwrite.
- [HEROS](https://arxiv.org/pdf/2306.05083) — sentence embeddings score negations as near-duplicates.
- [sqlite-vec](https://github.com/asg017/sqlite-vec) and its
  [Python guide](https://alexgarcia.xyz/sqlite-vec/python.html).
