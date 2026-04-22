package pl.nextsteppro.climbing.domain.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteSettingsRepository extends JpaRepository<SiteSetting, String> {
}
