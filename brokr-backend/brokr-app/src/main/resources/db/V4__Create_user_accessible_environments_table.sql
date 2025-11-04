-- brokr-app/src/main/resources/db/migration/V4__Create_user_accessible_environments_table.sql
CREATE TABLE user_accessible_environments (
                                              user_id VARCHAR(255) NOT NULL,
                                              environment_id VARCHAR(255) NOT NULL,
                                              PRIMARY KEY (user_id, environment_id),
                                              FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                              FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);