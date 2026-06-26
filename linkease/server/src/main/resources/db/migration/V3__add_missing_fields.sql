alter table clients add column package_total integer not null default 0;
alter table clients add column package_used  integer not null default 0;
alter table clients add column birth_date    text;

alter table sessions add column confirmed boolean not null default false;
alter table sessions add column paid      boolean not null default false;
