package com.fintrack.config;

import com.fintrack.dto.AccountForm;
import com.fintrack.dto.RegisterForm;
import com.fintrack.dto.TransactionForm;
import com.fintrack.entity.Account;
import com.fintrack.entity.AccountType;
import com.fintrack.entity.Category;
import com.fintrack.entity.Goal;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.repository.CategoryRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.service.AccountService;
import com.fintrack.service.BudgetService;
import com.fintrack.service.FamilyService;
import com.fintrack.service.GoalService;
import com.fintrack.service.TransactionService;
import com.fintrack.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Random;

/**
 * Демо-данные (профиль {@code demo}): семья Ивановых, счета, около 160 операций за полгода, бюджеты и цели.
 * Всё создаётся через сервисы приложения - так же, как через веб-формы, поэтому балансы сходятся.
 * Случайные суммы - {@code new Random(42)}: при каждом запуске данные одинаковые.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataLoader implements ApplicationRunner {

    public static final String DMITRY_EMAIL = "dmitry@demo.by";
    public static final String ANNA_EMAIL = "anna@demo.by";
    public static final String PASSWORD = "demo12345";

    // бюджеты на текущий месяц
    private static final Map<String, Integer> DMITRY_BUDGETS = Map.of("Продукты", 270, "Кафе и рестораны", 110,
            "Транспорт", 100, "Квартира и счета", 175, "Развлечения", 80, "Здоровье", 55);
    private static final Map<String, Integer> ANNA_BUDGETS = Map.of("Продукты", 230, "Кафе и рестораны", 90,
            "Транспорт", 80, "Квартира и счета", 145, "Развлечения", 70, "Здоровье", 45);

    private final UserService userService;
    private final UserRepository userRepository;
    private final AccountService accountService;
    private final CategoryRepository categoryRepository;
    private final TransactionService transactionService;
    private final FamilyService familyService;
    private final BudgetService budgetService;
    private final GoalService goalService;

    private final Random random = new Random(42);

    // заполняются в run()
    private User dmitry;
    private User anna;
    private Account shared;
    private Account salaryCard;
    private Account dmitryCash;
    private Account piggyBank;
    private Account annaCard;
    private Account annaCash;
    private LocalDate today;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(DMITRY_EMAIL)) {
            log.info("Демо-данные уже есть — пропускаем");
            return;
        }
        createPeopleAndAccounts();
        today = transactionService.today(dmitry.getId());
        YearMonth current = YearMonth.from(today);

        for (int back = 5; back >= 0; back--) {
            createMonth(current.minusMonths(back));
        }
        createCurrentMonthCafe(current);
        createBudgets(current);
        createGoals(current);
        log.info("Демо-данные созданы: {} и {} (пароль {})", DMITRY_EMAIL, ANNA_EMAIL, PASSWORD);
    }

    /* ---------- люди, семья, счета ---------- */

    private void createPeopleAndAccounts() {
        dmitry = register("Дмитрий Иванов", DMITRY_EMAIL);
        anna = register("Анна Иванова", ANNA_EMAIL);

        familyService.create(dmitry.getId(), "Семья Ивановых");
        String code = familyService.createInvite(dmitry.getId()).getCode();
        familyService.join(anna.getId(), code);

        // "Наличные" создаются при регистрации (CASH, 0, видно семье)
        dmitryCash = accountService.activeAccounts(dmitry.getId()).get(0);
        annaCash = accountService.activeAccounts(anna.getId()).get(0);
        shared = account(dmitry, "Общий счёт", AccountType.CARD, "2500", true);
        salaryCard = account(dmitry, "Зарплатная карта", AccountType.CARD, "800", true);
        piggyBank = account(dmitry, "Личная копилка", AccountType.SAVINGS, "400", false);
        annaCard = account(anna, "Карта Анны", AccountType.CARD, "600", true);
    }

    private User register(String name, String email) {
        RegisterForm form = new RegisterForm();
        form.setName(name);
        form.setEmail(email);
        form.setPassword(PASSWORD);
        form.setCurrency("BYN");
        return userService.register(form);
    }

    private Account account(User owner, String name, AccountType type, String balance, boolean shared) {
        AccountForm form = new AccountForm();
        form.setName(name);
        form.setType(type);
        form.setInitialBalance(new BigDecimal(balance));
        form.setSharedWithFamily(shared);
        return accountService.create(owner.getId(), form);
    }

    /* ---------- операции одного месяца ---------- */

    private void createMonth(YearMonth month) {
        // зарплаты 2-5 числа
        income(dmitry, salaryCard, "Зарплата", money(2350, 2450), month.atDay(2 + random.nextInt(4)));
        income(anna, annaCard, "Зарплата", money(1850, 1950), month.atDay(2 + random.nextInt(4)));

        // продукты: каждые 2-4 дня, по очереди Дмитрий и Анна (для семьи - 2-3 раза в неделю)
        boolean dmitryTurn = true;
        for (int day = 1 + random.nextInt(3); day <= month.lengthOfMonth(); day += 2 + random.nextInt(3)) {
            String title = pick("Супермаркет", "Продукты на неделю", "Рынок", "Пекарня");
            if (dmitryTurn) {
                expense(dmitry, shared, title, "Продукты", money(32, 72), month.atDay(day));
            } else {
                expense(anna, annaCard, title, "Продукты", money(28, 62), month.atDay(day));
            }
            dmitryTurn = !dmitryTurn;
        }

        // коммунальные платежи раз в месяц, связь у Анны
        expense(dmitry, shared, "Коммунальные платежи", "Квартира и счета", money(140, 170), month.atDay(10));
        expense(anna, annaCard, "Интернет и мобильная связь", "Квартира и счета", money(28, 36), month.atDay(12));

        // транспорт
        expense(dmitry, salaryCard, "Такси", "Транспорт", money(8, 18), month.atDay(6 + random.nextInt(5)));
        expense(dmitry, dmitryCash, "Бензин", "Транспорт", money(45, 60), month.atDay(18 + random.nextInt(5)));
        expense(anna, annaCard, "Проездной", "Транспорт", money(30, 35), month.atDay(1 + random.nextInt(3)));
        expense(anna, annaCash, "Такси", "Транспорт", money(7, 15), month.atDay(20 + random.nextInt(5)));

        // кафе - в прошлых месяцах в пределах лимита; текущий месяц - отдельно
        if (!month.equals(YearMonth.from(today))) {
            expense(dmitry, salaryCard, "Кофейня у работы", "Кафе и рестораны", money(6, 12), month.atDay(8 + random.nextInt(5)));
            expense(dmitry, salaryCard, "Пиццерия", "Кафе и рестораны", money(30, 45), month.atDay(20 + random.nextInt(6)));
            expense(anna, annaCard, "Обед в кафе", "Кафе и рестораны", money(12, 20), month.atDay(9 + random.nextInt(5)));
            expense(anna, annaCard, "Кофейня у работы", "Кафе и рестораны", money(5, 9), month.atDay(22 + random.nextInt(5)));
        }

        // развлечения и здоровье
        expense(dmitry, salaryCard, pick("Кино", "Концерт", "Боулинг"), "Развлечения", money(20, 45), month.atDay(13 + random.nextInt(8)));
        expense(anna, annaCard, pick("Кино", "Подписка на музыку", "Книги"), "Развлечения", money(10, 30), month.atDay(15 + random.nextInt(8)));
        if (month.getMonthValue() % 2 == 0) {
            expense(anna, annaCard, "Аптека", "Здоровье", money(12, 30), month.atDay(16 + random.nextInt(6)));
        } else {
            expense(dmitry, dmitryCash, "Аптека", "Здоровье", money(10, 25), month.atDay(16 + random.nextInt(6)));
        }

        // переводы между своими счетами
        if (month.getMonthValue() % 2 == 0) {
            transfer(dmitry, salaryCard, shared, "Пополнение общего счёта", money(400, 600), month.atDay(5));
        } else {
            transfer(dmitry, salaryCard, piggyBank, "В копилку", new BigDecimal("100.00"), month.atDay(5));
        }
        // снятие наличных - чтобы балансы "Наличных" не уходили в минус
        transfer(dmitry, salaryCard, dmitryCash, "Снятие наличных", new BigDecimal("120.00"), month.atDay(4));
        transfer(anna, annaCard, annaCash, "Снятие наличных", new BigDecimal("20.00"), month.atDay(4));
    }

    /** Кафе в текущем месяце превышают лимит (Дмитрий 110, Анна 90) - чтобы было видно предупреждение. */
    private void createCurrentMonthCafe(YearMonth month) {
        int days = today.getDayOfMonth();
        expense(dmitry, salaryCard, "Кофейня у работы", "Кафе и рестораны", new BigDecimal("8.40"), month.atDay(Math.min(2, days)));
        expense(dmitry, salaryCard, "Ресторан", "Кафе и рестораны", new BigDecimal("86.50"), month.atDay(Math.max(1, days / 2)));
        expense(dmitry, salaryCard, "Пиццерия", "Кафе и рестораны", new BigDecimal("34.20"), month.atDay(days));
        expense(anna, annaCard, "Обед в кафе", "Кафе и рестораны", new BigDecimal("18.90"), month.atDay(Math.min(3, days)));
        expense(anna, annaCard, "День рождения подруги", "Кафе и рестораны", new BigDecimal("79.00"), month.atDay(days));
    }

    /* ---------- бюджеты и цели ---------- */

    private void createBudgets(YearMonth month) {
        DMITRY_BUDGETS.forEach((name, limit) ->
                budgetService.setLimit(dmitry.getId(), category(name).getId(), month, BigDecimal.valueOf(limit)));
        ANNA_BUDGETS.forEach((name, limit) ->
                budgetService.setLimit(anna.getId(), category(name).getId(), month, BigDecimal.valueOf(limit)));
    }

    private void createGoals(YearMonth current) {
        Goal flat = goalService.create(dmitry.getId(), "Квартира", new BigDecimal("95000"),
                current.plusMonths(60).atDay(1), new BigDecimal("5"), new BigDecimal("6"), true);
        Goal vacation = goalService.create(dmitry.getId(), "Отпуск летом 2027", new BigDecimal("3000"),
                nextOrSame(current, YearMonth.of(2027, 6)), new BigDecimal("4"), BigDecimal.ZERO, true);
        Goal laptop = goalService.create(dmitry.getId(), "Новый ноутбук", new BigDecimal("2200"),
                nextOrSame(current, YearMonth.of(2027, 3)), BigDecimal.ZERO, BigDecimal.ZERO, false);

        // "Квартира": Дмитрий 10 900 и Анна 7 500 - по одному платежу в каждый из 6 месяцев
        int[] dmitryFlat = {1800, 1800, 1800, 1800, 1850, 1850};
        int[] annaFlat = {1250, 1250, 1250, 1250, 1250, 1250};
        for (int i = 0; i < 6; i++) {
            LocalDate date = contributionDate(current.minusMonths(5 - i));
            goalService.contribute(dmitry.getId(), flat.getId(), BigDecimal.valueOf(dmitryFlat[i]), date);
            goalService.contribute(anna.getId(), flat.getId(), BigDecimal.valueOf(annaFlat[i]), date);
        }
        // "Отпуск": 600 и 650 двумя платежами
        for (int i = 0; i < 2; i++) {
            LocalDate date = contributionDate(current.minusMonths(1 - i));
            goalService.contribute(dmitry.getId(), vacation.getId(), new BigDecimal("300"), date);
            goalService.contribute(anna.getId(), vacation.getId(), new BigDecimal("325"), date);
        }
        // "Ноутбук": 640 одним платежом
        goalService.contribute(dmitry.getId(), laptop.getId(), new BigDecimal("640"), contributionDate(current.minusMonths(1)));
    }

    /** Если месяц цели уже прошёл, берём тот же месяц следующего года. */
    private static LocalDate nextOrSame(YearMonth current, YearMonth wanted) {
        YearMonth target = wanted;
        while (!target.isAfter(current)) {
            target = target.plusYears(1);
        }
        return target.atDay(1);
    }

    private LocalDate contributionDate(YearMonth month) {
        LocalDate date = month.atDay(6);
        return date.isAfter(today) ? today : date;
    }

    /* ---------- помощники ---------- */

    private void income(User user, Account account, String title, BigDecimal amount, LocalDate date) {
        save(user, TransactionType.INCOME, account, null, title, amount, category("Зарплата"), date);
    }

    private void expense(User user, Account account, String title, String category, BigDecimal amount, LocalDate date) {
        save(user, TransactionType.EXPENSE, account, null, title, amount, category(category), date);
    }

    private void transfer(User user, Account from, Account to, String title, BigDecimal amount, LocalDate date) {
        save(user, TransactionType.TRANSFER, from, to, title, amount, null, date);
    }

    /** Операции "из будущего" (позже сегодняшнего дня) не создаются. */
    private void save(User user, TransactionType type, Account account, Account target, String title,
                      BigDecimal amount, Category category, LocalDate date) {
        if (date.isAfter(today)) {
            return;
        }
        TransactionForm form = new TransactionForm();
        form.setType(type);
        form.setTitle(title);
        form.setAmount(amount);
        form.setAccountId(account.getId());
        form.setTargetAccountId(target == null ? null : target.getId());
        form.setCategoryId(category == null ? null : category.getId());
        form.setOperationDate(date);
        transactionService.create(user.getId(), form);
    }

    private Category category(String name) {
        return categoryRepository.findAllByUserIsNullOrderByTypeAscNameAsc().stream()
                .filter(c -> c.getName().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Нет стандартной категории «" + name + "»"));
    }

    /** Случайная сумма от min до max с копейками. */
    private BigDecimal money(int min, int max) {
        int cents = min * 100 + random.nextInt((max - min) * 100 + 1);
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
    }

    private String pick(String... options) {
        return options[random.nextInt(options.length)];
    }
}
