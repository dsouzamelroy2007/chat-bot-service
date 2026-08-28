CREATE TABLE user_facts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    fact TEXT NOT NULL,
    source_conversation_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_facts_user_fact UNIQUE (user_id, fact)
);

CREATE INDEX idx_user_facts_user_id ON user_facts (user_id);
