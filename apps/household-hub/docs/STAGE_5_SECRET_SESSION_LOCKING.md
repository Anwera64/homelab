# Household Hub: Secret Session Locking

**Status:** 📐 Approved Specification
**Target:** `backend` (`auth`, `sessions`) + `client` (`:core:data`, `:core:presentation`, `:composeApp`)
**Date:** September 2026

---

## 1. Why this exists

Secret Mode is specified as *"Hardware-Enforced"* and *"Zero-Leak"*. Locking, as currently implemented, enforces nothing.

Three findings from the existing code:

1. **The unlock verifies nothing.** `SessionRepositoryImpl.unlockSecretSession` accepts any non-blank string:

   ```kotlin
   override suspend fun unlockSecretSession(sessionId: String, pinOrPassword: String): Boolean {
       if (pinOrPassword.isNotBlank()) {
           lockedSecretSessions.remove(sessionId)
           return true
       }
       return false
   }
   ```

   Typing `x` unlocks the session.

2. **Locked state does not survive process death.** `lockedSecretSessions` is an in-memory set. On a fresh start it is empty, so *nothing* is locked — the opposite of the intended behaviour.

3. **The server does not gate secret content at all.** `GET /api/v1/sessions/{id}` checks ownership only:

   > *"Strict Zero-Leak Privacy: Only the session owner can view this session."*

   A valid JWT returns the full message history for a secret session. A stolen phone, or plain `curl`, reads it regardless of what the UI displays.

Together these mean the lock is a UI convention. **The client was never a trust boundary**, so no client-side fix can make it real.

---

## 2. Design decisions

| Decision | Rationale |
| :--- | :--- |
| **Enforce on the server's read path** | The only version that cannot be spoofed. A client-side gate is decoration when the API serves the content anyway. |
| **Reuse `IPasswordHasher`, not the login flow** | The PIN is already hashed on the user record. Verification should use the same primitive — including `LoginUseCase`'s dummy-hash timing defence. |
| **Issue a scoped, short-lived unlock token** | Not a full access token. Scope `secret_read`, held by the device that unlocked. |
| **Never store unlock state server-side** | A `secret_unlocked_until` column outlives the device, so unlocking on a phone would silently unlock the kitchen tablet. Device-held tokens keep them independent. |
| **Rate-limit the unlock endpoint** | An attacker holding a valid JWT otherwise gets unlimited guesses at six digits — a worse oracle than the login screen. |
| **Sliding idle window, no absolute cap** | Staying unlocked past the window requires the app continuously foregrounded, which means someone is present. Locking an active user out is hostile for no security gain. |
| **Biometrics is a separate, later feature** | A face or fingerprint authenticates to the *phone*, never to the hub. It cannot produce a server-verifiable credential; it can only gate a device secret that is exchanged for one. |

---

## 3. Locking rules

| Trigger | Behaviour |
| :--- | :--- |
| 10 minutes without **user interaction** | Re-lock |
| App backgrounded | Re-lock immediately |
| Device locked | Re-lock immediately |
| Sign out | Re-lock immediately |
| Absolute session cap | **None** |

**Critical implementation detail — what counts as interaction.** The idle timer resets on *user input only*: tap, type, send, scroll. It must **not** reset on SSE deltas, health polls, or background sync. If network traffic counts as activity, a wall-mounted tablet nobody has touched stays unlocked indefinitely, and with no absolute cap nothing else catches it.

**Expiry is quiet.** No modal fires on a timer. The next interaction lands on the unlock screen. A stream already in flight completes before the lock engages — killing a response mid-token to enforce a timer is worse than the few seconds it costs.

**Unlock scope is all-or-nothing.** One successful unlock covers every secret session for that window. The threat model is someone picking up a device; per-session unlocking adds friction without addressing it.

---

## 4. Backend changes

### 4.1 New endpoint

```
POST /api/v1/auth/unlock-secret
  body:    { "pin": "<6 digits>" }
  returns: { "unlock_token": "<jwt>", "expires_in": 600 }
```

* Verifies with `IPasswordHasher.verify(pin, user.hashed_pin)`.
* On miss, verifies against the dummy hash before failing — mirroring `LoginUseCase`'s constant-time behaviour.
* Rate-limited per user with progressive backoff, sharing the login lockout implementation.
* The returned token carries `sub = user.id`, `scope = "secret_read"`, and a 10-minute expiry. It is **not** interchangeable with an access token.

### 4.1.1 Session-scoped tokens for chats you just made secret

Switching a chat to secret, or creating one, would otherwise lock the person out of the conversation they are in: the next read needs a `secret_read` token, and the app would bounce to the PIN screen mid-sentence.

* `PATCH /sessions/{id}/secret` (to `true`) and `POST /sessions` with `is_secret: true` return a `secret_read` token **scoped to that one session**, with the same 10-minute sliding expiry.
* It grants nothing the caller could not already read a second earlier. It must **not** be the all-sessions token: that would let anyone holding the phone make a throwaway chat secret and read every other secret chat.
* The PIN unlock (§4.1) still issues the all-sessions token and supersedes a scoped one.

### 4.2 Guarded read paths

`GetSessionUseCase` and the message-listing path must, when `session.is_secret`:

* require a valid, unexpired `secret_read` token belonging to the same user;
* raise `SecretSessionLockedException` → **HTTP 423 Locked** when absent or expired.

`423` rather than `401`/`403`: the caller is authenticated and authorised, the resource is temporarily sealed. It also keeps the client's existing 401 refresh logic from firing on a lock.

### 4.3 The session list redacts, it does not refuse

`GET /api/v1/sessions` returns every session, secret ones included. For secret sessions it withholds the fields derived from content unless a valid `secret_read` token accompanies the request:

| Field | Locked | Unlocked |
| :--- | :--- | :--- |
| `title` | `null` | Real title |
| `last_message_preview` | `null` | Real preview |
| `agent_id`, `is_secret`, `is_archived`, dates | Returned | Returned |

The list stays `200` — a locked secret session still exists and still needs a row; it just cannot say what it is about.

* **`last_message_preview` is a new field on `SessionRead`**, for all sessions: the latest message's content, truncated server-side. The Chats list shows it under every title; today nothing supplies it.
* **Secret sessions keep being auto-titled.** `reflect_turn` generates titles from content, and that stays: the messages are already stored in plain text, so a stored title adds no exposure. The leak was serving it without the PIN. Titles are what let a person tell two secret chats apart once unlocked.
* **Rejected: redacting on the client.** If the server always sends titles, anyone holding the access token reads them — the same hole §1 describes, one endpoint over.

### 4.4 Secret turns write no memories

`ReflectTurnUseCase` already drops milestones for secret turns, but still extracts memories — it only forces their scope to `personal`. Two leaks follow:

* **The audit screen shows them unlocked.** *What I know about you* has no lock, so a fact from a secret chat is readable by whoever holds the phone.
* **They reach ordinary chats.** `AssembleAgentContextUseCase` loads every personal memory into every session, secret or not.

**Decision:** when `is_secret_session or is_turn_secret`, skip memory extraction entirely, exactly as milestones are skipped. The title step still runs (§4.3). Secret means off the record; if learning from secret chats is wanted later, it needs secret-scoped memories that are gated like titles and injected only into secret sessions.

* **Existing data:** a one-off cleanup deletes memories whose `source_session_id` points at a secret session.
* **Not retroactive:** a session switched to secret after the fact (`PATCH /sessions/{id}/secret`) keeps the memories and milestones written while it was ordinary. The switch protects what comes next, and the UI says "Secret from here on".

### 4.5 Schema

* `users.hashed_pin` — replaces `hashed_password` (see the PIN migration).
* No new table. No `unlocked_until` column, deliberately.

---

## 5. Client changes

* **Replace the stub.** `unlockSecretSession(sessionId, pin)` calls the endpoint and stores the returned token in memory only — never on disk.
* **Persist locked state.** Locked-by-default is the correct posture: if no valid unlock token is held, every secret session is locked. This makes the current in-memory set unnecessary rather than requiring it to be persisted.
* **Send the token** on secret session reads, and on the session list while it is held.
* **Handle 423** by routing to the unlock screen rather than surfacing an error.
* **Render a withheld title as "Private conversation".** On re-lock, drop the titles and previews already held and refresh the list.
* **Unlock from the list, not only from a session.** Chats carries an unlock bar whenever secret sessions exist; tapping a locked row still unlocks and opens that session. Once unlocked, the bar offers *Lock now*, which discards the token immediately.
* **Keep secret sessions out of search**, locked or unlocked. MVP search filters titles and agent names on-device over the loaded list.
* **Drive the idle timer from UI input events**, per §3.
* **Clear the token** on background, device lock, and sign-out.

### 5.1 Consequence: no offline access to secret sessions

Unlock requires the hub. Secret conversations are therefore unreadable offline, including from any future client cache — a cache holding decrypted secret content would reintroduce exactly the hole this closes.

---

## 6. What this does and does not protect

**Does:** the hub refuses to transmit secret session content without fresh proof of the PIN. A stolen phone with a valid JWT gets `423`. `curl` with a stolen token gets `423`.

**Does not:** encrypt anything at rest. `chat_messages.content` remains plain text in SQLite, readable by anyone with filesystem or DB access to the hub itself.

That boundary should be stated plainly in the UI rather than implied away. The current copy — *"It isn't encryption — don't treat it as a safe"* — remains accurate after this work and should stay.

**If at-rest protection is wanted later**, it is a different feature: derive a key from the PIN and encrypt message content. The cost is absolute — a forgotten PIN would mean those conversations are unrecoverable by any means, including hub access. Worth deciding deliberately rather than half-claiming.

---

## 7. Phase 2: biometrics as an unlock shortcut

Biometrics does not replace any of the above. It replaces the *typing*.

1. After a successful PIN unlock, offer "use face or fingerprint next time".
2. Server issues a long-lived, device-bound secret; client stores it in Android Keystore / iOS Keychain with `setUserAuthenticationRequired(true)`.
3. A biometric prompt releases the secret; the client exchanges it for a `secret_read` token.
4. Backend gains issue and revoke endpoints, plus revoke-all for a lost device.

**Why it matters beyond convenience:** with a PIN, every expiry costs six digits, which creates pressure to stretch the idle window until it is meaningless. With a glance or a touch, the window can stay genuinely short. The security of the timer and the ergonomics of re-entry are the same problem.

### 7.1 One wording for every method

The UI never names a single method. It says **"face or fingerprint"** and uses the `biometricUnlock` icon (viewfinder corners around a keyhole) rather than a fingerprint or a face.

* **Android cannot say which method will be used.** `BiometricPrompt` chooses among whatever is enrolled — fingerprint, face, iris — and there is no reliable public API for which one. Any specific wording would be wrong on some phones.
* **iOS could say** (`LAContext.biometryType`), but one wording across both platforms keeps the copy and the design single-sourced.
* **The OS names it anyway.** The system sheet that appears on top of the unlock screen is Face ID's, Touch ID's or Android's own, already worded for that device.

### 7.2 Biometric only — no phone-passcode fallback

The released key must require a **strong biometric only**: `BIOMETRIC_STRONG` without `DEVICE_CREDENTIAL` on Android; `.biometryCurrentSet` on iOS. The fallback is the hub PIN, never the phone's passcode.

* **Partners often know each other's phone passcode.** Allowing it as a fallback would open secret sessions to exactly the person Secret Mode exists for.
* **The key is invalidated when enrolment changes** (`setInvalidatedByBiometricEnrollment(true)` / `.biometryCurrentSet`). If someone adds their own face or finger to your phone, the shortcut stops working and the next unlock asks for the PIN.

**Not available on Web/Wasm.** WebAuthn is a different mechanism and not worth carrying here — the web target keeps the PIN path.

**On shared devices, biometrics must stay off.** `BiometricPrompt` returns *success*, not *identity*: with two members enrolled on one tablet, either person's face or fingerprint would open either person's sessions. The setting is therefore per-device and must never sync with the account.

---

## 8. Test expectations

| Test | Assertion |
| :--- | :--- |
| `test_unlock_secret_rejects_wrong_pin` | Wrong PIN → 401, no token issued |
| `test_unlock_secret_timing_constant` | Unknown user and wrong PIN take comparable time |
| `test_unlock_secret_rate_limited` | Repeated failures trigger backoff |
| `test_secret_session_read_requires_unlock` | `GET /sessions/{id}` on a secret session without a token → 423 |
| `test_secret_session_read_with_expired_token` | Expired `secret_read` token → 423 |
| `test_unlock_token_not_accepted_as_access_token` | `secret_read` token rejected on ordinary authenticated routes |
| `test_non_secret_session_unaffected` | Normal sessions read without any unlock token |
| `test_session_list_redacts_secret_content` | `GET /sessions` without a token → secret rows present, `title` and `last_message_preview` null |
| `test_session_list_reveals_with_token` | Valid `secret_read` token → real titles and previews |
| `test_session_list_preview_for_normal_sessions` | Non-secret rows carry `last_message_preview` with no token |
| `test_reflect_turn_secret_writes_no_memories` | A secret session or secret turn creates and updates no `AgentMemory` rows |
| `test_reflect_turn_secret_still_titles` | A first secret turn still sets the session title |
| `test_toggle_secret_returns_scoped_token` | Switching a session to secret returns a token that reads that session only |
| `test_scoped_token_rejected_on_other_secret_sessions` | The scoped token gets 423 on every other secret session |
| `SecretLockViewModelTest.idle timer ignores stream events` | SSE deltas do not reset the idle window |
| `SecretLockViewModelTest.locks on background` | Backgrounding clears the token |
