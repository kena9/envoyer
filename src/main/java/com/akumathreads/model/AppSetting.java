package com.akumathreads.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Generic key-value store for application settings that need to persist
 * across restarts without code changes (e.g. auto-generated VAPID keys).
 */
@Entity
@Table(name = "app_settings")
@Getter @Setter @NoArgsConstructor
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 2048)
    private String value;

    public AppSetting(String key, String value) {
        this.key   = key;
        this.value = value;
    }
}
