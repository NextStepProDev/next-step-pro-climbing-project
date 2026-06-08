-- Seed promocji nad kalendarzem.
-- Przenosi dotychczas zakodowaną na stałe promocję (PL/EN/ES) do bazy jako gotowy,
-- edytowalny w panelu admina szablon ORAZ ustawia go jako aktywny (widoczny na stronie).
-- Treść = obecna promocja, z terminem przesuniętym na koniec CZERWCA.
-- Pola badge oraz CTA (ctaLabel/ctaUrl) są puste — admin może je dodać w panelu.
-- ON CONFLICT DO NOTHING — nie nadpisuje, jeśli admin zdążył już coś ustawić.

INSERT INTO site_settings (key, value) VALUES
    ('calendar_promo_presets', '[{"id":"b0000000-0000-4000-8000-000000000001","name":"Promocja czerwiec 2026","translations":{"pl":{"badge":"","title":"Promocja tylko do końca czerwca!!","description":"Kup 2 treningi i zapłać jak za dwa!!","ctaLabel":"","ctaUrl":""},"en":{"badge":"","title":"Promotion until end of June only!!","description":"Buy 2 training sessions and pay for two!!","ctaLabel":"","ctaUrl":""},"es":{"badge":"","title":"¡¡Promoción solo hasta fin de junio!!","description":"¡¡Compra 2 entrenamientos y paga como por dos!!","ctaLabel":"","ctaUrl":""}}}]')
ON CONFLICT (key) DO NOTHING;

INSERT INTO site_settings (key, value) VALUES
    ('calendar_promo_active', '{"activePresetId":"b0000000-0000-4000-8000-000000000001"}')
ON CONFLICT (key) DO NOTHING;
