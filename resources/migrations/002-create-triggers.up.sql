create or replace function resource_updated()
	returns trigger as '
	begin
		NEW.modified = (now() at time zone ''utc'');
		return NEW;
	end;
	'
	language 'plpgsql';

create trigger update_config_profile_modified
	before update
	on config_profile
	for each row execute procedure
		resource_updated();

create trigger update_program_modified
	before update
	on program
	for each row execute procedure
		resource_updated();

create trigger update_module_modified
	before update
	on module
	for each row execute procedure
		resource_updated();

create trigger update_test_run_modified
	before update
	on test_run
	for each row execute procedure
		resource_updated();

