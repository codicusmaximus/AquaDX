# Home setup (LAN mode)

LAN mode is for a server that only serves cabinets and people on your home network.
With it turned on:

* The web UI is hosted by the game server itself at `http://<server-ip>/`. There is nothing else to install.
* Players log in by tapping their name on the "Who's playing?" page, or by typing the 20-digit access code on their card.
  There are no emails, passwords or sign-ups. The first login for a card creates its web profile automatically.
* Song lists come from the server. Jacket images are downloaded from aquadx.net the first time they are shown
  (when the server has internet) and kept in `data/web-data/` so they work offline afterwards.
* Cabinets are not asked for a registered keychip.
* The server starts without internet.

Anyone on your network can log in as anyone. Don't expose port 80 to the internet in this mode.

## First time

1. Install Docker (Docker Desktop on Windows/macOS).
2. Get this repository and start it:

   ```sh
   git clone https://github.com/codicusmaximus/AquaDX
   cd AquaDX
   docker compose up -d --build
   ```

   The first build downloads dependencies and takes a while. Later builds are faster.
3. Point your cabinet's segatools `[dns] default=` at this computer's LAN IP, the same way as before.
4. On any phone or computer on the same network, open `http://<that-ip>/`.

## Already running AquaDX with Docker?

Your scores live in the Docker database volume, which is named after the folder you run `docker compose` from.
Update the folder you already use rather than cloning into a new one, or the server will start with an empty database:

```sh
cd <your existing AquaDX folder>
git remote set-url origin https://github.com/codicusmaximus/AquaDX
git pull origin v1-dev
docker compose up -d --build
```

If you edited `config/application.properties` or `docker-compose.yml`, git may ask you to keep or merge your changes.

## Settings

LAN mode is switched on by `AQUANET_LAN_ENABLED=true` in `docker-compose.yml`.
These optional settings go in `config/application.properties`:

| Setting | Default | What it does |
| --- | --- | --- |
| `aqua-net.lan.enabled` | `false` (the compose file turns it on) | LAN mode on or off |
| `aqua-net.lan.data-mirror` | `https://aquadx.net` | Where missing song data and jackets are downloaded from. Leave empty to never go online. |
| `aqua-net.lan.data-path` | `data/web-data` | Where downloaded data is kept. You can also put your own files here using the same layout as `aquadx.net/d/`. |

## Updating

```sh
git pull
docker compose up -d --build
```
