package com.fintrack.controller;

import com.fintrack.dto.BudgetForm;
import com.fintrack.exception.BusinessException;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.BudgetService;
import com.fintrack.util.DateFormat;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;

/**
 * Бюджеты: установка лимита и копирование с прошлого месяца. Панель — на странице «Счета и бюджеты».
 */
@Controller
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping("/budgets")
    public String setLimit(@AuthenticationPrincipal CustomUserDetails me,
                           @Valid @ModelAttribute BudgetForm form, BindingResult binding,
                           RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("toast", binding.getAllErrors().get(0).getDefaultMessage());
        } else {
            YearMonth month = YearMonth.parse(form.getMonth());
            try {
                budgetService.setLimit(me.id(), form.getCategoryId(), month, form.getLimitAmount());
                redirect.addFlashAttribute("toast", form.getLimitAmount() == null || form.getLimitAmount().signum() == 0
                        ? "Бюджет убран" : "Лимит сохранён");
            } catch (BusinessException e) {
                redirect.addFlashAttribute("toast", e.getMessage());
            }
        }
        redirect.addAttribute("month", form.getMonth());
        return "redirect:/accounts#budgets";
    }

    @PostMapping("/budgets/copy-previous")
    public String copyPrevious(@AuthenticationPrincipal CustomUserDetails me,
                               @RequestParam String month, RedirectAttributes redirect) {
        YearMonth target = YearMonth.parse(month);
        int copied = budgetService.copyPrevious(me.id(), target);
        redirect.addFlashAttribute("toast", copied == 0
                ? "Копировать нечего: в " + DateFormat.monthPrepositional(target.minusMonths(1).getMonth()) + " новых лимитов нет"
                : "Скопировано лимитов: " + copied);
        redirect.addAttribute("month", month);
        return "redirect:/accounts#budgets";
    }
}
