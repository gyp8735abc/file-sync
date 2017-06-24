
CREATE TABLE if not exists groups_info(
    ID BIGINT IDENTITY PRIMARY KEY,
    group_no NVARCHAR2(64),
    group_name NVARCHAR2(64),
    path NVARCHAR2(512),
    ready_time DATETIME,
    record_time DATETIME NOT NULL default CURRENT_TIMESTAMP
);
CREATE TABLE if not exists group_members(
    ID BIGINT IDENTITY PRIMARY KEY,
    group_id int not null,
    ip NVARCHAR2(64),
    port int,
    member_no NVARCHAR2(64),
    online NVARCHAR2(64) default 'N',
    record_time DATETIME NOT NULL default CURRENT_TIMESTAMP
);
CREATE TABLE if not exists group_file_non_sync(
	id BIGINT IDENTITY PRIMARY KEY,
	member_id BIGINT,
	file_path NVARCHAR2(512),
	event_type int,
	del int default 0,
	add_time BIGINT
);
CREATE TABLE if not exists group_files(
    ID BIGINT IDENTITY PRIMARY KEY,
    group_id int not null,
    path NVARCHAR2(512),
    md5 NVARCHAR2(64),
    last_modify_time BIGINT  NOT NULL,
    length int not null default 0,
    del int default 0,
    status int default 0,
    lock int default 0,
    modify_time DATETIME,
    record_time DATETIME NOT NULL default CURRENT_TIMESTAMP
);
create index if not exists idx_group_files_path on group_files (path);

