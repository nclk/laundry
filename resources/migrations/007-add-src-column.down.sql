alter table program drop column main;
alter table program add column data jsonb;

CREATE TABLE public.config_profile_program_map (
    config_profile text NOT NULL,
    program text NOT NULL,
    created timestamp without time zone DEFAULT timezone('utc'::text, now())
);
ALTER TABLE ONLY public.config_profile_program_map
    ADD CONSTRAINT config_profile_program_map_pkey PRIMARY KEY (config_profile, program);

CREATE TABLE public.config_profile (
    name text NOT NULL,
    data jsonb,
    created timestamp without time zone DEFAULT timezone('utc'::text, now()),
    modified timestamp without time zone,
    username text DEFAULT 'system'::text NOT NULL,
    documentation jsonb,
    meta jsonb
);
ALTER TABLE ONLY public.config_profile
    ADD CONSTRAINT config_profile_pkey PRIMARY KEY (name);

CREATE TRIGGER update_config_profile_modified BEFORE UPDATE ON public.config_profile FOR EACH ROW EXECUTE PROCEDURE public.resource_updated();

alter table module drop column dependencies;
alter table module drop column meta;
alter table module rename src to data;
alter table module alter column data type jsonb using (to_json(data));

