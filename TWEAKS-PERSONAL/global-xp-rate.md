# Global XP rate

## Intended behavior

Set the global experience multiplier to 5x for the personal production server.
The value is configured by `world.experienceRate=5.0` in
`game/src/main/resources/game.properties`.

## Boundary

This is a configuration-only gameplay change. It does not alter player saves,
storage mode, item drop rates, or any minigame-specific reward configuration.

## Verification

Build the `:game` artifact and confirm the packaged `game.properties` contains
`world.experienceRate=5.0`. After deployment, verify the running server loads
the new JAR and perform a small XP award test if practical.

## Upstream update safety

When syncing upstream, inspect the `world.experienceRate` line in
`game/src/main/resources/game.properties` and preserve the 5.0 value unless the
owner requests a different multiplier.
