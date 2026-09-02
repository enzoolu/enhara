alter table vehicles alter column vin drop not null;

create table vehicle_profile_fields (
    id uuid primary key,
    vehicle_id uuid not null references vehicles(id) on delete cascade,
    field_key varchar(40) not null,
    field_value varchar(512) not null,
    source varchar(32) not null,
    provider varchar(32),
    source_url varchar(512),
    observed_at timestamp with time zone,
    retrieved_at timestamp with time zone not null,
    provider_expires_at timestamp with time zone,
    confirmed_at timestamp with time zone,
    constraint uk_vehicle_profile_field unique (vehicle_id, field_key)
);

create table vehicle_provider_cache (
    id uuid primary key,
    provider varchar(32) not null,
    lookup_key varchar(128) not null,
    payload_json varchar(8000) not null,
    fetched_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    constraint uk_vehicle_provider_cache unique (provider, lookup_key)
);

create table vehicle_provider_statuses (
    id uuid primary key,
    vehicle_id uuid not null references vehicles(id) on delete cascade,
    provider varchar(32) not null,
    state varchar(24) not null,
    message varchar(500) not null,
    checked_at timestamp with time zone not null,
    data_fetched_at timestamp with time zone,
    constraint uk_vehicle_provider_status unique (vehicle_id, provider)
);

create table vehicle_photos (
    id uuid primary key,
    vehicle_id uuid not null references vehicles(id) on delete cascade,
    original_filename varchar(255) not null,
    media_type varchar(40) not null,
    storage_key varchar(100) not null unique,
    size_bytes bigint not null,
    width_pixels integer not null,
    height_pixels integer not null,
    caption varchar(240),
    created_at timestamp with time zone not null
);

create index idx_vehicle_profile_field_vehicle on vehicle_profile_fields(vehicle_id);
create index idx_vehicle_provider_cache_lookup on vehicle_provider_cache(provider, lookup_key);
create index idx_vehicle_photo_vehicle_created on vehicle_photos(vehicle_id, created_at desc);
