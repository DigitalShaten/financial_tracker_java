package com.fintrack.service;

import com.fintrack.entity.Account;
import com.fintrack.entity.FamilyInvite;
import com.fintrack.entity.Goal;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.FamilyInviteRepository;
import com.fintrack.repository.FamilyMemberRepository;
import com.fintrack.repository.GoalRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FamilyServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private FamilyService familyService;
    @Autowired
    private FamilyInviteRepository inviteRepository;
    @Autowired
    private FamilyMemberRepository memberRepository;
    @Autowired
    private GoalService goalService;
    @Autowired
    private GoalRepository goalRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private User dmitry;
    private User anna;

    @BeforeEach
    void setUp() {
        dmitry = fixtures.createUser("Дмитрий", "dmitry@test.by");
        anna = fixtures.createUser("Анна", "anna@test.by");
        familyService.create(dmitry.getId(), "Семья Ивановых");
    }

    @Test
    void inviteCodeHasExpectedFormat() {
        FamilyInvite invite = familyService.createInvite(dmitry.getId());

        assertThat(invite.getCode()).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{3}-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{3}");
        assertThat(invite.getExpiresAt()).isAfter(OffsetDateTime.now().plusHours(23));
    }

    @Test
    void joinIgnoresCaseAndSpaces() {
        String code = familyService.createInvite(dmitry.getId()).getCode();

        familyService.join(anna.getId(), "  " + code.toLowerCase().replace("-", " ") + " ");

        assertThat(memberRepository.findByUser_Id(anna.getId())).isPresent();
    }

    @Test
    void expiredCodeIsRejected() {
        FamilyInvite invite = familyService.createInvite(dmitry.getId());
        invite.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        inviteRepository.saveAndFlush(invite);

        assertThatThrownBy(() -> familyService.join(anna.getId(), invite.getCode()))
                .isInstanceOf(BusinessException.class).hasMessage("Код недействителен или истёк");
    }

    @Test
    void usedCodeIsRejected() {
        String code = familyService.createInvite(dmitry.getId()).getCode();
        familyService.join(anna.getId(), code);
        User boris = fixtures.createUser("Борис", "boris@test.by");

        assertThatThrownBy(() -> familyService.join(boris.getId(), code))
                .isInstanceOf(BusinessException.class).hasMessage("Код недействителен или истёк");
    }

    @Test
    void newCodeCancelsOldOne() {
        String old = familyService.createInvite(dmitry.getId()).getCode();
        familyService.createInvite(dmitry.getId());

        assertThatThrownBy(() -> familyService.join(anna.getId(), old)).isInstanceOf(BusinessException.class);
    }

    @Test
    void userCannotJoinTwice() {
        String code = familyService.createInvite(dmitry.getId()).getCode();
        familyService.join(anna.getId(), code);
        String second = familyService.createInvite(dmitry.getId()).getCode();

        assertThatThrownBy(() -> familyService.join(anna.getId(), second))
                .isInstanceOf(BusinessException.class).hasMessage("Вы уже состоите в семье");
        assertThatThrownBy(() -> familyService.create(anna.getId(), "Другая семья"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void memberCannotInvite() {
        familyService.join(anna.getId(), familyService.createInvite(dmitry.getId()).getCode());

        assertThatThrownBy(() -> familyService.createInvite(anna.getId()))
                .isInstanceOf(BusinessException.class).hasMessage("Приглашать может только владелец семьи");
    }

    @Test
    void ownerCannotLeaveWhileMembersExist() {
        familyService.join(anna.getId(), familyService.createInvite(dmitry.getId()).getCode());

        assertThatThrownBy(() -> familyService.leave(dmitry.getId())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> familyService.removeMember(dmitry.getId(), dmitry.getId()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> familyService.removeMember(anna.getId(), dmitry.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void dissolveKeepsMembersData() {
        familyService.join(anna.getId(), familyService.createInvite(dmitry.getId()).getCode());
        Account annaCard = fixtures.createAccount(anna, "Карта Анны", "100", true);
        Long txId = fixtures.createExpenseFor(anna, "Аптека", "23.90");
        Goal flat = goalService.create(dmitry.getId(), "Квартира", new BigDecimal("95000"),
                LocalDate.now().plusYears(5), new BigDecimal("5"), new BigDecimal("6"), true);
        goalService.contribute(anna.getId(), flat.getId(), new BigDecimal("7500"), LocalDate.now());

        familyService.dissolve(dmitry.getId());

        assertThat(memberRepository.findByUser_Id(anna.getId())).isEmpty();
        assertThat(memberRepository.findByUser_Id(dmitry.getId())).isEmpty();
        assertThat(accountRepository.findById(annaCard.getId())).isPresent();
        assertThat(transactionRepository.existsById(txId)).isTrue();
        Goal goal = goalRepository.findById(flat.getId()).orElseThrow();
        assertThat(goal.getFamily()).isNull();
        assertThat(goal.getOwner().getId()).isEqualTo(dmitry.getId());
        assertThat(goalService.saved(flat.getId())).isEqualByComparingTo("7500");
    }

    @Test
    void leavingMemberTakesOwnFamilyGoalsAsPersonal() {
        familyService.join(anna.getId(), familyService.createInvite(dmitry.getId()).getCode());
        Goal trip = goalService.create(anna.getId(), "Отпуск", new BigDecimal("3000"),
                LocalDate.now().plusYears(1), BigDecimal.ZERO, BigDecimal.ZERO, true);

        familyService.leave(anna.getId());

        assertThat(goalRepository.findById(trip.getId()).orElseThrow().getFamily()).isNull();
        assertThat(goalService.visibleGoals(dmitry.getId())).extracting(Goal::getName).doesNotContain("Отпуск");
        assertThat(goalService.visibleGoals(anna.getId())).extracting(Goal::getName).contains("Отпуск");
    }

    @Test
    void ownerRemovesMember() {
        familyService.join(anna.getId(), familyService.createInvite(dmitry.getId()).getCode());

        familyService.removeMember(dmitry.getId(), anna.getId());

        assertThat(memberRepository.findByUser_Id(anna.getId())).isEmpty();
        assertThat(memberRepository.findByUser_Id(dmitry.getId())).isPresent();
    }

    @Test
    void familyGoalPermissions() {
        familyService.join(anna.getId(), familyService.createInvite(dmitry.getId()).getCode());
        Goal flat = goalService.create(dmitry.getId(), "Квартира", new BigDecimal("95000"),
                LocalDate.now().plusYears(5), BigDecimal.ZERO, BigDecimal.ZERO, true);
        Goal trip = goalService.create(anna.getId(), "Отпуск", new BigDecimal("3000"),
                LocalDate.now().plusYears(1), BigDecimal.ZERO, BigDecimal.ZERO, true);

        // любой участник пополняет семейную цель
        goalService.contribute(anna.getId(), flat.getId(), BigDecimal.TEN, LocalDate.now());
        // изменять - создатель или владелец семьи
        assertThat(goalService.canEdit(anna.getId(), flat)).isFalse();
        assertThat(goalService.canEdit(dmitry.getId(), trip)).isTrue();
        assertThatThrownBy(() -> goalService.delete(anna.getId(), flat.getId())).isInstanceOf(BusinessException.class);
        goalService.delete(dmitry.getId(), trip.getId());
        assertThat(goalRepository.existsById(trip.getId())).isFalse();
    }
}
