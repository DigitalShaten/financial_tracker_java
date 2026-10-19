package com.fintrack.controller;

import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.DashboardService;
import com.fintrack.service.TransactionService;
import com.fintrack.service.ViewMode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final TransactionService transactionService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails me,
                            @RequestParam(required = false) String view,
                            @RequestParam(required = false) String month,
                            Model model) {
        YearMonth current = YearMonth.from(transactionService.today(me.id()));
        model.addAttribute("d", dashboardService.build(me.id(), ViewMode.parse(view), parseMonth(month, current)));
        return "dashboard";
    }

    /** Неверный или будущий месяц - показываем текущий. */
    static YearMonth parseMonth(String value, YearMonth current) {
        if (value == null || value.isBlank()) {
            return current;
        }
        try {
            YearMonth month = YearMonth.parse(value);
            return month.isAfter(current) ? current : month;
        } catch (DateTimeParseException e) {
            return current;
        }
    }
}
