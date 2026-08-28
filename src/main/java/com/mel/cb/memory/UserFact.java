package com.mel.cb.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A durable fact about a user, extracted from a conversation once it's summarized/evicted (see
 * {@link ConversationMemoryService}). Unlike the rest of conversation memory, this outlives the
 * Redis session TTL -- it's what lets the bot remember a user across separate sessions.
 */
@Entity
@Table(name = "user_facts", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "fact"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserFact {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String fact;

  @Column(name = "source_conversation_id")
  private String sourceConversationId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

}
