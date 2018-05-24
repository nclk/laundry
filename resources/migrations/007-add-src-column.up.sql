alter table module alter column data type text;
alter table module rename data to src;
alter table module add column meta jsonb;
alter table module add column dependencies jsonb;

drop table config_profile;
drop table config_profile_program_map;

alter table program drop column data;
alter table program add column main text;

