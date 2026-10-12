package com.fintrack.service;

import com.fintrack.dto.CurrentUserView;
import com.fintrack.dto.RegistrationForm;
import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegistrationForm form) {
        if (userRepository.existsByEmailIgnoreCase(form.email())) {
            throw new BusinessException("Пользователь с таким e-mail уже зарегистрирован");
        }

        User user = new User();
        user.setName(form.name().trim());
        user.setEmail(form.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(form.password()));
        user.setCurrency(form.currency());
        user.setRole(Role.USER);
        user.setEnabled(true);
        userRepository.save(user);

        // Создаем стартовый счет «Наличные»
        accountService.createDefaultCashAccount(user);

        return user;
    }

    @Transactional(readOnly = true)
    public CurrentUserView currentUserView(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        return new CurrentUserView(
                user.getId(),
                user.getName(),
                user.getCurrency(),
                null,
                user.getRole() == Role.ADMIN
        );
    }
}