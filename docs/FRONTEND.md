# Backend kantoru – instrukcja dla zespołu aplikacji mobilnej

Jak uruchomić backend u siebie w Dockerze, jak połączyć z nim aplikację (emulator, telefon, Expo Web)
i jak wyglądają odpowiedzi API dla każdego przypadku użycia.

Pełna specyfikacja: [`openapi.yaml`](openapi.yaml). Wszystkie żądania z przykładowymi odpowiedziami są w
kolekcji Postmana [`kantor.postman_collection.json`](kantor.postman_collection.json): przy każdym żądaniu,
w zakładce **Examples**.

---

## 1. Uruchomienie backendu (Docker)

Potrzebny jest tylko **Docker Desktop**. Java i baza danych nie są potrzebne na komputerze.

```powershell
cd banking-backend
copy .env.example .env.dev
```

W `.env.dev` uzupełnij `POSTGRES_PASSWORD` i `JWT_SECRET` (dowolny ciąg, min. 32 znaki). Pliku `.env.dev`
nie commitujemy.

```powershell
docker compose up --build -d
```

Pierwsze uruchomienie trwa kilka minut, bo Docker buduje obraz. Backend działa pod **http://localhost:2137**.
Szybki test w przeglądarce: http://localhost:2137/api/rates powinno zwrócić `401` z JSON-em `{"error": ...}`,
co znaczy, że serwer działa i wymaga logowania.

| Co | Polecenie |
|----|-----------|
| logi backendu (każde żądanie: `POST /api/exchange/buy -> 201 (35 ms)`) | `docker compose logs -f backend` |
| zatrzymanie (dane zostają) | `docker compose down` |
| wyczyszczenie bazy (konta, salda, transakcje) | `docker compose down -v` |
| po `git pull` z nowym backendem | `docker compose up --build -d` |

---

## 2. Adres backendu w aplikacji

Backend słucha na porcie **2137** komputera. To, jaki adres wpisać w aplikacji, zależy od tego, gdzie ona działa:

| Gdzie działa aplikacja | `API_URL` | Uwagi |
|---|---|---|
| **Emulator Androida** (Android Studio) | `http://10.0.2.2:2137` | `10.0.2.2` to w emulatorze „localhost komputera”. `localhost` w emulatorze wskazuje na sam emulator! |
| **Telefon przez USB** | `http://localhost:2137` | najpierw `adb reverse tcp:2137 tcp:2137` (powtórz po każdym podłączeniu telefonu) |
| **Telefon przez Wi-Fi** (Expo Go) | `http://<IP komputera>:2137`, np. `http://192.168.0.122:2137` | telefon i komputer w tej samej sieci; IP z `ipconfig` (IPv4); zapora Windows musi wpuścić port 2137 (patrz niżej) |
| **Expo Web** w przeglądarce (`localhost:8081`) | `http://localhost:2137` | CORS jest skonfigurowany dla `http://localhost:8081` i `http://localhost:19006` |
| Symulator iOS (Mac) | `http://localhost:2137` | |

Najwygodniej trzymać adres w zmiennej środowiskowej Expo, np. w pliku `.env` aplikacji:

```bash
EXPO_PUBLIC_API_URL=http://10.0.2.2:2137
```

```ts
export const API_URL = process.env.EXPO_PUBLIC_API_URL ?? "http://10.0.2.2:2137";
```

**Zapora Windows (tylko dla telefonu przez Wi-Fi).** W PowerShellu jako administrator:

```powershell
New-NetFirewallRule -DisplayName "Kantor backend 2137" -Direction Inbound -Protocol TCP -LocalPort 2137 -Action Allow -Profile Private
```

Sieć Wi-Fi musi być w Windows ustawiona jako **prywatna**. W sieciach uczelnianych z izolacją klientów
Wi-Fi to nie zadziała, wtedy zostaje USB i `adb reverse`.

**HTTP bez szyfrowania (cleartext).** Expo Go i buildy debug łączą się po `http://` bez problemu. Jeśli
build *preview/release* zgłasza `Network request failed`, to Android blokuje HTTP. Do testów lokalnych
dodaj w `app.json` plugin `expo-build-properties` z `"android": { "usesCleartextTraffic": true }`.
W wersji produkcyjnej backend powinien działać po HTTPS (NFR-07).

---

## 3. Zasady API, które trzeba znać

**Kwoty są w groszach (liczby całkowite).** `10000` = 100,00 PLN albo 100,00 USD. Wysyłaj liczbę
(`"amount": 10000`), nie tekst (`"100"`) ani ułamek (`100.5`), bo inaczej dostaniesz `400`.
Do wyświetlania: `(amount / 100).toLocaleString("pl-PL", { minimumFractionDigits: 2 })`.

**Kursy przychodzą jako tekst** (`"rate": "4.1010"`), żeby nie tracić dokładności. Do wyświetlenia
wystarczy pokazać tekst, a do wykresu: `Number(rate)`.

**Daty:** `createdAt` i `updatedAt` to czas UTC w ISO 8601 (`2026-11-25T17:36:28.955Z`), do pokazania w
strefie użytkownika. `effectiveDate` to data tabeli NBP (`2026-11-25`).

**Logowanie i token:**
1. `POST /api/auth/login` zwraca `accessToken` (JWT) i `expiresIn` (sekundy, domyślnie 3600).
2. Token zapisz w `expo-secure-store` (nie w AsyncStorage).
3. Każde żądanie poza `/api/auth/...` wysyłaj z nagłówkiem `Authorization: Bearer <accessToken>`.
4. Odpowiedź `401` na chronionym zasobie oznacza, że token wygasł albo jest zły. Wtedy usuń token i przejdź
   do logowania.
5. Wylogowanie (UC-03) to tylko usunięcie tokenu z urządzenia. Backend nie ma endpointu `logout`.

**Aplikacja nie wysyła kursu, salda ani id użytkownika.** Backend sam bierze kurs z NBP, liczy kwotę w PLN
i bierze użytkownika z tokenu. Dodatkowe pola w JSON-ie są ignorowane.

**Błędy** mają zawsze postać `{"error": "komunikat po polsku"}`. Komunikat można pokazać użytkownikowi
bezpośrednio.

| Kod | Kiedy | Co zrobić w aplikacji |
|-----|-------|-----------------------|
| 400 | złe dane (e-mail, kwota, waluta, daty, JSON) | pokaż `error` przy formularzu |
| 401 | brak, zły lub wygasły token; zły e-mail/hasło przy logowaniu | wyloguj i przejdź do logowania (albo pokaż błąd logowania) |
| 409 | e-mail już zajęty (rejestracja) | pokaż `error` |
| 422 | za mało środków | pokaż `error`, odśwież portfel |
| 503 | NBP nie działa, a backend nie ma jeszcze żadnej tabeli kursów | „Spróbuj później” |
| 500 | błąd serwera | ogólny komunikat |
| *brak odpowiedzi* | backend nie działa / zły `API_URL` | „Brak połączenia z serwerem” |

### Przykładowy klient API (TypeScript)

```ts
import * as SecureStore from "expo-secure-store";

const API_URL = process.env.EXPO_PUBLIC_API_URL ?? "http://10.0.2.2:2137";

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const token = await SecureStore.getItemAsync("accessToken");
  let res: Response;
  try {
    res = await fetch(API_URL + path, {
      method: options.method ?? "GET",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    });
  } catch {
    throw new ApiError(0, "Brak połączenia z serwerem");
  }
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    if (res.status === 401 && token) await SecureStore.deleteItemAsync("accessToken"); // sesja wygasła
    throw new ApiError(res.status, data?.error ?? "Nieoczekiwany błąd serwera");
  }
  return data as T;
}

// użycie
const { accessToken } = await api<{ accessToken: string }>("/api/auth/login", {
  method: "POST", body: { email, password },
});
await SecureStore.setItemAsync("accessToken", accessToken);
const wallet = await api<Wallet>("/api/wallet");
```

---

## 4. Endpointy i przykładowe odpowiedzi (po przypadkach użycia)

Przykłady pochodzą z prawdziwego backendu. Listy kursów są skrócone.

### UC-01 Zarejestruj się – `POST /api/auth/register`

Żądanie (bez tokenu). Hasło musi mieć min. 8 znaków.

```json
{ "email": "jan.kowalski@example.com", "password": "tajnehaslo1" }
```

`201 Created`. Konto ma od razu portfel z saldem 0 PLN. Rejestracja **nie loguje**, potem trzeba wywołać login.

```json
{
  "id": 1,
  "email": "jan.kowalski@example.com",
  "createdAt": "2026-11-25T17:36:28.046855Z"
}
```

Błędy:

```json
// 409 Conflict
{ "error": "Konto z tym adresem e-mail już istnieje" }
// 400 Bad Request
{ "error": "Nieprawidłowy format adresu e-mail" }
{ "error": "Hasło musi mieć co najmniej 8 znaków" }
```

### UC-02 Zaloguj się – `POST /api/auth/login`

```json
{ "email": "jan.kowalski@example.com", "password": "tajnehaslo1" }
```

`200 OK`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrYW50b3ItYmFja2VuZCIsInN1YiI6IjEiLCJpYXQiOjE3OTU2MjgxODgsImV4cCI6MTc5NTYzMTc4OH0.przykladowy-podpis",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

`401 Unauthorized`. Komunikat jest ten sam dla złego hasła i nieistniejącego konta:

```json
{ "error": "Nieprawidłowy e-mail lub hasło" }
```

### UC-03 Wyloguj się

Brak endpointu: usuń token z `SecureStore`. Każde żądanie bez tokenu albo z wygasłym tokenem dostaje:

```json
// 401 Unauthorized
{ "error": "Sesja wygasła lub token jest nieprawidłowy – zaloguj się ponownie" }
```

### UC-04 Zasil konto – `POST /api/wallet/top-up`

Tylko PLN. Kwota w groszach, max 100000000 (1 000 000,00).

```json
{ "amount": 100000 }
```

`201 Created`. Odpowiedź zawiera zapisaną transakcję i portfel po zmianie, więc nie trzeba osobno pobierać portfela.

```json
{
  "transaction": {
    "id": 1,
    "userId": 1,
    "type": "TOP_UP",
    "fromCurrency": null,
    "toCurrency": "PLN",
    "fromAmount": null,
    "toAmount": 100000,
    "rate": null,
    "rateType": null,
    "nbpTableNo": null,
    "nbpEffectiveDate": null,
    "createdAt": "2026-11-25T17:36:28.726983Z"
  },
  "wallet": {
    "userId": 1,
    "balances": [
      { "currency": "PLN", "amount": 100000, "updatedAt": "2026-11-25T17:36:28.726983Z" }
    ]
  }
}
```

Błędy (`400`):

```json
{ "error": "Kwota musi być większa od zera" }
{ "error": "Kwota nie może przekraczać 1 000 000,00" }
{ "error": "Nieprawidłowy lub pusty JSON w treści żądania (kwoty podaje się jako liczby całkowite w groszach)" }
```

### UC-05 / UC-09 Aktualne kursy – `GET /api/rates`

Tabela C NBP: `bid` to kurs, po którym kantor **kupuje** od klienta (klient sprzedaje), a `ask` to kurs,
po którym kantor **sprzedaje** (klient kupuje). `effectiveDate` i `tableNo` warto pokazać, bo gdy NBP
nie działa, backend zwraca ostatnią zapisaną tabelę (NFR-10).

`200 OK` (lista skrócona, w odpowiedzi jest ok. 13 walut):

```json
{
  "tableNo": "229/C/NBP/2026",
  "tradingDate": "2026-11-24",
  "effectiveDate": "2026-11-25",
  "fetchedAt": "2026-11-25T16:50:09.294302Z",
  "rates": [
    { "code": "AUD", "currency": "dolar australijski", "bid": "2.6753", "ask": "2.7293" },
    { "code": "EUR", "currency": "euro", "bid": "4.4113", "ask": "4.5005" },
    { "code": "USD", "currency": "dolar amerykański", "bid": "4.0198", "ask": "4.1010" }
  ]
}
```

`503 Service Unavailable`, tylko gdy NBP nie odpowiada, a w bazie nie ma jeszcze żadnej tabeli:

```json
{ "error": "Brak połączenia z API NBP" }
```

### UC-06 / UC-12 Kursy archiwalne (wykres) – `GET /api/rates/{kod}/history?from=RRRR-MM-DD&to=RRRR-MM-DD`

Np. `/api/rates/EUR/history?from=2026-11-11&to=2026-11-25`. Zakres to max 93 dni, `to` nie może być
z przyszłości. W weekendy i święta NBP nie publikuje tabel, więc tych dni nie ma na liście. Gdy w zakresie
nie ma żadnej tabeli, przychodzi pusta lista `[]`.

`200 OK` (od najstarszego, skrócone):

```json
[
  { "effectiveDate": "2026-11-12", "tableNo": "220/C/NBP/2026", "bid": "4.3514", "ask": "4.4394" },
  { "effectiveDate": "2026-11-13", "tableNo": "221/C/NBP/2026", "bid": "4.3615", "ask": "4.4497" },
  { "effectiveDate": "2026-11-16", "tableNo": "222/C/NBP/2026", "bid": "4.3623", "ask": "4.4505" }
]
```

Błędy (`400`):

```json
{ "error": "Zakres dat może obejmować najwyżej 93 dni" }
{ "error": "Data końcowa nie może być z przyszłości" }
{ "error": "Nieprawidłowa data w parametrze from – użyj formatu RRRR-MM-DD" }
{ "error": "Waluta XYZ nie występuje w tabeli kursów NBP" }
```

### Wycena przed transakcją (FR-M-09) – `POST /api/exchange/quote`

Pokazuje kurs i kwotę w PLN **bez zapisu**. Wywołaj ją przed ekranem potwierdzenia.
`type`: `BUY` (klient kupuje walutę) albo `SELL` (klient sprzedaje). `amount` to kwota waluty obcej w centach.

```json
{ "type": "BUY", "currency": "USD", "amount": 10000 }
```

`200 OK`. Kupno 100,00 USD kosztuje 410,10 PLN:

```json
{
  "type": "BUY",
  "currency": "USD",
  "amount": 10000,
  "rate": "4.1010",
  "rateType": "ASK",
  "plnAmount": 41010,
  "tableNo": "229/C/NBP/2026",
  "effectiveDate": "2026-11-25"
}
```

Dla `SELL` przychodzi `"rateType": "BID"`, a `plnAmount` to kwota, którą klient **dostanie**
(np. 100,00 USD daje `40198`, czyli 401,98 PLN).

### UC-07 Kup walutę – `POST /api/exchange/buy`

```json
{ "currency": "USD", "amount": 10000 }
```

`201 Created`. Koszt w PLN to `transaction.fromAmount`:

```json
{
  "transaction": {
    "id": 2,
    "userId": 1,
    "type": "BUY",
    "fromCurrency": "PLN",
    "toCurrency": "USD",
    "fromAmount": 41010,
    "toAmount": 10000,
    "rate": "4.1010",
    "rateType": "ASK",
    "nbpTableNo": "229/C/NBP/2026",
    "nbpEffectiveDate": "2026-11-25",
    "createdAt": "2026-11-25T17:36:28.955322Z"
  },
  "wallet": {
    "userId": 1,
    "balances": [
      { "currency": "PLN", "amount": 58990, "updatedAt": "2026-11-25T17:36:28.955322Z" },
      { "currency": "USD", "amount": 10000, "updatedAt": "2026-11-25T17:36:28.955322Z" }
    ]
  }
}
```

Kurs może się różnić od wyceny, jeśli w międzyczasie NBP opublikował nową tabelę. Pokazuj wtedy dane
z odpowiedzi `buy`, nie z wyceny.

Błędy:

```json
// 422 Unprocessable Entity
{ "error": "Brak wystarczających środków w walucie PLN" }
// 400 Bad Request
{ "error": "Waluta XYZ nie występuje w tabeli kursów NBP" }
{ "error": "Wymiana dotyczy walut obcych – PLN jest walutą rozliczeniową" }
{ "error": "Nieprawidłowy kod waluty – podaj 3 litery, np. USD" }
```

### UC-08 Sprzedaj walutę – `POST /api/exchange/sell`

```json
{ "currency": "USD", "amount": 5000 }
```

`201 Created`. Wypłata w PLN to `transaction.toAmount`:

```json
{
  "transaction": {
    "id": 3,
    "userId": 1,
    "type": "SELL",
    "fromCurrency": "USD",
    "toCurrency": "PLN",
    "fromAmount": 5000,
    "toAmount": 20099,
    "rate": "4.0198",
    "rateType": "BID",
    "nbpTableNo": "229/C/NBP/2026",
    "nbpEffectiveDate": "2026-11-25",
    "createdAt": "2026-11-25T17:36:28.984595Z"
  },
  "wallet": {
    "userId": 1,
    "balances": [
      { "currency": "PLN", "amount": 79089, "updatedAt": "2026-11-25T17:36:28.984595Z" },
      { "currency": "USD", "amount": 5000, "updatedAt": "2026-11-25T17:36:28.984595Z" }
    ]
  }
}
```

```json
// 422 Unprocessable Entity
{ "error": "Brak wystarczających środków w walucie USD" }
```

### UC-10 Przeglądaj portfel – `GET /api/wallet`

`200 OK`. Najpierw PLN, potem pozostałe waluty alfabetycznie. Waluta, której użytkownik jeszcze nie miał,
nie pojawia się na liście, a sprzedana do zera zostaje z `amount: 0`.

```json
{
  "userId": 1,
  "balances": [
    { "currency": "PLN", "amount": 79089, "updatedAt": "2026-11-25T17:36:28.984595Z" },
    { "currency": "USD", "amount": 5000, "updatedAt": "2026-11-25T17:36:28.984595Z" }
  ]
}
```

Nowe konto: `"balances": [{ "currency": "PLN", "amount": 0, ... }]`.

### UC-11 Historia transakcji – `GET /api/transactions?limit=100`

Od najnowszej. `limit` jest opcjonalny (1–500, domyślnie 100). Dla `TOP_UP` pola `from*`, `rate*` i `nbp*`
mają wartość `null`.

`200 OK`:

```json
[
  {
    "id": 3, "userId": 1, "type": "SELL",
    "fromCurrency": "USD", "toCurrency": "PLN", "fromAmount": 5000, "toAmount": 20099,
    "rate": "4.0198", "rateType": "BID", "nbpTableNo": "229/C/NBP/2026", "nbpEffectiveDate": "2026-11-25",
    "createdAt": "2026-11-25T17:36:28.984595Z"
  },
  {
    "id": 2, "userId": 1, "type": "BUY",
    "fromCurrency": "PLN", "toCurrency": "USD", "fromAmount": 41010, "toAmount": 10000,
    "rate": "4.1010", "rateType": "ASK", "nbpTableNo": "229/C/NBP/2026", "nbpEffectiveDate": "2026-11-25",
    "createdAt": "2026-11-25T17:36:28.955322Z"
  },
  {
    "id": 1, "userId": 1, "type": "TOP_UP",
    "fromCurrency": null, "toCurrency": "PLN", "fromAmount": null, "toAmount": 100000,
    "rate": null, "rateType": null, "nbpTableNo": null, "nbpEffectiveDate": null,
    "createdAt": "2026-11-25T17:36:28.726983Z"
  }
]
```

Nowe konto: `[]`.

---

## 5. Gdy coś nie działa

| Objaw | Przyczyna / rozwiązanie |
|-------|-------------------------|
| `Network request failed` w emulatorze | używasz `localhost` zamiast `10.0.2.2` |
| `Network request failed` na telefonie | zły IP / inna sieć Wi-Fi / zapora Windows; najszybciej: USB + `adb reverse tcp:2137 tcp:2137` i `localhost` |
| działa w Expo Go, nie działa w buildzie release | Android blokuje HTTP – `usesCleartextTraffic` (sekcja 2) |
| błąd CORS w przeglądarce (Expo Web) | Expo Web musi działać na `localhost:8081` albo `localhost:19006` |
| wszystko zwraca `401` | brak nagłówka `Authorization: Bearer ...` albo token wygasł (60 min) – zaloguj się ponownie |
| `400` przy kwocie | kwota musi być liczbą całkowitą w groszach (`10000`), nie `"100"` ani `100.5` |
| backend nie startuje | `docker compose logs backend`; zwykle brak `.env.dev` albo za krótki `JWT_SECRET` |
| dziwne dane po zmianach w backendzie | `docker compose down -v` i `docker compose up --build -d` (czysta baza) |

**Mock bez backendu:** w Postmanie kliknij na kolekcji **⋯ → Mock collection**. Postman zwróci zapisane
przykłady odpowiedzi, co przydaje się do pracy nad UI, zanim backend będzie u wszystkich działał.
