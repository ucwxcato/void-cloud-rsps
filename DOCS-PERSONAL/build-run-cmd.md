# Build & Run Commands

## Build

```bash
# Standard build (skip tests for faster iteration)
./gradlew --stop
./gradlew :game:build -x test

# If spotless/ktlint crashes with ClosedByInterruptException  
# (known Gradle 9.6 / spotless 7.0.4 / ktlint compat issue):
./gradlew :game:build -x test -x spotlessKotlinCheck -x spotlessKotlinGradleCheck

```
    
## Run server
./gradlew --stop
```bash
./gradlew :game:run
```

## Run client

```bash
java -jar client.jar
```

## Notes

- Requires JDK 21 or newer (this repo uses JDK 25).
- Cache files (`.idx`, `.dat2`, `.dylib`, `.dll`) must be present under `data/cache/` — they are gitignored. Download the cache zip from https://mega.nz/folder/ZMN2AQaZ#4rJgfzbVW0_mWsr1oPLh1A and extract there.
- `./gradlew` requires the wrapper jar — see `gradle/wrapper/`. If the wrapper jar is missing, see `DOCS/syncing-upstream.md` §wrapper bootstrap.
- For DPI scaling issues on Windows, see `DOCS/dpi-scaling.md` (or the run-client.bat helper).
