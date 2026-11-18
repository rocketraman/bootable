-- db-scheduler's table, as db-scheduler defines it for SQLite, for boot-scheduler-db.
-- A repeatable migration, so it fits an application's own versions, whatever they are, and an existing database:
-- every statement is idempotent, and a later change to this file adds what is missing the same way.
CREATE TABLE IF NOT EXISTS scheduled_tasks (
  task_name varchar(100),
  task_instance varchar(100),
  task_data blob,
  execution_time timestamp,
  picked bit,
  picked_by varchar(50),
  last_success timestamp,
  last_failure timestamp,
  consecutive_failures int,
  last_heartbeat timestamp,
  version bigint,
  priority smallint,
  PRIMARY KEY (task_name, task_instance)
);

-- index names are per database in SQLite, so they are prefixed with the table's
CREATE INDEX IF NOT EXISTS scheduled_tasks_execution_time_idx ON scheduled_tasks (execution_time);
CREATE INDEX IF NOT EXISTS scheduled_tasks_last_heartbeat_idx ON scheduled_tasks (last_heartbeat);
CREATE INDEX IF NOT EXISTS scheduled_tasks_priority_execution_time_idx ON scheduled_tasks (priority DESC, execution_time ASC);
