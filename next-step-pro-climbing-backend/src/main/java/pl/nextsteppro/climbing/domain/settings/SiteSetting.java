package pl.nextsteppro.climbing.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "site_settings")
public class SiteSetting {

    @Id
    @Column(name = "key", length = 100)
    private String key;

    @Column(name = "value", columnDefinition = "TEXT")
    @Nullable
    private String value;

    protected SiteSetting() {}

    public SiteSetting(String key, @Nullable String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    @Nullable
    public String getValue() {
        return value;
    }

    public void setValue(@Nullable String value) {
        this.value = value;
    }
}
