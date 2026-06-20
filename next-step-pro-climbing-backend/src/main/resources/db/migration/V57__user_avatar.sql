-- Avatar (zdjęcie profilowe) zarejestrowanego użytkownika.
-- Plik trzymany w folderze "avatars/" przez FileStorageService; tu tylko nazwa pliku.
ALTER TABLE users ADD COLUMN avatar_filename VARCHAR(255);
