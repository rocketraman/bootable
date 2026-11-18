-- db-scheduler's table, as db-scheduler defines it for MySQL 8, for boot-scheduler-db; MariaDB takes it too.
-- A repeatable migration, so it fits an application's own versions, whatever they are, and an existing database:
-- every statement is idempotent. MySQL has no CREATE INDEX IF NOT EXISTS, so the indexes are part of the table.
CREATE TABLE IF NOT EXISTS scheduled_tasks (
  task_name varchar(100) NOT NULL,
  task_instance varchar(100) NOT NULL,
  task_data blob,
  execution_time datetime(6) NOT NULL,
  picked boolean NOT NULL,
  picked_by varchar(50),
  last_success datetime(6) NULL,
  last_failure datetime(6) NULL,
  consecutive_failures int,
  last_heartbeat datetime(6) NULL,
  version bigint NOT NULL,
  priority smallint,
  PRIMARY KEY (task_name, task_instance),
  INDEX execution_time_idx (execution_time),
  INDEX last_heartbeat_idx (last_heartbeat),
  INDEX priority_execution_time_idx (priority DESC, execution_time ASC)
);
