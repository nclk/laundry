alter table program add column documentation jsonb default null;

alter table config_profile add column documentation jsonb default null;
alter table config_profile add column meta jsonb default null;
