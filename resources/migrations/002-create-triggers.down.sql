drop trigger if exists update_config_profile_modified
	on config_profile;
drop trigger if exists update_program_modified
	on program;
drop trigger if exists update_module_modified
	on module;
drop function if exists config_profile_updated();
