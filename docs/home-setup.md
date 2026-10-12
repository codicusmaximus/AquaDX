# Home setup (LAN mode)

LAN mode is for a server that only serves cabinets and people on your home network.
With it turned on:

* The web UI is hosted by the game server itself at `http://<server-ip>:8011/`. There is nothing else to install.
* Players log in by tapping their name on the "Who's playing?" page, or by typing the 20-digit access code on their card.
  There are no emails, passwords or sign-ups. The first login for a card creates its web profile automatically.
* Song lists come from the server. Jacket images are downloaded from aquadx.net the first time they are shown
  (when the server has internet) and kept in `data/web-data/` so they work offline afterwards.
* Cabinets are not asked for a registered keychip.
* The server starts without internet.

Anyone on your network can log in as anyone. Don't expose the server's ports to the internet in this mode.

## First time

1. Install Docker (Docker Desktop on Windows/macOS), or Podman with podman-compose on Linux.
2. Get this repository and start it:

   ```sh
   git clone https://github.com/codicusmaximus/AquaDX
   cd AquaDX
   docker compose up -d --build
   ```

   The first build downloads dependencies and takes a while. Later builds are faster.
   With Podman, use `podman-compose up -d --build` instead.
3. In `config/application.properties`, set `allnet.server.host=` to this computer's LAN IP (for example `192.168.1.20`).
   This is the address cabinets are told to use for game traffic.
4. Let cabinets reach port 80 (see below), then point your cabinet's segatools `[dns] default=` at this computer's LAN IP, the same way as before.
5. On any phone or computer on the same network, open `http://<that-ip>:8011/`.

## Ports

The server runs on port **8011**: the web UI and all game traffic go there.

Cabinets are the one exception. Their first request at boot always goes to port 80 (segatools redirects the address, not the port),
and the server's answer tells them to use port 8011 from then on. So port 80 on this computer has to lead to the server. Pick one:

* **Docker, with port 80 free:** uncomment the `- "80:8011"` line in `docker-compose.yml`.
* **Fedora or another Linux with firewalld (works with rootless Podman):** forward port 80 to 8011 in the firewall:

  ```sh
  sudo firewall-cmd --permanent --add-forward-port=port=80:proto=tcp:toport=8011
  sudo firewall-cmd --permanent --add-port=8011/tcp --add-port=22345/tcp --add-port=8443/tcp
  sudo firewall-cmd --reload
  ```

  This only applies to requests from other machines, which is what cabinets are.

Port 22345 (card reader, Aime DB) and 8443 (billing) are also used by cabinets and stay as they are.

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

The server now runs on port 8011 instead of 80, so your cabinet needs the port 80 step from [Ports](#ports) after updating.

## Settings

LAN mode is switched on by `AQUANET_LAN_ENABLED=true` in `docker-compose.yml`, which also sets the port (`SERVER_PORT` and `ALLNET_SERVER_PORT`).
These optional settings go in `config/application.properties`:

| Setting | Default | What it does |
| --- | --- | --- |
| `aqua-net.lan.enabled` | `false` (the compose file turns it on) | LAN mode on or off |
| `allnet.server.host` | the address the request arrived on | The address cabinets use for game traffic. Set it to this computer's LAN IP when running in Docker or Podman. |
| `aqua-net.lan.data-mirror` | `https://aquadx.net` | Where missing song data and jackets are downloaded from. Leave empty to never go online. |
| `aqua-net.lan.data-path` | `data/web-data` | Where downloaded data is kept. You can also put your own files here using the same layout as `aquadx.net/d/`. |

## Updating

```sh
git pull
docker compose up -d --build
```
