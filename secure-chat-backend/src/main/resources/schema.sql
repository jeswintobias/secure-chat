-- ============================================================
-- Secure Chat System — PostgreSQL Schema
-- ============================================================
-- Designed for production use with proper indexing, constraints,
-- and UUID primary keys for distributed-safe ID generation.
-- ============================================================

-- -------------------- ENUM Types --------------------
DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = ''user_role'') THEN
        CREATE TYPE user_role AS ENUM (''USER'', ''ADMIN'');
    END IF;
END ';

DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = ''privacy_setting'') THEN
        CREATE TYPE privacy_setting AS ENUM (''EVERYONE'', ''CONTACTS'', ''NOBODY'');
    END IF;
END ';

DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = ''conversation_type'') THEN
        CREATE TYPE conversation_type AS ENUM (''PRIVATE'', ''GROUP'');
    END IF;
END ';

DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = ''message_type'') THEN
        CREATE TYPE message_type AS ENUM (''TEXT'', ''SYSTEM'', ''IMAGE'', ''FILE'', ''AUDIO'', ''VIDEO'');
    END IF;
END ';

-- Add FILE value to existing enum if it's missing
DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = ''FILE'' AND enumtypid = (SELECT oid FROM pg_type WHERE typname = ''message_type'')) THEN
        ALTER TYPE message_type ADD VALUE IF NOT EXISTS ''FILE'';
    END IF;
END ';

-- Add AUDIO value to existing enum if it's missing
DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = ''AUDIO'' AND enumtypid = (SELECT oid FROM pg_type WHERE typname = ''message_type'')) THEN
        ALTER TYPE message_type ADD VALUE IF NOT EXISTS ''AUDIO'';
    END IF;
END ';

-- Add VIDEO value to existing enum if it's missing
DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = ''VIDEO'' AND enumtypid = (SELECT oid FROM pg_type WHERE typname = ''message_type'')) THEN
        ALTER TYPE message_type ADD VALUE IF NOT EXISTS ''VIDEO'';
    END IF;
END ';

DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = ''auth_provider'') THEN
        CREATE TYPE auth_provider AS ENUM (''LOCAL'', ''GOOGLE'');
    END IF;
END ';

-- -------------------- Users Table --------------------
CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(50)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255),
    auth_provider   auth_provider NOT NULL DEFAULT 'LOCAL',
    provider_id     VARCHAR(255),
    profile_picture_url VARCHAR(512),
    role            user_role    NOT NULL DEFAULT 'USER',
    online_status   BOOLEAN      NOT NULL DEFAULT FALSE,
    last_seen       TIMESTAMP WITH TIME ZONE,
    last_seen_privacy privacy_setting NOT NULL DEFAULT 'EVERYONE',
    read_receipts   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email)
);

-- Migration: add OAuth columns to existing users table
DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''users'' AND column_name = ''auth_provider'') THEN
        ALTER TABLE users ADD COLUMN auth_provider auth_provider NOT NULL DEFAULT ''LOCAL'';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''users'' AND column_name = ''provider_id'') THEN
        ALTER TABLE users ADD COLUMN provider_id VARCHAR(255);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''users'' AND column_name = ''profile_picture_url'') THEN
        ALTER TABLE users ADD COLUMN profile_picture_url VARCHAR(512);
    END IF;
END ';

-- Make password_hash nullable for OAuth users
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- -------------------- Conversations Table --------------------
-- Unified table for both private and group conversations.
CREATE TABLE IF NOT EXISTS conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type            conversation_type NOT NULL,
    name            VARCHAR(100),          -- NULL for PRIVATE, required for GROUP
    referral_code   VARCHAR(64),           -- Only used for GROUP conversations
    public_key      VARCHAR(512),          -- Group encryption public key
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- -------------------- Conversation Members (Join Table) --------------------
CREATE TABLE IF NOT EXISTS conversation_members (
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
    joined_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (conversation_id, user_id)
);

-- -------------------- Messages Table --------------------
CREATE TABLE IF NOT EXISTS messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID         NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       UUID         NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
    content         TEXT         NOT NULL,
    message_type    message_type NOT NULL DEFAULT 'TEXT',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE,         -- NULL = non-ephemeral
    attachment_url  TEXT,                              -- URL/path to uploaded file
    attachment_type VARCHAR(100),                      -- MIME type (e.g. image/png, application/pdf)
    original_name   VARCHAR(500),                      -- original human-readable filename
    pinned          BOOLEAN      NOT NULL DEFAULT FALSE,
    pinned_by       VARCHAR(50),                       -- username who pinned
    pinned_at       TIMESTAMP WITH TIME ZONE           -- when it was pinned
);

-- -------------------- Pinned Conversations (per-user) --------------------
CREATE TABLE IF NOT EXISTS pinned_conversations (
    user_id         UUID NOT NULL REFERENCES users(id)           ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id)   ON DELETE CASCADE,
    pinned_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (user_id, conversation_id)
);

-- -------------------- Message Reads (Read Receipts) --------------------
CREATE TABLE IF NOT EXISTS message_reads (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id  UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    read_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_message_reads_message_user UNIQUE (message_id, user_id)
);

-- -------------------- Connection Request Status Enum --------------------
DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = ''request_status'') THEN
        CREATE TYPE request_status AS ENUM (''PENDING'', ''ACCEPTED'', ''REJECTED'');
    END IF;
END ';

-- -------------------- Connection Requests --------------------
CREATE TABLE IF NOT EXISTS connection_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status          request_status NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_connection_request UNIQUE (sender_id, receiver_id)
);

-- ============================================================
-- Schema migrations for existing databases
-- ============================================================

-- Add privacy and delete columns if they don't exist
DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''users'' AND column_name = ''last_seen'') THEN
        ALTER TABLE users ADD COLUMN last_seen TIMESTAMP WITH TIME ZONE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''users'' AND column_name = ''last_seen_privacy'') THEN
        ALTER TABLE users ADD COLUMN last_seen_privacy privacy_setting NOT NULL DEFAULT ''EVERYONE'';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''users'' AND column_name = ''read_receipts'') THEN
        ALTER TABLE users ADD COLUMN read_receipts BOOLEAN NOT NULL DEFAULT TRUE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''users'' AND column_name = ''is_deleted'') THEN
        ALTER TABLE users ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END ';

-- Add attachment columns if they don't exist
DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''messages'' AND column_name = ''attachment_url'') THEN
        ALTER TABLE messages ADD COLUMN attachment_url TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''messages'' AND column_name = ''attachment_type'') THEN
        ALTER TABLE messages ADD COLUMN attachment_type VARCHAR(100);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''messages'' AND column_name = ''original_name'') THEN
        ALTER TABLE messages ADD COLUMN original_name VARCHAR(500);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''messages'' AND column_name = ''pinned'') THEN
        ALTER TABLE messages ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''messages'' AND column_name = ''pinned_by'') THEN
        ALTER TABLE messages ADD COLUMN pinned_by VARCHAR(50);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''messages'' AND column_name = ''pinned_at'') THEN
        ALTER TABLE messages ADD COLUMN pinned_at TIMESTAMP WITH TIME ZONE;
    END IF;
END ';

-- ============================================================
-- Performance Indexes
-- ============================================================

-- Primary query pattern: fetch messages for a conversation ordered by time
CREATE INDEX IF NOT EXISTS idx_messages_conversation_created
    ON messages (conversation_id, created_at DESC);

-- Efficient expired message cleanup via scheduled task or query filter
CREATE INDEX IF NOT EXISTS idx_messages_expires_at
    ON messages (expires_at)
    WHERE expires_at IS NOT NULL;

-- Fast user lookup by username (login / search)
CREATE INDEX IF NOT EXISTS idx_users_username
    ON users (username);

-- Prefix search on username (e.g. LIKE 'abc%') — varchar_pattern_ops
-- enables efficient left-anchored pattern matching via B-tree
CREATE INDEX IF NOT EXISTS idx_users_username_pattern
    ON users (username varchar_pattern_ops);

-- Fast member lookup for a conversation
CREATE INDEX IF NOT EXISTS idx_conv_members_user
    ON conversation_members (user_id);

-- Referral code lookup for group join
CREATE INDEX IF NOT EXISTS idx_conversations_referral
    ON conversations (referral_code)
    WHERE referral_code IS NOT NULL;

-- Pinned conversations lookup per user
CREATE INDEX IF NOT EXISTS idx_pinned_conversations_user
    ON pinned_conversations (user_id);

-- Pinned messages lookup per conversation
CREATE INDEX IF NOT EXISTS idx_messages_pinned
    ON messages (conversation_id, pinned)
    WHERE pinned = TRUE;

-- Connection requests: fast lookup of pending requests by receiver
CREATE INDEX IF NOT EXISTS idx_connection_requests_receiver_status
    ON connection_requests (receiver_id, status)
    WHERE status = 'PENDING';

-- Connection requests: fast lookup of sent requests by sender
CREATE INDEX IF NOT EXISTS idx_connection_requests_sender
    ON connection_requests (sender_id);

-- ============================================================
-- E2EE (End-to-End Encryption) Tables
-- ============================================================

-- -------------------- User Key Bundles --------------------
-- Stores each user's ECDH public key (private key never leaves the client).
CREATE TABLE IF NOT EXISTS user_key_bundles (
    user_id         UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    public_key      TEXT NOT NULL,                              -- Base64-encoded ECDH P-256 public key (JWK format)
    key_algorithm   VARCHAR(50) NOT NULL DEFAULT 'ECDH-P256',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- -------------------- Conversation Key Bundles --------------------
-- Per-member encrypted copy of the group AES-256-GCM symmetric key.
-- For PRIVATE chats this table is unused (keys are derived via ECDH).
CREATE TABLE IF NOT EXISTS conversation_key_bundles (
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
    encrypted_key   TEXT NOT NULL,                              -- Base64-encoded AES key, wrapped with user's public key
    key_version     INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (conversation_id, user_id)
);

-- ============================================================
-- E2EE Schema migrations for existing databases
-- ============================================================

-- Add E2EE columns to messages table if they don't exist
DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''messages'' AND column_name = ''encrypted'') THEN
        ALTER TABLE messages ADD COLUMN encrypted BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''messages'' AND column_name = ''iv'') THEN
        ALTER TABLE messages ADD COLUMN iv TEXT;
    END IF;
END ';

-- ============================================================
-- Message Edit & Delete Schema migrations
-- ============================================================

-- Add edit tracking columns to messages
DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''messages'' AND column_name = ''edited'') THEN
        ALTER TABLE messages ADD COLUMN edited BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''messages'' AND column_name = ''edited_at'') THEN
        ALTER TABLE messages ADD COLUMN edited_at TIMESTAMP WITH TIME ZONE;
    END IF;
END ';

-- Add soft-delete columns to messages
DO ' BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''messages'' AND column_name = ''deleted'') THEN
        ALTER TABLE messages ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''messages'' AND column_name = ''deleted_at'') THEN
        ALTER TABLE messages ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ''messages'' AND column_name = ''deleted_by'') THEN
        ALTER TABLE messages ADD COLUMN deleted_by VARCHAR(50);
    END IF;
END ';

-- -------------------- User Deleted Messages ("Delete for Me") --------------------
-- Tracks which messages each user has individually hidden from their view.
-- The message is NOT deleted for other participants.
CREATE TABLE IF NOT EXISTS user_deleted_messages (
    user_id     UUID NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    message_id  UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    deleted_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (user_id, message_id)
);

-- -------------------- Message Reactions --------------------
-- Stores individual emoji reactions on messages.
-- Each user can react with a specific emoji only once per message (toggle on/off).
CREATE TABLE IF NOT EXISTS message_reactions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id  UUID         NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id     UUID         NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    emoji       VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_message_reactions_message_user_emoji UNIQUE (message_id, user_id, emoji)
);

-- ============================================================
-- E2EE Performance Indexes
-- ============================================================

-- Fast key bundle lookup by user
CREATE INDEX IF NOT EXISTS idx_user_key_bundles_user
    ON user_key_bundles (user_id);

-- Fast key bundle lookup per conversation
CREATE INDEX IF NOT EXISTS idx_conversation_key_bundles_conversation
    ON conversation_key_bundles (conversation_id);

-- Fast lookup of a specific user's key bundle in a conversation
CREATE INDEX IF NOT EXISTS idx_conversation_key_bundles_user
    ON conversation_key_bundles (user_id);

-- ============================================================
-- Message Edit, Delete & Reaction Indexes
-- ============================================================

-- Fast "delete for me" lookup per user
CREATE INDEX IF NOT EXISTS idx_user_deleted_messages_user
    ON user_deleted_messages (user_id);

-- Fast reaction lookup per message
CREATE INDEX IF NOT EXISTS idx_message_reactions_message
    ON message_reactions (message_id);

-- Fast reaction lookup per user (for "my reactions" queries)
CREATE INDEX IF NOT EXISTS idx_message_reactions_user
    ON message_reactions (user_id);

-- ============================================================
-- OAuth Provider Indexes
-- ============================================================

-- Fast Google provider lookup by provider_id
CREATE INDEX IF NOT EXISTS idx_users_provider
    ON users (auth_provider, provider_id) WHERE provider_id IS NOT NULL;
