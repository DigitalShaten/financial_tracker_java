package com.fintrack.controller;

import com.fintrack.dto.RegisterForm;
import com.fintrack.exception.BusinessException;
import com.fintrack.service.UserService;
import com.fintrack.util.Choices;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout,
                        @RequestParam(required = false) String registered,
                        @RequestParam(required = false) String deleted,
                        Authentication authentication, HttpSession session, Model model) {
        if (HomeController.isLoggedIn(authentication)) {
            return "redirect:/";
        }
        if (error != null) {
            Object exception = session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            boolean blocked = exception instanceof DisabledException || exception instanceof LockedException;
            model.addAttribute("errorMessage", blocked
                    ? "Аккаунт заблокирован администратором"
                    : "Неверный e-mail или пароль");
        }
        if (registered != null) {
            model.addAttribute("infoMessage", "Аккаунт создан, войдите");
        } else if (logout != null) {
            model.addAttribute("infoMessage", "Вы вышли из аккаунта");
        } else if (deleted != null) {
            model.addAttribute("infoMessage", "Аккаунт удалён");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Authentication authentication, Model model) {
        if (HomeController.isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("form", new RegisterForm());
        model.addAttribute("currencies", Choices.CURRENCIES);
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult binding, Model model) {
        if (!binding.hasErrors()) {
            try {
                userService.register(form);
                return "redirect:/login?registered";
            } catch (BusinessException e) {
                binding.rejectValue("email", "taken", e.getMessage());
            }
        }
        model.addAttribute("currencies", Choices.CURRENCIES);
        return "auth/register";
    }
}
