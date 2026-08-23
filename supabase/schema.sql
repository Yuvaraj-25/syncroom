-- ==============================================================================
-- SyncRoom Supabase / PostgreSQL Database Schema
-- ==============================================================================
-- Run this script in the Supabase SQL Editor (or standard PostgreSQL instance)
-- to initialize tables, indexes, and constraints.
-- ==============================================================================

-- 1. Enable pgcrypto / uuid extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 2. Users Table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name VARCHAR(255),
    email VARCHAR(255) NOT NULL UNIQUE,
    image TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for fast user lookups by email
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- 3. User Recent Rooms Table (Mapped to User.recentRooms collection)
CREATE TABLE IF NOT EXISTS user_recent_rooms (
    user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_id VARCHAR(255) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    room_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, room_order)
);

CREATE INDEX IF NOT EXISTS idx_user_recent_rooms_user ON user_recent_rooms(user_id);

-- 4. Rooms Table
CREATE TABLE IF NOT EXISTS rooms (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    room_id VARCHAR(255) NOT NULL UNIQUE,
    owner_id VARCHAR(255),
    is_private BOOLEAN DEFAULT FALSE,
    password VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for fast room lookups by room_id
CREATE INDEX IF NOT EXISTS idx_rooms_room_id ON rooms(room_id);

-- 5. Messages Table
CREATE TABLE IF NOT EXISTS messages (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    room_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    text TEXT NOT NULL,
    user_name VARCHAR(255),
    user_image TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for querying chat messages by roomId ordered by timestamp
CREATE INDEX IF NOT EXISTS idx_messages_room_created ON messages(room_id, created_at ASC);

-- ==============================================================================
-- Row Level Security (RLS) Configuration (Optional for Supabase Client Access)
-- ==============================================================================

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_recent_rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;

-- Allow public / service-role read/write for backend API operation
CREATE POLICY "Allow all operations for backend service" ON users FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for backend service" ON user_recent_rooms FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for backend service" ON rooms FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for backend service" ON messages FOR ALL USING (true) WITH CHECK (true);
