package com.fintrack.service;

import com.fintrack.dto.CategoryForm;
import com.fintrack.entity.Category;
import com.fintrack.entity.CategoryType;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private CategoryService categoryService;

    private User anna;
    private User boris;

    @BeforeEach
    void setUp() {
        anna = fixtures.createUser("Анна", "anna@test.by");
        boris = fixtures.createUser("Борис", "boris@test.by");
    }

    private Category createOwn(User user, String name, CategoryType type) {
        CategoryForm form = new CategoryForm();
        form.setName(name);
        form.setType(type);
        return categoryService.create(user.getId(), form);
    }

    @Test
    void availableContainsStandardAndOwnCategories() {
        createOwn(anna, "Хобби", CategoryType.EXPENSE);
        createOwn(boris, "Рыбалка", CategoryType.EXPENSE);

        assertThat(categoryService.available(anna.getId())).extracting("name")
                .contains("Продукты", "Зарплата", "Хобби").doesNotContain("Рыбалка");
    }

    @Test
    void categoryOfWrongTypeIsRejected() {
        Long salary = fixtures.standardCategory("Зарплата").getId();

        assertThatThrownBy(() -> categoryService.getAvailable(anna.getId(), salary, TransactionType.EXPENSE))
                .isInstanceOf(BusinessException.class);
        assertThat(categoryService.getAvailable(anna.getId(), salary, TransactionType.INCOME).getName())
                .isEqualTo("Зарплата");
    }

    @Test
    void foreignCategoryIsNotFound() {
        Category fishing = createOwn(boris, "Рыбалка", CategoryType.EXPENSE);

        assertThatThrownBy(() -> categoryService.getAvailable(anna.getId(), fishing.getId(), TransactionType.EXPENSE))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void standardCategoryCannotBeChanged() {
        Long food = fixtures.standardCategory("Продукты").getId();

        assertThatThrownBy(() -> categoryService.rename(anna.getId(), food, "Еда"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> categoryService.toggleArchive(anna.getId(), food))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void duplicateNameIsRejected() {
        assertThatThrownBy(() -> createOwn(anna, "продукты", CategoryType.EXPENSE))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ownCategoryIsRenamedAndArchived() {
        Category hobby = createOwn(anna, "Хобби", CategoryType.EXPENSE);

        categoryService.rename(anna.getId(), hobby.getId(), "Увлечения");
        assertThat(categoryService.toggleArchive(anna.getId(), hobby.getId())).isTrue();

        assertThat(categoryService.own(anna.getId())).singleElement()
                .satisfies(c -> {
                    assertThat(c.name()).isEqualTo("Увлечения");
                    assertThat(c.archived()).isTrue();
                });
        assertThat(categoryService.available(anna.getId())).extracting("name").doesNotContain("Увлечения");
    }
}
