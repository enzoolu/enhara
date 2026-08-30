create table trips (
    id uuid primary key,
    vehicle_id uuid not null references vehicles(id) on delete cascade,
    started_at timestamp with time zone not null,
    ended_at timestamp with time zone,
    distance_km double precision not null,
    average_speed_kph double precision not null,
    max_speed_kph double precision not null,
    harsh_acceleration_count integer not null,
    harsh_braking_count integer not null,
    high_rpm_seconds bigint not null,
    driving_score integer not null
);

create index idx_trip_vehicle_started on trips(vehicle_id, started_at desc);
create index idx_trip_vehicle_ended on trips(vehicle_id, ended_at);
