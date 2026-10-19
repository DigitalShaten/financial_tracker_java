package com.fintrack.service;

import com.fintrack.dto.AccountForm;
import com.fintrack.entity.Account;
import com.fintrack.entity.AccountType;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private User anna;
    private User boris;

    @BeforeEach
    void setUp() {
        anna = fixtures.createUser("Анна", "anna@test.by");
        boris = fixtures.createUser("Борис", "boris@test.by");
    }

    @Test
    void createsAccountWithInitialBalanceInUserCurrency() {
        Account card = fixtures.createAccount(anna, "Карта", "150.25", true);

        assertThat(card.getBalance()).isEqualByComparingTo("150.25");
        assertThat(card.getCurrency()).isEqualTo("BYN");
        assertThat(accountService.activeAccounts(anna.getId())).extracting(Account::getName)
                .containsExactly("Карта", "Наличные");
    }

    @Test
    void updateChangesNameTypeAndVisibilityButNotBalance() {
        Account card = fixtures.createAccount(anna, "Карта", "100", true);
        AccountForm form = new AccountForm();
        form.setName("Копилка");
        form.setType(AccountType.SAVINGS);
        form.setInitialBalance(new BigDecimal("999"));
        form.setSharedWithFamily(false);

        accountService.update(anna.getId(), card.getId(), form);

        Account saved = accountRepository.findById(card.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Копилка");
        assertThat(saved.getType()).isEqualTo(AccountType.SAVINGS);
        assertThat(saved.isSharedWithFamily()).isFalse();
        assertThat(saved.getBalance()).isEqualByComparingTo("100");
    }

    @Test
    void foreignAccountIsNotFound() {
        Account borisCard = fixtures.createAccount(boris, "Карта Бориса", "10", true);
        Long id = borisCard.getId();

        assertThatThrownBy(() -> accountService.getOwned(anna.getId(), id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> accountService.toggleArchive(anna.getId(), id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> accountService.setVisibility(anna.getId(), id, false)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> accountService.delete(anna.getId(), id)).isInstanceOf(NotFoundException.class);
        assertThat(accountRepository.findById(id)).get().extracting(Account::isSharedWithFamily).isEqualTo(true);
    }

    @Test
    void archivedAccountIsHiddenFromForms() {
        Account card = fixtures.createAccount(anna, "Карта", "0", true);

        assertThat(accountService.toggleArchive(anna.getId(), card.getId())).isTrue();

        assertThat(accountService.activeAccounts(anna.getId())).extracting(Account::getName).containsExactly("Наличные");
        assertThat(accountService.toggleArchive(anna.getId(), card.getId())).isFalse();
    }

    @Test
    void accountWithOperationsCannotBeDeleted() {
        Account card = fixtures.createAccount(anna, "Карта", "0", true);
        Transaction tx = new Transaction();
        tx.setUser(anna);
        tx.setAccount(card);
        tx.setType(TransactionType.EXPENSE);
        tx.setAmount(new BigDecimal("5"));
        tx.setTitle("Кофе");
        tx.setOperationDate(LocalDate.now());
        tx.setCategory(fixtures.standardCategory("Кафе и рестораны"));
        transactionRepository.save(tx);

        assertThatThrownBy(() -> accountService.delete(anna.getId(), card.getId()))
                .isInstanceOf(BusinessException.class);
        assertThat(accountRepository.existsById(card.getId())).isTrue();
    }

    @Test
    void emptyAccountIsDeleted() {
        Account card = fixtures.createAccount(anna, "Карта", "0", true);

        accountService.delete(anna.getId(), card.getId());

        assertThat(accountRepository.existsById(card.getId())).isFalse();
    }
}
