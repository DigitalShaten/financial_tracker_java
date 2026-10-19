package com.fintrack.repository;

import com.fintrack.entity.Account;
import com.fintrack.entity.AccountType;
import com.fintrack.entity.NotificationSettings;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private NotificationSettingsRepository settingsRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void twelveStandardCategoriesAreCreatedByMigration() {
        assertThat(categoryRepository.countByUserIsNull()).isEqualTo(12);
    }

    @Test
    void entitiesAreSavedAndLoaded() {
        User user = new User();
        user.setEmail("schema@test.by");
        user.setPasswordHash("hash");
        user.setName("Схема");
        userRepository.save(user);

        NotificationSettings settings = new NotificationSettings();
        settings.setUser(user);
        settingsRepository.save(settings);

        Account account = new Account();
        account.setUser(user);
        account.setName("Карта");
        account.setType(AccountType.CARD);
        account.setCurrency("BYN");
        account.setBalance(new BigDecimal("100.50"));
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setUser(user);
        tx.setAccount(account);
        tx.setType(TransactionType.EXPENSE);
        tx.setAmount(new BigDecimal("46.80"));
        tx.setTitle("Супермаркет");
        tx.setOperationDate(LocalDate.of(2026, 9, 18));
        tx.setCategory(categoryRepository.findAllByUserIsNullOrderByTypeAscNameAsc().get(0));
        transactionRepository.save(tx);

        entityManager.flush();
        entityManager.clear();

        Transaction loaded = transactionRepository.findByIdAndUser_Id(tx.getId(), user.getId()).orElseThrow();
        assertThat(loaded.getAmount()).isEqualByComparingTo("46.80");
        assertThat(settingsRepository.findById(user.getId())).get()
                .extracting(NotificationSettings::getReportHour).isEqualTo((short) 10);
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getVersion()).isZero();
    }
}
