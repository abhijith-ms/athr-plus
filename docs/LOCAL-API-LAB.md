# Desktop API lab

Use the same Ather OTP, REST, and telemetry request formats as the Android app
from this computer. The Python scripts contain no account credentials.

Credentials live at `~/.local/share/atherpro-lab/session.json`, outside the
repository. The directory has permissions `700`; files have permissions `600`.
The token is a local plaintext file protected by filesystem permissions, not an
encrypted vault. Other processes running as your user can read it.

The tool never accepts tokens, phone numbers, or OTPs as command arguments.
Hidden terminal prompts keep them out of shell history and output. An OTP is
used in memory and is never saved. A pending login temporarily saves the phone
number outside the repository and removes it after successful verification.

## Set up and sign in

Run these commands from the project root:

```sh
python3 -m venv ../.venv-ather-lab
. ../.venv-ather-lab/bin/activate
python -m pip install websocket-client
python3 scripts/secret-guard.py install
python3 scripts/ather-lab.py login
```

Enter the registered Indian mobile number without the country prefix, then the
SMS OTP, at the hidden prompts. A single scooter is selected automatically.
For accounts with multiple scooters:

```sh
python3 scripts/ather-lab.py vehicles
python3 scripts/ather-lab.py select 1
```

For assisted login across separate steps, use `request-otp`, then `verify-otp`.
Both prompt privately; the token is never printed. This tooling is configured
for country code `IN`, matching this project's default login.

## Read and monitor

REST commands use the Python standard library:

```sh
python3 scripts/ather-lab.py profile
python3 scripts/ather-lab.py rides
python3 scripts/ather-lab.py health
```

Monitoring requires `websocket-client`. The setup above installs it in a virtual
environment outside the checkout. Activate that environment in each terminal,
or use `../.venv-ather-lab/bin/python` explicitly.

```sh
python3 scripts/ather-lab.py monitor --seconds 60
python3 scripts/ather-lab.py monitor --seconds 0 --capture
```

The socket subscribes to the same seven shadow paths as the Android app. Frames
include receive times and are reported updates, which may be partial. They are
not merged into a complete dashboard state. `--seconds 0` runs until Ctrl+C.
Network failure stops monitoring; rerun to reconnect. GPS, account identifiers,
token fields, and JWT values are redacted before output or capture. This is
best-effort redaction, so keep captures private rather than uploading them.

The latest redacted REST response is saved as `last-response.json`; captures
append to `monitor.jsonl`. Both live alongside the session outside Git.
Unavailable APIs return their HTTP status without dumping response bodies.
The optional charging-command diagnostics below also save redacted error bodies.
An expired token requires OTP login again; there is no automatic refresh.

## Test charging

In one terminal, run `monitor --seconds 0`. In another, preview a command:

```sh
python3 scripts/ather-lab.py stop
python3 scripts/ather-lab.py start
```

To send a real command to the selected scooter:

```sh
python3 scripts/ather-lab.py stop --execute
python3 scripts/ather-lab.py start --execute
```

These post the exact desired-shadow payload used by `AtherApiClient.kt`.
There are no automatic command retries. An HTTP success response alone does not
establish application-level acceptance or execution. Verify the response body,
subsequent reported charging status, heartbeat, and physical charger behaviour.

### Supervised 450S stop-command capture

This experiment isolates manual Pause from the phone's automatic limiter. Keep
the scooter parked and charging, disable the Athr+ charge limit, and have someone
observe the charger. Do not run `charge-limit.py` during this test. Sign in using
the hidden local prompts above; never paste an OTP or session file into chat.

In terminal 1, from the project root, capture telemetry for three minutes:

```sh
../.venv-ather-lab/bin/python scripts/ather-lab.py monitor --seconds 180 --capture
```

Wait for the initial charging report. In terminal 2, also from the project root,
preview the stop command, then send exactly one request:

```sh
../.venv-ather-lab/bin/python scripts/ather-lab.py stop --diagnostics
../.venv-ather-lab/bin/python scripts/ather-lab.py stop --execute --diagnostics
```

`--diagnostics` never implies `--execute`: the first command sends nothing and
writes no diagnostic capture. The flag is also available for `start`, but do not
send a start/resume or another stop during this observation. Watch the physical
charger and scooter for at least two minutes after the stop request; note whether
charging continues, stops, or stops and restarts, and the observation time.

Diagnostics overwrite `~/.local/share/atherpro-lab/command-response.json` with
the latest diagnostic command. A pending record is saved before dispatch; if
private storage cannot be written, no command is sent. The final record contains:

- `requested_at` and `finished_at` in UTC, plus `command_timestamp_ms` matching
  the timestamp in the command payload, and the requested `action`.
- `http_status` (null when no status arrived), `outcome`, and `response_body`.
- `body_format`: `json` or `text` when a nonempty body was received. Empty or
  unreadable bodies remain null; connection/read failures are recorded without
  raw exception text. A pending record can remain if the process is killed.

HTTP errors retain their failure exit status. Non-JSON responses are captured
but still fail the tool's JSON contract, even with HTTP 2xx. JSON bodies are
preserved without guessing the meaning of undocumented error fields; a 2xx with
an error body is not proof of acceptance. If final persistence fails after sending,
the tool warns that the command may have been sent: do not blindly retry.

The capture contains no request/response headers, URL, or authentication payload.
Sensitive JSON fields, known session/vehicle values and JWTs are redacted. Free
text containing private-field or authentication markers is suppressed entirely,
including multi-line text, rather than risking a partial credential leak.
Redaction is best-effort; keep both captures private and review before sharing.
The file uses the same private storage permissions as the session and is removed
by `logout`. Git ignores it and the secret guard blocks accidental staging.

Compare `command-response.json` with `monitor.jsonl` and the physical observation.
Telemetry receive times are UTC (add 5 hours 30 minutes for IST); they are not
necessarily scooter measurement times. A desired `action: stop` echo alone is
not confirmation. Missing reports leave execution unknown; continued physical
charging establishes that no sustained cutoff was observed. Preserve this
capture before another diagnostic command overwrites it.

For a one-time automatic stop at a chosen percentage, run:

```sh
python3 scripts/charge-limit.py 97
```

This actively arms a cutoff; it never starts charging. Keep the computer awake,
online, and the process running. It refreshes the cloud snapshot approximately
every five seconds even if the socket provides no change events, and reconnects after telemetry network failures
and sends one stop command at the first fresh battery reading at or above the
target. An initial cloud snapshot must have a source timestamp no older than
120 seconds. A subsequent battery update with no source timestamp is treated
as current when received. Unrelated frames never refresh an old battery value.
The actual cutoff can exceed the target if scooter updates are delayed.

The watcher records its PID, battery reading, freshness, and phase in
`~/.local/share/atherpro-lab/charge-limit-state.json`. `stop_accepted` means
API acceptance; `stop_confirmed` requires a subsequent physical charging update
or a refreshed snapshot with a source timestamp after the stop command.
If no confirmation arrives within 60 seconds, it records `stop_unconfirmed`
and exits without retrying. `error`, `cancelled`, and `stop_unconfirmed` mean
the cutoff process has stopped. Ctrl+C or SIGTERM cancels the watcher; cancellation
does not send a scooter command. A local lock prevents two cutoff watchers
running at once. Status and log files remain outside Git.

## Prevent accidental publication

`secret-guard.py install` adds local `pre-commit` and `pre-push` Git hooks. The
commit hook scans staged blobs. The push hook scans every outgoing commit tree
and commit message, including earlier commits where a credential was later
deleted. Checks block session/capture filenames, JWT-like values, literal bearer
tokens, long token assignments, and exact tokens/phone/selected vehicle IDs saved
by this lab. Error messages show only the path and reason, never secret values.

Additional ignore rules prevent common session files from being staged normally.
Credentials outside the checkout are not included by `git add .`, APK builds,
or ordinary Git pushes. Keep them there; do not copy them into source files,
Android assets, test fixtures, or release bundles.

These hooks protect this checkout, not every clone or GitHub itself. Reinstall
after cloning or changing Python environments. Git options that bypass hooks,
editing hook files, and uploading files through a browser can bypass the guard.
Pattern checks cannot identify every possible unknown secret. Nothing here
automatically commits, pushes, or changes GitHub branch protection.

Check the current tracked commit or staged contents manually:

```sh
python3 scripts/secret-guard.py tree
python3 scripts/secret-guard.py staged
```

Remove local credentials and captures when finished:

```sh
python3 scripts/ather-lab.py logout
```

Local deletion does not revoke an existing token on Ather's server.
