# Control the Server with Docker

This is a simple guide for controlling the Void server on Hetzner after connecting over SSH.

## 1. Connect to Hetzner

From your computer, run:

```bash
ssh root@YOUR_SERVER_IP
```

Then go to the server folder:

```bash
cd /opt/void
```

All Docker commands below should be run from `/opt/void`.

## 2. Start the server

To start the existing server container:

```bash
docker compose up -d
```

`-d` means detached mode: the server runs in the background and your SSH terminal remains usable.

Starting the container does not delete or reset player saves. Saves are stored outside the code checkout at:

```text
/srv/void-cloud-rsps-data/saves/
```

## 3. Check whether it is running

```bash
docker compose ps
```

A healthy running server should show a status similar to:

```text
Up
```

If it shows `Exited`, the container stopped or crashed. Check the logs before starting it repeatedly.

## 4. See how the server is running

Show recent startup and error output:

```bash
docker compose logs --tail=200 void
```

Follow the live log output:

```bash
docker compose logs -f --tail=100 void
```

Press `Ctrl+C` to stop watching the logs. This does not stop the server.

Check CPU and memory usage:

```bash
docker stats
```

For a continuously updating view every second, including host memory/swap and the Void container:

```bash
watch -n 1 'free -h; echo; docker stats --no-stream'
```

Press `Ctrl+C` to exit the live monitor.

Check the container's published ports:

```bash
docker compose ps
```

The game server uses TCP port `43594`. Port `8080` is only used if the web server is enabled.

## Connect with the Windows client

From the repository on Windows, run:

```text
client-hetzner\client.bat
```

This tracked launcher connects to `2.28.141.196:43594`. Keep the server
running before launching it. The desktop client requires `-ip` and `-p` in the
launcher; `-Dvoid.server` will not configure this JAR.

## 5. Stop the server

Stop only the Void game container cleanly:

```bash
docker compose stop void
```

This stops the application but preserves the container, image, external saves, and Docker volumes.

Confirm it stopped:

```bash
docker compose ps -a
```

## 6. Restart the server

To stop and start the game container again:

```bash
docker compose restart void
```

Use this for a normal restart when the image and configuration have not changed.

## 7. Start after changing code

If you changed source code, first compile and rebuild the image:

```bash
./gradlew :game:build -x test --no-daemon
docker compose build void
docker compose up -d
```

Then check the startup logs:

```bash
docker compose ps
docker compose logs --tail=200 void
```

## 8. Important commands to avoid

Do not use these as normal control commands:

- `docker compose down -v` — can remove Docker volumes.
- `rm -rf /srv/void-cloud-rsps-data` — can delete player saves.
- `git clean -fdx` — can remove ignored runtime files.
- Replacing `/srv/void-cloud-rsps-data/saves/` with an empty directory.

Before updates, back up the saves according to `Production-Update-Safety.md`.

## Quick reference

```bash
# Connect
ssh root@YOUR_SERVER_IP

# Enter the project
cd /opt/void

# Start
docker compose up -d

# Check status
docker compose ps

# View logs
docker compose logs -f --tail=100 void

# Stop watching logs
Ctrl+C

# Stop server
docker compose stop void
```
