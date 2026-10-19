package com.fintrack.controller.admin;

import com.fintrack.exception.BusinessException;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Пользователи: список с поиском по e-mail и пагинацией, блокировка и разблокировка.
 */
@Controller
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminService adminService;

    @GetMapping("/admin")
    public String index() {
        return "redirect:/admin/users";
    }

    @GetMapping("/admin/users")
    public String users(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                        Model model) {
        model.addAttribute("q", q);
        model.addAttribute("users", adminService.users(q, page));
        return "admin/users";
    }

    @PostMapping("/admin/users/{id}/block")
    public String block(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                        @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                        RedirectAttributes redirect) {
        return change(me, id, false, "Пользователь заблокирован", q, page, redirect);
    }

    @PostMapping("/admin/users/{id}/unblock")
    public String unblock(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id,
                          @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                          RedirectAttributes redirect) {
        return change(me, id, true, "Пользователь разблокирован", q, page, redirect);
    }

    private String change(CustomUserDetails me, Long id, boolean enabled, String message, String q, int page,
                          RedirectAttributes redirect) {
        try {
            adminService.setEnabled(me.id(), id, enabled);
            redirect.addFlashAttribute("toast", message);
        } catch (BusinessException e) {
            redirect.addFlashAttribute("toast", e.getMessage());
        }
        // возвращаемся на ту же страницу списка с тем же поиском
        redirect.addAttribute("q", q);
        redirect.addAttribute("page", page);
        return "redirect:/admin/users";
    }
}
