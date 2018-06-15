alter table checkpoint drop column success,
		       add column success boolean default true;
