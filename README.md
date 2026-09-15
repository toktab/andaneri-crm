# Andaneri CRM

A sales CRM for Andaneri syrups (Taste Lab LLC): the bars, cafes, restaurants and hotels we sell to,
everything that happened with each of them, and what to do next. It replaces the "Sales Report Form"
spreadsheet, the notes app and remembering follow-ups.

- **backend/**: Spring Boot 4 (Java 17+), MySQL, Flyway, JWT sign-in
- **frontend/**: React 19 + Vite + TypeScript + Tailwind, Georgian UI with an English switch

## Run it

### 1. Database (once)

Create an empty MySQL schema and a user for it: either run `db/create-local-database.sql` in MySQL
Workbench, or make a schema yourself. Then put the connection in `backend/.env`
(copy `backend/.env.example`):

```properties
DB_URL=jdbc:mysql://localhost:3306/andaneri?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
DB_USERNAME=root
DB_PASSWORD=your-password
```

The tables are created by the backend itself (Flyway) the first time it starts.

### 2. Start everything

Run `CrmApplication` from IntelliJ (or `cd backend && mvnw spring-boot:run`). That one start does
it all: it connects to MySQL, creates or updates the tables, and builds the frontend in watch mode
(`npm install` the first time), then serves the whole app on **http://localhost:8082**. The log says
`Andaneri CRM is ready` when the page is up; frontend changes rebuild on save, reload the browser.
`backend/.env` is found whether the working directory is `backend/` or the project root.

No MySQL at hand? `mvnw spring-boot:run -Dspring-boot.run.profiles=demo` runs on a file database in
`backend/data/` filled with example businesses, calls and orders (logins `toko`, `nino`,
`supervisor`, password `demo12345`).

For hot reload while working on the frontend only, `cd frontend && npm run dev` still works on
http://localhost:5180 (it forwards `/api` to the backend); set `FRONTEND_AUTOSTART=false` then.

## Logins

| login | password | what it is |
|---|---|---|
| `test` | `test` | Throwaway admin for trying things out. **Set `TEST_ACCOUNT=false` in `backend/.env` before real use**: that also switches the account off. |
| `admin` | `ADMIN_PASSWORD` from `.env` | The first admin, created only when there are no users yet. Change its password after signing in. |
| `ROOT_USERNAME` | `ROOT_PASSWORD` | Root: everything an admin can do plus **Security**. Set in `.env`, applied on every start; changing the password there ends root's old sessions. |

Add the real team under **Admin → Team members** (salesperson, supervisor or admin).

## Security centre (root only)

Every sign-in and failed attempt (username, IP, browser, time, and the password typed, masked as
`p•••d (9)` unless `LOG_FULL_FAILED_PASSWORDS=true`), every IP that used the API and which accounts
it signed into, every API request, and every row anyone added, changed or deleted with the value
before and after. From there root can block or whitelist IPs and ranges (`10.0.0.0/24`), switch the
whitelist on, set how many failures block an IP and for how long (10 in 15 minutes by default,
blocked automatically), and end a person's sessions on every device. Logs are kept 180 days.

## Main: projects, sheets, Excel mode

**Main** holds every business the way the spreadsheet did. On the left, **projects** (one per Excel
file, usually) with their **sheets**; create, rename, move, merge and delete them from the `…` menu.
On the right, the names of the chosen sheet: click one, or its arrow, and its whole file opens in a
side panel: next steps and when, every call, visit and meeting, what they use and want, orders.
**Excel mode** shows every column, extra fields included: double-click or Enter edits a cell and it
saves straight away, arrows move, tick rows to move them to a sheet or change status, priority or who
has them. Lists and the grid only draw the rows on screen, so thousands scroll smoothly.

## Excel mode

Main → **Excel mode** works like Excel:

| do | how |
|---|---|
| select | click a cell; drag, or Shift + click / Shift + arrows, for a range; click a row number or column letter for the whole row or column; Ctrl + A for everything |
| edit | start typing to replace the cell; F2, a double-click or the **fx** bar to change what is there; drop-down columns open their list |
| move | Enter / Shift + Enter down and up, Tab / Shift + Tab right and left, arrows, Home / End, Ctrl + arrows to the edges, Page Up / Down |
| clear | Delete clears the selection |
| copy / paste | Ctrl + C, Ctrl + X, Ctrl + V, to and from Excel or Google Sheets; one value pasted into a selection fills all of it |
| fill down | Ctrl + D copies the top row of the selection down |
| undo | Ctrl + Z, redo Ctrl + Y (also the arrows next to the fx bar) |
| widths | drag a column letter's edge; double-click it to reset |

Every change saves straight away, one request per row. Selecting several rows shows the bulk bar (move to
sheet, status, priority, assign).

## Backups

Admin → **Backups** → **Back up now** makes a zip and downloads it:

- `backup.json`: everything - projects and sheets, extra fields, the product catalog, the team (no
  passwords), settings, private notes, and every business with its contacts, calls, visits, tasks,
  comments, orders and answers;
- `businesses-import.json`: the businesses with full history in the CRM's import format (Import / export →
  JSON import brings them back);
- `README.txt`: what is in there and the format.

JSON rather than Excel because it is exact and fast; **Convert to Excel** on the same page turns any backup
(.zip or .json) into an .xlsx with one sheet per kind of data.

The server keeps each backup for 14 days so other admins can download it too, then deletes it. Every make,
download and deletion is logged with the user, IP address, browser and time. When the last backup is 7 days
old, admins see a "backup due" line at the top. All three numbers are under Admin → Settings.

## Reminders on phones and laptops

User menu (round initial, top right) → **Reminders**. Three ways, side by side, so the team can keep
what works best:

- **Notifications on this device**: push messages ("17:00 · Meeting · Bar X", contact, phone, notes)
  that arrive even with the CRM closed. Android and computers: Chrome, Edge, Firefox, Safari. iPhone
  (iOS 16.4+): only after Safari → Share → **Add to Home Screen**, then turning it on from that icon.
  The signing keys are created by the server on first use and kept in the database; nothing to set up.
- **Calendar subscription**: a private link that puts all your tasks, with alerts, into the iPhone,
  Mac or Outlook calendar. Google Calendar refreshes such links only every 12-24 hours.
- **Add to my calendar** on a meeting or visit: one tap puts that one task into the phone's calendar.

Default: 30 minutes before (each person picks their own; each task can override, or say no reminder).
All-day tasks are reminded at 09:00 on the day.

## My history

**My history** in the menu: everything you added, changed or deleted, newest first, with the business
it belongs to and every field from what to what. Supervisors and admins can pick a colleague.

## Bringing in the old spreadsheet

**Import / export → Excel or CSV import**:

1. **File**: choose it.
2. **Where**: a new project named after the file (or an existing one, or none), and which sheets,
   each under the name it will have in the CRM.
3. **Columns**: CRM fields on the left, the file's columns on the right with their values. Drag a
   column onto a field, or click the column then the field. A column that fits nowhere can be
   **skipped** (you see exactly which values are left out first) or become a **new field**.
4. **Import**: preview each sheet (what each row becomes, duplicates), then import them all.

Every history cell (first contact, meeting, samples, second contact, comment, answer) becomes an
entry on that business's timeline **with the original text kept word for word**, marked "from Excel".
On top of that the import reads brands (Monin, 1883...), flavors (grenadine, pistachio...), yes / no
syrup use, contacts with phones, dates, customer status and the next step, which becomes a task.
Duplicates are found by ID code, phone (however it is written) and name.

Export: the whole list, or a filtered one from **Businesses**, as Excel or as JSON with full history.
The JSON file can be imported back (also on another install).

## What is where

| screen | for |
|---|---|
| Today | tasks for today, overdue, coming up; alerts (time to reorder, samples requested, leads going cold); pipeline; sales |
| Calls | call mode: today's calls one after another, big phone numbers, last time and what is planned, red "ask them" questions, one-tap results, quick note |
| Businesses | search and every filter (status, type, district, brand used, flavor used or wanted, customers, not contacted for N days...), Excel / JSON export |
| A business | tabs: summary, calls, visits & meetings, their answers (liked / did not like), history, orders, details; log a call or visit once and it updates status, usage, interests and the next step |
| Pipeline | drag cards between stages |
| Calendar | month, week, day and list; every task is an entry |
| Tasks, Notes | the task list; quick private notes that can be attached to a business later |
| Products | the Andaneri price list (September 2026), brands, flavors |
| Reports | pick the period, the person and the sections: call results, meeting results, new clients, bottles sold, best-selling / most wanted / liked / disliked flavors, brands in the market; Excel, JSON, print |

The eye icon at the top turns on **observation mode**: everything visible, nothing editable. The
sun / moon icon next to it switches light, dark, or the device's own setting.

## Hosting

- **Railway** (no server to manage, no GitHub needed): [docs/RAILWAY.md](docs/RAILWAY.md), deployed
  from this folder with `railway up`.
- **Your own server**: [docs/HOSTING.md](docs/HOSTING.md): one small VPS, `docker compose up -d --build`,
  HTTPS by Caddy, daily database backups.

## Tests

```bash
cd backend && mvnw test         # parser unit tests + the whole API end to end on H2
cd frontend && npm run build    # typecheck + production build
```

## Settings (`backend/.env`)

| key | default | meaning |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | local `andaneri_crm` | MySQL connection |
| `JWT_SECRET` | dev value | signs sign-in tokens; set a long random string anywhere but your own computer |
| `TOKEN_HOURS` | 168 | how long a sign-in lasts |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | admin / andaneri-admin | first admin, only while there are no users |
| `TEST_ACCOUNT` | true | the test / test login |
| `DEMO_DATA` | false | example data; never on the real database |
| `CRM_ZONE` | Asia/Tbilisi | what "today" means |
| `ROOT_USERNAME`, `ROOT_PASSWORD` | empty (no root) | the root account |
| `LOG_FULL_FAILED_PASSWORDS` | false | keep failed passwords in full instead of masked |
| `ROOT_BYPASS_WHITELIST` | true | root can sign in from an IP outside the whitelist |
| `FORWARD_HEADERS` | none | `native` behind a reverse proxy, so the real visitor IP is logged |
| `FRONTEND_AUTOSTART` | true | build the frontend when the backend starts from the IDE |

Team-wide numbers (days before a lead counts as cold, default days between orders) are under
**Admin → Settings**.

Reminders appear in the browser while the CRM is open (allow them from the user menu). Nothing
reminds anyone while the tab is closed; that would need push notifications, not built yet.
