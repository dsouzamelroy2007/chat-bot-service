package com.mel.cb.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mel.cb.embedding.FactEmbeddingService;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Exercises {@link UserFactRepository} against a real Postgres, schema created by Flyway's actual
 * migration (not Hibernate {@code ddl-auto}, which stays {@code none} in production) -- proves the
 * {@code (user_id, fact)} unique constraint from V1__create_user_facts.sql is enforced, since
 * {@link ConversationMemoryService} relies on catching {@link DataIntegrityViolationException} to
 * skip an already-recorded fact.
 * <p>
 * Wires just enough Spring (JPA repositories + a Flyway-migrated datasource) by hand rather than a
 * {@code @DataJpaTest} slice -- Spring Boot 4.1 moved that annotation to a dedicated test-starter
 * module not (yet) on this project's dependency list, and the app's other Testcontainers-backed
 * tests ({@code RedisQuotaTrackerTest}) already favor this kind of minimal, no-full-context setup.
 * The container is started in a static initializer (not {@code @Testcontainers}/{@code @Container})
 * so it's guaranteed running before the {@code @Bean} methods below reference it during context
 * refresh, without depending on JUnit-extension-vs-Spring-context ordering.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UserFactRepositoryTest.TestConfig.class)
@Transactional
class UserFactRepositoryTest {

  // pgvector/pgvector:pg16 -- same Postgres 16 base as plain
  // postgres:16-alpine but with the pgvector extension preinstalled, required by
  // V2__add_embedding_to_user_facts.sql's CREATE EXTENSION vector.
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")
      .asCompatibleSubstituteFor("postgres"));

  static {
    POSTGRES.start();
  }

  @Configuration
  @EnableJpaRepositories(basePackageClasses = UserFactRepository.class)
  @EnableTransactionManagement
  static class TestConfig {

    @Bean
    DataSource dataSource() {
      HikariDataSource dataSource = new HikariDataSource();
      dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
      dataSource.setUsername(POSTGRES.getUsername());
      dataSource.setPassword(POSTGRES.getPassword());
      Flyway.configure().dataSource(dataSource).load().migrate();
      return dataSource;
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
      LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
      emf.setDataSource(dataSource);
      emf.setPackagesToScan(UserFact.class.getPackageName());
      emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      return emf;
    }

    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
      return new JpaTransactionManager(entityManagerFactory);
    }

  }

  @Autowired
  private UserFactRepository repository;

  @Test
  void savingTheSameFactTwiceForOneUserViolatesTheUniqueConstraint() {
    Instant now = Instant.now();
    repository.saveAndFlush(new UserFact(null, "user-1", "likes coffee", "conv-1", now, now));

    UserFact duplicate = new UserFact(null, "user-1", "likes coffee", "conv-2", now, now);
    assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(duplicate));
  }

  @Test
  void sameFactTextIsAllowedForDifferentUsers() {
    Instant now = Instant.now();
    repository.saveAndFlush(new UserFact(null, "user-1", "likes coffee", "conv-1", now, now));
    repository.saveAndFlush(new UserFact(null, "user-2", "likes coffee", "conv-2", now, now));

    assertEquals(1, repository.findByUserId("user-1").size());
    assertEquals(1, repository.findByUserId("user-2").size());
  }

  @Test
  void findByUserIdReturnsOnlyThatUsersFacts() {
    Instant now = Instant.now();
    repository.saveAndFlush(new UserFact(null, "user-1", "likes coffee", "conv-1", now, now));
    repository.saveAndFlush(new UserFact(null, "user-1", "based in Amsterdam", "conv-1", now, now));
    repository.saveAndFlush(new UserFact(null, "user-2", "likes tea", "conv-2", now, now));

    List<UserFact> facts = repository.findByUserId("user-1");

    assertEquals(2, facts.size());
  }

  @Test
  void findMostSimilarOrdersByCosineDistanceClosestFirst() {
    Instant now = Instant.now();
    UserFact closeToQuery = repository.saveAndFlush(new UserFact(null, "user-1", "likes coffee", "conv-1", now, now));
    UserFact farFromQuery = repository.saveAndFlush(new UserFact(null, "user-1", "based in Amsterdam", "conv-1", now, now));
    repository.updateEmbedding(closeToQuery.getId(), literal(vector(0, 0.9f, 1, 0.1f)));
    repository.updateEmbedding(farFromQuery.getId(), literal(vector(0, 0.1f, 1, 0.9f)));

    List<UserFact> results = repository.findMostSimilar("user-1", literal(vector(0, 1f, 1, 0f)), 10);

    assertEquals(List.of("likes coffee", "based in Amsterdam"), results.stream().map(UserFact::getFact).toList());
  }

  @Test
  void findMostSimilarExcludesFactsWithNoEmbedding() {
    Instant now = Instant.now();
    UserFact embedded = repository.saveAndFlush(new UserFact(null, "user-1", "likes coffee", "conv-1", now, now));
    repository.saveAndFlush(new UserFact(null, "user-1", "no embedding yet", "conv-1", now, now));
    repository.updateEmbedding(embedded.getId(), literal(vector(0, 1f)));

    List<UserFact> results = repository.findMostSimilar("user-1", literal(vector(0, 1f)), 10);

    assertEquals(List.of("likes coffee"), results.stream().map(UserFact::getFact).toList());
  }

  @Test
  void findMostSimilarIsScopedToTheGivenUser() {
    Instant now = Instant.now();
    UserFact user1Fact = repository.saveAndFlush(new UserFact(null, "user-1", "likes coffee", "conv-1", now, now));
    UserFact user2Fact = repository.saveAndFlush(new UserFact(null, "user-2", "likes tea", "conv-2", now, now));
    repository.updateEmbedding(user1Fact.getId(), literal(vector(0, 1f)));
    repository.updateEmbedding(user2Fact.getId(), literal(vector(0, 1f)));

    List<UserFact> results = repository.findMostSimilar("user-1", literal(vector(0, 1f)), 10);

    assertEquals(List.of("likes coffee"), results.stream().map(UserFact::getFact).toList());
  }

  @Test
  void findMostSimilarLimitsToTopK() {
    Instant now = Instant.now();
    for (int i = 0; i < 5; i++) {
      UserFact fact = repository.saveAndFlush(new UserFact(null, "user-1", "fact " + i, "conv-1", now, now));
      repository.updateEmbedding(fact.getId(), literal(vector(0, 1f)));
    }

    List<UserFact> results = repository.findMostSimilar("user-1", literal(vector(0, 1f)), 3);

    assertEquals(3, results.size());
  }

  /** 768-dim vector (matches V2's {@code vector(768)} width) with the given index/value pairs set,
   * all other dimensions zero. */
  private static float[] vector(Object... indexValuePairs) {
    float[] v = new float[768];
    for (int i = 0; i < indexValuePairs.length; i += 2) {
      v[(int) indexValuePairs[i]] = (float) indexValuePairs[i + 1];
    }
    return v;
  }

  private static String literal(float[] vector) {
    return FactEmbeddingService.toPgVectorLiteral(vector);
  }

}
