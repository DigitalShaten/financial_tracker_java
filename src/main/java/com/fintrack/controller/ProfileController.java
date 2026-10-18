package com.fintrack.controller;

import com.fintrack.dto.DeleteAccountForm;
import com.fintrack.dto.PasswordForm;
import com.fintrack.dto.ProfileForm;
import com.fintrack.exception.BusinessException;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.UserService;
import com.fintrack.util.Choices;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Профиль: данные, пароль, удаление учётной записи.
 */
@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping("/profile")
    public String page(@AuthenticationPrincipal CustomUserDetails me, Model model) {
        fill(model, me.id());
        return "profile/index";
    }

    @PostMapping("/profile")
    public String update(@AuthenticationPrincipal CustomUserDetails me,
                         @Valid @ModelAttribute("profileForm") ProfileForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                userService.updateProfile(me.id(), form);
                redirect.addFlashAttribute("toast", "Профиль сохранён");
                return "redirect:/profile";
            } catch (BusinessException e) {
                binding.rejectValue("timeZone", "invalid", e.getMessage());
            }
        }
        fill(model, me.id());
        return "profile/index";
    }

    @PostMapping("/profile/password")
    public String password(@AuthenticationPrincipal CustomUserDetails me,
                           @Valid @ModelAttribute("passwordForm") PasswordForm form, BindingResult binding,
                           Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                userService.changePassword(me.id(), form);
                redirect.addFlashAttribute("toast", "Пароль изменён");
                return "redirect:/profile";
            } catch (BusinessException e) {
                binding.reject("password", e.getMessage());
            }
        }
        fill(model, me.id());
        return "profile/index";
    }

    @PostMapping("/profile/delete")
    public String delete(@AuthenticationPrincipal CustomUserDetails me,
                         @Valid @ModelAttribute("deleteForm") DeleteAccountForm form, BindingResult binding,
                         Model model, Authentication authentication,
                         HttpServletRequest request, HttpServletResponse response) {
        if (!binding.hasErrors()) {
            try {
                userService.deleteAccount(me.id(), form.getPassword());
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                return "redirect:/login?deleted";
            } catch (BusinessException e) {
                binding.reject("delete", e.getMessage());
            }
        }
        fill(model, me.id());
        return "profile/index";
    }

    private void fill(Model model, Long userId) {
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", userService.profileForm(userId));
        }
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new PasswordForm());
        }
        if (!model.containsAttribute("deleteForm")) {
            model.addAttribute("deleteForm", new DeleteAccountForm());
        }
        model.addAttribute("currencies", Choices.CURRENCIES);
        model.addAttribute("timeZones", Choices.TIME_ZONES);
    }
}
