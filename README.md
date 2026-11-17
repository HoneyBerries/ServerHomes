# ServerHomes

Named homes for [Paper](https://papermc.io) and [Folia](https://papermc.io/software/folia) 26.3, stored in SQLite.

| Command                       | Permission               | Description                          |
|-------------------------------|--------------------------|--------------------------------------|
| `/sethome <name>`             | `serverhomes.sethome`    | Save your location as a home.        |
| `/home <name>`                | `serverhomes.home`       | Teleport to a home.                  |
| `/delhome <name>`             | `serverhomes.delhome`    | Delete a home.                       |
| `/renamehome <name> <new>`    | `serverhomes.renamehome` | Rename a home.                       |
| `/homes`                      | `serverhomes.homes`      | List your homes.                     |

All of the above default to everyone. `serverhomes.unlimited` (default: op) ignores the home limit.
Home names are 1-32 characters: letters, numbers, `-` and `_` (case-insensitive).

## Configuration

`plugins/ServerHomes/config.yml` is created on first start:

```yaml
max-homes: 5   # -1 for unlimited
```

Homes live in `plugins/ServerHomes/homes.db`. The SQLite driver is bundled in the jar.

## Build

Requires JDK 25 and Maven:

```
mvn package
```

The jar is written to `target/ServerHomes-<version>.jar`.
