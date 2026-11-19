-- db-scheduler's table, as db-scheduler defines it for PostgreSQL, for boot-scheduler-db.
-- A repeatable migration, so it fits an application's own versions, whatever they are, and an existing database:
-- every statement is idempotent, and a later change to this file adds what is missing the same way.
CREATE TABLE IF NOT EXISTS scheduled_tasks (
  task_name text NOT NULL,
  task_instance text NOT NULL,
  task_data bytea,
  execution_time timestamp with time zone NOT NULL,
  picked boolean NOT NULL,
  picked_by text,
  last_success timestamp with time zone,
  last_failure timestamp with time zone,
  consecutive_failures int,
  last_heartbeat timestamp with time zone,
  version bigint NOT NULL,
  priority smallint,
  PRIMARY KEY (task_name, task_instance)
);

-- index names are per schema in PostgreSQL, so they are prefixed with the table's
CREATE INDEX IF NOT EXISTS scheduled_tasks_execution_time_idx ON scheduled_tasks (execution_time);
CREATE INDEX IF NOT EXISTS scheduled_tasks_last_heartbeat_idx ON scheduled_tasks (last_heartbeat);
CREATE INDEX IF NOT EXISTS scheduled_tasks_priority_execution_time_idx ON scheduled_tasks (priority DESC, execution_time ASC);
