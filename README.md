# Kantor walut – backend (Web Service)

REST API mobilnego kantoru walut: rejestracja i logowanie, zasilenie konta symulowanym przelewem,
kursy aktualne i archiwalne z API NBP (tabela C), kupno i sprzedaż walut, portfel oraz historia transakcji.

Cała logika sald i transakcji działa po stronie serwera – aplikacja mobilna nigdy nie przesyła kursu, salda
ani identyfikatora użytkownika.

## Stos technologiczny

| Element          | Technologia                                           |
|------------------|-------------------------------------------------------|
| Język            | Kotlin 2.4 (JVM 25)                                   |
| Serwer HTTP      | Javalin 7, JSON przez Gson                            |
| Baza danych      | PostgreSQL + JDBI (SQL) + HikariCP (pula połączeń)    |
| Migracje         | Flyway (`src/main/resources/db/migration`)            |
| Uwierzytelnianie | hasła: bcrypt, sesja: token JWT (`Bearer`)            |
| Kursy walut      | publiczne API NBP – https://api.nbp.pl                |

## Struktura projektu

Taka sama jak w backendzie weather-api: `controller → usecase → service / databaseConnector`.

```
src/main/kotlin/
├── Main.kt                     start: konfiguracja z .env, baza, CORS, obsługa błędów, rejestracja kontrolerów
├── controller/                 endpointy HTTP – odczyt i walidacja żądania, kod odpowiedzi
│   ├── AuthMiddleware.kt       sprawdza token JWT przed każdym /api/... (poza /api/auth/...)
│   ├── AuthController.kt       /api/auth/register, /api/auth/login
│   ├── WalletController.kt     /api/wallet, /api/wallet/top-up
│   ├── RatesController.kt      /api/rates, /api/rates/{code}/history
│   ├── ExchangeController.kt   /api/exchange/quote, /buy, /sell
│   └── TransactionController.kt /api/transactions
├── usecase/                    przypadki użycia (po jednym na UC z diagramu przypadków użycia)
│   ├── RegisterUser.kt         UC-01 Zarejestruj się
│   ├── LoginUser.kt            UC-02 Zaloguj się
│   ├── TopUpAccount.kt         UC-04 Zasil konto
│   ├── ReadOrFetchRateTable.kt UC-05 / UC-09 Aktualne kursy (z bazy albo z NBP)
│   ├── ReadHistoricalRates.kt  UC-06 Kursy archiwalne
│   ├── QuoteExchange.kt        wycena przed transakcją (FR-M-09)
│   ├── BuyCurrency.kt          UC-07 Kup walutę
│   ├── SellCurrency.kt         UC-08 Sprzedaj walutę
│   ├── ReadWallet.kt           UC-10 Portfel
│   └── ReadTransactionHistory.kt UC-11 Historia transakcji
├── service/
│   ├── AuthService.kt          hashowanie haseł (bcrypt) i tokeny JWT
│   ├── RateService.kt          pobieranie kursów z API NBP i ich pamięć podręczna w bazie
│   └── ExchangeService.kt      wycena wymiany: wybór kursu (ASK/BID) i zaokrąglenia
├── databaseConnector/          zapytania SQL
│   ├── DatabaseFactory.kt      połączenie, migracje Flyway, transakcje
│   └── UserRepository.kt, WalletRepository.kt, TransactionRepository.kt,
│       RateTableRepository.kt, CurrencyRepository.kt
├── model/                      klasy danych: User, Wallet, Balance, Transaction, RateTable, Rate, ...
└── utils/
    ├── NbpApiMapper.kt         zamiana JSON z NBP na obiekty modelu
    ├── GsonMapper.kt           JSON dla Javalina (daty ISO 8601, kursy jako tekst)
    ├── Validation.kt           walidacja danych wejściowych (400 Bad Request)
    └── Dates.kt                dzisiejsza data w strefie Europe/Warsaw
src/main/resources/db/migration/V1__INIT_SCHEMA.sql   schemat bazy danych
src/test/kotlin/                testy jednostkowe logiki (wycena, portfel, hasła i tokeny, walidacja)
```

## Uruchomienie

### Wymagania

- **JDK 25** (np. Temurin albo JBR z Android Studio). Gradle pobierze się sam przez Gradle Wrapper.
- **Docker Desktop** – do bazy danych PostgreSQL.

### 1. Konfiguracja

Skopiuj wzór konfiguracji i uzupełnij hasła oraz `JWT_SECRET` (losowy ciąg min. 32 znaków):

```bash
cp .env.example .env.dev
```

Plik `.env.dev` jest w `.gitignore` – sekrety nie trafiają do repozytorium.

### 2a. Wszystko w Dockerze (najprościej, np. na prezentację)

```bash
docker compose up --build
```

Backend: http://localhost:2137 (baza jest dostępna tylko wewnątrz sieci Dockera).

### 2b. Baza w Dockerze, backend lokalnie (do programowania)

```bash
docker compose -f docker-compose.dev.yaml up -d
```

```bash
./gradlew run
```

Na Windowsie w PowerShellu: `.\gradlew.bat run`. Można też uruchomić `Main.kt` z IntelliJ IDEA
(katalog roboczy = katalog projektu, żeby został znaleziony plik `.env.dev`).

Przy starcie Flyway sam tworzy tabele. pgAdmin jest pod http://localhost:1100
(serwer: `postgres`, port `5432`, dane z `.env.dev`).

### Testy

Testy jednostkowe logiki (wycena, portfel, hasła i tokeny, walidacja):

```bash
./gradlew test
```

Testy API całego systemu (backend + baza + NBP) – wszystkie przypadki użycia UC-01 … UC-12, autoryzacja,
izolacja danych użytkowników i równoczesne transakcje. Wymagają Node.js 18+ (bez instalowania pakietów)
i uruchomionego backendu:

```bash
node --test "api-tests/*.test.mjs"
```

Te same scenariusze są w kolekcji Postmana `docs/kantor.postman_collection.json` (opis niżej).

### Reset bazy danych

Usuwa wszystkie dane (konta, salda, transakcje) – przydatne m.in. wtedy, gdy baza powstała jeszcze
ze starych migracji szkieletu i Flyway zgłasza niezgodność:

```bash
docker compose -f docker-compose.dev.yaml down -v
```

### Połączenie z aplikacją mobilną

Pełna instrukcja dla zespołu aplikacji mobilnej (adresy dla emulatora, telefonu i Expo Web, obsługa tokenu
i błędów, przykładowe odpowiedzi dla każdego przypadku użycia): **[docs/FRONTEND.md](docs/FRONTEND.md)**.

- emulator Androida: `http://10.0.2.2:2137`
- telefon w tej samej sieci Wi-Fi: `http://<IP komputera>:2137`
- przeglądarkowa wersja Expo (`http://localhost:8081`) jest dopuszczona w ustawieniach CORS w `Main.kt`.

HTTP jest dopuszczalny tylko lokalnie – na produkcji backend powinien stać za HTTPS (NFR-07).

## API

Wszystkie endpointy poza `/api/auth/...` wymagają nagłówka `Authorization: Bearer <accessToken>`.

**Kwoty podajemy w jednostkach groszowych** (liczby całkowite): `10000` = 100,00 PLN albo 100,00 USD.

| Metoda | Ścieżka                                         | Treść żądania                                 | Odpowiedź                          |
|--------|-------------------------------------------------|-----------------------------------------------|------------------------------------|
| POST   | `/api/auth/register`                            | `{"email", "password"}` (hasło min. 8 znaków) | 201 – użytkownik                   |
| POST   | `/api/auth/login`                               | `{"email", "password"}`                       | 200 – `accessToken`, `expiresIn`   |
| GET    | `/api/wallet`                                   | –                                             | 200 – salda we wszystkich walutach |
| POST   | `/api/wallet/top-up`                            | `{"amount"}` (tylko PLN)                      | 201 – transakcja + portfel         |
| GET    | `/api/rates`                                    | –                                             | 200 – aktualna tabela C            |
| GET    | `/api/rates/{code}/history?from=RRRR-MM-DD&to=RRRR-MM-DD` | – (max 93 dni)                      | 200 – kursy archiwalne             |
| POST   | `/api/exchange/quote`                           | `{"type": "BUY"/"SELL", "currency", "amount"}`| 200 – wycena (bez zapisu)          |
| POST   | `/api/exchange/buy`                             | `{"currency", "amount"}`                      | 201 – transakcja + portfel         |
| POST   | `/api/exchange/sell`                            | `{"currency", "amount"}`                      | 201 – transakcja + portfel         |
| GET    | `/api/transactions?limit=100`                   | –                                             | 200 – transakcje od najnowszej     |

Pełna dokumentacja i testy API są w katalogu `docs/`:

- `docs/openapi.yaml` – specyfikacja OpenAPI 3 (Swagger): wklej do https://editor.swagger.io albo zaimportuj w Postmanie,
- `docs/kantor.postman_collection.json` – kolekcja Postmana z testami: Import → plik, potem „Run collection”
  (cały scenariusz od rejestracji do historii transakcji; każde uruchomienie zakłada nowego użytkownika).

Wylogowanie (UC-03) odbywa się w aplikacji mobilnej – usuwa ona token z urządzenia; token i tak wygasa
po `JWT_EXPIRATION_MINUTES` minutach.

### Błędy

Każdy błąd ma postać `{"error": "komunikat po polsku"}`:

| Kod | Kiedy                                                                 |
|-----|-----------------------------------------------------------------------|
| 400 | nieprawidłowe dane (e-mail, kwota, kod waluty, daty, JSON)            |
| 401 | brak / nieprawidłowy / wygasły token albo błędny e-mail lub hasło     |
| 409 | konto z tym e-mailem już istnieje                                     |
| 422 | brak wystarczających środków                                          |
| 503 | API NBP nie odpowiada, a w bazie nie ma jeszcze żadnej tabeli kursów  |

### Przykładowy scenariusz (curl w Git Bash)

```bash
API=http://localhost:2137

curl -X POST $API/api/auth/register -H "Content-Type: application/json" \
  -d '{"email":"jan@example.com","password":"tajnehaslo1"}'

TOKEN=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"jan@example.com","password":"tajnehaslo1"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

# zasilenie konta kwotą 1000,00 PLN
curl -X POST $API/api/wallet/top-up -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"amount":100000}'

# wycena i kupno 100,00 USD
curl -X POST $API/api/exchange/quote -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"type":"BUY","currency":"USD","amount":10000}'
curl -X POST $API/api/exchange/buy -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"currency":"USD","amount":10000}'

curl $API/api/wallet -H "Authorization: Bearer $TOKEN"
curl $API/api/transactions -H "Authorization: Bearer $TOKEN"
```

## Zasady działania kantoru

- **Źródło kursów** – tabela C NBP (kursy kupna i sprzedaży). Klient **kupuje walutę po kursie sprzedaży
  (ASK)**, a **sprzedaje po kursie kupna (BID)**. Wymiana odbywa się tylko w parach PLN ↔ waluta obca.
- **Pamięć podręczna kursów** – tabela jest zapisywana w bazie. Nową pobieramy z NBP dopiero wtedy, gdy
  zapisana nie jest dzisiejsza, i najwyżej raz na 15 minut. Gdy NBP nie odpowiada, używamy ostatniej
  zapisanej tabeli – jej numer i data są zawsze w odpowiedzi.
- **Kwoty** to liczby całkowite w groszach (centach), kursy to `numeric` / `BigDecimal` – bez liczb
  zmiennoprzecinkowych. Wynik wymiany zaokrąglamy do grosza **na korzyść kantoru**: przy kupnie w górę,
  przy sprzedaży w dół.
- **Historia** – każda transakcja zapisuje kurs, jego rodzaj (ASK/BID), numer i datę tabeli NBP oraz czas
  serwera. Trigger w bazie blokuje modyfikację i usuwanie transakcji, więc zmiana kursów nie wpływa na historię.
- **Spójność** – zmiana obu sald i zapis transakcji wykonują się w jednej transakcji bazodanowej (wszystko
  albo nic). Przed zmianą salda blokujemy wiersz użytkownika (`SELECT ... FOR UPDATE`), więc równoczesne
  żądania tego samego użytkownika wykonują się po kolei. Dodatkowo `CHECK (amount >= 0)` w bazie nie
  pozwala, by saldo spadło poniżej zera.
- **Bezpieczeństwo** – hasła tylko jako hash bcrypt, sesja jako token JWT z czasem ważności, sekrety
  wyłącznie w `.env.dev` (poza repozytorium).
