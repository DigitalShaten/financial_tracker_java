package com.fintrack.controller;

import com.fintrack.dto.RegistrationForm;
import com.fintrack.exception.BusinessException;
import com.fintrack.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", RegistrationForm.empty());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form,
                           BindingResult binding) {
        if (binding.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.register(form);
        } catch (BusinessException e) {
            binding.rejectValue("email", "taken", e.getMessage());
            return "auth/register";
        }
        return "redirect:/login?registered";
    }
}
