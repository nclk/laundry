create table if not exists config_profile (
	name text primary key,
	data jsonb,
	created timestamp without time zone default (
		now() at time zone 'utc'
	),
        modified timestamp without time zone
);
create table if not exists program (
	name text primary key,
	data jsonb,
	created timestamp without time zone default (
		now() at time zone 'utc'
	),
	modified timestamp without time zone
);
create table if not exists config_profile_program_map (
	config_profile text,
	program text,
	primary key (config_profile, program),
	created timestamp without time zone default (
		now() at time zone 'utc'
	)
);
create table if not exists module (
	name text primary key,
	data jsonb,
	created timestamp without time zone default (
		now() at time zone 'utc'
	),
	modified timestamp without time zone
);

