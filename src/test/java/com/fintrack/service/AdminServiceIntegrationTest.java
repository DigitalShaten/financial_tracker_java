package com.fintrack.service;

import com.fintrack.dto.AdminForm;
import com.fintrack.dto.AdminUserView;
import com.fintrack.dto.CategoryForm;
import com.fintrack.entity.Account;
import com.fintrack.entity.Category;
import com.fintrack.entity.CategoryType;
import com.fintrack.entity.Role;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.repository.CategoryRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private User anna;

    @BeforeEach
    void setUp() {
        admin = fixtures.createAdmin("root@test.by");
        anna = fixtures.createUser("Анна", "anna@test.by");
    }

    /* ---------- пользователи ---------- */

    @Test
    void blockAndUnblockUser() {
        adminService.setEnabled(admin.getId(), anna.getId(), false);
        assertThat(userRepository.findById(anna.getId()).orElseThrow().isEnabled()).isFalse();

        adminService.setEnabled(admin.getId(), anna.getId(), true);
        assertThat(userRepository.findById(anna.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void adminCannotBlockHimself() {
        assertThatThrownBy(() -> adminService.setEnabled(admin.getId(), admin.getId(), false))
                .isInstanceOf(BusinessException.class);
        assertThat(userRepository.findById(admin.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void otherAdminIsNotBlockedThroughUserList() {
        User second = fixtures.createAdmin("second@test.by");

        assertThatThrownBy(() -> adminService.setEnabled(admin.getId(), second.getId(), false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void usersAreSearchedByEmailAndPaged() {
        for (int i = 1; i <= 21; i++) {
            fixtures.createUser("Пользователь " + i, "user" + i + "@mail.by");
        }

        Page<AdminUserView> first = adminService.users("", 0);
        assertThat(first.getTotalElements()).isEqualTo(22);   // admin в список не входит
        assertThat(first.getContent()).hasSize(AdminService.USERS_PAGE_SIZE);
        assertThat(adminService.users("", 1).getContent()).hasSize(2);

        assertThat(adminService.users("ANNA", 0).getContent()).extracting(AdminUserView::email)
                .containsExactly("anna@test.by");
    }

    /* ---------- категории ---------- */

    @Test
    void createRenameAndArchiveStandardCategory() {
        CategoryForm form = new CategoryForm();
        form.setName("Образование");
        form.setType(CategoryType.EXPENSE);
        adminService.createCategory(form);
        Category created = categoryRepository.findAllByUserIsNullOrderByTypeAscNameAsc().stream()
                .filter(c -> c.getName().equals("Образование")).findFirst().orElseThrow();
        assertThat(categoryService.available(anna.getId())).extracting(c -> c.name()).contains("Образование");

        assertThatThrownBy(() -> adminService.createCategory(form)).isInstanceOf(BusinessException.class);

        adminService.renameCategory(created.getId(), "Учёба");
        adminService.toggleArchive(created.getId());

        assertThat(categoryService.available(anna.getId())).extracting(c -> c.name())
                .doesNotContain("Образование", "Учёба");
    }

    @Test
    void categoryWithOperationsIsNotDeleted() {
        Account card = fixtures.createAccount(anna, "Карта", "100", true);
        fixtures.expense(anna, card, "Аптека", "10", "Здоровье", transactionService.today(anna.getId()));
        Long health = fixtures.standardCategory("Здоровье").getId();

        assertThatThrownBy(() -> adminService.deleteCategory(health))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("есть операции");
        assertThat(categoryRepository.findById(health)).isPresent();
    }

    @Test
    void unusedCategoryIsDeleted() {
        Long gifts = fixtures.standardCategory("Подарки").getId();

        adminService.deleteCategory(gifts);

        assertThat(categoryRepository.findById(gifts)).isEmpty();
    }

    @Test
    void userCategoryIsNotVisibleToAdminActions() {
        CategoryForm form = new CategoryForm();
        form.setName("Хобби");
        Long own = categoryService.create(anna.getId(), form).getId();

        assertThatThrownBy(() -> adminService.deleteCategory(own)).isInstanceOf(NotFoundException.class);
    }

    /* ---------- администраторы ---------- */

    @Test
    void lastAdminIsNotDeleted() {
        // в тестовой базе есть ещё администратор из app.admin.* - удаляем всех, кроме root, через сервис
        userRepository.findAllByRoleOrderByEmail(Role.ADMIN).stream()
                .filter(a -> !a.getId().equals(admin.getId()))
                .forEach(a -> adminService.deleteAdmin(admin.getId(), a.getId()));

        assertThatThrownBy(() -> adminService.deleteAdmin(admin.getId(), admin.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Нельзя удалить последнего администратора");
    }

    @Test
    void adminCannotDeleteHimselfButCanDeleteOther() {
        User second = fixtures.createAdmin("second@test.by");

        assertThatThrownBy(() -> adminService.deleteAdmin(admin.getId(), admin.getId()))
                .isInstanceOf(BusinessException.class);

        adminService.deleteAdmin(admin.getId(), second.getId());
        assertThat(userRepository.findById(second.getId())).isEmpty();
    }

    @Test
    void userIsNotDeletedAsAdmin() {
        assertThatThrownBy(() -> adminService.deleteAdmin(admin.getId(), anna.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createAndUpdateAdmin() {
        AdminForm form = new AdminForm();
        form.setName("Мария");
        form.setEmail("Maria@Test.by");
        form.setPassword("short");
        assertThatThrownBy(() -> adminService.createAdmin(form)).hasMessage("Пароль — от 8 до 72 символов");

        form.setPassword("password1");
        adminService.createAdmin(form);
        User maria = userRepository.findByEmail("maria@test.by").orElseThrow();
        assertThat(maria.getRole()).isEqualTo(Role.ADMIN);

        form.setEmail("anna@test.by");
        assertThatThrownBy(() -> adminService.createAdmin(form)).hasMessage("Этот e-mail уже занят");

        AdminForm edit = new AdminForm();
        edit.setName("Мария Петрова");
        edit.setEmail("maria@test.by");
        edit.setPassword("");
        adminService.updateAdmin(maria.getId(), edit);
        User updated = userRepository.findById(maria.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Мария Петрова");
        assertThat(passwordEncoder.matches("password1", updated.getPasswordHash())).isTrue();

        edit.setPassword("newpassword");
        adminService.updateAdmin(maria.getId(), edit);
        assertThat(passwordEncoder.matches("newpassword",
                userRepository.findById(maria.getId()).orElseThrow().getPasswordHash())).isTrue();
    }
}
