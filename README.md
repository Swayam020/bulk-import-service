# Bulk Import Service

Accepts a CSV upload, returns **`202 Accepted` with a job ID immediately**, and imports the
file in the background. A malformed row fails alone while the rest commit, and the failures
come back as a downloadable error report keyed by line number.

**Stack:** Java 17 · Spring Boot · PostgreSQL · Spring Data JPA · Docker · JUnit 5

**Status:** not started · target start after the ledger reaches M3

---

## Why this project

It is the async job pattern, which is everywhere in real backends and almost never on a
fresher CV. Three things worth an interview conversation:

1. **`202 Accepted` + polling** — a 200k-row file never holds an HTTP connection open.
2. **Partial failure** — batching inside bounded transactions, so one bad row doesn't roll
   back 50,000 good ones. This is the part most people get wrong.
3. **Crash recovery** — job state is persisted, so an interrupted run resumes or fails
   cleanly instead of leaving a half-written table.

---

## The questions this project has to let you answer

Answers go in `NOTES.md` as you build. Don't reconstruct them the night before an interview.

- Why `202` and not just process it in the request? At what file size does the difference
  actually start to matter, and what breaks first — timeout, memory, or the thread pool?
- Where does the file go between the upload returning and the worker picking it up?
- What is your transaction boundary? Draw it. Why does a bad row on line 4,001 not roll back
  lines 1–4,000?
- You batch 500 rows at a time. Why 500? What happens at 1 and what happens at 100,000?
- I `kill -9` the process at 60% through. What does the status endpoint say afterwards, and
  what happens when the worker restarts?
- The same file gets uploaded twice. What should happen, and what does happen?
- How do you read a 200k-row CSV without loading it into memory? Show me the code path.
- Your worker runs in the same JVM as the API. When would that stop being acceptable, and
  what would you replace it with?

---

## Build plan

One milestone per sitting, one commit per real day.

| # | Milestone | Done when |
|---|---|---|
| M0 | Scaffold + Postgres + `/actuator/health` | App boots against a real Postgres |
| M1 | `POST /imports` accepts multipart CSV, stores the file, returns `202` + job ID | Response is immediate regardless of file size |
| M2 | `ImportJob` entity — status, counts, timestamps; `GET /imports/{id}` polling endpoint | Status transitions are visible over HTTP |
| M3 | Background worker picks up queued jobs and imports them | End-to-end works on a small clean file |
| M4 | Streaming parse — never hold the whole file in memory | Verified with a generated 200k-row file |
| M5 | Batched commits in bounded transactions | Bad row fails alone; good rows persist |
| M6 | Error report — collected failures keyed by line number, downloadable | `GET /imports/{id}/errors` returns a usable CSV |
| M7 | Crash recovery — persisted progress, resume or clean fail | `kill -9` mid-import, restart, correct state |
| M8 | Dockerfile, `compose.yaml`, README with the sequence diagram | Runs from a clean clone |
| M9 | Frontend — upload a file, watch job status, download the error report | A 200k-row file can be uploaded and tracked from a browser |
| M10 | Deploy — backend + frontend, each with a live URL | README updated with both |

**M4 and M5 are the interesting ones.** M4 is where you learn why `Files.readAllLines()` is a
trap, and M5 is where you learn what `@Transactional` actually scopes. Don't rush past them
to get to the Docker milestone.

---

## Setup

Java 25 is installed, Maven is not — use the Initializr wrapper. Container runtime is podman.

```bash
cd ~/projects/bulk-import-service
curl -G https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d javaVersion=17 \
  -d groupId=com.swayam -d artifactId=bulkimport -d name=bulkimport \
  -d packageName=com.swayam.bulkimport \
  -d dependencies=web,data-jpa,postgresql,validation,actuator \
  -o starter.zip && unzip starter.zip && rm starter.zip
```

```bash
podman run -d --name import-pg -p 5433:5432 \
  -e POSTGRES_PASSWORD=import -e POSTGRES_DB=import docker.io/library/postgres:17
```

> Port **5433** on purpose — the ledger's Postgres already holds 5432. Two projects, two
> databases, both running at once.

Generate a test file when you get to M4:

```bash
python3 -c "
print('id,name,email,amount')
for i in range(200000):
    print(f'{i},user{i},user{i}@example.com,{i%997}.{i%100:02d}')
" > /tmp/big.csv
```

Then corrupt a few lines by hand — that's your M5 test input.

## Git

```bash
git add -A && git commit -m "docs: project spec and build plan"
```
