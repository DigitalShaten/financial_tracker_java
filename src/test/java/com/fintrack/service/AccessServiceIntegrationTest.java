package com.fintrack.service;

import com.fintrack.entity.Account;
import com.fintrack.entity.User;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.AccountRepository;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private AccessService accessService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountRepository accountRepository;

    private User dmitry;
    private User anna;
    private Account shared;
    private Account hidden;
    private Account archived;
    private Account annaCard;
    private Account dmitryCash;

    @BeforeEach
    void setUp() {
        dmitry = fixtures.createUser("Дмитрий", "dmitry@test.by");
        anna = fixtures.createUser("Анна", "anna@test.by");
        fixtures.family("Семья Ивановых", dmitry, anna);
        shared = fixtures.createAccount(dmitry, "Общий счёт", "100", true);
        hidden = fixtures.createAccount(dmitry, "Личная копилка", "500", false);
        archived = fixtures.createAccount(dmitry, "Старая карта", "0", true);
        accountService.toggleArchive(dmitry.getId(), archived.getId());
        annaCard = fixtures.createAccount(anna, "Карта Анны", "50", true);
        dmitryCash = accountRepository.findAllByUser_IdAndArchivedFalseOrderByName(dmitry.getId()).stream()
                .filter(a -> a.getName().equals("Наличные")).findFirst().orElseThrow();
    }

    @Test
    void meModeIncludesAllOwnActiveAccounts() {
        assertThat(accessService.visibleAccountIds(dmitry.getId(), ViewMode.me()))
                .containsExactlyInAnyOrder(shared.getId(), hidden.getId(), dmitryCash.getId());
    }

    @Test
    void allModeHidesOtherMembersHiddenAccounts() {
        var forAnna = accessService.visibleAccountIds(anna.getId(), ViewMode.all());

        assertThat(forAnna).contains(annaCard.getId(), shared.getId(), dmitryCash.getId())
                .doesNotContain(hidden.getId(), archived.getId());
    }

    @Test
    void memberModeShowsOnlyThatMembersOpenAccounts() {
        var dmitryForAnna = accessService.visibleAccountIds(anna.getId(), ViewMode.parse(dmitry.getId().toString()));

        assertThat(dmitryForAnna).containsExactlyInAnyOrder(shared.getId(), dmitryCash.getId());
    }

    @Test
    void memberFromAnotherFamilyIsNotFound() {
        User boris = fixtures.createUser("Борис", "boris@test.by");
        User vera = fixtures.createUser("Вера", "vera@test.by");
        fixtures.family("Семья Петровых", boris, vera);

        assertThatThrownBy(() -> accessService.visibleAccountIds(anna.getId(), ViewMode.parse(boris.getId().toString())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> accessService.visibleAccountIds(anna.getId(), ViewMode.parse("abc")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void userWithoutFamilySeesOnlyOwnAccounts() {
        User boris = fixtures.createUser("Борис", "boris@test.by");

        assertThat(accessService.visibleAccountIds(boris.getId(), ViewMode.all())).hasSize(1);
        assertThat(accessService.familyMembers(boris.getId())).isEmpty();
    }
}
