package com.fintrack.controller;

import com.fintrack.dto.MemberView;
import com.fintrack.dto.NotificationSettingsForm;
import com.fintrack.exception.BusinessException;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.FamilyService;
import com.fintrack.service.MonthlyReportService;
import com.fintrack.service.TelegramLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.IntStream;

@Controller
@RequiredArgsConstructor
public class TelegramController {

    private final TelegramLinkService linkService;
    private final MonthlyReportService reportService;
    private final FamilyService familyService;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @GetMapping("/telegram")
    public String page(@AuthenticationPrincipal CustomUserDetails me, Model model) {
        model.addAttribute("settingsForm", linkService.settingsForm(me.id()));
        fill(model, me);
        return "telegram/index";
    }

    @PostMapping("/telegram/link-code")
    public String linkCode(@AuthenticationPrincipal CustomUserDetails me, RedirectAttributes redirect) {
        linkService.createCode(me.id());
        redirect.addFlashAttribute("toast", "Код создан. Отправьте его боту в Telegram");
        return "redirect:/telegram";
    }

    @PostMapping("/telegram/unlink")
    public String unlink(@AuthenticationPrincipal CustomUserDetails me, RedirectAttributes redirect) {
        linkService.unlink(me.id());
        redirect.addFlashAttribute("toast", "Бот отключён от аккаунта");
        return "redirect:/telegram";
    }

    @PostMapping("/telegram/settings")
    public String settings(@AuthenticationPrincipal CustomUserDetails me,
                           @Valid @ModelAttribute("settingsForm") NotificationSettingsForm form,
                           BindingResult binding, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            fill(model, me);
            return "telegram/index";
        }
        linkService.updateSettings(me.id(), form);
        redirect.addFlashAttribute("toast", "Настройки сохранены");
        return "redirect:/telegram";
    }

    @PostMapping("/telegram/test-report")
    public String testReport(@AuthenticationPrincipal CustomUserDetails me, RedirectAttributes redirect) {
        try {
            reportService.sendTestReport(me.id());
            redirect.addFlashAttribute("toast", "Тестовый отчёт отправлен в Telegram");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/telegram";
    }

    private void fill(Model model, CustomUserDetails me) {
        List<MemberView> others = familyService.findMembership(me.id()).isPresent()
                ? familyService.members(me.id()).stream().filter(m -> !m.me()).toList()
                : List.of();
        model.addAttribute("status", linkService.status(me.id()));
        model.addAttribute("otherMembers", others);
        model.addAttribute("botUsername", botUsername);
        model.addAttribute("preview", reportService.previewText(me.id()));
        model.addAttribute("reportMonth", reportService.previousMonth(me.id()).toString());
        model.addAttribute("days", IntStream.rangeClosed(1, 28).boxed().toList());
        model.addAttribute("hours", IntStream.rangeClosed(0, 23).boxed().toList());
    }
}
