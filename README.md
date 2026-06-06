# Create Factory Controller

A [Create](https://github.com/Creators-of-Create/Create) addon for Minecraft 1.21.1 (NeoForge) that adds a **Factory Controller** block — a virtual panel that lets you place and monitor multiple Create factory gauges on a single block, connected through a logistics network.

## Features

- **Factory Controller block** — a horizontal-directional block that opens a GUI for managing a virtual 2D panel of gauges
- **Virtual gauge panel** — place any gauge item registered in Deployer's panel registry onto any `(x, y)` slot; each gauge tracks its own logistics network, filter, and amount threshold
- **Connection graph** — draw directed connections between gauges on the panel, with cycling arrow-bend styles
- **Live status** — each gauge computes and syncs `satisfied`, `promisedSatisfied`, and `waitingForNetwork` states to the client
- **Restocker support** — configurable recipe address, output amount, and promise-clearing interval per gauge

## Dependencies

| Dependency | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.231 |
| Create | 6.0.11-292 |
| Deployer | 0.1.2 |

## Building

Requires Java 21.

```bash
./gradlew build
```

Output jar is placed in `build/libs/`.

To run a dev client:

```bash
./gradlew runClient
```

## License

All Rights Reserved

## Credits

- Winding Noise - Music Box.mp3 by charcrone -- https://freesound.org/s/347035/ -- License: Creative Commons 0
