package com.team11.backend.users.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 설정 5-2 알림 화면. 가입 시 만들지 않고 처음 조회/수정할 때 기본값으로 생성(get-or-create). */
@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor
public class NotificationSettings {

    @Id
    private Long userId;

    private boolean wakeAlarmEnabled = true;
    private boolean bedtimeAlarmEnabled = true;
    private int bedtimeLeadMin = 30;

    public NotificationSettings(Long userId) {
        this.userId = userId;
    }

    public void update(boolean wakeAlarmEnabled, boolean bedtimeAlarmEnabled, int bedtimeLeadMin) {
        this.wakeAlarmEnabled = wakeAlarmEnabled;
        this.bedtimeAlarmEnabled = bedtimeAlarmEnabled;
        this.bedtimeLeadMin = bedtimeLeadMin;
    }
}
