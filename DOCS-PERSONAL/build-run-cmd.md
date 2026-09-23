# Build and Run Commands

## Compile on Hetzner

The Hetzner server has approximately 4 GB RAM. Use the repository's conservative Gradle and Kotlin memory settings.

```bash
cd /opt/void
./gradlew --stop
./gradlew :game:build -x test --no-daemon
```

A successful file-storage build produces:

```text
game/build/libs/void-server-dev.jar
```

If compilation fails with an out-of-memory error, do not start multiple builds. Check memory and compiler processes first:

```bash
free -h
pgrep -af 'GradleDaemon|KotlinCompileDaemon' || true
```

Only stop stale compiler daemons when no build is running.

## Docker image and server

Ensure the cache exists under `data/cache/` before building the image. Production player saves are mounted from `/srv/void-cloud-rsps-data/saves/`; they are not part of the image.

```bash
docker compose config --quiet
docker compose build void
docker compose up -d
docker compose ps
docker compose logs --tail=200 void
```

Production storage settings:

```properties
storage.type=files
storage.players.path=./data/saves/
```

## Run directly without Docker

For local development only:

```bash
./gradlew --stop
./gradlew :game:run
```

## Client

```bash
java -jar client.jar
```

## Notes

- Requires JDK 21 or newer.
- Cache files (`.idx`, `.dat2`, `.dylib`, `.dll`) are runtime assets and are not expected to be tracked by Git.
- Do not use `git clean -fdx` on production.
- Never put production saves inside a disposable build or Docker image.
