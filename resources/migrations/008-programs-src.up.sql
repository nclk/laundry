alter table program add column meta jsonb;
alter table program add column dependencies jsonb;
alter table program drop column documentation;
alter table program rename main to src;

