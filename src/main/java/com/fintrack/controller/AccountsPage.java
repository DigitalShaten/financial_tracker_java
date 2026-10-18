package com.fintrack.controller;

import com.fintrack.dto.AccountForm;
import com.fintrack.dto.CategoryForm;
import com.fintrack.service.*;
import com.fintrack.util.DateFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.YearMonth;

/**
 * Собирает модель страницы «Счета и бюджеты». Нужна нескольким контроллерам:
 * при ошибке в любой форме страница показывается заново со всеми панелями.
 */
@Component
@RequiredArgsConstructor
public class AccountsPage {

    private final AccountService accountService;
    private final CategoryService categoryService;
    private final BudgetService budgetService;
    private final AccessService accessService;
    private final TransactionService transactionService;

    public void fill(Model model, Long userId, String month) {
        model.addAttribute("accounts", accountService.accountViews(userId));
        model.addAttribute("ownCategories", categoryService.own(userId));
        model.addAttribute("standardCategories", categoryService.standard());
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm", new AccountForm());
        }
        if (!model.containsAttribute("categoryForm")) {
            model.addAttribute("categoryForm", new CategoryForm());
        }
        YearMonth current = YearMonth.from(transactionService.today(userId));
        YearMonth budgetMonth = month == null ? current : DashboardController.parseMonth(month, current.plusMonths(1));
        model.addAttribute("budgetMonth", budgetMonth.toString());
        model.addAttribute("budgetMonthTitle", DateFormat.monthLower(budgetMonth));
        model.addAttribute("budgetPrev", budgetMonth.minusMonths(1).toString());
        // Бюджет можно задать заранее — на следующий месяц
        model.addAttribute("budgetNext", budgetMonth.isAfter(current) ? null : budgetMonth.plusMonths(1).toString());
        model.addAttribute("budgetRows", budgetService.editorRows(userId,
                accessService.visibleAccountIds(userId, ViewMode.me()), budgetMonth));
    }
}
