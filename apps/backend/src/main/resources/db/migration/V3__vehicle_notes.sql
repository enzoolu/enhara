create table vehicle_notes (
    id uuid primary key,
    vehicle_id uuid not null references vehicles(id) on delete cascade,
    title varchar(120) not null,
    description varchar(1000) not null,
    category varchar(24) not null,
    due_at timestamp with time zone,
    status varchar(16) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    completed_at timestamp with time zone
);

create index idx_vehicle_note_vehicle_updated on vehicle_notes(vehicle_id, updated_at desc);
create index idx_vehicle_note_vehicle_status on vehicle_notes(vehicle_id, status);
