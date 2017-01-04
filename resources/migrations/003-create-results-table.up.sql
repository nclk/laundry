create table if not exists test_run (
	id uuid primary key,
	seed bigint not null,
	program_name text not null,
	num_checkpoints bigint,
	num_failures bigint,
	env jsonb not null,
	status text not null default 'pending',
	created timestamp without time zone default (
		now() at time zone 'utc'
	)
);

create table if not exists checkpoint (
	id uuid not null primary key,
	test_run_id uuid,
	success boolean not null default true,
	data jsonb not null,
	created timestamp without time zone default (
		now() at time zone 'utc'
	)
);

create table if not exists harvest (
	id serial primary key,
	name text not null,
	test_run_id uuid not null,
	data jsonb not null,
	created timestamp without time zone default (
		now() at time zone 'utc'
	)
);

create table if not exists log_entry (
	id serial primary key,
	test_run_id uuid not null,
	level text not null default 'trace',
        message text not null,
	created timestamp without time zone default (
		now() at time zone 'utc'
	)
);

create table if not exists raw_result (
	test_run_id uuid not null primary key,
	raw jsonb
);
