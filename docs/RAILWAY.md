# Hosting on Railway (no GitHub needed)

Railway builds the project's `Dockerfile` from your own computer with the Railway CLI (`railway up`),
so no GitHub repository or GitHub login is involved. You get two services in one project:
**MySQL** and **andaneri-crm**, with HTTPS on a `*.up.railway.app` address (or your own domain).

Files Railway uses from this folder: `Dockerfile`, `railway.json` (build + health check),
`.railwayignore` (keeps `backend/.env` and build leftovers on your computer).

## 1. Account and CLI (once)

1. Go to https://railway.com, **Login**, and sign up with **email** (a sign-in code or link is mailed
   to you). Do not pick GitHub.
2. Install the CLI (Node.js is already on this computer):

   ```bash
   npm i -g @railway/cli
   ```

3. Sign in; a browser tab opens where you confirm with your email:

   ```bash
   railway login
   ```

   No browser on that machine? `railway login --browserless` prints a code to confirm instead.

## 2. Project and database

In the project folder (`C:\Users\Administrator\IdeaProjects\andaneri-crm`):

```bash
railway init
railway add --database mysql
railway add --service andaneri-crm
```

`railway init` asks for a name: `andaneri-crm`. The second command creates a service called **MySQL**.

In the dashboard (https://railway.com/dashboard, open the project) pick a region close to Georgia for
both services: service **Settings → Deploy → Region → EU West**.

## 3. Variables of the andaneri-crm service

Dashboard → **andaneri-crm** → **Variables** → **Raw Editor**, paste, fill the empty values, **Update**.
Secrets: in PowerShell, `[Convert]::ToBase64String((1..48 | % { Get-Random -Max 256 }))` gives one.

```properties
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
JWT_SECRET=
ROOT_USERNAME=
ROOT_PASSWORD=
ADMIN_USERNAME=admin
ADMIN_PASSWORD=
CRM_ZONE=Asia/Tbilisi
TEST_ACCOUNT=false
DEMO_DATA=false
LOG_FULL_FAILED_PASSWORDS=false
CLIENT_IP_HEADER=X-Real-IP
JAVA_OPTS=-Xms128m -Xmx512m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError
```

- The `${{MySQL.…}}` parts are filled in by Railway from the database service; leave them as they are.
- `ADMIN_PASSWORD` **must** be set: without it the first admin would get the built-in default password.
- `CLIENT_IP_HEADER=X-Real-IP`: Railway's proxy sends the visitor's address in that header, so the
  security centre shows real IPs and blocks the right ones.
- `JAVA_OPTS` caps Java's memory. Railway bills memory used, and without a cap Java happily grows.
- After saving, use the 3-dot menu to **Seal** the secret ones (JWT, ROOT, ADMIN passwords): sealed
  values can no longer be read back, not even by you, so keep them in your password manager.

## 4. Deploy

```bash
railway up --service andaneri-crm
```

This uploads the folder (minus `.gitignore` and `.railwayignore` entries), builds the image on Railway
(first build about 5-10 minutes: npm install, Maven, then the jar) and starts it. The health check
waits for `/login` to answer. The first start creates all tables in the new database.

Then give it an address:

```bash
railway domain --service andaneri-crm
```

It prints `https://andaneri-crm-production-xxxx.up.railway.app`. Your own domain later:
`railway domain crm.yourdomain.ge --service andaneri-crm`, then add the DNS record it shows.

## 5. Check it

1. Open the address, sign in with `ROOT_USERNAME` / `ROOT_PASSWORD`.
2. **Security → Overview** should show **your own** public IP (compare with https://ifconfig.me).
3. Make sure the IP header cannot be faked. In PowerShell:

   ```bash
   curl.exe -s -H "X-Real-IP: 1.2.3.4" https://YOUR-ADDRESS/api/auth/me
   ```

   Then **Security → Requests**: the newest request must show your real IP, **not** `1.2.3.4`. If it
   shows `1.2.3.4`, delete the `CLIENT_IP_HEADER` variable (IP blocking would be fakeable) and redeploy.
4. Sign in as `admin` with `ADMIN_PASSWORD`, change its password, add the team under **Admin**.
5. Bring the data in: **Import / export → Excel** with the Sales Report Form (the simplest way; your
   local database only holds test data).

## Updating after changes

```bash
railway up --service andaneri-crm
```

Database changes run by themselves when the new version starts.

## Backups

Open the **MySQL** service → **Backups** and turn on scheduled backups if your plan offers them. For a
copy on your own computer, enable the database's public TCP proxy temporarily (MySQL → Settings →
Networking; network traffic through it is billed), then with the connection details from its
Variables tab:

```bash
mysqldump --single-transaction -h HOST -P PORT -u root -p railway > andaneri-backup.sql
```

and switch the public proxy off again.

## Cost

Trial: a one-time $5 credit. Hobby: $5 a month including $5 of usage. Java with a 512 MB cap plus a
small MySQL usually runs a bit over that; check **Usage** in the dashboard after a few days.

## When something is wrong

```bash
railway logs --service andaneri-crm
```

- `Communications link failure`: a `DB_*` variable is wrong, or MySQL is still starting (it retries on
  the next deploy; redeploy once MySQL shows healthy).
- `No users yet: set ADMIN_PASSWORD`: set that variable and redeploy.
- Health check failing: look at the log for the first `ERROR` line.
