# Household Hub: Memory

**Status:** 📐 Specification — behaviour agreed, screens still to be designed
**Target:** `backend` (`memories`, `chat`, `integrations`, `bootstrap`) + `client` (`:core:*`, `:composeApp`)
**Date:** October 2026

This is the spec for Stage 5 slice 5. It replaces the per-turn reflection, the gossip milestones and the
"Profile, memory and sharing" slice as first planned. It was agreed decision by decision on
12 October 2026, with measurements taken on the live hub; §11 holds those numbers, and §12 lists what
is still open.

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
| No turn is running | Never start in front of a live answer |

A chat that fails a condition is tried again on a later pass. A chat left unreflected by a restart or a
skipped pass is picked up the next time the model is in memory.

**The marker.** Each chat stores the last message that was reflected and when. A chat the member comes
back to reflects only the new part. It also stores what the last run did, for the screens (§8).

**What reflection may save.**

- Only what the member states. The assistant's text is context for understanding it, never a source.
- Nothing from a secret chat or a secret turn. Secrecy is read per message, so a chat switched to
  secret part-way keeps what came before, as the locking spec's §4.4 requires.
- Dates are written as absolute dates, as today.

**What it does with each fact** is §6: save it, or raise a proposal.

**Later trigger.** When the chat connection becomes a WebSocket (#165), a socket that
closes and stays closed can trigger the same job sooner. The sweep stays as the safety net.

### 2.3 Chat title

Reflection used to write the title on the first turn. Now the title is the start of the member's first
message, cut with "…" when long, so a new chat is recognisable at once. The first reflection replaces
it with a proper one, unless the member has renamed the chat.

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
| replaced-by and retired-at | **new** | Set when a memory is retired in favour of another, so the history can be read and undone |

A memory is never deleted by the system, only made inactive.

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

The hub is a development environment. The migration clears `agent_memories` and drops
`gossip_milestones`; nothing is carried over.

### 3.4 `conversation_sessions`

Gains the reflection marker (last reflected message, when) and a small record of what the last run
added, proposed and retired.

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

A block in the system prompt, filled up to a token budget (a setting, not a row count).

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
  them with it. Later turns replay the same text, so the cache holds. A memory already attached to an
  earlier message in the window is not attached again.
- **It stays silent** when nothing passes the cut-off, which is the normal case.
- **It gives up** when the embedder has to load (6.3 seconds, measured): a wait cap skips recall for
  that message.

The cut-off is a setting and starts conservative. Similarity scores are only comparable within one
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
| unclear | The model cannot tell | Proposal showing both |

In `remember` the member is already looking at an approval card, so the relation and the closest
memory are shown there and the member decides; no separate proposal is raised.

A **proposal** is a pending change to a stored memory. The stored memory stays active until the member
answers. This is the guard against a background step quietly losing information, which is the reason
another memory system (Mem0) reportedly stopped updating automatically.

When a memory is replaced, the old one is made inactive and records what replaced it and when.

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

**Scope.** Personal or household, decided per event like any other memory.

**Staying true.**

| The event is changed… | What happens to the memory |
| :--- | :--- |
| Through the hub (`UpdateCalendarEventUseCase`, `DeleteCalendarEventUseCase`) | Updated or retired at once. No proposal: the member already made the change |
| Outside the hub, in a calendar app | A periodic check finds it |

**The periodic check.** The hub keeps no copy of the calendar and has no sync: events are read live from
Apple or Google. So a pass asks the calendar for the event of each linked, unexpired memory
(`ICalendarConnector.get_event`). Gone means retire; moved means update. It needs no model, so it runs
on its own schedule, apart from the reflection sweep. A calendar that cannot be reached changes nothing.

---

## 8. What the member needs to see

The screens are **not designed here**. They are settled when the slice is planned, and the current
"Profile & memory" boards on the canvas describe the old system. This section only records what the
screens must make possible.

- When a chat was last reflected, and what that run saved.
- Proposed changes to memories, and a way to answer them.
- The memories themselves: removing one, its expiry, and who said a shared one.

Noted from the discussion as input, not as decisions: the reflection time shown in the transcript after
the last reflected message and in the chat's overflow menu, with the time and what was saved.

Three things the old design had that no longer apply:

- **The publish notice and its Undo.** No turn publishes anything now, so there is nothing to confirm
  at the end of a turn.
- **Two separate lists** for memories and shared facts. They are one store; a shared fact is a
  household memory.
- **"Nothing said in Secret Mode is ever written down"** stays true, and is delivered by this slice.

---

## 9. Logging

Thorough on purpose. Reflection logs nothing today, which is why #133 had to be read out of the
database. Every problem in this slice should be readable from Loki without guessing.

Every line carries the member and chat ids, in the form the tool-call lines already use (`_log_call`
in `process_chat_turn.py`). Content from a secret turn is never logged.

| Area | What is logged |
| :--- | :--- |
| Sweep | For each chat it considers: reflected, or skipped and why (not idle yet, model not in memory, a turn running, nothing new, secret) |
| Reflection run | Messages covered, duration, tokens; for each fact found, its neighbours with scores, the relation the model chose, and the outcome (saved, proposal, dropped) with the memory id |
| `remember` / `forget` | The closest stored memory and its score, beside the existing tool line |
| Embedding and search | Whether `sqlite-vec` loaded at startup, each fallback to Python or to keywords, failures, durations, how many missing vectors a pass filled in |
| Prompt block | Budget used against the limit, how many memories went in, which were left out |
| Recall | Per message: the top scores, what was attached, the cut-off in force, how long it took, and why when it attached nothing (below cut-off, embedder loading, wait cap) |
| Calendar link | A memory updated or retired because the hub changed its event; for the periodic check, how many were checked, changed and retired, and any calendar that could not be reached |
| Proposals | Created, accepted, rejected |
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
| Sweep respects turns | With a turn running, nothing is reflected |
| Marker | A second pass over an unchanged chat does nothing; a returned-to chat reflects only the new messages |
| Member's words only | A fact that appears only in the assistant's answer is not saved |
| Secret writes nothing | A secret chat or secret turn creates and changes no memory (moved here from the locking spec's §8) |
| Secret still titled | A secret chat still gets its title (moved here from the locking spec's §8) |
| Not retroactive | In a chat switched to secret part-way, the earlier messages are still reflected |
| Title | A new chat's title is the start of the first message with "…"; reflection replaces it; a renamed chat keeps its name |
| `remember` | The fact is stored only after approval, and is refused in a secret turn |
| Same fact | A rewording, or the same fact in another language, adds no row |
| Changed fact | A changed or contradicting fact creates a proposal and leaves the stored memory active |
| Replacement | Accepting a proposal makes the old memory inactive and records what replaced it |
| Fixed block | Preferences and health are present whatever their age; the block never exceeds its budget; expired and inactive memories are absent |
| Recall | A related memory outside the block is attached and replayed unchanged on the next turn; an unrelated message attaches nothing |
| Recall gives up | A slow embedder skips recall and the turn still answers |
| Search fallback | With the extension unavailable, search returns the same order from Python |
| Event link | An event created in a chat is remembered once with its id and expiry; created and removed in the same chat, it is not |
| Event changed in the hub | Removing the event retires its memory; moving it updates the memory |
| Event changed outside | The periodic check retires a memory whose event is gone, and changes nothing when the calendar is unreachable |
| One store | No `/gossip` route exists; a household memory carries who said it into the prompt line |
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

## 12. Open questions

**Design — left open, settled when the slice is planned**

1. Where proposals live: a third row in "What the agents keep", a band on the memory page, or a tab.
2. Whether a proposal can be edited before accepting. The design notes say memories are never edited.
3. Whether an unanswered proposal expires.
4. A count only, or a notification as well.
5. What becomes of "Shared with the household", now that the Household tab shows the same rows.
6. Where and how the last reflection is shown in a chat.

**Behaviour and settings**

7. Defaults for the token budget, the recall cut-off and the maximum attached memories.
8. Whether chats can be renamed today, which decides how the title rule is built.
9. Whether one request can avoid extending the model's 15 minutes in video memory. A reflection call
   restarts that clock.
10. Event memories: how often the periodic check runs; how a repeating event is remembered and when it
    expires; whether a moved event's sentence is rebuilt from the event's fields or reworded by the
    model at the next reflection.
11. Whether memory text appears in log lines, as tool arguments do today (cut to 500 characters), or
    only ids.
12. Where per-message secrecy is read from. `process_chat_turn.py` keeps `privacy_trigger_detected` in
    a message's metadata; that this covers every secret turn is not yet confirmed.
13. Whether `remember` and `forget` can be set to run without asking, like adding an event, or always
    ask, like removing one.
14. How the slice is cut into PRs, decided in the slice's own session.

---

## 13. Out of scope

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
