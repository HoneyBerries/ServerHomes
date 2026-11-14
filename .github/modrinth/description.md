ServerHomes gives every player named homes they can set, list, rename and teleport to. It is a small, focused plugin with no setup beyond an optional home limit.

## Commands

| Command | What it does |
|---|---|
| `/sethome <name>` | Saves your current position and facing as a home. Reusing a name moves that home. |
| `/home <name>` | Teleports you to a home. |
| `/delhome <name>` | Deletes a home. |
| `/renamehome <name> <new name>` | Renames a home. |
| `/homes` | Lists all of your homes. |

Home names can be 1 to 32 characters long: letters, numbers, `-` and `_`. They are not case-sensitive. Invalid names are rejected as you type, and tab completion suggests your existing homes.

## Permissions

Every command is available to all players by default.

| Permission | Command / effect | Default |
|---|---|---|
| `serverhomes.sethome` | `/sethome` | everyone |
| `serverhomes.home` | `/home` | everyone |
| `serverhomes.delhome` | `/delhome` | everyone |
| `serverhomes.renamehome` | `/renamehome` | everyone |
| `serverhomes.homes` | `/homes` | everyone |
| `serverhomes.unlimited` | Ignores the home limit | op |

## Configuration

`plugins/ServerHomes/config.yml` is created on first start:

```yaml
max-homes: 5   # -1 for unlimited
```

Overwriting an existing home never counts against the limit.

## Under the hood

- Homes are stored in a SQLite database (`plugins/ServerHomes/homes.db`). The driver is bundled, so there is nothing to install.
- All database work runs on its own thread, so saving or looking up homes never stalls the main server thread.
- Teleports use Paper's asynchronous teleport, so the destination chunk loads without freezing the server.
- Commands are built on Paper's Brigadier command API.

## Requirements

- Paper 26.3 (or a compatible fork such as Purpur) on Java 25
- Folia is not supported
