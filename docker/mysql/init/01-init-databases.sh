#!/bin/bash
set -euo pipefail

# The MySQL image creates MYSQL_DATABASE and MYSQL_USER first. This script then
# gives the same non-root account access to Nacos' isolated database and imports
# the schema vendored verbatim from the Nacos Server 3.2.3 source tag.
mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS \`${SALES_DATABASE:-agent_demo_sales}\`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS \`${NACOS_DATABASE:-nacos_config}\`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON \`${SALES_DATABASE:-agent_demo_sales}\`.* TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON \`${NACOS_DATABASE:-nacos_config}\`.* TO '${MYSQL_USER}'@'%';
FLUSH PRIVILEGES;
SQL

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  --default-character-set=utf8mb4 "${NACOS_DATABASE:-nacos_config}" \
  < /docker-entrypoint-initdb.d/02-nacos-mysql-schema.sql.source
