--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

CREATE TABLE if not exists items_import_modification_date(
    id varchar(255) NOT NULL,
	modification_date varchar(255),
	primary key (id)
);
