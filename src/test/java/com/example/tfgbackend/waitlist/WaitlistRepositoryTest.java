package com.example.tfgbackend.waitlist;

import com.example.tfgbackend.AbstractRepositoryTest;
import com.example.tfgbackend.classtype.ClassType;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WaitlistRepositoryTest extends AbstractRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired WaitlistRepository repository;

    private User alice;
    private User bob;
    private User carol;
    private ClassSession session;

    @BeforeEach
    void setUp() {
        ClassType spinning = em.persistAndFlush(ClassType.builder()
                .name("Spinning").description("Cycling").level("INTERMEDIATE").build());
        session = em.persistAndFlush(ClassSession.builder()
                .classType(spinning)
                .startTime(LocalDateTime.now().plusDays(1))
                .durationMinutes(45).maxCapacity(1).room("1A").build());
        alice = em.persistAndFlush(User.builder()
                .name("Alice").email("alice@test.com")
                .passwordHash("$2a$10$hash").role(UserRole.CUSTOMER).build());
        bob = em.persistAndFlush(User.builder()
                .name("Bob").email("bob@test.com")
                .passwordHash("$2a$10$hash").role(UserRole.CUSTOMER).build());
        carol = em.persistAndFlush(User.builder()
                .name("Carol").email("carol@test.com")
                .passwordHash("$2a$10$hash").role(UserRole.CUSTOMER).build());
    }

    private WaitlistEntry persistEntry(User user, ClassSession ses, int position) {
        return em.persistAndFlush(WaitlistEntry.builder()
                .user(user).session(ses).position(position).build());
    }

    @Test
    void findBySessionIdOrderByPositionAsc_MultipleEntries_ReturnsSortedByPosition() {
        persistEntry(bob, session, 2);
        persistEntry(alice, session, 1);
        persistEntry(carol, session, 3);
        em.clear();

        List<WaitlistEntry> entries = repository.findBySessionIdOrderByPositionAsc(session.getId());

        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).getPosition()).isEqualTo(1);
        assertThat(entries.get(1).getPosition()).isEqualTo(2);
        assertThat(entries.get(2).getPosition()).isEqualTo(3);
    }

    @Test
    void findByUserIdAndSessionId_ExistingEntry_ReturnsEntry() {
        persistEntry(alice, session, 1);
        em.clear();

        Optional<WaitlistEntry> found = repository.findByUserIdAndSessionId(alice.getId(), session.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getPosition()).isEqualTo(1);
    }

    @Test
    void findByUserIdAndSessionId_NoEntry_ReturnsEmpty() {
        Optional<WaitlistEntry> found = repository.findByUserIdAndSessionId(alice.getId(), session.getId());
        assertThat(found).isEmpty();
    }

    @Test
    void countBySessionId_MultipleEntries_ReturnsCorrectCount() {
        persistEntry(alice, session, 1);
        persistEntry(bob, session, 2);
        em.clear();

        assertThat(repository.countBySessionId(session.getId())).isEqualTo(2);
    }

    @Test
    void findByUserId_MultipleSessionWaitlists_ReturnsAllUserEntries() {
        ClassType yoga = em.persistAndFlush(ClassType.builder()
                .name("Yoga").description("Flex").level("BASIC").build());
        ClassSession session2 = em.persistAndFlush(ClassSession.builder()
                .classType(yoga)
                .startTime(LocalDateTime.now().plusDays(2))
                .durationMinutes(60).maxCapacity(1).room("2B").build());

        persistEntry(alice, session, 1);
        persistEntry(alice, session2, 1);
        persistEntry(bob, session, 2); // bob's entry, not alice's
        em.clear();

        List<WaitlistEntry> aliceEntries = repository.findByUserId(alice.getId());

        assertThat(aliceEntries).hasSize(2)
                .allMatch(e -> e.getUser().getId().equals(alice.getId()));
    }
}
