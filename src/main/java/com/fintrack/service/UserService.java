package com.fintrack.service;

import com.fintrack.dto.CurrentUserView;
import com.fintrack.dto.MemberChip;
import com.fintrack.dto.PasswordForm;
import com.fintrack.dto.ProfileForm;
import com.fintrack.dto.RegisterForm;
import com.fintrack.entity.Account;
import com.fintrack.entity.AccountType;
import com.fintrack.entity.FamilyMember;
import com.fintrack.entity.FamilyRole;
import com.fintrack.entity.NotificationSettings;
import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.BudgetRepository;
import com.fintrack.repository.FamilyInviteRepository;
import com.fintrack.repository.FamilyMemberRepository;
import com.fintrack.repository.FamilyRepository;
import com.fintrack.repository.GoalRepository;
import com.fintrack.repository.NotificationSettingsRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Пользователи: регистрация, профиль, пароль, удаление аккаунта, данные для шаблонов, первый администратор.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    static final String DEFAULT_ACCOUNT_NAME = "Наличные";

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final FamilyMemberRepository memberRepository;
    private final FamilyRepository familyRepository;
    private final FamilyInviteRepository inviteRepository;
    private final GoalRepository goalRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Регистрация в одной транзакции: пользователь, настройки уведомлений по умолчанию
     * и счёт "Наличные" (видно семье, баланс 0).
     */
    @Transactional
    public User register(RegisterForm form) {
        String email = normalizeEmail(form.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Пользователь с таким e-mail уже зарегистрирован");
        }
        User user = new User();
        user.setEmail(email);
        user.setName(form.getName().trim());
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setCurrency(form.getCurrency());
        user.setRole(Role.USER);
        userRepository.save(user);

        NotificationSettings settings = new NotificationSettings();
        settings.setUser(user);
        settingsRepository.save(settings);

        Account cash = new Account();
        cash.setUser(user);
        cash.setName(DEFAULT_ACCOUNT_NAME);
        cash.setType(AccountType.CASH);
        cash.setCurrency(user.getCurrency());
        cash.setSharedWithFamily(true);
        accountRepository.save(cash);

        log.info("Зарегистрирован пользователь {}", user.getId());
        return user;
    }

    /** Создаёт администратора при старте, если пользователя с таким e-mail ещё нет. */
    @Transactional
    public void createAdminIfMissing(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setName("Администратор");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        log.info("Создан первый администратор {}", email);
    }

    /** Данные о текущем пользователе для шаблонов; null, если пользователя уже нет. */
    @Transactional(readOnly = true)
    public CurrentUserView currentUserView(Long userId) {
        Optional<User> found = userRepository.findById(userId);
        if (found.isEmpty()) {
            return null;
        }
        User user = found.get();
        Optional<FamilyMember> membership = memberRepository.findByUser_Id(userId);
        String familyName = membership.map(m -> m.getFamily().getName()).orElse(null);
        boolean owner = membership.map(m -> m.getRole() == FamilyRole.OWNER).orElse(false);

        List<MemberChip> chips = new ArrayList<>();
        chips.add(new MemberChip(CurrentUserView.initialOf(user.getName()), user.getName(), true));
        membership.ifPresent(m -> memberRepository.findAllWithUsers(m.getFamily().getId()).stream()
                .filter(other -> !other.getUser().getId().equals(userId))
                .forEach(other -> chips.add(new MemberChip(
                        CurrentUserView.initialOf(other.getUser().getName()), other.getUser().getName(), false))));

        return new CurrentUserView(user.getId(), user.getName(), user.getCurrency(), familyName,
                membership.isPresent(), owner, user.getRole() == Role.ADMIN, List.copyOf(chips));
    }

    @Transactional(readOnly = true)
    public ProfileForm profileForm(Long userId) {
        User user = get(userId);
        ProfileForm form = new ProfileForm();
        form.setName(user.getName());
        form.setCurrency(user.getCurrency());
        form.setTimeZone(user.getTimeZone());
        return form;
    }

    /**
     * Профиль: имя, валюта, часовой пояс. Валюта счетов меняется вместе с валютой пользователя
     * (суммы не пересчитываются по курсу).
     */
    @Transactional
    public void updateProfile(Long userId, ProfileForm form) {
        try {
            ZoneId.of(form.getTimeZone());
        } catch (DateTimeException e) {
            throw new BusinessException("Неизвестный часовой пояс");
        }
        User user = get(userId);
        user.setName(form.getName().trim());
        user.setTimeZone(form.getTimeZone());
        if (!user.getCurrency().equals(form.getCurrency())) {
            user.setCurrency(form.getCurrency());
            accountRepository.updateCurrency(userId, form.getCurrency());
        }
    }

    @Transactional
    public void changePassword(Long userId, PasswordForm form) {
        User user = get(userId);
        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Текущий пароль указан неверно");
        }
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("Новый пароль и повтор не совпадают");
        }
        user.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
    }

    /**
     * Удаление учётной записи с подтверждением паролем. Владелец семьи с другими участниками
     * сначала распускает семью; если он в семье один - семья удаляется вместе с ним.
     * Остальные данные пользователя удаляются каскадно (ON DELETE CASCADE в схеме).
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = get(userId);
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException("Неверный пароль");
        }
        Optional<FamilyMember> membership = memberRepository.findByUser_Id(userId);
        if (membership.isPresent() && membership.get().getRole() == FamilyRole.OWNER) {
            Long familyId = membership.get().getFamily().getId();
            if (memberRepository.countByFamily_Id(familyId) > 1) {
                throw new BusinessException("Сначала распустите семью");
            }
            goalRepository.detachAllFromFamily(familyId);
            inviteRepository.deleteAllByFamilyId(familyId);
            memberRepository.deleteAllByFamilyId(familyId);
            familyRepository.deleteFamilyById(familyId);
        }
        // Операции и бюджеты ссылаются на категории пользователя, поэтому удаляются первыми
        transactionRepository.deleteAllByUserId(userId);
        budgetRepository.deleteAllByUserId(userId);
        userRepository.deleteUserById(userId);
        log.info("Удалён пользователь {}", userId);
    }

    @Transactional(readOnly = true)
    public User get(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
