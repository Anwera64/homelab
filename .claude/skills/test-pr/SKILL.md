---
name: test-pr
description: Rebase a PR and prepare it for manual testing on the local hub and phone
---
Given a PR number:
1. Fetch latest master and rebase or merge it into the PR branch. Resolve conflicts and explain each resolution.
2. Run the full hub test suite and the client build. Stop and report if anything fails.
3. Rebuild and restart the local hub in Docker. Install the app on the connected phone.
4. Give me ONE unified manual test prompt covering the PR's changes.
5. While I test, use Grafana logs (not docker logs) to investigate anything I report.
6. Present a plan before coding any fix. After I confirm it works, push and check CI.
