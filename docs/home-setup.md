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

Anyone on your network can log in as anyone. Don't expose the server's ports to the internet in this mode.

## First time

1. Install Docker (Docker Desktop on Windows/macOS). On Fedora or with rootless Podman, follow [podman-fedora.md](podman-fedora.md) instead.
2. Get this repository and start it:

   ```sh
   git clone https://github.com/codicusmaximus/AquaDX
   cd AquaDX
   docker compose up -d --build
   ```

   The first build downloads dependencies and takes a while. Later builds are faster.
3. In `config/application.properties`, set `allnet.server.host=` to this computer's LAN IP (for example `192.168.1.20`).
   This is the address cabinets are told to use for game traffic.
4. Point your cabinet's segatools `[dns] default=` at this computer's LAN IP, the same way as before.
5. On any phone or computer on the same network, open `http://<that-ip>/`.

## Ports

The server runs on port **80** by default: the web UI and all game traffic go there.
Cabinets always send their first request at boot (ALL.Net) to port 80, because segatools redirects the address, not the port.
Port 22345 (card reader, Aime DB) and 8443 (billing) are also used by cabinets.

Rootless Podman needs permission to use port 80; see [podman-fedora.md](podman-fedora.md#2-let-podman-use-port-80-and-open-the-firewall).

### Using another port

To run on a different port, create a file named `.env` next to `docker-compose.yml`:

```sh
AQUADX_PORT=8011
```

The web UI is then at `http://<server-ip>:8011/`, and cabinets are told to use 8011 after their first request.
That first request still goes to port 80, so something has to pass port 80 on to the server, for example a firewall rule:

```sh
sudo firewall-cmd --permanent --add-forward-port=port=80:proto=tcp:toport=8011
sudo firewall-cmd --permanent --add-port=8011/tcp
sudo firewall-cmd --reload
```

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

LAN mode is switched on by `AQUANET_LAN_ENABLED=true` in `docker-compose.yml`, which also sets the port from `AQUADX_PORT` (see [Using another port](#using-another-port)).
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
