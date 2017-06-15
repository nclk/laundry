alter table test_run add column modified timestamp without time zone;

create trigger update_test_run_modified
	before update
	on test_run
	for each row execute procedure
		resource_updated();

