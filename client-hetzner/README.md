# Void Hetzner Windows Client

This package is the working Windows client setup for the Void server.

## Requirements

- Windows
- Java 21 or newer installed and available as `java` in Command Prompt
- Internet access to the Void server

## Launching

1. Extract the ZIP without changing the files' relative locations.
2. Open the extracted folder.
3. Double-click `client.bat`.
4. Log in with your Void account.

The launcher is already configured for the live server at:

```text
2.28.141.196:43594
```

Do not launch the JAR by itself unless you know how to provide the server
address. Use `client.bat` so the correct `-ip` and `-p` options are supplied.

If Windows says Java is not recognized, install a Java 21 JDK and reopen
Command Prompt before trying again.
