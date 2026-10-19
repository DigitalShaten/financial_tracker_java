package com.fintrack.support;

import com.fintrack.dto.AccountForm;
import com.fintrack.dto.RegisterForm;
import com.fintrack.dto.TransactionForm;
import com.fintrack.entity.Account;
import com.fintrack.entity.AccountType;
import com.fintrack.entity.Category;
import com.fintrack.entity.CategoryType;
import com.fintrack.entity.Role;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.repository.CategoryRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.AccountService;
import com.fintrack.service.FamilyService;
import com.fintrack.service.TelegramLinkService;
import com.fintrack.service.TransactionService;
import com.fintrack.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;

@TestComponent
@RequiredArgsConstructor
public class TestFixtures {

    public static final String PASSWORD = "secret123";

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accountService;
    private final CategoryRepository categoryRepository;
    private final TransactionService transactionService;
    private final FamilyService familyService;
    private final TelegramLinkService telegramLinkService;

    /** Регистрирует пользователя так же, как форма регистрации (со счётом "Наличные"). */
    public User createUser(String name, String email) {
        RegisterForm form = new RegisterForm();
        form.setName(name);
        form.setEmail(email);
        form.setPassword(PASSWORD);
        form.setCurrency("BYN");
        return userService.register(form);
    }

    public User createAdmin(String email) {
        User admin = new User();
        admin.setEmail(email);
        admin.setName("Админ");
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setRole(Role.ADMIN);
        return userRepository.save(admin);
    }

    public Account createAccount(User user, String name, String balance, boolean shared) {
        AccountForm form = new AccountForm();
        form.setName(name);
        form.setType(AccountType.CARD);
        form.setInitialBalance(new BigDecimal(balance));
        form.setSharedWithFamily(shared);
        return accountService.create(user.getId(), form);
    }

    /** Стандартная категория по названию (из миграции V2). */
    public Category standardCategory(String name) {
        return categoryRepository.findAllByUserIsNullOrderByTypeAscNameAsc().stream()
                .filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    public Category firstStandard(CategoryType type) {
        return categoryRepository.findAllByUserIsNullOrderByTypeAscNameAsc().stream()
                .filter(c -> c.getType() == type).findFirst().orElseThrow();
    }

    /** Расход "Продукты" на первом счёте пользователя; возвращает id операции. */
    public Long createExpenseFor(User user, String title, String amount) {
        Account account = accountService.activeAccounts(user.getId()).get(0);
        return expense(user, account, title, amount, "Продукты", transactionService.today(user.getId())).getId();
    }

    public com.fintrack.entity.Transaction expense(User user, Account account, String title, String amount,
                                                  String category, LocalDate date) {
        return operation(user, TransactionType.EXPENSE, account, null, title, amount, category, date);
    }

    public com.fintrack.entity.Transaction income(User user, Account account, String title, String amount,
                                                 LocalDate date) {
        return operation(user, TransactionType.INCOME, account, null, title, amount, "Зарплата", date);
    }

    public com.fintrack.entity.Transaction operation(User user, TransactionType type, Account account, Account target,
                                                    String title, String amount, String category, LocalDate date) {
        TransactionForm form = new TransactionForm();
        form.setType(type);
        form.setTitle(title);
        form.setAmount(new BigDecimal(amount));
        form.setAccountId(account.getId());
        form.setTargetAccountId(target == null ? null : target.getId());
        form.setCategoryId(category == null ? null : standardCategory(category).getId());
        form.setOperationDate(date);
        return transactionService.create(user.getId(), form);
    }

    /** Семья: первый пользователь - владелец, остальные вступают по коду. */
    public void family(String name, User owner, User... members) {
        familyService.create(owner.getId(), name);
        for (User member : members) {
            String code = familyService.createInvite(owner.getId()).getCode();
            familyService.join(member.getId(), code);
        }
    }

    /** Подключает Telegram-чат пользователя так же, как это делает бот: код с сайта -> сообщение боту. */
    public void linkTelegram(User user, long chatId) {
        String code = telegramLinkService.createCode(user.getId()).getLinkCode();
        telegramLinkService.link(code, chatId);
    }

    public CustomUserDetails principalOf(User user) {
        return CustomUserDetails.from(userRepository.findById(user.getId()).orElseThrow());
    }
}
