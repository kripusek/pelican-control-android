# Pelican Control 2.1.0 — natywny Android

Aplikacja Kotlin / Jetpack Compose na Androida 10+. Brak WebView i ekranów HTML. Adres panelu podaje użytkownik w zakładce Konto; https://panel.example.com to wyłącznie przykład.

## Połączenie

W zakładce Konto wpisz pełny klucz API klienta Pelicana (`pacc_…`). Aplikacja sprawdzi konto przed zapisaniem. Klucz głównego administratora może korzystać również z API administracyjnego. Pozostali administratorzy mogą podać osobny klucz aplikacyjny, z przyznanymi uprawnieniami. Przełącznik Użytkownik / Admin zmienia interfejs; nie nadaje praw. Klucze są szyfrowane przez Android Keystore. APK nie zawiera danych logowania ani klucza dostępu do panelu.

Pełny klucz klienta skopiuj z powiadomienia wyświetlonego bezpośrednio po jego utworzeniu w Profil → API Keys. W Pelicanie 1.0.0-rc.2 pełny klucz ma 48 znaków; 16-znakowy wpis widoczny później na liście jest identyfikatorem. Klucz `papp_…` należy do osobnego pola administracyjnego. Od wersji 2.0.1 można zapisać samo połączenie administracyjne.

Podpisany APK jest dostępny w zakładce **Releases** tego repozytorium.

## Ekrany użytkownika

- pulpit i lista serwerów;
- stan, CPU/RAM/dysk, start/stop/restart i konsola WSS;
- przeglądarka plików, edytor tekstu, pobieranie, wysyłanie do 25 MB, katalogi, nazwy, archiwa i usuwanie;
- kopie zapasowe: tworzenie, pobieranie, nazwy, blokada, usuwanie, przywracanie;
- harmonogramy, zadania, porty, dostęp użytkowników, bazy danych, zmienne startowe i aktywność;
- CS2: ustawienia mapy i trybu, zapis startowy, zastosowanie teraz, Workshop, MatchZy, CS2AC, CS2FOW.

Wybranie trybu Wingman ogranicza listę do zainstalowanych map obsługujących Wingmana i usuwa niezgodny poprzedni wybór. Powrót do pozostałych trybów przywraca pełną listę. Mapy Workshop wymagają oznaczenia Wingman lub 2v2 w metadanych Steam. API sprawdza zgodność przed zapisem lub zmianą mapy.

## Administracja

Natywne listy, szczegóły i formularze dla serwerów, użytkowników, węzłów, portów, dodatków, eggs, ról, hostów baz i montowań. Obsługa tworzenia/edycji/usuwania tam, gdzie udostępnia ją API Pelicana; limity, zawieszanie, reinstalacja i transfery serwerów. Edytor uprawnień ról oraz podstawowe ustawienia ogólne panelu korzystają z dodatkowych endpointów serwerowych. Część zaawansowanych struktur API (np. środowisko egga, listy ID) ma natywny edytor JSON.

To nie jest kopia każdego ekranu Filament: brak konfiguracji SMTP/OAuth/captcha/S3, edytora skryptów eggs, powiadomień push oraz pełnego konfiguratora blokad rosterów MatchZy. Własne konfiguracje dodatków można edytować w plikach. Logowanie aplikacji odbywa się kluczem API, nie hasłem panelu. Wyświetlenie hasła istniejącej bazy nie jest obsługiwane. Przydziały i uprawnienia nadal weryfikuje serwer.

## Integracja serwerowa

Pliki w `server/` są wdrożone w `/var/www/pelican/plugins/cs2-match-control/src/`:

- `MobileApiProvider.php` → `Providers/`;
- `MobileCs2Controller.php`, `MobileAdminController.php` → `Http/`.
- pliki z `server/Services/` → `Services/`;
- `server/Pages/MatchSettingsPage.php` → `Filament/Server/Pages/`.

Te pliki aktualizują istniejący dodatek `cs2-match-control`, który wymaga usług pozostałych dodatków MatchZy/CS2AC/CS2FOW. Nie kopiuj do repozytorium konfiguracji serwera ani kluczy dostępowych. Zgodność natywnych map Wingman opisuje `MapCompatibility::WINGMAN_MAPS`, według szablonu `2v2` w pliku gry `gamemodes.txt`. Po zmianie puli map przez Valve należy zaktualizować katalog. Mapy z biblioteki Workshop dodane przed tą wersją należy ponownie dodać tym samym ID, aby odświeżyć ich oznaczenia trybu.

Pelican automatycznie ładuje provider dodatku. Endpointy pod `/api/client/servers/{uuid}/mobile-cs2` korzystają z autoryzacji klienta i uprawnień konkretnego serwera. `/api/application/mobile-control` obsługuje role i podstawowe ustawienia administracyjne. Integracja korzysta z istniejących usług dodatków MatchZy/AC/FOW/Workshop; standardowe ekrany panelu pozostają dostępne w Pelicanie.

Naprawiono też błąd API Pelicana: nowe role muszą otrzymać `guard_name = Role::DEFAULT_GUARD_NAME`, zamiast dziedziczyć guard żądania Sanctum. Poprawka w `server/role-api-guard.patch`. Bez niej usuwanie roli utworzonej przez API kończyło się HTTP 500. Aktualizacja Pelicana/dodatku może wymagać ponownego naniesienia integracji. Wersja 2.0.1 została sprawdzona z Pelicanem `1.0.0-rc.2`; manifesty zainstalowanych dodatków mają deklarację zgodności `^1.0.0-rc.2`.

## Budowanie

JDK 21, Android SDK 36, Build Tools 35.0.0. Ustaw `ANDROID_HOME` lub lokalny `sdk.dir` w `local.properties`, następnie uruchom:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Na Windows użyj `gradlew.bat`. Dla podpisanej wersji release potrzebne są własne `.local/release.jks` i `.local/signing.properties` (`password`, alias `pelican`). Klucz oryginalnych wydań jest zachowany lokalnie; nie jest publikowany w repozytorium. Własny podpis różni się od podpisu APK z Releases. Klucze podpisu, dane QA i `local.properties` są ignorowane przez Git.

Workflow GitHub Actions uruchamia kompilację debug, testy jednostkowe i lint. Testy urządzenia znajdują się w `app/src/androidTest`; testy integracyjne wymagają świadomego podania tymczasowej konfiguracji QA na emulatorze i nie uruchamiają się automatycznie przeciwko serwerowi.

## Weryfikacja

- kompilacja debug/release, lint, testy jednostkowe formularzy, API i filtrowania map;
- emulator: nawigacja, Keystore, rzeczywiste ekrany serwera, plików, CS2 i administracji;
- prawdziwe API: 8 list klienta i 8 list administracyjnych, zasoby, WSS i logi;
- rzeczywisty zapis/odczyt/usunięcie nowego pliku QA, CRUD tymczasowej roli, CRUD nieaktywnego harmonogramu;
- zapis i wyczyszczenie uprawnień tymczasowej roli, ochrona roli Root Admin;
- gość 401, obce konto 404, ograniczony użytkownik 403 przy próbach zapisu ustawień, konfiguracji ochrony, Workshop i komend, administracja dla zwykłego użytkownika 403.

Nie wykonywano testowych restartów ani zmian bieżącej mapy. Destrukcyjne operacje mają potwierdzenie i nie były testowane na działających zasobach. Tymczasowe dane testowe i klucze są usuwane po testach.

Samodzielny test reguł backendu: `php server/tests/MapCompatibilityTest.php`.
