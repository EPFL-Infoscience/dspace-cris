--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

-- --
-- Remove unique constraint on entity_id and shortname of table cris_layout_tab if it exists.
-- Now the entity_id and shortname aren't unique because entity_type can have custom_filter in it
-- --
ALTER TABLE cris_layout_tab DROP CONSTRAINT IF EXISTS cris_layout_tab_entity_shortname_unique;

-- --
-- Adds unique constraint on entity_id, shortname, custom_filter.
-- Since postgresql does not support "add constraint if not exists", the script deletes the
-- constraint if it already exists before trying to add it. 
-- --
ALTER TABLE cris_layout_tab DROP CONSTRAINT IF EXISTS cris_layout_tab_entity_shortname_custom_filter_unique;
ALTER TABLE cris_layout_tab ADD CONSTRAINT cris_layout_tab_entity_shortname_custom_filter_unique UNIQUE(entity_id, shortname, custom_filter);