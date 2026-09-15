-- One-time local setup: run as root, for example in MySQL Workbench.
-- Creates the CRM database and a user that can only connect from this machine.
-- Replace CHANGE_ME with a password of your own, and put the same one in backend/.env as DB_PASSWORD.
-- The tables themselves are created by the backend (Flyway) the first time it starts.
CREATE DATABASE IF NOT EXISTS andaneri_crm CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'andaneri'@'localhost' IDENTIFIED BY 'CHANGE_ME';
CREATE USER IF NOT EXISTS 'andaneri'@'127.0.0.1' IDENTIFIED BY 'CHANGE_ME';

GRANT ALL PRIVILEGES ON andaneri_crm.* TO 'andaneri'@'localhost', 'andaneri'@'127.0.0.1';
FLUSH PRIVILEGES;
