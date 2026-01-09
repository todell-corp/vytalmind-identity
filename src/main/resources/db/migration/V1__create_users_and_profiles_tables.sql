-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    idp_id TEXT UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_users_idp_id ON users(idp_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_deleted ON users(deleted);

-- User profiles table
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    birthdate DATE,
    weight_goal_kg NUMERIC(6,2) CHECK (weight_goal_kg > 0 AND weight_goal_kg < 1000),
    height_cm NUMERIC(6,2) CHECK (height_cm > 0 AND height_cm < 300),
    weight_goal_original VARCHAR(50),
    height_original VARCHAR(50),
    target_blood_sugar_min INTEGER CHECK (target_blood_sugar_min >= 0 AND target_blood_sugar_min <= 1000),
    target_blood_sugar_max INTEGER CHECK (target_blood_sugar_max >= 0 AND target_blood_sugar_max <= 1000),
    activity_level VARCHAR(20) CHECK (activity_level IN ('SEDENTARY', 'LOW_ACTIVE', 'ACTIVE', 'VERY_ACTIVE')),
    sex VARCHAR(10) CHECK (sex IN ('MALE', 'FEMALE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_blood_sugar_range CHECK (
        target_blood_sugar_min IS NULL OR
        target_blood_sugar_max IS NULL OR
        target_blood_sugar_min <= target_blood_sugar_max
    )
);

CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
