create table clients (
    id          bigserial primary key,
    name        text not null,
    phone       text not null default '',
    email       text not null default '',
    color_hex   text not null default '#2196F3',
    hourly_rate numeric not null default 0
);

create table locations (
    id         bigserial primary key,
    name       text not null,
    address    text not null default '',
    color_hex  text not null default '#4CAF50',
    maps_link  text
);

create table sessions (
    id          bigserial primary key,
    date        date not null,
    start_time  time not null,
    end_time    time not null,
    location_id bigint references locations(id) on delete set null,
    notes       text not null default '',
    reminded    boolean not null default false
);

create table session_clients (
    session_id bigint not null references sessions(id) on delete cascade,
    client_id  bigint not null references clients(id) on delete cascade,
    primary key (session_id, client_id)
);

create table availability_slots (
    id          bigserial primary key,
    date        date not null,
    start_time  time not null,
    end_time    time not null,
    location_id bigint references locations(id) on delete set null
);

create table app_settings (
    key   text primary key,
    value text not null
);
