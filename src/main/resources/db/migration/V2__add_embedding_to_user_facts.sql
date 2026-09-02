CREATE EXTENSION IF NOT EXISTS vector;

-- Width must match chatbot.embedding.dimensions (FactEmbeddingProperties) exactly -- confirmed
-- live (a real request with a real GEMINI_API_KEY) that Gemini's OpenAI-compatible /embeddings
-- endpoint honors the `dimensions` request param for gemini-embedding-001 and returns exactly a
-- 768-length vector, not the model's larger native size.
ALTER TABLE user_facts ADD COLUMN embedding vector(768);

-- No ANN index: every query is scoped by user_id first (idx_user_facts_user_id already exists)
-- and per-user fact counts are small -- add ivfflat/hnsw later only if that stops being true.
