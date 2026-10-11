---
name: test-pr
description: Rebase a PR and prepare it for manual testing on the server's hub and the phone
---
Given a PR number (none given: the PR of the current branch):
1. Fetch latest master and rebase or merge it into the PR branch. Resolve conflicts and explain each resolution. Push the result: the server builds from GitHub, not from this PC.
2. Run the full hub test suite and the client build. Stop and report if anything fails.
3. Deploy the PR's hub. The hub runs on the server (`ssh anwera97@server.lan`, checkout `~/homelab`, compose in `hosts/server`), not on this PC, and it is the hub the household uses:
   - Only when the PR changes the hub (`apps/household-hub/backend`). A client-only PR skips this.
   - Say so before switching, then `git fetch` and `git switch` to the PR branch in `~/homelab`.
   - `docker compose build household-hub` with the output in a file, and check its exit code. Only then `docker compose up -d household-hub`, and wait for it to be healthy.
   - Anything that needs root on the server is a copy-paste block for me.
4. Install the app on the connected phone, when the PR changes the client.
5. Give me ONE unified manual test prompt covering the PR's changes.
6. While I test, use Grafana logs (not docker logs) to investigate anything I report.
7. Present a plan before coding any fix. After I confirm it works, push and check CI.
8. Put the server back: once the PR is merged, or the test is given up, `git switch master`, fast-forward it, rebuild and restart `household-hub` the same way. Don't leave the server on a PR branch overnight: the 04:00 update fast-forwards whatever branch is checked out, so master's merges would not be applied.
