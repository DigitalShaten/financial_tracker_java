package com.fintrack.service;

import com.fintrack.dto.RegisterForm;
import com.fintrack.entity.Account;
import com.fintrack.entity.AccountType;
import com.fintrack.entity.NotificationSettings;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.FamilyMemberRepository;
import com.fintrack.repository.NotificationSettingsRepository;
import com.fintrack.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private NotificationSettingsRepository settingsRepository;
    @Mock
    private FamilyMemberRepository memberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private static RegisterForm form(String email) {
        RegisterForm form = new RegisterForm();
        form.setName("Анна");
        form.setEmail(email);
        form.setPassword("password1");
        form.setCurrency("BYN");
        return form;
    }

    @Test
    void takenEmailIsRejected() {
        when(userRepository.existsByEmail("anna@demo.by")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(form("anna@demo.by")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("уже зарегистрирован");
        verify(userRepository, never()).save(any());
    }

    @Test
    void passwordIsHashed() {
        when(passwordEncoder.encode("password1")).thenReturn("$2a$10$hash");

        User user = userService.register(form("anna@demo.by"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hash").isNotEqualTo("password1");
    }

    @Test
    void emailIsStoredInLowerCase() {
        User user = userService.register(form("  Anna@Demo.BY "));

        assertThat(user.getEmail()).isEqualTo("anna@demo.by");
        verify(userRepository).existsByEmail("anna@demo.by");
    }

    @Test
    void settingsAndCashAccountAreCreated() {
        User user = userService.register(form("anna@demo.by"));

        ArgumentCaptor<NotificationSettings> settings = ArgumentCaptor.forClass(NotificationSettings.class);
        verify(settingsRepository).save(settings.capture());
        assertThat(settings.getValue().getUser()).isSameAs(user);
        assertThat(settings.getValue().isMonthlyReport()).isTrue();

        ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(account.capture());
        assertThat(account.getValue().getName()).isEqualTo("Наличные");
        assertThat(account.getValue().getType()).isEqualTo(AccountType.CASH);
        assertThat(account.getValue().getBalance()).isEqualByComparingTo("0");
        assertThat(account.getValue().isSharedWithFamily()).isTrue();
        assertThat(account.getValue().getUser()).isSameAs(user);
    }
}
