# Hetzner Compile Guide

Use this guide after connecting to the Hetzner server over SSH. It compiles the current `/opt/void` checkout without starting Docker.

## Compile one time

```bash
cd /opt/void
./gradlew --stop
./gradlew :game:build -x test --no-daemon
```

Wait for `BUILD SUCCESSFUL`. The expected JAR is:

```text
/opt/void/game/build/libs/void-server-dev.jar
```

## Monitor a long compile

From a second SSH session, these read-only commands show memory and active compiler processes:

```bash
free -h
pgrep -af 'GradleDaemon|KotlinCompileDaemon' || true
```

Do not start a second build while one is running.

## If compilation fails from memory pressure

First wait for the failed command to return to the shell. Then stop stale daemons only:

```bash
cd /opt/void
./gradlew --stop
pgrep -af 'GradleDaemon|KotlinCompileDaemon' || true
```

Do not delete `.gradle`, build outputs, caches, `data/cache`, or any save directory as a memory fix.

## After a successful compile

Verify the cache before building the image:

```bash
find /opt/void/data/cache -type f | wc -l
du -sh /opt/void/data/cache
```

Then follow `Hetzner-Cloud-Deployment.md` for the file-storage Docker deployment. Do not start Docker until the external save backup and storage configuration have been verified.
