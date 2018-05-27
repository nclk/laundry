alter table program rename src to main;
alter table program add column documentation jsonb;
alter table program drop column dependencies;
alter table program drop column meta;

