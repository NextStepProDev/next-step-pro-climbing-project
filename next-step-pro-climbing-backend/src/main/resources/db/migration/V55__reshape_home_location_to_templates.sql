-- Reshape sekcji "Gdzie teraz szkolę" do modelu szablonów (activePresetId).
--
-- Kontekst: V54 została pierwotnie wdrożona ze starym modelem
-- (home_location_active = {enabled, translations} + presety z polem `title` per język).
-- Późniejszy redesign przepisał TĘ SAMĄ migrację V54, co złamało checksum Flyway na
-- produkcji. V54 przywrócono do oryginalnej, wdrożonej treści; reshape danych, który
-- V54 miała wykonać, przeniesiono tutaj — do nowej, niezmienialnej migracji.
--
-- Nowy backend nie potrafi sparsować starego formatu (Jackson FAIL_ON_UNKNOWN_PROPERTIES
-- na polu `title` oraz `enabled/translations` w home_location_active), więc oba klucze
-- nadpisujemy bezwarunkowo (DO UPDATE) kanonicznym szablonem "Andaluzja". Po deployu sekcja
-- wygląda jak wcześniej, ale jest edytowalna w panelu admina jako lista szablonów.

INSERT INTO site_settings (key, value) VALUES
    ('home_location_presets', '[{"id":"a0000000-0000-4000-8000-000000000001","name":"Andaluzja","translations":{"pl":{"badge":"Obecnie w Andaluzji — rezerwacje otwarte","subtitle":"Obecnie w Andaluzji, prowadzę kursy wspinaczkowe oraz pozostałe zajęcia w:","locations":["El Chorro","Granada","Motril","Los Cahorros","Órgiva"]},"en":{"badge":"Now in Andalusia — bookings open","subtitle":"Based in Andalusia – currently climbing and coaching in:","locations":["El Chorro","Granada","Motril","Los Cahorros","Órgiva"]},"es":{"badge":"Ahora en Andalucía — reservas abiertas","subtitle":"Con base en Andalucía – actualmente escalando y entrenando en:","locations":["El Chorro","Granada","Motril","Los Cahorros","Órgiva"]}}}]')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

INSERT INTO site_settings (key, value) VALUES
    ('home_location_active', '{"activePresetId":"a0000000-0000-4000-8000-000000000001"}')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
