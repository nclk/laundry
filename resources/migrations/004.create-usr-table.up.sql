create table usr (
	username text primary key,
	created timestamp without time zone default (
		now() at time zone 'utc'
	)
);

insert into usr (username) values ('system');

alter table config_profile add column username text not null default 'system';
