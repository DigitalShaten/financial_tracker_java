package com.fintrack.service;

import com.fintrack.dto.*;
import com.fintrack.entity.*;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.*;
import com.fintrack.service.calc.GoalCalculator;
import com.fintrack.service.calc.GoalPlan;
import com.fintrack.util.DateFormat;
import com.fintrack.util.MoneyFormat;
import com.fintrack.util.Plurals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Цели: личные и семейные, пополнения, план (взнос в месяц), реалистичность и статус месяца.
 */
@Service
@RequiredArgsConstructor
public class GoalService {

    private static final BigDecimal SLIDER_MAX_PRICE = new BigDecimal("300000");

    private final GoalRepository goalRepository;
    private final GoalContributionRepository contributionRepository;
    private final FamilyMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AccessService accessService;
    private final Clock clock;

    // ---------------------------------------------------------------- чтение

    /** Видимые пользователю цели: его личные и все семейные цели его семьи. */
    @Transactional(readOnly = true)
    public List<Goal> visibleGoals(Long userId) {
        List<Goal> goals = new ArrayList<>(goalRepository.findAllByOwner_IdAndFamilyIsNullOrderByCreatedAtAscIdAsc(userId));
        memberRepository.findByUser_Id(userId).ifPresent(m ->
                goals.addAll(goalRepository.findAllByFamily_IdOrderByCreatedAtAscIdAsc(m.getFamily().getId())));
        return goals;
    }

    /** Видимая цель или 404. */
    @Transactional(readOnly = true)
    public Goal getVisible(Long userId, Long goalId) {
        return visibleGoals(userId).stream().filter(g -> g.getId().equals(goalId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Цель не найдена"));
    }

    /** Изменять и удалять: личную — создатель, семейную — создатель или владелец семьи. */
    @Transactional(readOnly = true)
    public boolean canEdit(Long userId, Goal goal) {
        if (goal.getOwner().getId().equals(userId)) {
            return true;
        }
        if (!goal.isFamilyGoal()) {
            return false;
        }
        return memberRepository.findByUser_Id(userId)
                .filter(m -> m.getFamily().getId().equals(goal.getFamily().getId()))
                .map(m -> m.getRole() == FamilyRole.OWNER).orElse(false);
    }

    @Transactional(readOnly = true)
    public BigDecimal saved(Long goalId) {
        return savedByGoal(List.of(goalId)).getOrDefault(goalId, BigDecimal.ZERO);
    }

    /** Месяцев до цели (минимум 1) по часовому поясу пользователя. */
    public int monthsLeft(Goal goal, ZoneId zone) {
        long months = ChronoUnit.MONTHS.between(YearMonth.now(clock.withZone(zone)), YearMonth.from(goal.getTargetDate()));
        return (int) Math.max(1, months);
    }

    /** План цели: сколько откладывать в месяц с учётом уже накопленного. */
    @Transactional(readOnly = true)
    public GoalPlan plan(Goal goal, BigDecimal saved, ZoneId zone) {
        return GoalCalculator.calculate(goal.getTargetAmount(), monthsLeft(goal, zone),
                goal.getInflationRate(), goal.getAnnualRate(), saved);
    }

    // ---------------------------------------------------------------- изменение

    @Transactional
    public Goal create(Long userId, GoalForm form) {
        return create(userId, form.getName(), form.getPrice(), targetDateAfterYears(userId, form.getYears()),
                form.getInflation(), form.getRate(), form.isFamily());
    }

    /** Создание с точной датой цели (используется и демо-данными). */
    @Transactional
    public Goal create(Long userId, String name, BigDecimal price, LocalDate targetDate,
                       BigDecimal inflation, BigDecimal rate, boolean family) {
        Goal goal = new Goal();
        goal.setOwner(userRepository.getReferenceById(userId));
        if (family) {
            FamilyMember member = memberRepository.findByUser_Id(userId)
                    .orElseThrow(() -> new BusinessException("Семейную цель можно создать, только состоя в семье"));
            goal.setFamily(member.getFamily());
        }
        goal.setName(name.trim());
        goal.setTargetAmount(price);
        goal.setTargetDate(targetDate.withDayOfMonth(1));
        goal.setInflationRate(inflation);
        goal.setAnnualRate(rate);
        return goalRepository.save(goal);
    }

    /** Сохранить план: ползунок «Срок, лет» задаёт дату — первое число месяца через годы × 12 месяцев. */
    @Transactional
    public void updatePlan(Long userId, Long goalId, GoalPlanForm form) {
        Goal goal = getEditable(userId, goalId);
        goal.setTargetAmount(form.getPrice());
        goal.setTargetDate(targetDateAfterYears(userId, form.getYears()));
        goal.setInflationRate(form.getInflation());
        goal.setAnnualRate(form.getRate());
    }

    @Transactional
    public void delete(Long userId, Long goalId) {
        goalRepository.delete(getEditable(userId, goalId));
    }

    /** Пополнить можно любую видимую цель (семейную — любой участник семьи). */
    @Transactional
    public GoalContribution contribute(Long userId, Long goalId, BigDecimal amount, LocalDate date) {
        Goal goal = getVisible(userId, goalId);
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessException("Сумма пополнения — больше нуля, не больше двух знаков после запятой");
        }
        GoalContribution contribution = new GoalContribution();
        contribution.setGoal(goal);
        contribution.setUser(userRepository.getReferenceById(userId));
        contribution.setAmount(amount);
        contribution.setContributedAt(date);
        return contributionRepository.save(contribution);
    }

    @Transactional
    public GoalContribution contributeToday(Long userId, Long goalId, BigDecimal amount) {
        return contribute(userId, goalId, amount, LocalDate.now(clock.withZone(zone(userId))));
    }

    private Goal getEditable(Long userId, Long goalId) {
        Goal goal = getVisible(userId, goalId);
        if (!canEdit(userId, goal)) {
            throw new BusinessException("Изменять семейную цель может её создатель или владелец семьи");
        }
        return goal;
    }

    private LocalDate targetDateAfterYears(Long userId, int years) {
        return YearMonth.now(clock.withZone(zone(userId))).plusMonths(years * 12L).atDay(1);
    }

    // ---------------------------------------------------------------- реалистичность

    /**
     * Свободные деньги — среднее (доходы − расходы) за 3 последних полных месяца
     * по видимым счетам: для личной цели — свои счета, для семейной — режим «Вместе».
     */
    @Transactional(readOnly = true)
    public BigDecimal freeCash(Long userId, boolean family) {
        Set<Long> accounts = accessService.visibleAccountIds(userId, family ? ViewMode.all() : ViewMode.me());
        YearMonth current = YearMonth.now(clock.withZone(zone(userId)));
        LocalDate from = current.minusMonths(3).atDay(1);
        LocalDate to = current.minusMonths(1).atEndOfMonth();
        BigDecimal income = transactionRepository.sumByTypeOrZero(accounts, TransactionType.INCOME, from, to);
        BigDecimal expense = transactionRepository.sumByTypeOrZero(accounts, TransactionType.EXPENSE, from, to);
        return income.subtract(expense).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
    }

    /** Три порога: ≤ 75% — достижима, ≤ 100% — впритык, иначе — не успеть; свободных денег нет — отдельно. */
    public RealismStatus assessRealism(BigDecimal payment, BigDecimal freeCash, String currency) {
        if (freeCash.signum() <= 0) {
            return new RealismStatus("Свободных денег в последние месяцы не оставалось",
                    "За три последних месяца расходы не меньше доходов — сначала стоит сократить траты.", false);
        }
        BigDecimal ratio = payment.divide(freeCash, 4, RoundingMode.HALF_UP);
        int percent = ratio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
        if (ratio.compareTo(new BigDecimal("0.75")) <= 0) {
            return new RealismStatus("Цель достижима", "Взнос — " + percent
                    + "% от среднего свободного остатка за 3 месяца (" + MoneyFormat.formatWhole(freeCash, currency)
                    + " в месяц).", true);
        }
        if (ratio.compareTo(BigDecimal.ONE) <= 0) {
            return new RealismStatus("Получится, но впритык", "Взнос забирает " + percent
                    + "% свободных денег. Любые непредвиденные траты собьют план — подумайте о сроке чуть длиннее.", false);
        }
        return new RealismStatus("При текущих тратах не успеть", "Не хватает "
                + MoneyFormat.formatWhole(payment.subtract(freeCash), currency)
                + " в месяц. Увеличьте срок или сократите расходы на эту сумму.", false);
    }

    /** «План на октябрь выполнен, опережаете на X» или «Осталось внести X в этом месяце». */
    @Transactional(readOnly = true)
    public String monthStatus(Goal goal, BigDecimal payment, ZoneId zone, String currency) {
        YearMonth month = YearMonth.now(clock.withZone(zone));
        BigDecimal paid = Optional.ofNullable(contributionRepository.sumByGoalBetween(goal.getId(),
                month.atDay(1), month.atEndOfMonth())).orElse(BigDecimal.ZERO);
        if (paid.compareTo(payment) >= 0) {
            return "План на " + DateFormat.monthName(month.getMonth()) + " выполнен, опережаете на "
                    + MoneyFormat.formatWhole(paid.subtract(payment), currency);
        }
        return "Осталось внести " + MoneyFormat.formatWhole(payment.subtract(paid), currency) + " в этом месяце";
    }

    // ---------------------------------------------------------------- для обзора и страницы

    /**
     * Плановые взносы за месяц, которые уменьшают «Можно потратить сегодня»: личные цели пользователя
     * плюс его доля в семейных (взнос / число участников). В режиме «Вместе» — семейные цели целиком,
     * в режиме участника — его доля в семейных целях.
     */
    @Transactional(readOnly = true)
    public BigDecimal plannedMonthlySavings(Long userId, ViewMode mode) {
        ZoneId zone = zone(userId);
        List<Goal> goals = visibleGoals(userId);
        Map<Long, BigDecimal> saved = savedByGoal(goals.stream().map(Goal::getId).toList());
        long members = Math.max(1, accessService.familyMembers(userId).size());
        YearMonth current = YearMonth.now(clock.withZone(zone));
        BigDecimal total = BigDecimal.ZERO;
        for (Goal goal : goals) {
            if (YearMonth.from(goal.getTargetDate()).isBefore(current)) {
                continue; // срок прошёл — цель неактивна
            }
            BigDecimal payment = plan(goal, saved.getOrDefault(goal.getId(), BigDecimal.ZERO), zone).monthlyPayment();
            if (!goal.isFamilyGoal()) {
                if (mode.includesMe()) {
                    total = total.add(payment);
                }
            } else if (mode.isAll()) {
                total = total.add(payment);
            } else {
                total = total.add(payment.divide(BigDecimal.valueOf(members), 2, RoundingMode.HALF_UP));
            }
        }
        return total;
    }

    /** До трёх целей для обзора; в режиме участника — только семейные. */
    @Transactional(readOnly = true)
    public List<GoalMini> miniGoals(Long userId, ViewMode mode, String currency) {
        List<Goal> goals = visibleGoals(userId).stream()
                .filter(g -> mode.includesMe() || g.isFamilyGoal())
                .limit(3).toList();
        Map<Long, BigDecimal> saved = savedByGoal(goals.stream().map(Goal::getId).toList());
        return goals.stream().map(g -> {
            BigDecimal s = saved.getOrDefault(g.getId(), BigDecimal.ZERO);
            return new GoalMini(g.getId(), g.getName(), g.isFamilyGoal(), MoneyFormat.formatWhole(s),
                    MoneyFormat.formatWhole(g.getTargetAmount(), currency), percent(s, g.getTargetAmount()));
        }).toList();
    }

    /** Карточки целей (активная выделяется рамкой). */
    @Transactional(readOnly = true)
    public List<GoalCard> cards(Long userId, Long activeId) {
        ZoneId zone = zone(userId);
        String currency = currency(userId);
        List<Goal> goals = visibleGoals(userId);
        Map<Long, BigDecimal> saved = savedByGoal(goals.stream().map(Goal::getId).toList());
        return goals.stream().map(g -> {
            BigDecimal s = saved.getOrDefault(g.getId(), BigDecimal.ZERO);
            BigDecimal future = plan(g, s, zone).futureCost();
            return new GoalCard(g.getId(), g.getName(), g.isFamilyGoal(),
                    "Срок: " + DateFormat.monthLower(YearMonth.from(g.getTargetDate())), percent(s, future),
                    MoneyFormat.formatWhole(s) + " из " + MoneyFormat.formatWhole(future, currency),
                    g.getId().equals(activeId));
        }).toList();
    }

    /** Всё о выбранной цели для страницы «Цели». */
    @Transactional(readOnly = true)
    public GoalDetail detail(Long userId, Long goalId) {
        Goal goal = getVisible(userId, goalId);
        ZoneId zone = zone(userId);
        String currency = currency(userId);
        BigDecimal saved = saved(goal.getId());
        int months = monthsLeft(goal, zone);
        GoalPlan plan = plan(goal, saved, zone);
        BigDecimal freeCash = freeCash(userId, goal.isFamilyGoal());
        int years = (int) Math.min(20, Math.max(1, Math.round(months / 12.0)));

        List<ContributorTotal> totals = contributionRepository.contributors(goal.getId());
        BigDecimal sum = totals.stream().map(ContributorTotal::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Contributor> contributors = totals.stream().map(t -> new Contributor(t.name(),
                CurrentUserView.initialOf(t.name()), t.userId().equals(userId),
                MoneyFormat.formatWhole(t.total(), currency), percent(t.total(), sum), t.total())).toList();
        String note = goal.isFamilyGoal()
                ? monthStatus(goal, plan.monthlyPayment(), zone, currency)
                : "Личная цель, видна только вам · " + monthStatus(goal, plan.monthlyPayment(), zone, currency);

        return new GoalDetail(goal.getId(), goal.getName(), goal.isFamilyGoal(), canEdit(userId, goal),
                goal.getTargetAmount(), years, goal.getInflationRate(), goal.getAnnualRate(),
                SLIDER_MAX_PRICE.max(goal.getTargetAmount()),
                saved, MoneyFormat.formatWhole(saved, currency), freeCash,
                YearMonth.now(clock.withZone(zone)).toString(),
                MoneyFormat.formatWhole(plan.monthlyPayment()),
                Plurals.count(months, "ежемесячный взнос", "ежемесячных взноса", "ежемесячных взносов")
                        + ", до: " + DateFormat.monthLower(YearMonth.from(goal.getTargetDate())),
                MoneyFormat.formatWhole(plan.futureCost(), currency),
                MoneyFormat.formatWhole(plan.totalContributions(), currency),
                "+ " + MoneyFormat.formatWhole(plan.interestEarned(), currency),
                "+ " + MoneyFormat.formatWhole(plan.inflationIncrease(), currency),
                assessRealism(plan.monthlyPayment(), freeCash, currency), contributors, note,
                MoneyFormat.formatWhole(SLIDER_MAX_PRICE.max(goal.getTargetAmount()), currency));
    }

    /**
     * Цели для отчёта за месяц: процент от стоимости и «идёте по плану», если пополнения за месяц
     * не меньше планового взноса. Личный отчёт — свои личные и семейные цели, семейный — только семейные.
     */
    @Transactional(readOnly = true)
    public List<GoalProgress> reportGoals(Long userId, boolean familyOnly, YearMonth month) {
        ZoneId zone = zone(userId);
        List<Goal> goals = visibleGoals(userId).stream().filter(g -> !familyOnly || g.isFamilyGoal()).toList();
        Map<Long, BigDecimal> saved = savedByGoal(goals.stream().map(Goal::getId).toList());
        return goals.stream().map(g -> {
            BigDecimal s = saved.getOrDefault(g.getId(), BigDecimal.ZERO);
            BigDecimal payment = plan(g, s, zone).monthlyPayment();
            BigDecimal paid = Optional.ofNullable(contributionRepository.sumByGoalBetween(g.getId(),
                    month.atDay(1), month.atEndOfMonth())).orElse(BigDecimal.ZERO);
            boolean onTrack = payment.signum() > 0 && paid.compareTo(payment) >= 0;
            return new GoalProgress(g.getName(), percent(s, g.getTargetAmount()), onTrack);
        }).toList();
    }

    /** Сколько отложено на цели за месяц: в личном отчёте — пополнения пользователя, в семейном — пополнения семейных целей. */
    @Transactional(readOnly = true)
    public BigDecimal savedInMonth(Long userId, boolean family, YearMonth month) {
        BigDecimal sum;
        if (family) {
            List<Long> familyGoals = visibleGoals(userId).stream().filter(Goal::isFamilyGoal).map(Goal::getId).toList();
            sum = familyGoals.isEmpty() ? null
                    : contributionRepository.sumByGoalsBetween(familyGoals, month.atDay(1), month.atEndOfMonth());
        } else {
            sum = contributionRepository.sumByUsersBetween(List.of(userId), month.atDay(1), month.atEndOfMonth());
        }
        return sum == null ? BigDecimal.ZERO : sum;
    }

    private Map<Long, BigDecimal> savedByGoal(List<Long> goalIds) {
        if (goalIds.isEmpty()) {
            return new HashMap<>();
        }
        return contributionRepository.sumsByGoals(goalIds).stream()
                .collect(Collectors.toMap(GoalSum::goalId, GoalSum::total));
    }

    private static int percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() <= 0) {
            return 0;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100)).intValue();
    }

    private ZoneId zone(Long userId) {
        return ZoneId.of(userRepository.findById(userId).map(User::getTimeZone).orElse("Europe/Minsk"));
    }

    private String currency(Long userId) {
        return userRepository.findById(userId).map(User::getCurrency).orElse("BYN");
    }
}
