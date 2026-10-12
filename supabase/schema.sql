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
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    password VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE rooms
    ADD COLUMN IF NOT EXISTS is_closed BOOLEAN NOT NULL DEFAULT FALSE;

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
    payload TEXT,
    client_message_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Store the complete chat message shape while keeping the searchable core columns.
ALTER TABLE messages ADD COLUMN IF NOT EXISTS payload TEXT;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS client_message_id VARCHAR(255);

-- Index for querying chat messages by roomId ordered by timestamp
CREATE INDEX IF NOT EXISTS idx_messages_room_created ON messages(room_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_messages_room_client_id ON messages(room_id, client_message_id);

-- 5a. Persistent room playback state, queue, and replay history
CREATE TABLE IF NOT EXISTS room_media_state (
    room_id VARCHAR(255) PRIMARY KEY,
    payload TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 6. Pinned Study Room Notes
CREATE TABLE IF NOT EXISTS pinned_notes (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    room_id VARCHAR(255) NOT NULL,
    note_id VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    pinned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_pinned_notes_room_note UNIQUE (room_id, note_id)
);

CREATE INDEX IF NOT EXISTS idx_pinned_notes_room_pinned
    ON pinned_notes(room_id, pinned_at DESC);

-- ==============================================================================
-- Row Level Security (RLS) Configuration (Optional for Supabase Client Access)
-- ==============================================================================

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_recent_rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE pinned_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE room_media_state ENABLE ROW LEVEL SECURITY;

-- Allow public / service-role read/write for backend API operation
CREATE POLICY "Allow all operations for backend service" ON users FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for backend service" ON user_recent_rooms FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for backend service" ON rooms FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for backend service" ON messages FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for backend service" ON pinned_notes FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for backend service" ON room_media_state FOR ALL USING (true) WITH CHECK (true);
