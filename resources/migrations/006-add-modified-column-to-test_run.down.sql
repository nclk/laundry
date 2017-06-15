drop trigger if exists update_test_run_modified on test_run;
alter table test_run drop column modified;
