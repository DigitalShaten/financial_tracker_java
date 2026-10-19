-- настройки ежемесячного отчёта и уведомлений
CREATE TABLE notification_settings (
    user_id           BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    monthly_report    BOOLEAN  NOT NULL DEFAULT TRUE,
    report_day        SMALLINT NOT NULL DEFAULT 1,
    report_hour       SMALLINT NOT NULL DEFAULT 10,
    family_report     BOOLEAN  NOT NULL DEFAULT TRUE,
    budget_alerts     BOOLEAN  NOT NULL DEFAULT TRUE,
    last_report_month DATE
);

-- пользователям, которые зарегистрировались раньше, - настройки по умолчанию
INSERT INTO notification_settings (user_id)
SELECT id FROM users WHERE role = 'USER';
