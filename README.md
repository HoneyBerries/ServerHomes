# ServerHomes

Minimal `/sethome` and `/home` plugin for [Paper](https://papermc.io) 26.3.

| Command    | Permission            | Description                          |
|------------|-----------------------|--------------------------------------|
| `/sethome` | `serverhomes.sethome` | Save your current location as home.  |
| `/home`    | `serverhomes.home`    | Teleport to your saved home.         |

Both permissions default to everyone. Homes are stored in `plugins/ServerHomes/homes.yml`.

## Build

Requires JDK 25 and Maven:

```
mvn package
```

The jar is written to `target/ServerHomes-<version>.jar`.
