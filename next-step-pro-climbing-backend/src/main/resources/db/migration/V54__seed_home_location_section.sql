-- Seed sekcji "Gdzie teraz szkolę" na stronie głównej.
-- Przenosi dotychczas zakodowane na stałe wartości (badge/tytuł/podtytuł + lista miejsc,
-- PL/EN/ES) do bazy: jako aktywną treść ORAZ jako gotowy preset "Andaluzja".
-- Po deployu sekcja wygląda dokładnie tak jak wcześniej, ale jest w pełni edytowalna w panelu admina.
-- ON CONFLICT DO NOTHING — nie nadpisuje, jeśli admin zdążył już coś ustawić.

INSERT INTO site_settings (key, value) VALUES
    ('home_location_active', '{"enabled":true,"translations":{"pl":{"badge":"Obecnie w Andaluzji — rezerwacje otwarte","title":"Gdzie teraz szkolę","subtitle":"Obecnie w Andaluzji, prowadzę kursy wspinaczkowe oraz pozostałe zajęcia w:","locations":["El Chorro","Granada","Motril","Los Cahorros","Órgiva"]},"en":{"badge":"Now in Andalusia — bookings open","title":"Where I am now","subtitle":"Based in Andalusia – currently climbing and coaching in:","locations":["El Chorro","Granada","Motril","Los Cahorros","Órgiva"]},"es":{"badge":"Ahora en Andalucía — reservas abiertas","title":"Dónde estoy ahora","subtitle":"Con base en Andalucía – actualmente escalando y entrenando en:","locations":["El Chorro","Granada","Motril","Los Cahorros","Órgiva"]}}}')
ON CONFLICT (key) DO NOTHING;

INSERT INTO site_settings (key, value) VALUES
    ('home_location_presets', '[{"id":"a0000000-0000-4000-8000-000000000001","name":"Andaluzja","translations":{"pl":{"badge":"Obecnie w Andaluzji — rezerwacje otwarte","title":"Gdzie teraz szkolę","subtitle":"Obecnie w Andaluzji, prowadzę kursy wspinaczkowe oraz pozostałe zajęcia w:","locations":["El Chorro","Granada","Motril","Los Cahorros","Órgiva"]},"en":{"badge":"Now in Andalusia — bookings open","title":"Where I am now","subtitle":"Based in Andalusia – currently climbing and coaching in:","locations":["El Chorro","Granada","Motril","Los Cahorros","Órgiva"]},"es":{"badge":"Ahora en Andalucía — reservas abiertas","title":"Dónde estoy ahora","subtitle":"Con base en Andalucía – actualmente escalando y entrenando en:","locations":["El Chorro","Granada","Motril","Los Cahorros","Órgiva"]}}}]')
ON CONFLICT (key) DO NOTHING;
