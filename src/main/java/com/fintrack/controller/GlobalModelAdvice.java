package com.fintrack.controller;

import com.fintrack.dto.CurrentUserView;
import com.fintrack.security.CustomUserDetails;
import com.fintrack.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.math.BigDecimal;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final UserService userService;

    @ModelAttribute("currentUser")
    public CurrentUserView currentUser(@AuthenticationPrincipal CustomUserDetails me) {
        return me == null ? null : userService.currentUserView(me.id());
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        // Пробелы по краям убираются, пустая строка становится null
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
        // Суммы можно вводить с запятой и пробелами: "1 286,40"
        binder.registerCustomEditor(BigDecimal.class, new MoneyEditor());
    }
}
