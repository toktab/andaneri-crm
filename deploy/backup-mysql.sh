#!/bin/sh
# A compressed dump of the CRM database into ./backups, keeping 30 days.
# Run daily from cron, e.g.:  15 3 * * *  /opt/andaneri-crm/deploy/backup-mysql.sh
# Then copy ./backups somewhere else too (another server, cloud storage): a backup on the same disk
# does not survive losing the server.
set -eu
cd "$(dirname "$0")/.."
mkdir -p backups
stamp=$(date +%Y-%m-%d_%H%M)
docker compose exec -T db sh -c 'exec mysqldump --single-transaction --quick --routines -uroot -p"$MYSQL_ROOT_PASSWORD" andaneri' \
  | gzip > "backups/andaneri-$stamp.sql.gz"
find backups -name 'andaneri-*.sql.gz' -mtime +30 -delete
echo "backups/andaneri-$stamp.sql.gz"
