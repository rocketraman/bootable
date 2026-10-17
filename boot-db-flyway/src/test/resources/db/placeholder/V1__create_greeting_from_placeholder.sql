CREATE TABLE greeting (
  id INTEGER PRIMARY KEY,
  text TEXT NOT NULL
);
INSERT INTO greeting (id, text) VALUES (1, '${greeting}');
