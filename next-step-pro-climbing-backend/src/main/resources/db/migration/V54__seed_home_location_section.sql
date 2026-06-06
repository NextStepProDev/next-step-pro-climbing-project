-- Seed sekcji "Gdzie teraz szkolę" na stronie głównej.
-- Model: "na stronie" = referencja do szablonu (activePresetId). Sekcja pokazuje się tylko,
-- gdy wskazany szablon istnieje. Tytuł jest stały i tłumaczony we froncie (i18n).
-- Seedujemy szablon "Andaluzja" (badge/podtytuł/miejsca per język) i ustawiamy go jako aktywny.
-- Po deployu sekcja wygląda dokładnie tak jak wcześniej, ale jest edytowalna w panelu admina.
-- ON CONFLICT DO NOTHING — nie nadpisuje, jeśli admin zdążył już coś ustawić.

INSERT INTO site_settings (key, value) VALUES
    ('home_location_presets', '[{"id":"a0000000-0000-4000-8000-000000000001","name":"Andaluzja","translations":{"pl":{"badge":"Obecnie w Andaluzji — rezerwacje otwarte","subtitle":"Obecnie w Andaluzji, prowadzę kursy wspinaczkowe oraz pozostałe zajęcia w:","locations":["El Chorro","Granada","Motril","Los Cahorros","Órgiva"]},"en":{"badge":"Now in Andalusia — bookings open","subtitle":"Based in Andalusia – currently climbing and coaching in:","locations":["El Chorro","Granada","Motril","Los Cahorros","Órgiva"]},"es":{"badge":"Ahora en Andalucía — reservas abiertas","subtitle":"Con base en Andalucía – actualmente escalando y entrenando en:","locations":["El Chorro","Granada","Motril","Los Cahorros","Órgiva"]}}}]')
ON CONFLICT (key) DO NOTHING;

INSERT INTO site_settings (key, value) VALUES
    ('home_location_active', '{"activePresetId":"a0000000-0000-4000-8000-000000000001"}')
ON CONFLICT (key) DO NOTHING;
