package com.mel.cb.memory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserFactRepository extends JpaRepository<UserFact, Long> {

  List<UserFact> findByUserId(String userId);

  /**
   * Sets a fact's embedding after the fact itself has already been saved (RAG follow-up, docs/
   * PLAN.md) -- a native query since Hibernate has no built-in mapping for pgvector's {@code vector}
   * type, and adding one (a custom {@code UserType}, or Spring AI's own {@code VectorStore}
   * abstraction) is more than this needs for two narrowly-scoped queries. {@code @Transactional} is
   * required here unlike plain {@code save()} -- {@code SimpleJpaRepository}'s own class-level
   * transaction only covers the CRUD methods it implements, not a custom {@code @Modifying} query
   * declared on this interface.
   */
  @Transactional
  @Modifying
  @Query(value = "UPDATE user_facts SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
  void updateEmbedding(@Param("id") Long id, @Param("embedding") String embeddingLiteral);

  /**
   * Top-K facts for a user, ordered by cosine distance to {@code queryEmbeddingLiteral} (pgvector's
   * {@code <=>} operator). Explicit column list, not {@code SELECT *} -- an unmapped {@code embedding}
   * column in the result set would confuse {@link UserFact}'s entity mapping, which has no matching
   * field. No ANN index backs this: every call is scoped by {@code user_id} first (the existing
   * {@code idx_user_facts_user_id} B-tree narrows rows before the vector sort runs), and per-user
   * fact counts are small and only grow on the already-rare eviction path -- see
   * {@code V2__add_embedding_to_user_facts.sql} for the full reasoning.
   */
  @Query(value = """
      SELECT id, user_id, fact, source_conversation_id, created_at, updated_at
      FROM user_facts
      WHERE user_id = :userId AND embedding IS NOT NULL
      ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
      LIMIT :topK
      """, nativeQuery = true)
  List<UserFact> findMostSimilar(@Param("userId") String userId,
      @Param("queryEmbedding") String queryEmbeddingLiteral, @Param("topK") int topK);

}
