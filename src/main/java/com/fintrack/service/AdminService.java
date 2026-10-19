package com.fintrack.service;

import com.fintrack.dto.AdminCategoryView;
import com.fintrack.dto.AdminForm;
import com.fintrack.dto.AdminUserView;
import com.fintrack.dto.AdminView;
import com.fintrack.dto.CategoryForm;
import com.fintrack.entity.Category;
import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.CategoryRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.util.DateFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;

/**
 * Раздел администратора: пользователи (поиск, блокировка), стандартные категории, администраторы.
 * Финансовые данные пользователей администратору не показываются.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    public static final int USERS_PAGE_SIZE = 20;

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;

    /* ---------- Пользователи ---------- */

    /** Пользователи с ролью USER, поиск по части e-mail, по 20 на странице, новые сверху. */
    @Transactional(readOnly = true)
    public Page<AdminUserView> users(String emailQuery, int page) {
        String query = emailQuery == null ? "" : emailQuery.trim();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), USERS_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        return userRepository.findByRoleAndEmailContainingIgnoreCase(Role.USER, query, pageable)
                .map(u -> new AdminUserView(u.getId(), u.getName(), u.getEmail(),
                        DateFormat.dayMonthYear(u.getCreatedAt().atZoneSameInstant(ZoneId.of(u.getTimeZone())).toLocalDate()),
                        u.isEnabled()));
    }

    /** Блокировка или разблокировка пользователя. Себя заблокировать нельзя; администраторов - тоже (их удаляют). */
    @Transactional
    public void setEnabled(Long adminId, Long userId, boolean enabled) {
        if (adminId.equals(userId)) {
            throw new BusinessException("Нельзя заблокировать самого себя");
        }
        User user = userRepository.findById(userId).filter(u -> u.getRole() == Role.USER)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        user.setEnabled(enabled);
        log.info("Администратор {} {} пользователя {}", adminId, enabled ? "разблокировал" : "заблокировал", userId);
    }

    /* ---------- Стандартные категории ---------- */

    @Transactional(readOnly = true)
    public List<AdminCategoryView> standardCategories() {
        return categoryRepository.findAllByUserIsNullOrderByTypeAscNameAsc().stream()
                .map(c -> new AdminCategoryView(c.getId(), c.getName(), c.getType(), c.isArchived(),
                        transactionRepository.existsByCategory_Id(c.getId())))
                .toList();
    }

    @Transactional
    public void createCategory(CategoryForm form) {
        String name = form.getName().trim();
        if (categoryRepository.existsByUserIsNullAndTypeAndNameIgnoreCase(form.getType(), name)) {
            throw new BusinessException("Такая стандартная категория уже есть");
        }
        Category category = new Category();
        category.setName(name);
        category.setType(form.getType());
        categoryRepository.save(category);
    }

    @Transactional
    public void renameCategory(Long categoryId, String newName) {
        Category category = standardCategory(categoryId);
        String name = newName == null ? "" : newName.trim();
        if (name.isEmpty() || name.length() > 60) {
            throw new BusinessException("Название категории — от 1 до 60 символов");
        }
        if (!name.equalsIgnoreCase(category.getName())
                && categoryRepository.existsByUserIsNullAndTypeAndNameIgnoreCase(category.getType(), name)) {
            throw new BusinessException("Такая стандартная категория уже есть");
        }
        category.setName(name);
    }

    /** Архивирует категорию (её больше нельзя выбрать в новых операциях) или возвращает из архива. */
    @Transactional
    public boolean toggleArchive(Long categoryId) {
        Category category = standardCategory(categoryId);
        category.setArchived(!category.isArchived());
        return category.isArchived();
    }

    /** Удаление - только если по категории нет ни одной операции (бюджеты на неё удаляет база). */
    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = standardCategory(categoryId);
        if (transactionRepository.existsByCategory_Id(categoryId)) {
            throw new BusinessException("По категории «" + category.getName() + "» есть операции — её можно только архивировать");
        }
        categoryRepository.delete(category);
    }

    private Category standardCategory(Long id) {
        return categoryRepository.findByIdAndUserIsNull(id).orElseThrow(() -> new NotFoundException("Категория не найдена"));
    }

    /* ---------- Администраторы ---------- */

    @Transactional(readOnly = true)
    public List<AdminView> admins(Long currentAdminId) {
        return userRepository.findAllByRoleOrderByEmail(Role.ADMIN).stream()
                .map(a -> new AdminView(a.getId(), a.getName(), a.getEmail(), a.getId().equals(currentAdminId)))
                .toList();
    }

    @Transactional
    public void createAdmin(AdminForm form) {
        String email = UserService.normalizeEmail(form.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Этот e-mail уже занят");
        }
        String password = requirePassword(form.getPassword());
        User admin = new User();
        admin.setName(form.getName().trim());
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        log.info("Создан администратор {}", email);
    }

    /** Изменение имени, e-mail и (если введён) пароля администратора. */
    @Transactional
    public void updateAdmin(Long adminId, AdminForm form) {
        User admin = admin(adminId);
        String email = UserService.normalizeEmail(form.getEmail());
        if (!email.equals(admin.getEmail()) && userRepository.existsByEmail(email)) {
            throw new BusinessException("Этот e-mail уже занят");
        }
        admin.setName(form.getName().trim());
        admin.setEmail(email);
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            admin.setPasswordHash(passwordEncoder.encode(requirePassword(form.getPassword())));
        }
    }

    /** Удаление администратора: последнего и самого себя удалить нельзя. */
    @Transactional
    public void deleteAdmin(Long currentAdminId, Long adminId) {
        admin(adminId);
        if (userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new BusinessException("Нельзя удалить последнего администратора");
        }
        if (currentAdminId.equals(adminId)) {
            throw new BusinessException("Нельзя удалить самого себя — попросите другого администратора");
        }
        userRepository.deleteUserById(adminId);
        log.info("Администратор {} удалил администратора {}", currentAdminId, adminId);
    }

    private User admin(Long id) {
        return userRepository.findById(id).filter(u -> u.getRole() == Role.ADMIN)
                .orElseThrow(() -> new NotFoundException("Администратор не найден"));
    }

    private static String requirePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new BusinessException("Пароль — от 8 до 72 символов");
        }
        return password;
    }
}
