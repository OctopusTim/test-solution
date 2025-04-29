CREATE TABLE author
(
    id     serial primary key,
    full_name varchar(255) not null,
    created_at timestamp not null default now()
);

ALTER TABLE budget ADD COLUMN author_id INTEGER;

ALTER TABLE budget ADD CONSTRAINT fk_budget_author
    FOREIGN KEY (author_id) REFERENCES author (id) on delete set null;