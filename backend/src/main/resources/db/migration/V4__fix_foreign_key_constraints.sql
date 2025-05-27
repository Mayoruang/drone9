-- Fix foreign key constraints to ensure CASCADE DELETE works properly

-- First, drop existing constraints if they exist
ALTER TABLE drone_geofence DROP CONSTRAINT IF EXISTS fk_drone_geofence_drone_id;
ALTER TABLE drone_geofence DROP CONSTRAINT IF EXISTS fk_drone_geofence_geofence_id;
ALTER TABLE drone_geofence DROP CONSTRAINT IF EXISTS fkoj8ffdih44dylk1jb2jjrh4te;

-- Re-create the constraints with proper CASCADE DELETE
ALTER TABLE drone_geofence 
ADD CONSTRAINT fk_drone_geofence_drone_id 
FOREIGN KEY (drone_id) REFERENCES drones(drone_id) ON DELETE CASCADE;

ALTER TABLE drone_geofence 
ADD CONSTRAINT fk_drone_geofence_geofence_id 
FOREIGN KEY (geofence_id) REFERENCES geofences(geofence_id) ON DELETE CASCADE;

-- Add some logging for verification
DO $$
BEGIN
    RAISE NOTICE 'Foreign key constraints updated with CASCADE DELETE';
END $$; 