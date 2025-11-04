-- brokr-app/src/main/resources/db/migration/V9__Create_update_updated_at_column_function.sql
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$ BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
 $$ language 'plpgsql';