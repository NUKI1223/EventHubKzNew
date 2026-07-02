# Admin Audit Log + User Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A permanent, filterable audit trail of all business actions surfaced in the admin panel, plus admin hard-deletion of users with a full cross-service cascade.

**Architecture:** New `audit-service` (port 8091) consumes every domain topic on the existing Kafka bus and persists immutable `audit_log` rows (idempotent by `topic:partition:offset`). User deletion extends the existing `DELETE /api/users/{id}`: user-service wipes the profile, publishes `user.deleted`, and every service cleans its own data via choreography. Two new admin tabs (Журнал, Пользователи) in the React frontend.

**Tech Stack:** Java 21, Spring Boot 3.4 (parent pom pins versions), Spring Kafka, Spring Data JPA + Specifications, PostgreSQL, Eureka, Spring Cloud Gateway, React 19 + react-i18next, JUnit 5 + Mockito + Testcontainers.

**Spec:** `docs/superpowers/specs/2026-07-02-admin-audit-log-design.md`

## Global Constraints

- Run Maven **from the repo root** with `-pl <module>`; never from inside a module dir (produces non-bootable JARs — see memory `feedback_docker_rebuild`).
- Commit messages: lowercase conventional commits (`feat(scope): ...`); **no Co-Authored-By / attribution lines.**
- Docker redeploy recipe per service: `mvn clean package -DskipTests -pl <svc>` → `docker compose build <svc>` → `docker rm -f <container(s)>` → `docker compose up -d <svc>`.
- Every user-facing frontend string goes through react-i18next with **both `ru.json` and `kk.json`** entries.
- New service port: **8091** (8090 is taken by Kafka UI host mapping). Kafka consumer group: `audit-service-group`.
- Common event DTOs live in `org.ngcvfb.eventhubkz.common.events`, extend `BaseEvent`, use `@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)` + static `create(...)` calling `initBase()`.
- Audit rows are immutable: no update/delete endpoints, ever.
- **Spec deviation (deliberate):** self-deletion via `DELETE /api/users/{id}` stays allowed (it is the platform's legal "right to deletion" path and already works today); the admin-specific guard is only "cannot delete another ADMIN". The admin UI hides your own row and ADMIN rows.

---

### Task 1: Common event DTOs — `UserDeletedEvent`, `EventRequestCreatedEvent`

**Files:**
- Create: `eventhubkz-common/src/main/java/org/ngcvfb/eventhubkz/common/events/UserDeletedEvent.java`
- Create: `eventhubkz-common/src/main/java/org/ngcvfb/eventhubkz/common/events/EventRequestCreatedEvent.java`

**Interfaces:**
- Produces: `UserDeletedEvent.create(Long userId, String username, String email, Long deletedBy, String reason)`; `EventRequestCreatedEvent.create(Long requestId, Long requesterId, String requesterEmail, String eventTitle)`. Getters follow Lombok (`getUserId()`, `getDeletedBy()`, `getRequestId()`, …). Used by Tasks 5, 7, 8, 9, 10, 11, 12.

- [ ] **Step 1: Write `UserDeletedEvent`**

```java
package org.ngcvfb.eventhubkz.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Публикуется user-service при жёстком удалении пользователя (админом или самим
 * пользователем). Потребители чистят свои данные; audit-service сохраняет запись навсегда.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserDeletedEvent extends BaseEvent {
    private Long userId;
    private String username;
    private String email;
    private Long deletedBy;
    private String reason;

    public static UserDeletedEvent create(Long userId, String username, String email,
                                          Long deletedBy, String reason) {
        UserDeletedEvent event = UserDeletedEvent.builder()
                .userId(userId)
                .username(username)
                .email(email)
                .deletedBy(deletedBy)
                .reason(reason)
                .build();
        event.initBase();
        return event;
    }
}
```

- [ ] **Step 2: Write `EventRequestCreatedEvent`**

```java
package org.ngcvfb.eventhubkz.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Публикуется event-service при подаче организатором заявки на мероприятие.
 * Потребитель — audit-service (REQUEST_CREATED).
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventRequestCreatedEvent extends BaseEvent {
    private Long requestId;
    private Long requesterId;
    private String requesterEmail;
    private String eventTitle;

    public static EventRequestCreatedEvent create(Long requestId, Long requesterId,
                                                  String requesterEmail, String eventTitle) {
        EventRequestCreatedEvent event = EventRequestCreatedEvent.builder()
                .requestId(requestId)
                .requesterId(requesterId)
                .requesterEmail(requesterEmail)
                .eventTitle(eventTitle)
                .build();
        event.initBase();
        return event;
    }
}
```

- [ ] **Step 3: Build the common module**

Run: `mvn -q clean install -DskipTests -pl eventhubkz-common` (from repo root)
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add eventhubkz-common/src/main/java/org/ngcvfb/eventhubkz/common/events/UserDeletedEvent.java eventhubkz-common/src/main/java/org/ngcvfb/eventhubkz/common/events/EventRequestCreatedEvent.java
git commit -m "feat(common): add user.deleted and event-request.created event DTOs"
```

---

### Task 2: audit-service module skeleton

**Files:**
- Create: `audit-service/pom.xml`
- Create: `audit-service/src/main/java/org/ngcvfb/auditservice/AuditServiceApplication.java`
- Create: `audit-service/src/main/resources/application.yml`
- Create: `audit-service/Dockerfile`
- Modify: `pom.xml` (root — add module after line 25 `<module>registration-service</module>`)

**Interfaces:**
- Produces: bootable Spring Boot app `audit-service` on port 8091, Eureka-registered, DB `audit_db`, Kafka consumer group `audit-service-group`. Tasks 3–6 add code inside this module.

- [ ] **Step 1: Create `audit-service/pom.xml`** (mirror of registration-service minus Feign/Redis/cache)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.ngcvfb</groupId>
        <artifactId>eventhubkz-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>audit-service</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.ngcvfb</groupId>
            <artifactId>eventhubkz-common</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-brave</artifactId>
        </dependency>
        <dependency>
            <groupId>io.zipkin.reporter2</groupId>
            <artifactId>zipkin-reporter-brave</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Note: check `registration-service/pom.xml` for whether Testcontainers versions come from the parent `dependencyManagement`; if `event-service/pom.xml` declares explicit versions for `org.testcontainers:*`, copy those `<version>` tags verbatim.

- [ ] **Step 2: Create the application class**

```java
package org.ngcvfb.auditservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuditServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
```

- [ ] **Step 3: Create `application.yml`**

```yaml
server:
  port: 8091

spring:
  application:
    name: audit-service

  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5437/audit_db}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: audit-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: org.ngcvfb.eventhubkz.common.events

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
    fetch-registry: true
    register-with-eureka: true
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${random.uuid}

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_URL:http://localhost:9411/api/v2/spans}
  tracing:
    sampling:
      probability: 0.1
```

Note: before finalizing, diff against `registration-service/src/main/resources/application.yml` `management:` block and mirror whatever keys it actually has (the `zipkin`/`tracing` structure must match the working services).

- [ ] **Step 4: Create `audit-service/Dockerfile`**

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8091

ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 5: Register the module in the root `pom.xml`**

In `pom.xml`, after `<module>registration-service</module>` add:

```xml
        <module>audit-service</module>
```

- [ ] **Step 6: Build**

Run: `mvn -q clean package -DskipTests -pl audit-service` (repo root)
Expected: BUILD SUCCESS, `audit-service/target/audit-service-1.0.0-SNAPSHOT.jar` exists

- [ ] **Step 7: Commit**

```bash
git add pom.xml audit-service/
git commit -m "feat(audit): scaffold audit-service module (port 8091)"
```

---

### Task 3: `AuditLog` entity, `AuditAction` enum, repository with filtered search

**Files:**
- Create: `audit-service/src/main/java/org/ngcvfb/auditservice/model/AuditAction.java`
- Create: `audit-service/src/main/java/org/ngcvfb/auditservice/model/AuditTargetType.java`
- Create: `audit-service/src/main/java/org/ngcvfb/auditservice/model/AuditLog.java`
- Create: `audit-service/src/main/java/org/ngcvfb/auditservice/repository/AuditLogRepository.java`
- Create: `audit-service/src/main/java/org/ngcvfb/auditservice/repository/AuditLogSpecs.java`
- Test: `audit-service/src/test/java/org/ngcvfb/auditservice/repository/AuditLogRepositoryIT.java`
- Test: `audit-service/src/test/java/org/ngcvfb/auditservice/PostgresTestcontainer.java`

**Interfaces:**
- Produces: `AuditLog` (builder, fields below), `AuditAction` (10 values), `AuditTargetType` (`USER|EVENT|REQUEST`), `AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog>` with `boolean existsByDedupKey(String dedupKey)`; `AuditLogSpecs.filter(AuditAction, Long actorId, AuditTargetType, LocalDateTime from, LocalDateTime to)` → `Specification<AuditLog>`. Used by Tasks 4, 6.

- [ ] **Step 1: Copy the Testcontainers base** — copy `event-service/src/test/java/org/ngcvfb/eventservice/PostgresTestcontainer.java` to `audit-service/src/test/java/org/ngcvfb/auditservice/PostgresTestcontainer.java`, changing only the package to `org.ngcvfb.auditservice`. Read the source file first and keep its container version/config identical.

- [ ] **Step 2: Write the failing repository IT**

```java
package org.ngcvfb.auditservice.repository;

import org.junit.jupiter.api.Test;
import org.ngcvfb.auditservice.PostgresTestcontainer;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditLog;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditLogRepositoryIT extends PostgresTestcontainer {

    @Autowired AuditLogRepository repository;

    private AuditLog row(AuditAction action, Long actorId, LocalDateTime at, String dedup) {
        return AuditLog.builder()
                .action(action).actorId(actorId).actorName("u" + actorId)
                .targetType(AuditTargetType.EVENT).targetId(1L).targetLabel("Event #1")
                .occurredAt(at).dedupKey(dedup)
                .build();
    }

    @Test
    void filtersByActionActorAndDateRange() {
        LocalDateTime now = LocalDateTime.now();
        repository.save(row(AuditAction.EVENT_LIKED, 1L, now.minusDays(2), "t:0:1"));
        repository.save(row(AuditAction.EVENT_LIKED, 2L, now, "t:0:2"));
        repository.save(row(AuditAction.USER_REGISTERED, 1L, now, "t:0:3"));

        var page = repository.findAll(
                AuditLogSpecs.filter(AuditAction.EVENT_LIKED, 2L, null, now.minusDays(1), null),
                PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getDedupKey()).isEqualTo("t:0:2");
    }

    @Test
    void existsByDedupKeyDetectsDuplicates() {
        repository.save(row(AuditAction.EVENT_CREATED, 1L, LocalDateTime.now(), "topic:0:42"));
        assertThat(repository.existsByDedupKey("topic:0:42")).isTrue();
        assertThat(repository.existsByDedupKey("topic:0:43")).isFalse();
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `mvn -q test -pl audit-service -Dtest=AuditLogRepositoryIT`
Expected: COMPILATION ERROR (`AuditLog` does not exist)

- [ ] **Step 4: Write the model + repository + specs**

`AuditAction.java`:

```java
package org.ngcvfb.auditservice.model;

public enum AuditAction {
    USER_REGISTERED, USER_DELETED,
    EVENT_CREATED, EVENT_UPDATED, EVENT_DELETED,
    EVENT_LIKED, EVENT_RSVP,
    REQUEST_CREATED, REQUEST_APPROVED, REQUEST_REJECTED
}
```

`AuditTargetType.java`:

```java
package org.ngcvfb.auditservice.model;

public enum AuditTargetType { USER, EVENT, REQUEST }
```

`AuditLog.java`:

```java
package org.ngcvfb.auditservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Одна строка аудита. Строки никогда не изменяются и не удаляются —
 * в том числе записи об удалённых пользователях.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_occurred_at", columnList = "occurredAt"),
        @Index(name = "idx_audit_actor", columnList = "actorId"),
        @Index(name = "idx_audit_action", columnList = "action")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    private Long actorId;
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private AuditTargetType targetType;

    private Long targetId;
    private String targetLabel;

    @Column(length = 2000)
    private String details;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    // topic:partition:offset — защита от повторной доставки Kafka
    @Column(nullable = false, unique = true, length = 128)
    private String dedupKey;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

`AuditLogRepository.java`:

```java
package org.ngcvfb.auditservice.repository;

import org.ngcvfb.auditservice.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    boolean existsByDedupKey(String dedupKey);
}
```

`AuditLogSpecs.java`:

```java
package org.ngcvfb.auditservice.repository;

import jakarta.persistence.criteria.Predicate;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditLog;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class AuditLogSpecs {

    private AuditLogSpecs() {}

    public static Specification<AuditLog> filter(AuditAction action, Long actorId,
                                                 AuditTargetType targetType,
                                                 LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null)     predicates.add(cb.equal(root.get("action"), action));
            if (actorId != null)    predicates.add(cb.equal(root.get("actorId"), actorId));
            if (targetType != null) predicates.add(cb.equal(root.get("targetType"), targetType));
            if (from != null)       predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null)         predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q test -pl audit-service -Dtest=AuditLogRepositoryIT`
Expected: `Tests run: 2, Failures: 0` (needs Docker running for Testcontainers)

- [ ] **Step 6: Commit**

```bash
git add audit-service/src
git commit -m "feat(audit): auditlog entity, repository and filter specifications"
```

---

### Task 4: `AuditRecordService` with dedup

**Files:**
- Create: `audit-service/src/main/java/org/ngcvfb/auditservice/service/AuditRecordService.java`
- Test: `audit-service/src/test/java/org/ngcvfb/auditservice/service/AuditRecordServiceTest.java`

**Interfaces:**
- Consumes: `AuditLogRepository`, `AuditLogSpecs` (Task 3).
- Produces: `void record(AuditAction action, Long actorId, String actorName, AuditTargetType targetType, Long targetId, String targetLabel, String details, LocalDateTime occurredAt, String dedupKey)` and `Page<AuditLog> search(AuditAction, Long, AuditTargetType, LocalDateTime, LocalDateTime, Pageable)`. Used by Tasks 5, 6.

- [ ] **Step 1: Write the failing test**

```java
package org.ngcvfb.auditservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditLog;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.ngcvfb.auditservice.repository.AuditLogRepository;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditRecordServiceTest {

    @Mock AuditLogRepository repository;
    @InjectMocks AuditRecordService service;

    @Test
    void recordsNewEntry() {
        when(repository.existsByDedupKey("user.registered:0:5")).thenReturn(false);

        service.record(AuditAction.USER_REGISTERED, 1L, "aidar",
                AuditTargetType.USER, 1L, "aidar", null,
                LocalDateTime.now(), "user.registered:0:5");

        verify(repository).save(any(AuditLog.class));
    }

    @Test
    void skipsDuplicateDelivery() {
        when(repository.existsByDedupKey("user.registered:0:5")).thenReturn(true);

        service.record(AuditAction.USER_REGISTERED, 1L, "aidar",
                AuditTargetType.USER, 1L, "aidar", null,
                LocalDateTime.now(), "user.registered:0:5");

        verify(repository, never()).save(any());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -pl audit-service -Dtest=AuditRecordServiceTest`
Expected: COMPILATION ERROR (`AuditRecordService` does not exist)

- [ ] **Step 3: Implement**

```java
package org.ngcvfb.auditservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditLog;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.ngcvfb.auditservice.repository.AuditLogRepository;
import org.ngcvfb.auditservice.repository.AuditLogSpecs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditRecordService {

    private final AuditLogRepository repository;

    @Transactional
    public void record(AuditAction action, Long actorId, String actorName,
                       AuditTargetType targetType, Long targetId, String targetLabel,
                       String details, LocalDateTime occurredAt, String dedupKey) {
        if (repository.existsByDedupKey(dedupKey)) {
            log.info("Skipped duplicate audit record: {}", dedupKey);
            return;
        }
        repository.save(AuditLog.builder()
                .action(action)
                .actorId(actorId).actorName(actorName)
                .targetType(targetType).targetId(targetId).targetLabel(targetLabel)
                .details(details)
                .occurredAt(occurredAt != null ? occurredAt : LocalDateTime.now())
                .dedupKey(dedupKey)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(AuditAction action, Long actorId, AuditTargetType targetType,
                                 LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return repository.findAll(
                AuditLogSpecs.filter(action, actorId, targetType, from, to), pageable);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -pl audit-service -Dtest=AuditRecordServiceTest`
Expected: `Tests run: 2, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add audit-service/src
git commit -m "feat(audit): audit record service with topic-offset dedup"
```

---

### Task 5: `AuditKafkaConsumer` — nine topics → audit rows

**Files:**
- Create: `audit-service/src/main/java/org/ngcvfb/auditservice/kafka/AuditKafkaConsumer.java`
- Test: `audit-service/src/test/java/org/ngcvfb/auditservice/kafka/AuditKafkaConsumerTest.java`

**Interfaces:**
- Consumes: `AuditRecordService.record(...)` (Task 4); common event DTOs (existing + Task 1).
- Produces: Kafka listeners for `user.registered`, `user.deleted`, `event.created`, `event.updated`, `event.deleted`, `event.liked`, `event.registered`, `event-request.created`, `event-request.reviewed`.

- [ ] **Step 1: Write the failing test** (representative mappings: liked, reviewed→two actions, deleted user)

```java
package org.ngcvfb.auditservice.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.ngcvfb.auditservice.service.AuditRecordService;
import org.ngcvfb.eventhubkz.common.events.EventLikedEvent;
import org.ngcvfb.eventhubkz.common.events.EventRequestReviewedEvent;
import org.ngcvfb.eventhubkz.common.events.UserDeletedEvent;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditKafkaConsumerTest {

    @Mock AuditRecordService recordService;
    @InjectMocks AuditKafkaConsumer consumer;

    @Test
    void mapsEventLiked() {
        EventLikedEvent e = EventLikedEvent.create(6L, 1L, "a@kz", "aidar", 2L, "o@kz", "DataFest");

        consumer.onEventLiked(e, "event.liked", 0, 17L);

        verify(recordService).record(eq(AuditAction.EVENT_LIKED), eq(1L), eq("aidar"),
                eq(AuditTargetType.EVENT), eq(6L), eq("DataFest"),
                isNull(), any(), eq("event.liked:0:17"));
    }

    @Test
    void mapsRequestReviewedApprovedAndRejected() {
        EventRequestReviewedEvent approved =
                EventRequestReviewedEvent.create(5L, 1L, "a@kz", "Meetup", true, null);
        EventRequestReviewedEvent rejected =
                EventRequestReviewedEvent.create(6L, 1L, "a@kz", "Spam", false, "спам");

        consumer.onRequestReviewed(approved, "event-request.reviewed", 0, 1L);
        consumer.onRequestReviewed(rejected, "event-request.reviewed", 0, 2L);

        verify(recordService).record(eq(AuditAction.REQUEST_APPROVED), isNull(), isNull(),
                eq(AuditTargetType.REQUEST), eq(5L), eq("Meetup"),
                isNull(), any(), eq("event-request.reviewed:0:1"));
        verify(recordService).record(eq(AuditAction.REQUEST_REJECTED), isNull(), isNull(),
                eq(AuditTargetType.REQUEST), eq(6L), eq("Spam"),
                eq("спам"), any(), eq("event-request.reviewed:0:2"));
    }

    @Test
    void mapsUserDeletedWithSnapshotInDetails() {
        UserDeletedEvent e = UserDeletedEvent.create(7L, "spammer", "s@kz", 2L, "спам-события");

        consumer.onUserDeleted(e, "user.deleted", 0, 3L);

        verify(recordService).record(eq(AuditAction.USER_DELETED), eq(2L), isNull(),
                eq(AuditTargetType.USER), eq(7L), eq("spammer"),
                eq("email=s@kz; reason=спам-события"), any(), eq("user.deleted:0:3"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -pl audit-service -Dtest=AuditKafkaConsumerTest`
Expected: COMPILATION ERROR

- [ ] **Step 3: Implement the consumer**

```java
package org.ngcvfb.auditservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.ngcvfb.auditservice.service.AuditRecordService;
import org.ngcvfb.eventhubkz.common.events.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditKafkaConsumer {

    private static final String GROUP = "audit-service-group";

    private final AuditRecordService recordService;

    private static String dedup(String topic, int partition, long offset) {
        return topic + ":" + partition + ":" + offset;
    }

    @KafkaListener(topics = "user.registered", groupId = GROUP)
    public void onUserRegistered(@Payload UserRegisteredEvent e,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.USER_REGISTERED, e.getUserId(), e.getUsername(),
                AuditTargetType.USER, e.getUserId(), e.getUsername(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "user.deleted", groupId = GROUP)
    public void onUserDeleted(@Payload UserDeletedEvent e,
                              @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                              @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                              @Header(KafkaHeaders.OFFSET) long offset) {
        String details = "email=" + e.getEmail() + "; reason=" + e.getReason();
        recordService.record(AuditAction.USER_DELETED, e.getDeletedBy(), null,
                AuditTargetType.USER, e.getUserId(), e.getUsername(),
                details, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event.created", groupId = GROUP)
    public void onEventCreated(@Payload EventCreatedEvent e,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                               @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.EVENT_CREATED, e.getOrganizerId(), e.getOrganizerEmail(),
                AuditTargetType.EVENT, e.getId(), e.getTitle(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event.updated", groupId = GROUP)
    public void onEventUpdated(@Payload EventUpdatedEvent e,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                               @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.EVENT_UPDATED, null, null,
                AuditTargetType.EVENT, e.getId(), e.getTitle(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event.deleted", groupId = GROUP)
    public void onEventDeleted(@Payload EventDeletedEvent e,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                               @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.EVENT_DELETED, null, null,
                AuditTargetType.EVENT, e.getId(), "event #" + e.getId(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event.liked", groupId = GROUP)
    public void onEventLiked(@Payload EventLikedEvent e,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                             @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                             @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.EVENT_LIKED, e.getUserId(), e.getUsername(),
                AuditTargetType.EVENT, e.getLikedEventId(), e.getEventTitle(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event.registered", groupId = GROUP)
    public void onEventRegistered(@Payload EventRegisteredEvent e,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.EVENT_RSVP, e.getUserId(), e.getUsername(),
                AuditTargetType.EVENT, e.getRegisteredEventId(), e.getEventTitle(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event-request.created", groupId = GROUP)
    public void onRequestCreated(@Payload EventRequestCreatedEvent e,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset) {
        recordService.record(AuditAction.REQUEST_CREATED, e.getRequesterId(), e.getRequesterEmail(),
                AuditTargetType.REQUEST, e.getRequestId(), e.getEventTitle(),
                null, e.getTimestamp(), dedup(topic, partition, offset));
    }

    @KafkaListener(topics = "event-request.reviewed", groupId = GROUP)
    public void onRequestReviewed(@Payload EventRequestReviewedEvent e,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset) {
        AuditAction action = e.isApproved() ? AuditAction.REQUEST_APPROVED
                                            : AuditAction.REQUEST_REJECTED;
        recordService.record(action, null, null,
                AuditTargetType.REQUEST, e.getRequestId(), e.getEventTitle(),
                e.getAdminComment(), e.getTimestamp(), dedup(topic, partition, offset));
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -pl audit-service`
Expected: all green

- [ ] **Step 5: Commit**

```bash
git add audit-service/src
git commit -m "feat(audit): kafka consumer mapping nine domain topics to audit rows"
```

---

### Task 6: `AuditController` — `GET /api/admin/audit`

**Files:**
- Create: `audit-service/src/main/java/org/ngcvfb/auditservice/dto/AuditLogDTO.java`
- Create: `audit-service/src/main/java/org/ngcvfb/auditservice/controller/AuditController.java`
- Test: `audit-service/src/test/java/org/ngcvfb/auditservice/controller/AuditControllerTest.java`

**Interfaces:**
- Consumes: `AuditRecordService.search(...)` (Task 4).
- Produces: `GET /api/admin/audit?page&size&action&actorId&targetType&from&to` → `Page<AuditLogDTO>`; 403 unless `X-User-Role: ADMIN`. `AuditLogDTO` fields: `id, action, actorId, actorName, targetType, targetId, targetLabel, details, occurredAt`. Used by gateway route (Task 13) and frontend (Task 14).

- [ ] **Step 1: Write the failing test** (standalone MockMvc)

```java
package org.ngcvfb.auditservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditLog;
import org.ngcvfb.auditservice.service.AuditRecordService;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock AuditRecordService recordService;
    @InjectMocks AuditController controller;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void forbiddenWithoutAdminRole() throws Exception {
        mvc.perform(get("/api/admin/audit").header("X-User-Role", "USER"))
           .andExpect(status().isForbidden());
    }

    @Test
    void returnsPageForAdmin() throws Exception {
        AuditLog row = AuditLog.builder()
                .id(1L).action(AuditAction.EVENT_LIKED).actorId(1L).actorName("aidar")
                .occurredAt(LocalDateTime.now()).dedupKey("t:0:1").build();
        when(recordService.search(eq(AuditAction.EVENT_LIKED), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row)));

        mvc.perform(get("/api/admin/audit")
                        .header("X-User-Role", "ADMIN")
                        .param("action", "EVENT_LIKED"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content[0].actorName").value("aidar"))
           .andExpect(jsonPath("$.content[0].action").value("EVENT_LIKED"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -pl audit-service -Dtest=AuditControllerTest`
Expected: COMPILATION ERROR

- [ ] **Step 3: Implement DTO + controller**

`AuditLogDTO.java`:

```java
package org.ngcvfb.auditservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ngcvfb.auditservice.model.AuditLog;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private Long id;
    private String action;
    private Long actorId;
    private String actorName;
    private String targetType;
    private Long targetId;
    private String targetLabel;
    private String details;
    private LocalDateTime occurredAt;

    public static AuditLogDTO from(AuditLog a) {
        return AuditLogDTO.builder()
                .id(a.getId())
                .action(a.getAction().name())
                .actorId(a.getActorId()).actorName(a.getActorName())
                .targetType(a.getTargetType() == null ? null : a.getTargetType().name())
                .targetId(a.getTargetId()).targetLabel(a.getTargetLabel())
                .details(a.getDetails())
                .occurredAt(a.getOccurredAt())
                .build();
    }
}
```

`AuditController.java`:

```java
package org.ngcvfb.auditservice.controller;

import lombok.RequiredArgsConstructor;
import org.ngcvfb.auditservice.dto.AuditLogDTO;
import org.ngcvfb.auditservice.model.AuditAction;
import org.ngcvfb.auditservice.model.AuditTargetType;
import org.ngcvfb.auditservice.service.AuditRecordService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditRecordService recordService;

    @GetMapping
    public ResponseEntity<Page<AuditLogDTO>> search(
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Только для администратора");
        }
        var pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        return ResponseEntity.ok(
                recordService.search(action, actorId, targetType, from, to, pageable)
                        .map(AuditLogDTO::from));
    }
}
```

- [ ] **Step 4: Run all audit-service tests**

Run: `mvn -q test -pl audit-service`
Expected: all green

- [ ] **Step 5: Commit**

```bash
git add audit-service/src
git commit -m "feat(audit): admin audit search endpoint with role guard"
```

---

### Task 7: event-service publishes `event-request.created`

**Files:**
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/kafka/EventKafkaProducer.java` (add topic + method)
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/service/EventRequestService.java:65-80` (`createRequest`)
- Test: `event-service/src/test/java/org/ngcvfb/eventservice/service/EventRequestServiceCreateTest.java`

**Interfaces:**
- Consumes: `EventRequestCreatedEvent.create(...)` (Task 1).
- Produces: message on `event-request.created` keyed by requestId (consumed by Task 5's listener).

- [ ] **Step 1: Write the failing test**

First read `EventRequestService`'s constructor dependencies (`event-service/.../service/EventRequestService.java` imports/fields) and mock ALL of them; the test below assumes `EventRequestRepository eventRequestRepository` and `EventKafkaProducer kafkaProducer` are among them — adjust `@Mock` list to the real constructor.

```java
package org.ngcvfb.eventservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.eventhubkz.common.events.EventRequestCreatedEvent;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.EventRequest;
import org.ngcvfb.eventservice.repository.EventRequestRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventRequestServiceCreateTest {

    @Mock EventRequestRepository eventRequestRepository;
    @Mock EventKafkaProducer kafkaProducer;
    // add remaining constructor deps as @Mock here after reading the class

    @InjectMocks EventRequestService service;

    @Test
    void createRequestPublishesAuditEvent() {
        EventRequest req = new EventRequest();
        req.setTitle("Meetup");
        req.setRequesterId(1L);
        req.setRequesterEmail("a@kz");
        req.setRegistrationType("NATIVE");
        when(eventRequestRepository.save(any(EventRequest.class))).thenAnswer(inv -> {
            EventRequest saved = inv.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        service.createRequest(req);

        verify(kafkaProducer).sendEventRequestCreated(any(EventRequestCreatedEvent.class));
    }
}
```

Note: `EventRequest` model — check the actual field names (`requesterEmail` vs `organizerEmail`) in `event-service/.../model/EventRequest.java` and adjust setters; the diploma report names `requester_id` + email fields, verify before writing.

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -pl event-service -Dtest=EventRequestServiceCreateTest`
Expected: COMPILATION ERROR (`sendEventRequestCreated` not defined)

- [ ] **Step 3: Implement**

In `EventKafkaProducer.java` add next to the other topic constants:

```java
    private static final String TOPIC_EVENT_REQUEST_CREATED = "event-request.created";
```

and next to the other send methods:

```java
    public void sendEventRequestCreated(EventRequestCreatedEvent event) {
        log.info("Sending event-request.created: requestId={}", event.getRequestId());
        kafkaTemplate.send(TOPIC_EVENT_REQUEST_CREATED,
                String.valueOf(event.getRequestId()), event);
    }
```

(import `org.ngcvfb.eventhubkz.common.events.EventRequestCreatedEvent`).

In `EventRequestService.createRequest(...)`, after `EventRequest saved = eventRequestRepository.save(request);` and the log line, add:

```java
        kafkaProducer.sendEventRequestCreated(EventRequestCreatedEvent.create(
                saved.getId(), saved.getRequesterId(), saved.getRequesterEmail(),
                saved.getTitle()));
```

If `EventRequestService` does not already inject `EventKafkaProducer`, add it as a `private final` field (constructor injection via Lombok `@RequiredArgsConstructor` is already the class style). Adjust the email getter to the real field name.

- [ ] **Step 4: Run tests**

Run: `mvn -q test -pl event-service`
Expected: all green (including pre-existing tests)

- [ ] **Step 5: Commit**

```bash
git add event-service/src
git commit -m "feat(event): publish event-request.created for audit trail"
```

---

### Task 8: user-service — deletion guards, snapshot, `user.deleted` publication, admin list

**Files:**
- Create: `user-service/src/main/java/org/ngcvfb/userservice/kafka/UserKafkaProducer.java`
- Modify: `user-service/src/main/java/org/ngcvfb/userservice/service/UserService.java:110-116` (`deleteUser`) + add `getAdminUserList`, `mapToAdminDTO`
- Modify: `user-service/src/main/java/org/ngcvfb/userservice/controller/UserController.java:69-77` (delete endpoint) + add `GET /api/users/admin/list`
- Modify: `user-service/src/main/resources/application.yml` (producer serializers)
- Test: `user-service/src/test/java/org/ngcvfb/userservice/service/UserServiceDeleteTest.java`

**Interfaces:**
- Consumes: `UserDeletedEvent.create(...)` (Task 1).
- Produces: `UserService.deleteUser(Long id, Long requesterId, String requesterRole, String reason)` (replaces the old 1-arg signature); `UserKafkaProducer.sendUserDeleted(UserDeletedEvent)`; `GET /api/users/admin/list?q=` → `List<UserDTO>` incl. `email`, `role` (admin only). Message on `user.deleted` consumed by Tasks 5, 9, 10, 11, 12; endpoint consumed by Task 15.

- [ ] **Step 1: Add producer serializer config** to `user-service/src/main/resources/application.yml` under the existing `spring.kafka:` key (it currently has only `bootstrap-servers`):

```yaml
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:29092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

- [ ] **Step 2: Write `UserKafkaProducer`**

```java
package org.ngcvfb.userservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.UserDeletedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserKafkaProducer {

    private static final String TOPIC_USER_DELETED = "user.deleted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendUserDeleted(UserDeletedEvent event) {
        log.info("Sending user.deleted: userId={}, deletedBy={}",
                event.getUserId(), event.getDeletedBy());
        kafkaTemplate.send(TOPIC_USER_DELETED, String.valueOf(event.getUserId()), event);
    }
}
```

- [ ] **Step 3: Write the failing tests**

```java
package org.ngcvfb.userservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.eventhubkz.common.events.UserDeletedEvent;
import org.ngcvfb.userservice.kafka.UserKafkaProducer;
import org.ngcvfb.userservice.model.Role;
import org.ngcvfb.userservice.model.User;
import org.ngcvfb.userservice.repository.UserRepository;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceDeleteTest {

    @Mock UserRepository userRepository;
    @Mock UserKafkaProducer kafkaProducer;
    // add remaining UserService constructor deps as @Mock after reading the class

    @InjectMocks UserService userService;

    private User user(long id, Role role) {
        return User.builder().id(id).username("u" + id).email("u" + id + "@kz")
                .password("x").role(role).build();
    }

    @Test
    void adminCannotDeleteAnotherAdmin() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.ADMIN)));

        assertThatThrownBy(() -> userService.deleteUser(2L, 1L, "ADMIN", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void deletePublishesSnapshotEvent() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.USER)));

        userService.deleteUser(2L, 1L, "ADMIN", "спам");

        verify(userRepository).deleteById(2L);
        verify(kafkaProducer).sendUserDeleted(any(UserDeletedEvent.class));
    }

    @Test
    void selfDeleteStillAllowed() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.USER)));

        userService.deleteUser(2L, 2L, "USER", null);

        verify(userRepository).deleteById(2L);
        verify(kafkaProducer).sendUserDeleted(any(UserDeletedEvent.class));
    }
}
```

- [ ] **Step 4: Run to verify it fails**

Run: `mvn -q test -pl user-service -Dtest=UserServiceDeleteTest`
Expected: COMPILATION ERROR (new signature does not exist)

- [ ] **Step 5: Implement in `UserService`** — replace the existing `deleteUser(Long id)`:

```java
    @Transactional
    public void deleteUser(Long id, Long requesterId, String requesterRole, String reason) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        boolean self = id.equals(requesterId);
        if (!self && user.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Нельзя удалить другого администратора");
        }
        String username = user.getUsername();
        String email = user.getEmail();
        userRepository.deleteById(id);
        kafkaProducer.sendUserDeleted(
                UserDeletedEvent.create(id, username, email, requesterId, reason));
        log.info("User deleted: {} by {} (reason: {})", id, requesterId, reason);
    }
```

Add `private final UserKafkaProducer kafkaProducer;` to the fields (class uses `@RequiredArgsConstructor`). Add imports: `org.ngcvfb.eventhubkz.common.events.UserDeletedEvent`, `org.ngcvfb.userservice.kafka.UserKafkaProducer`, `org.springframework.http.HttpStatus`, `org.springframework.web.server.ResponseStatusException`, `org.springframework.transaction.annotation.Transactional`, `org.ngcvfb.userservice.model.Role`.

Add the admin list methods:

```java
    // Админский список: email и роль включены — не использовать в публичных ответах.
    public List<UserDTO> getAdminUserList(String q) {
        List<User> users = (q == null || q.isBlank())
                ? userRepository.findAll()
                : userRepository.findAll().stream()
                    .filter(u -> u.getUsername().toLowerCase().contains(q.toLowerCase())
                              || u.getEmail().toLowerCase().contains(q.toLowerCase()))
                    .toList();
        return users.stream().map(this::mapToAdminDTO).toList();
    }

    private UserDTO mapToAdminDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
```

- [ ] **Step 6: Update `UserController`** — replace the delete endpoint body and add the admin list:

```java
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @RequestHeader("X-User-Id") Long requesterId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        requireSelfOrAdmin(id, requesterId, role);
        userService.deleteUser(id, requesterId, role, reason);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/list")
    public ResponseEntity<List<UserDTO>> getAdminUserList(
            @RequestParam(required = false) String q,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Только для администратора");
        }
        return ResponseEntity.ok(userService.getAdminUserList(q));
    }
```

(check existing imports; `ResponseStatusException`/`HttpStatus` may need adding).

**Route-order caveat:** `GET /api/users/admin/list` must match the `user-service-read` gateway route (`Path=/api/users/**` + AuthenticationFilter) — it does; no gateway change needed. But `@GetMapping("/{id}")` on line 39 would swallow `/admin/list` only if Spring matched path variables first — it does not (exact segments win); still, verify with the smoke call in Task 13.

- [ ] **Step 7: Run tests**

Run: `mvn -q test -pl user-service`
Expected: all green

- [ ] **Step 8: Commit**

```bash
git add user-service/src
git commit -m "feat(user): user.deleted event, admin-guarded deletion with reason, admin user list"
```

---

### Task 9: event-service consumes `user.deleted` — organizer cascade

**Files:**
- Create: `event-service/src/main/java/org/ngcvfb/eventservice/kafka/UserKafkaConsumer.java`
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/service/EventService.java` (add `deleteAllForUser`)
- Modify: `event-service/src/main/java/org/ngcvfb/eventservice/repository/EventRequestRepository.java` (add `deleteByRequesterId`)
- Modify: `event-service/src/main/resources/application.yml` (kafka consumer config if missing)
- Test: `event-service/src/test/java/org/ngcvfb/eventservice/service/EventServiceUserCascadeTest.java`

**Interfaces:**
- Consumes: `UserDeletedEvent` (Task 1); existing `EventService.deleteEvent(Long id, Long requesterId, String role)`; `EventRepository.findByOrganizerId(Long)`.
- Produces: `EventService.deleteAllForUser(Long userId)` — deletes the user's events (emitting `event.deleted` per event via the existing flow) and event requests.

- [ ] **Step 1: Check consumer config.** Open `event-service/src/main/resources/application.yml`. If `spring.kafka.consumer` is missing, add (matching registration-service):

```yaml
    consumer:
      group-id: event-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: org.ngcvfb.eventhubkz.common.events
```

- [ ] **Step 2: Write the failing test**

```java
package org.ngcvfb.eventservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.InjectMocks;
import org.ngcvfb.eventservice.client.RegistrationClient;
import org.ngcvfb.eventservice.client.UserClient;
import org.ngcvfb.eventservice.kafka.EventKafkaProducer;
import org.ngcvfb.eventservice.model.Event;
import org.ngcvfb.eventservice.repository.EventRepository;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceUserCascadeTest {

    @Mock EventRepository eventRepository;
    @Mock EventKafkaProducer kafkaProducer;
    @Mock RegistrationClient registrationClient;
    @Mock UserClient userClient;

    @InjectMocks @Spy EventService eventService;

    @Test
    void deletesAllOrganizerEventsViaExistingFlow() {
        Event e1 = Event.builder().id(10L).organizerId(7L).build();
        Event e2 = Event.builder().id(11L).organizerId(7L).build();
        when(eventRepository.findByOrganizerId(7L)).thenReturn(List.of(e1, e2));
        doNothing().when(eventService).deleteEvent(anyLong(), anyLong(), anyString());

        eventService.deleteAllForUser(7L);

        verify(eventService).deleteEvent(10L, 7L, "ADMIN");
        verify(eventService).deleteEvent(11L, 7L, "ADMIN");
    }
}
```

Note: `@Spy` on `@InjectMocks` lets us stub `deleteEvent` (it has its own tests). If `EventService` has more constructor deps than the four mocks above, add them.

- [ ] **Step 3: Run to verify it fails**

Run: `mvn -q test -pl event-service -Dtest=EventServiceUserCascadeTest`
Expected: COMPILATION ERROR (`deleteAllForUser` not defined)

- [ ] **Step 4: Implement.** In `EventRequestRepository` add:

```java
    void deleteByRequesterId(Long requesterId);
```

In `EventService` add (needs `EventRequestRepository` — if not already injected, inject it as `private final EventRequestRepository eventRequestRepository;` and update ALL existing constructor-based tests' mock lists; if that widens the diff too much, put the request cleanup into `UserKafkaConsumer` instead and inject the repository there — prefer the consumer variant if `EventService` does not already have the field):

```java
    @Transactional
    public void deleteAllForUser(Long userId) {
        List<Event> events = eventRepository.findByOrganizerId(userId);
        log.info("Deleting {} events of removed user {}", events.size(), userId);
        for (Event event : events) {
            // deleteEvent эмитит event.deleted — поиск и регистрации почистятся сами
            deleteEvent(event.getId(), userId, "ADMIN");
        }
    }
```

`UserKafkaConsumer.java`:

```java
package org.ngcvfb.eventservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.eventhubkz.common.events.UserDeletedEvent;
import org.ngcvfb.eventservice.repository.EventRequestRepository;
import org.ngcvfb.eventservice.service.EventService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserKafkaConsumer {

    private final EventService eventService;
    private final EventRequestRepository eventRequestRepository;

    @KafkaListener(topics = "user.deleted", groupId = "event-service-group")
    @Transactional
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received user.deleted: {}", event.getUserId());
        try {
            eventService.deleteAllForUser(event.getUserId());
            eventRequestRepository.deleteByRequesterId(event.getUserId());
        } catch (Exception e) {
            log.error("Failed to cascade user deletion: {}", event.getUserId(), e);
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn -q test -pl event-service`
Expected: all green

- [ ] **Step 6: Commit**

```bash
git add event-service/src
git commit -m "feat(event): cascade organizer events and requests on user.deleted"
```

---

### Task 10: like-service cleanup on `user.deleted`

**Files:**
- Modify: `like-service/src/main/java/org/ngcvfb/likeservice/repository/EventLikeRepository.java` (add `deleteByUserId`)
- Modify: `like-service/src/main/java/org/ngcvfb/likeservice/service/EventLikeService.java` (add `deleteAllLikesForUser`)
- Modify: `like-service/src/main/java/org/ngcvfb/likeservice/kafka/EventKafkaConsumer.java` (add listener)
- Test: `like-service/src/test/java/org/ngcvfb/likeservice/service/EventLikeServiceUserCleanupTest.java`

**Interfaces:**
- Consumes: `UserDeletedEvent` (Task 1); existing repo `findByUserId(Long)`; cache `likeCount` keyed by eventId.
- Produces: `EventLikeService.deleteAllLikesForUser(Long userId)`.

- [ ] **Step 1: Write the failing test**

Read `EventLikeService` constructor deps first and mirror them as mocks; the essentials:

```java
package org.ngcvfb.likeservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.likeservice.model.EventLike;
import org.ngcvfb.likeservice.repository.EventLikeRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventLikeServiceUserCleanupTest {

    @Mock EventLikeRepository likeRepository;
    @Mock CacheManager cacheManager;
    @Mock Cache cache;
    // add remaining EventLikeService constructor deps as @Mock

    @InjectMocks EventLikeService service;

    @Test
    void deletesLikesAndEvictsAffectedCounters() {
        EventLike l1 = new EventLike(); l1.setUserId(7L); l1.setEventId(10L);
        EventLike l2 = new EventLike(); l2.setUserId(7L); l2.setEventId(11L);
        when(likeRepository.findByUserId(7L)).thenReturn(List.of(l1, l2));
        when(cacheManager.getCache("likeCount")).thenReturn(cache);

        service.deleteAllLikesForUser(7L);

        verify(likeRepository).deleteByUserId(7L);
        verify(cache).evict(10L);
        verify(cache).evict(11L);
    }
}
```

Note: check `EventLike` model for setters/builder — adapt construction to the real class (it may be `@Builder`).

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -pl like-service -Dtest=EventLikeServiceUserCleanupTest`
Expected: COMPILATION ERROR

- [ ] **Step 3: Implement.** Repository — add:

```java
    void deleteByUserId(Long userId);
```

Service — add (inject `CacheManager` as a new `private final` field; class uses `@RequiredArgsConstructor`):

```java
    @Transactional
    public void deleteAllLikesForUser(Long userId) {
        List<EventLike> likes = likeRepository.findByUserId(userId);
        likeRepository.deleteByUserId(userId);
        Cache cache = cacheManager.getCache("likeCount");
        if (cache != null) {
            likes.forEach(like -> cache.evict(like.getEventId()));
        }
        log.info("Deleted {} likes of removed user {}", likes.size(), userId);
    }
```

(imports: `org.springframework.cache.Cache`, `org.springframework.cache.CacheManager`). Consumer — add to `EventKafkaConsumer`:

```java
    @KafkaListener(topics = "user.deleted", groupId = "like-service-group")
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received user.deleted: {}", event.getUserId());
        try {
            likeService.deleteAllLikesForUser(event.getUserId());
        } catch (Exception e) {
            log.error("Failed to delete likes for user: {}", event.getUserId(), e);
        }
    }
```

(check the consumer's existing field name for the service and its group-id literal — mirror them; import `UserDeletedEvent`).

- [ ] **Step 4: Run tests**

Run: `mvn -q test -pl like-service`
Expected: all green

- [ ] **Step 5: Commit**

```bash
git add like-service/src
git commit -m "feat(like): clean up user likes and evict counters on user.deleted"
```

---

### Task 11: registration-service cleanup on `user.deleted`

**Files:**
- Modify: `registration-service/src/main/java/org/ngcvfb/registrationservice/repository/EventRegistrationRepository.java` (add `deleteByUserId`)
- Modify: `registration-service/src/main/java/org/ngcvfb/registrationservice/service/EventRegistrationService.java` (add `deleteAllRegistrationsForUser`)
- Modify: `registration-service/src/main/java/org/ngcvfb/registrationservice/kafka/EventKafkaConsumer.java` (add listener)
- Test: `registration-service/src/test/java/org/ngcvfb/registrationservice/service/EventRegistrationUserCleanupTest.java`

**Interfaces:**
- Consumes: `UserDeletedEvent` (Task 1); repo `findByUserId(Long)`; cache `registrationCount`.
- Produces: `EventRegistrationService.deleteAllRegistrationsForUser(Long userId)`.

- [ ] **Step 1: Write the failing test** — mirror Task 10's test exactly, substituting: `EventRegistrationRepository`, `EventRegistration` model, cache name `"registrationCount"`, service `EventRegistrationService`, method `deleteAllRegistrationsForUser`. Read the service's constructor deps first (it has Feign `EventClient` and a Kafka producer — mock them all).

```java
package org.ngcvfb.registrationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.registrationservice.model.EventRegistration;
import org.ngcvfb.registrationservice.repository.EventRegistrationRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventRegistrationUserCleanupTest {

    @Mock EventRegistrationRepository registrationRepository;
    @Mock CacheManager cacheManager;
    @Mock Cache cache;
    // add remaining EventRegistrationService constructor deps as @Mock

    @InjectMocks EventRegistrationService service;

    @Test
    void deletesRegistrationsAndEvictsCounters() {
        EventRegistration r1 = new EventRegistration(); r1.setUserId(7L); r1.setEventId(10L);
        when(registrationRepository.findByUserId(7L)).thenReturn(List.of(r1));
        when(cacheManager.getCache("registrationCount")).thenReturn(cache);

        service.deleteAllRegistrationsForUser(7L);

        verify(registrationRepository).deleteByUserId(7L);
        verify(cache).evict(10L);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -pl registration-service -Dtest=EventRegistrationUserCleanupTest`
Expected: COMPILATION ERROR

- [ ] **Step 3: Implement** — same shape as Task 10: repo `void deleteByUserId(Long userId);`, service method with `CacheManager` field + `"registrationCount"` evictions, consumer listener with group `registration-service-group` calling `registrationService.deleteAllRegistrationsForUser(event.getUserId())` in try/catch.

- [ ] **Step 4: Run tests**

Run: `mvn -q test -pl registration-service`
Expected: all green

- [ ] **Step 5: Commit**

```bash
git add registration-service/src
git commit -m "feat(registration): clean up user registrations and counters on user.deleted"
```

---

### Task 12: notification-service cleanup on `user.deleted`

**Files:**
- Modify: `notification-service/src/main/java/org/ngcvfb/notificationservice/kafka/NotificationKafkaConsumer.java` (add listener)
- Modify: `notification-service/src/main/java/org/ngcvfb/notificationservice/service/NotificationService.java` (add `deleteAllForUser`)
- Test: `notification-service/src/test/java/org/ngcvfb/notificationservice/service/NotificationUserCleanupTest.java`

**Interfaces:**
- Consumes: `UserDeletedEvent` (Task 1); existing `NotificationRepository.deleteByUserId(Long)`.
- Produces: `NotificationService.deleteAllForUser(Long userId)`.

- [ ] **Step 1: Write the failing test**

```java
package org.ngcvfb.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ngcvfb.notificationservice.repository.NotificationRepository;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationUserCleanupTest {

    @Mock NotificationRepository notificationRepository;
    // add remaining NotificationService constructor deps as @Mock

    @InjectMocks NotificationService service;

    @Test
    void deletesAllUserNotifications() {
        service.deleteAllForUser(7L);
        verify(notificationRepository).deleteByUserId(7L);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -pl notification-service -Dtest=NotificationUserCleanupTest`
Expected: COMPILATION ERROR

- [ ] **Step 3: Implement.** In `NotificationService`:

```java
    @Transactional
    public void deleteAllForUser(Long userId) {
        notificationRepository.deleteByUserId(userId);
        log.info("Deleted notifications of removed user {}", userId);
    }
```

In `NotificationKafkaConsumer` (mirror the existing handler style; import `UserDeletedEvent`):

```java
    @KafkaListener(topics = "user.deleted", groupId = "notification-service-group")
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received user.deleted: {}", event.getUserId());
        try {
            notificationService.deleteAllForUser(event.getUserId());
        } catch (Exception e) {
            log.error("Failed to delete notifications for user: {}", event.getUserId(), e);
        }
    }
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -pl notification-service`
Expected: all green

- [ ] **Step 5: Commit**

```bash
git add notification-service/src
git commit -m "feat(notification): purge user notifications on user.deleted"
```

---

### Task 13: infrastructure — compose, audit_db, gateway routes, prometheus

**Files:**
- Modify: `docker-compose.yml` (audit-service block + init-script mount on postgres-notifications)
- Create: `db-init/audit/01-create-audit-db.sql`
- Modify: `api-gateway/src/main/resources/application.yml` (audit route BEFORE the `/api/admin/**` rewrite route at ~line 157)
- Modify: `monitoring/prometheus/prometheus.yml` (scrape job)

**Interfaces:**
- Consumes: audit-service image (Task 2), controller path `/api/admin/audit` (Task 6).
- Produces: running `audit-service` container reachable via gateway.

- [ ] **Step 1: DB init script** — `db-init/audit/01-create-audit-db.sql`:

```sql
-- Выполняется только при инициализации СВЕЖЕГО тома postgres-notifications.
-- На существующем томе базу создаёт ручной шаг из README (docker exec ... CREATE DATABASE).
CREATE DATABASE audit_db;
```

In the `postgres-notifications` service block of `docker-compose.yml`, add under `volumes:`:

```yaml
      - ./db-init/audit:/docker-entrypoint-initdb.d
```

- [ ] **Step 2: For the RUNNING stack (existing volume), create the DB manually:**

Run: `docker exec postgres-notifications psql -U postgres -c "CREATE DATABASE audit_db;"`
Expected: `CREATE DATABASE` (or "already exists" on rerun — fine)

- [ ] **Step 3: compose service** — add after the `registration-service` block, adjusting only name/port/env vs. that block:

```yaml
  audit-service:
    build:
      context: ./audit-service
      dockerfile: Dockerfile
    container_name: audit-service
    mem_limit: 512m
    ports:
      - "8091:8091"
    environment:
      - JAVA_TOOL_OPTIONS=-Xmx256m -XX:+UseSerialGC
      - SPRING_PROFILES_ACTIVE=docker
      - EUREKA_SERVER_URL=http://eureka-server:8761/eureka/
      - DB_URL=jdbc:postgresql://postgres-notifications:5432/audit_db
      - DB_USER=${DB_USER}
      - DB_PASSWORD=${DB_PASSWORD}
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - ZIPKIN_URL=http://zipkin:9411/api/v2/spans
    depends_on:
      eureka-server:
        condition: service_healthy
      postgres-notifications:
        condition: service_started
      kafka:
        condition: service_healthy
    networks:
      - eventhub-network
    logging: *default-logging
```

- [ ] **Step 4: gateway route.** In `api-gateway/src/main/resources/application.yml`, **immediately BEFORE** the route with `RewritePath=/api/admin/(?<segment>.*)` (order matters — that route would swallow `/api/admin/audit`), insert:

```yaml
        # Audit Service — admin-only journal (MUST precede the /api/admin/** rewrite route)
        - id: audit-service-admin
          uri: lb://AUDIT-SERVICE
          predicates:
            - Path=/api/admin/audit/**,/api/admin/audit
          filters:
            - name: AuthenticationFilter
```

- [ ] **Step 5: prometheus job** — in `monitoring/prometheus/prometheus.yml` after the registration-service job:

```yaml
  # Audit Service
  - job_name: 'audit-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['audit-service:8091']
    scrape_interval: 10s
```

- [ ] **Step 6: Build, deploy, smoke-test**

```bash
mvn clean package -DskipTests -pl audit-service
docker compose build audit-service
docker compose up -d audit-service
sleep 40
curl -sf http://localhost:8091/actuator/health   # {"status":"UP"}
# через шлюз (нужен админский JWT):
# TOKEN=$(curl -s -X POST http://localhost:8180/auth/login -H "Content-Type: application/json" -d '{"email":"dinara.zhumabaeva@example.kz","password":"password123"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")
# curl -s "http://localhost:8180/api/admin/audit?size=5" -H "Authorization: Bearer $TOKEN"
```

Expected: health UP; gateway call returns a JSON page (content may be empty until events flow).

- [ ] **Step 7: Rebuild + redeploy the modified producers/consumers** (event, user, like, registration, notification services) with the standard recipe, then like an event in the UI and re-query the audit endpoint — a row with `EVENT_LIKED` must appear.

- [ ] **Step 8: Commit**

```bash
git add docker-compose.yml db-init/ api-gateway/src/main/resources/application.yml monitoring/prometheus/prometheus.yml
git commit -m "feat(infra): wire audit-service into compose, gateway and prometheus"
```

---

### Task 14: frontend — «Журнал» tab (audit log)

**Files:**
- Create: `frontend/src/components/AdminAuditLog.jsx`
- Modify: `frontend/src/components/AdminDashboard.jsx` (register tab)
- Modify: `frontend/src/i18n/locales/ru.json`, `frontend/src/i18n/locales/kk.json` (append `admin.audit*` keys inside the existing `"admin"` object — edit textually, do NOT round-trip through `json.dump`, it reformats the whole file)

**Interfaces:**
- Consumes: `GET /api/admin/audit` (Task 6) via `import api from '../api'`; `Pagination` component (`{ page, totalPages, onChange, total }` props); css classes from `AdminDashboard.css`.
- Produces: tab key `audit` in AdminDashboard.

- [ ] **Step 1: i18n keys.** Append inside the `"admin"` object of `ru.json` (before its closing `}`):

```json
    "tabAudit": "Журнал",
    "auditEmpty": "Записей пока нет",
    "auditLoadError": "Не удалось загрузить журнал",
    "auditColTime": "Время",
    "auditColActor": "Кто",
    "auditColAction": "Действие",
    "auditColTarget": "Объект",
    "auditColDetails": "Детали",
    "auditFilterAction": "Все действия",
    "auditFilterActor": "ID пользователя",
    "auditFilterFrom": "С даты",
    "auditFilterTo": "По дату",
    "auditApply": "Применить",
    "auditReset": "Сбросить",
    "auditAction_USER_REGISTERED": "Регистрация пользователя",
    "auditAction_USER_DELETED": "Удаление пользователя",
    "auditAction_EVENT_CREATED": "Событие создано",
    "auditAction_EVENT_UPDATED": "Событие обновлено",
    "auditAction_EVENT_DELETED": "Событие удалено",
    "auditAction_EVENT_LIKED": "Лайк события",
    "auditAction_EVENT_RSVP": "Запись на событие",
    "auditAction_REQUEST_CREATED": "Заявка подана",
    "auditAction_REQUEST_APPROVED": "Заявка одобрена",
    "auditAction_REQUEST_REJECTED": "Заявка отклонена"
```

`kk.json` — same keys:

```json
    "tabAudit": "Журнал",
    "auditEmpty": "Әзірге жазбалар жоқ",
    "auditLoadError": "Журналды жүктеу мүмкін болмады",
    "auditColTime": "Уақыты",
    "auditColActor": "Кім",
    "auditColAction": "Әрекет",
    "auditColTarget": "Нысан",
    "auditColDetails": "Мәліметтер",
    "auditFilterAction": "Барлық әрекеттер",
    "auditFilterActor": "Пайдаланушы ID",
    "auditFilterFrom": "Басталу күні",
    "auditFilterTo": "Аяқталу күні",
    "auditApply": "Қолдану",
    "auditReset": "Тазарту",
    "auditAction_USER_REGISTERED": "Пайдаланушы тіркелді",
    "auditAction_USER_DELETED": "Пайдаланушы жойылды",
    "auditAction_EVENT_CREATED": "Іс-шара құрылды",
    "auditAction_EVENT_UPDATED": "Іс-шара жаңартылды",
    "auditAction_EVENT_DELETED": "Іс-шара жойылды",
    "auditAction_EVENT_LIKED": "Іс-шараға лайк",
    "auditAction_EVENT_RSVP": "Іс-шараға тіркелу",
    "auditAction_REQUEST_CREATED": "Өтінім берілді",
    "auditAction_REQUEST_APPROVED": "Өтінім мақұлданды",
    "auditAction_REQUEST_REJECTED": "Өтінім қабылданбады"
```

- [ ] **Step 2: Component** — `AdminAuditLog.jsx`:

```jsx
import React, { useState, useEffect, useCallback } from 'react';
import api from '../api';
import { formatDate } from '../utils/dateUtils';
import Skeleton from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';
import Pagination from './Pagination';
import { useTranslation } from 'react-i18next';

const ACTIONS = [
  'USER_REGISTERED', 'USER_DELETED',
  'EVENT_CREATED', 'EVENT_UPDATED', 'EVENT_DELETED',
  'EVENT_LIKED', 'EVENT_RSVP',
  'REQUEST_CREATED', 'REQUEST_APPROVED', 'REQUEST_REJECTED',
];

const AdminAuditLog = () => {
  const { t } = useTranslation();
  const [rows, setRows] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [action, setAction] = useState('');
  const [actorId, setActorId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const load = useCallback(async (p = 0) => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({ page: p, size: 20 });
      if (action) params.set('action', action);
      if (actorId) params.set('actorId', actorId);
      if (from) params.set('from', `${from}T00:00:00`);
      if (to) params.set('to', `${to}T23:59:59`);
      const res = await api.get(`/api/admin/audit?${params}`);
      setRows(res.data.content);
      setTotalPages(res.data.totalPages);
      setTotal(res.data.totalElements);
      setPage(p);
    } catch (e) {
      setError(t('admin.auditLoadError'));
    } finally {
      setLoading(false);
    }
  }, [action, actorId, from, to, t]);

  useEffect(() => { load(0); }, []); // первичная загрузка

  const resetFilters = () => { setAction(''); setActorId(''); setFrom(''); setTo(''); };

  if (error) return <PageError message={error} onRetry={() => load(page)} />;

  return (
    <div className="adm-audit">
      <div className="adm-audit__filters">
        <select value={action} onChange={e => setAction(e.target.value)} aria-label={t('admin.auditColAction')}>
          <option value="">{t('admin.auditFilterAction')}</option>
          {ACTIONS.map(a => <option key={a} value={a}>{t(`admin.auditAction_${a}`)}</option>)}
        </select>
        <input type="number" value={actorId} onChange={e => setActorId(e.target.value)}
               placeholder={t('admin.auditFilterActor')} />
        <input type="date" value={from} onChange={e => setFrom(e.target.value)}
               aria-label={t('admin.auditFilterFrom')} />
        <input type="date" value={to} onChange={e => setTo(e.target.value)}
               aria-label={t('admin.auditFilterTo')} />
        <button onClick={() => load(0)}>{t('admin.auditApply')}</button>
        <button onClick={resetFilters}>{t('admin.auditReset')}</button>
      </div>

      {loading ? <Skeleton /> : rows.length === 0 ? (
        <EmptyState icon="inbox" title={t('admin.auditEmpty')} />
      ) : (
        <>
          <table className="adm-audit__table">
            <thead>
              <tr>
                <th>{t('admin.auditColTime')}</th>
                <th>{t('admin.auditColActor')}</th>
                <th>{t('admin.auditColAction')}</th>
                <th>{t('admin.auditColTarget')}</th>
                <th>{t('admin.auditColDetails')}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.id}>
                  <td>{formatDate(r.occurredAt)}</td>
                  <td>{r.actorName || (r.actorId ? `#${r.actorId}` : '—')}</td>
                  <td><span className={`adm-audit__chip adm-audit__chip--${r.action.toLowerCase()}`}>
                    {t(`admin.auditAction_${r.action}`)}
                  </span></td>
                  <td>{r.targetLabel || '—'}</td>
                  <td>{r.details || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={totalPages} onChange={load} total={total} />
        </>
      )}
    </div>
  );
};

export default AdminAuditLog;
```

Check `formatDate` accepts ISO strings (`frontend/src/utils/dateUtils.js`) — if its signature differs, use whatever the admin components already use. Add minimal styles for `.adm-audit__filters`, `.adm-audit__table`, `.adm-audit__chip` to `frontend/src/css/AdminDashboard.css` following its existing conventions (reuse table styling from the support tab if present).

- [ ] **Step 3: Register the tab.** In `AdminDashboard.jsx`:

```jsx
import AdminAuditLog from './AdminAuditLog';
```

extend `TABS`:

```jsx
const TABS = [
  { key: 'requests', labelKey: 'admin.tabRequests' },
  { key: 'support',  labelKey: 'admin.tabSupport' },
  { key: 'audit',    labelKey: 'admin.tabAudit' },
];
```

and render:

```jsx
      {tab === 'audit' && <AdminAuditLog />}
```

- [ ] **Step 4: Build**

Run: `cd frontend && npx vite build`
Expected: `✓ built`

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "feat(admin): audit journal tab with filters and pagination"
```

---

### Task 15: frontend — «Пользователи» tab (deletion)

**Files:**
- Create: `frontend/src/components/AdminUsers.jsx`
- Modify: `frontend/src/components/AdminDashboard.jsx` (register tab)
- Modify: `frontend/src/i18n/locales/ru.json`, `frontend/src/i18n/locales/kk.json` (append inside `"admin"`)

**Interfaces:**
- Consumes: `GET /api/users/admin/list?q=` and `DELETE /api/users/{id}?reason=` (Task 8); current user id from wherever the header gets it (see `Header.jsx` — it decodes the JWT / localStorage; reuse the same helper).
- Produces: tab key `users`.

- [ ] **Step 1: i18n.** `ru.json` `"admin"` additions:

```json
    "tabUsers": "Пользователи",
    "usersSearch": "Поиск по имени или email...",
    "usersColId": "ID",
    "usersColName": "Имя",
    "usersColEmail": "Email",
    "usersColRole": "Роль",
    "usersDelete": "Удалить",
    "usersLoadError": "Не удалось загрузить пользователей",
    "usersEmpty": "Никого не найдено",
    "usersDeleteTitle": "Удалить пользователя {{name}}?",
    "usersDeleteWarning": "Действие необратимо: профиль, лайки, регистрации, уведомления и все его события будут удалены. Запись останется в журнале аудита.",
    "usersDeleteReason": "Причина (попадёт в журнал)",
    "usersDeleteConfirm": "Удалить навсегда",
    "usersDeleteCancel": "Отмена",
    "usersDeleteSuccess": "Пользователь удалён",
    "usersDeleteError": "Не удалось удалить пользователя"
```

`kk.json`:

```json
    "tabUsers": "Пайдаланушылар",
    "usersSearch": "Аты немесе email бойынша іздеу...",
    "usersColId": "ID",
    "usersColName": "Аты",
    "usersColEmail": "Email",
    "usersColRole": "Рөлі",
    "usersDelete": "Жою",
    "usersLoadError": "Пайдаланушыларды жүктеу мүмкін болмады",
    "usersEmpty": "Ешкім табылмады",
    "usersDeleteTitle": "{{name}} пайдаланушысын жою керек пе?",
    "usersDeleteWarning": "Әрекет қайтарылмайды: профиль, лайктар, тіркелулер, хабарламалар және оның барлық іс-шаралары жойылады. Жазба аудит журналында қалады.",
    "usersDeleteReason": "Себебі (журналға жазылады)",
    "usersDeleteConfirm": "Біржола жою",
    "usersDeleteCancel": "Болдырмау",
    "usersDeleteSuccess": "Пайдаланушы жойылды",
    "usersDeleteError": "Пайдаланушыны жою мүмкін болмады"
```

- [ ] **Step 2: Component** — `AdminUsers.jsx`. Before writing, open `frontend/src/components/Header.jsx` and reuse its exact way of getting the current user id/role from the JWT (there is a decode helper or localStorage read — mirror it; below it is called `getCurrentUserId()` as a placeholder for THAT mechanism):

```jsx
import React, { useState, useEffect, useCallback } from 'react';
import api from '../api';
import toast from 'react-hot-toast';
import Skeleton from './Skeleton';
import EmptyState from './EmptyState';
import PageError from './PageError';
import { useTranslation } from 'react-i18next';

const AdminUsers = () => {
  const { t } = useTranslation();
  const [users, setUsers] = useState([]);
  const [q, setQ] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [target, setTarget] = useState(null);   // пользователь в модалке
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const currentUserId = getCurrentUserId(); // заменить на реальный помощник из Header.jsx

  const load = useCallback(async (query = '') => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get(`/api/users/admin/list${query ? `?q=${encodeURIComponent(query)}` : ''}`);
      setUsers(res.data);
    } catch (e) {
      setError(t('admin.usersLoadError'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { load(); }, [load]);

  const confirmDelete = async () => {
    if (!target || busy) return;
    setBusy(true);
    try {
      const params = reason.trim() ? `?reason=${encodeURIComponent(reason.trim())}` : '';
      await api.delete(`/api/users/${target.id}${params}`);
      toast.success(t('admin.usersDeleteSuccess'));
      setTarget(null);
      setReason('');
      load(q);
    } catch (e) {
      toast.error(t('admin.usersDeleteError'));
    } finally {
      setBusy(false);
    }
  };

  if (error) return <PageError message={error} onRetry={() => load(q)} />;

  return (
    <div className="adm-users">
      <form className="adm-users__search" onSubmit={e => { e.preventDefault(); load(q); }}>
        <input value={q} onChange={e => setQ(e.target.value)}
               placeholder={t('admin.usersSearch')} />
      </form>

      {loading ? <Skeleton /> : users.length === 0 ? (
        <EmptyState icon="search" title={t('admin.usersEmpty')} />
      ) : (
        <table className="adm-users__table">
          <thead>
            <tr>
              <th>{t('admin.usersColId')}</th>
              <th>{t('admin.usersColName')}</th>
              <th>{t('admin.usersColEmail')}</th>
              <th>{t('admin.usersColRole')}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {users.map(u => (
              <tr key={u.id}>
                <td>{u.id}</td>
                <td>{u.username}</td>
                <td>{u.email}</td>
                <td>{u.role}</td>
                <td>
                  {u.role !== 'ADMIN' && u.id !== currentUserId && (
                    <button className="adm-users__delete" onClick={() => setTarget(u)}>
                      {t('admin.usersDelete')}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {target && (
        <div className="adm-users__modal-backdrop" onClick={() => !busy && setTarget(null)}>
          <div className="adm-users__modal" onClick={e => e.stopPropagation()}>
            <h3>{t('admin.usersDeleteTitle', { name: target.username })}</h3>
            <p>{t('admin.usersDeleteWarning')}</p>
            <input value={reason} onChange={e => setReason(e.target.value)}
                   placeholder={t('admin.usersDeleteReason')} />
            <div className="adm-users__modal-actions">
              <button onClick={() => setTarget(null)} disabled={busy}>
                {t('admin.usersDeleteCancel')}
              </button>
              <button className="adm-users__delete" onClick={confirmDelete} disabled={busy}>
                {t('admin.usersDeleteConfirm')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminUsers;
```

Add styles for `.adm-users__*` (search row, table, red delete button, modal backdrop/dialog) to `AdminDashboard.css`, reusing its palette; the logout-confirm modal in `Header.jsx`/its css already has a backdrop+dialog pattern — mirror those class rules.

- [ ] **Step 3: Register the tab** in `AdminDashboard.jsx` (import, `{ key: 'users', labelKey: 'admin.tabUsers' }` between support and audit, `{tab === 'users' && <AdminUsers />}`).

- [ ] **Step 4: Build**

Run: `cd frontend && npx vite build`
Expected: `✓ built`

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "feat(admin): users tab with guarded hard deletion and reason"
```

---

### Task 16: E2E verification + README

**Files:**
- Modify: `README.md` (service table row, Kafka topic table rows, mermaid diagram node, container count)

- [ ] **Step 1: Redeploy everything touched** (event-service ×2 replicas, user-service, like-service, registration-service, notification-service, api-gateway, audit-service) using the standard recipe; frontend dev server picks changes up automatically.

- [ ] **Step 2: E2E scenario via browser (Playwright, pattern from scratchpad smoke scripts):**
1. Log in as admin (`dinara.zhumabaeva@example.kz` / `password123`), open `/admin` → «Журнал»: rows from recent activity exist; filter by action works. Screenshot.
2. «Пользователи»: search a seeded non-admin user; delete with reason «тест аудита». Screenshot of modal.
3. Verify: user gone from list; his likes/registrations/notifications rows deleted (spot-check via `docker exec postgres-likes psql ... "SELECT count(*) FROM event_likes WHERE user_id=<id>"`); his events gone from `/eventlist` and search; «Журнал» shows `USER_DELETED` with reason and snapshot.
4. `console --errors` / page errors: none.

- [ ] **Step 3: README** — add to the service table: `| audit-service | 8091 | Immutable audit trail of all domain events (admin journal) |`; add `user.deleted` and `event-request.created` rows to the Kafka topics table (consumers: audit-service + cleanup services); add `AUDIT[audit-service]` node consuming from Kafka in the mermaid diagram; bump the container count in the Delivery row (23 → 24).

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: document audit-service and user deletion cascade"
```

---

## Self-Review Notes

- **Spec coverage:** audit-service+DB (T2–3), dedup (T4), 9 topics incl. 2 new (T1, T5, T7, T8), admin API (T6), deletion cascade per service (T8–12), gateway/compose/prometheus (T13), two tabs + i18n (T14–15), README + E2E (T16). Spec's "cannot delete yourself" guard replaced by "UI hides own row, backend keeps legal self-deletion" — recorded as deliberate deviation in Global Constraints.
- **Order-sensitive gotcha:** gateway `/api/admin/**` rewrite route must come AFTER the audit route (T13 Step 4).
- **Existing-volume gotcha:** `audit_db` must be created manually on the running stack (T13 Step 2); init script only covers fresh volumes.
- **Constructor-mock caveat:** several service tests must mirror real constructor dependency lists — each such task says to read the class first and adjust `@Mock`s.
