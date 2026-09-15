# Hosting Andaneri CRM

The short version: one small Linux server, Docker Compose, a domain name. Caddy gets the HTTPS
certificate by itself, MySQL keeps the data in a Docker volume, and a nightly script backs it up.

## What to rent

A VPS with **2 vCPU, 4 GB RAM, 40 GB disk**, Ubuntu 24.04. The CRM uses under 1 GB; the rest keeps
MySQL fast and leaves room. Hetzner (CX22, Falkenstein or Helsinki) or DigitalOcean / Vultr
(Frankfurt) are all fine; roughly 5-25 EUR a month. A European location keeps it quick from Tbilisi.

Plus a domain, e.g. `crm.andaneri.ge`: add an **A record** pointing at the server's IP.

## First setup (once)

```bash
# on the server, as root
apt update && apt upgrade -y
curl -fsSL https://get.docker.com | sh
adduser deploy && usermod -aG docker deploy

# firewall: SSH and the web only; MySQL and the app are never exposed
ufw allow OpenSSH && ufw allow 80 && ufw allow 443 && ufw enable
```

Use SSH keys and turn off password login (`PasswordAuthentication no` in `/etc/ssh/sshd_config`).

## Put the app on it

```bash
su - deploy
git clone <your repository> /opt/andaneri-crm     # or copy the folder with scp / WinSCP
cd /opt/andaneri-crm
cp .env.production.example .env
nano .env                                          # fill every value; secrets: openssl rand -base64 48
docker compose up -d --build
docker compose logs -f app                         # wait for "Started CrmApplication"
```

Open `https://<your domain>`. Sign in as root, check **Security**, then as `admin`: change its
password and add the team under **Admin**. The test / test login is off in production.

The first start creates all tables. To bring the existing data over, either import the Excel files
through **Import / export** again, or copy your local database:

```bash
# on your computer
mysqldump -uroot -p andaneri > andaneri.sql
# on the server
docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" andaneri' < andaneri.sql
docker compose restart app
```

## Updating

```bash
cd /opt/andaneri-crm
git pull
docker compose up -d --build
```

Database changes run by themselves on start (Flyway). Take a backup first (below).

## Backups

```bash
chmod +x deploy/backup-mysql.sh
crontab -e
# 15 3 * * * /opt/andaneri-crm/deploy/backup-mysql.sh >> /opt/andaneri-crm/backups/backup.log 2>&1
```

That keeps 30 days of dumps in `backups/`. **Also copy them off the server** (rclone to Google Drive
or Backblaze B2, or the provider's own backup option): a backup on the same disk does not survive
losing the server. Try a restore once, so you know it works:

```bash
gunzip -c backups/andaneri-2026-09-15_0315.sql.gz | docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" andaneri'
```

## Security checklist

- `.env` has long random `JWT_SECRET`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `ROOT_PASSWORD`, and it
  is never committed or sent around.
- Only ports 22, 80, 443 open. The app itself has no public port; Caddy is the only way in, which is
  also why the IPs in **Security** can be trusted (`FORWARD_HEADERS=native`).
- The **IP whitelist** is optional. If the team works from the office and phones on mobile data,
  addresses change, so leave it off and rely on the automatic blocking of repeated failures. Turn it
  on only if everyone signs in from fixed addresses. Root can always get in
  (`ROOT_BYPASS_WHITELIST=true`), so the whitelist cannot lock you out.
- `LOG_FULL_FAILED_PASSWORDS` stays `false` unless you really need it: a failed attempt is very often
  someone's real password with one typo, and full text would put those in the log.
- To change the root password: edit `ROOT_PASSWORD` in `.env`, `docker compose up -d`. Old root
  sessions end.
- Keep the server updated: `apt upgrade` monthly, and `docker compose pull && docker compose up -d --build`
  now and then for MySQL and Caddy fixes.

## When something is wrong

```bash
docker compose ps                  # all three should be "running" / "healthy"
docker compose logs --tail 200 app
docker compose logs --tail 100 caddy   # certificate problems: usually the DNS record is not pointing here yet
```
