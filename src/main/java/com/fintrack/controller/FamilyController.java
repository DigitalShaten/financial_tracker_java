package com.fintrack.controller;

import com.fintrack.exception.BusinessException;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.AccountService;
import com.fintrack.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;
    private final AccountService accountService;

    @GetMapping("/family")
    public String page(@AuthenticationPrincipal CustomUserDetails me, Model model) {
        fill(model, me, null);
        return "family/index";
    }

    /** Ссылка-приглашение открывает форму вступления с подставленным кодом. */
    @GetMapping("/family/join")
    public String joinForm(@AuthenticationPrincipal CustomUserDetails me,
                           @RequestParam(required = false) String code, Model model) {
        fill(model, me, code);
        return "family/index";
    }

    @PostMapping("/family")
    public String create(@AuthenticationPrincipal CustomUserDetails me, @RequestParam String name,
                         RedirectAttributes redirect) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            redirect.addFlashAttribute("toast", "Название семьи — от 1 до 100 символов");
            return "redirect:/family";
        }
        try {
            familyService.create(me.id(), name);
            redirect.addFlashAttribute("toast", "Семья создана. Пригласите участников по коду");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/family";
    }

    @PostMapping("/family/join")
    public String join(@AuthenticationPrincipal CustomUserDetails me, @RequestParam String code, Model model,
                       RedirectAttributes redirect) {
        try {
            familyService.join(me.id(), code);
            redirect.addFlashAttribute("toast", "Вы вступили в семью");
            return "redirect:/family";
        } catch (BusinessException e) {
            fill(model, me, code);
            model.addAttribute("joinError", e.getMessage());
            return "family/index";
        }
    }

    @PostMapping("/family/invites")
    public String invite(@AuthenticationPrincipal CustomUserDetails me, RedirectAttributes redirect) {
        familyService.createInvite(me.id());
        redirect.addFlashAttribute("toast", "Создан новый код, старый больше не работает");
        return "redirect:/family";
    }

    @PostMapping("/family/members/{userId}/remove")
    public String remove(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long userId,
                         RedirectAttributes redirect) {
        familyService.removeMember(me.id(), userId);
        redirect.addFlashAttribute("toast", "Участник исключён из семьи");
        return "redirect:/family";
    }

    @PostMapping("/family/leave")
    public String leave(@AuthenticationPrincipal CustomUserDetails me, RedirectAttributes redirect) {
        try {
            familyService.leave(me.id());
            redirect.addFlashAttribute("toast", "Вы вышли из семьи");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/family";
    }

    @PostMapping("/family/dissolve")
    public String dissolve(@AuthenticationPrincipal CustomUserDetails me, RedirectAttributes redirect) {
        familyService.dissolve(me.id());
        redirect.addFlashAttribute("toast", "Семья распущена. Все данные остались у участников");
        return "redirect:/family";
    }

    private void fill(Model model, CustomUserDetails me, String code) {
        var membership = familyService.findMembership(me.id());
        model.addAttribute("joinCode", code);
        if (membership.isEmpty()) {
            return;
        }
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        model.addAttribute("members", familyService.members(me.id()));
        model.addAttribute("invite", familyService.activeInvite(me.id(), baseUrl).orElse(null));
        model.addAttribute("myAccounts", accountService.accountViews(me.id()).stream().filter(a -> !a.archived()).toList());
    }
}
