# Rootless Podman on Fedora

This is the [home setup](home-setup.md) for Fedora Workstation with rootless Podman and SELinux left on.
Run every command below as your normal user, not with `sudo`, unless it says `sudo`.
Rootless and root Podman keep separate images and volumes, so mixing them makes your database seem to vanish.

## 1. Install

```sh
sudo dnf install podman podman-compose git
```

## 2. Let Podman use port 80, and open the firewall

Cabinets send their first boot request to port 80, and rootless Podman can't use ports below 1024 until you allow it:

```sh
echo "net.ipv4.ip_unprivileged_port_start=80" | sudo tee /etc/sysctl.d/90-aquadx.conf
sudo sysctl --system
sudo firewall-cmd --permanent --add-port=80/tcp --add-port=22345/tcp --add-port=8443/tcp
sudo firewall-cmd --reload
```

The sysctl setting lets any program on this computer use ports 80 to 1023 without root.

## 3. Get AquaDX and start it

Keep the folder somewhere in your home directory (see [SELinux](#selinux) for why).

```sh
git clone https://github.com/codicusmaximus/AquaDX
cd AquaDX
podman-compose up -d --build
```

The first build takes a while. Then:

1. In `config/application.properties`, set `allnet.server.host=` to this computer's LAN IP (for example `192.168.1.20`),
   then run `podman-compose restart app`. Without it, cabinets are told to use the container's internal address and can't connect.
2. Point your cabinet's segatools `[dns] default=` at this computer's LAN IP.
3. Open `http://<that-ip>/` on any phone or computer on your network.

## 4. Start it after a reboot

Rootless containers don't start on their own after a reboot. These two commands fix that:

```sh
loginctl enable-linger $USER
systemctl --user enable --now podman-restart.service
```

The first keeps your user's services running without you being logged in. The second starts every container whose
restart setting is `always` (both AquaDX containers are) when the computer boots.

## Moving your database from another server

On the old server, from its AquaDX folder (with Docker there, use `docker compose` and `docker`):

```sh
docker compose stop app
docker compose exec -T db mariadb-dump -ucat -pmeow --single-transaction main > aquadx.sql
docker compose start app
```

Copy `aquadx.sql` into the AquaDX folder on this computer, then:

```sh
podman-compose stop app
DB=$(podman ps --format '{{.Names}}' | grep _db)
podman exec -i $DB mariadb -uroot -pmeow -e "DROP DATABASE main; CREATE DATABASE main; GRANT ALL ON main.* TO 'cat'@'%';"
podman exec -i $DB mariadb -ucat -pmeow main < aquadx.sql
podman-compose up -d
```

The third line deletes the database on this computer, so only run it when you're replacing it.
Any database updates the new version needs run automatically when the server starts.
Also copy the old `data/` folder (uploaded profile pictures) and any settings you changed in `config/application.properties`.

If the old server used MySQL instead of MariaDB, see [mysql_to_mariadb.md](mysql_to_mariadb.md) first.

To back up the database later, use the same `mariadb-dump` line with `podman exec`.
Don't copy the database files directly: in rootless Podman they belong to user IDs that only exist inside the container.

## SELinux

You don't need to turn SELinux off.

* The `config` and `data` folders are mounted with `:z` in `docker-compose.yml`, which labels them so the containers can use them.
  This only works on a normal Linux filesystem. If the AquaDX folder is on an NTFS or exFAT drive, move it into your home directory.
* Don't change `:z` to `:Z` or point the mounts at your whole home directory. Relabelling folders other programs use can break them.
* The database lives in a Podman volume, which is labelled automatically.
* If something is blocked anyway, `sudo ausearch -m avc -ts recent` shows what SELinux denied.

## Updating

```sh
git pull
podman-compose up -d --build
```
