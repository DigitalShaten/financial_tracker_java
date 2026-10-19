package com.fintrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "notification_settings")
@Getter
@Setter
@NoArgsConstructor
public class NotificationSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "monthly_report", nullable = false)
    private boolean monthlyReport = true;

    /** День месяца для отчёта, 1-28. В БД - SMALLINT, поэтому тип short. */
    @Column(name = "report_day", nullable = false)
    private short reportDay = 1;

    /** Час отправки отчёта, 0-23. */
    @Column(name = "report_hour", nullable = false)
    private short reportHour = 10;

    @Column(name = "family_report", nullable = false)
    private boolean familyReport = true;

    @Column(name = "budget_alerts", nullable = false)
    private boolean budgetAlerts = true;

    /** Месяц (первое число), за который последний раз отправлен ежемесячный отчёт. */
    @Column(name = "last_report_month")
    private LocalDate lastReportMonth;
}
