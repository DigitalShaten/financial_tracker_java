-- PostgreSQL не создаёт индексы для внешних ключей сам, добавляем под частые запросы

-- список операций и суммы: фильтр по счетам и периоду
CREATE INDEX idx_tx_account_date ON transactions (account_id, operation_date);
-- переводы на счёт и проверка "есть ли операции" при удалении счёта/категории
CREATE INDEX idx_tx_target_account ON transactions (target_account_id);
CREATE INDEX idx_tx_category ON transactions (category_id);

CREATE INDEX idx_accounts_user ON accounts (user_id);
CREATE INDEX idx_categories_user ON categories (user_id);
CREATE INDEX idx_budgets_user_month ON budgets (user_id, month);

CREATE INDEX idx_goals_owner ON goals (owner_id);
CREATE INDEX idx_goals_family ON goals (family_id);
CREATE INDEX idx_goal_contrib_goal ON goal_contributions (goal_id);

CREATE INDEX idx_invites_family ON family_invites (family_id);
