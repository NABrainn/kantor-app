-- Schemat bazy danych kantoru walut (PostgreSQL).
-- Kwoty są liczbami całkowitymi w jednostkach groszowych (grosze, centy) – założenie Z-07.
-- Kursy są typu numeric (arytmetyka dziesiętna, bez liczb zmiennoprzecinkowych) – NFR-06.

CREATE EXTENSION IF NOT EXISTS citext;

-- Adres e-mail bez rozróżniania wielkości liter, z kontrolą formatu
CREATE DOMAIN email AS citext CHECK ( value ~ '^[a-zA-Z0-9.!#$%&''*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$' );


-- Słownik walut. PLN dodajemy od razu, pozostałe waluty trafiają tu automatycznie z tabeli C NBP.
CREATE TABLE currencies (
    code char(3)      PRIMARY KEY,
    name varchar(100) NOT NULL
);

INSERT INTO currencies (code, name) VALUES ('PLN', 'złoty polski');


-- Użytkownicy. Hasło przechowujemy wyłącznie jako hash bcrypt (NFR-01).
CREATE TABLE users (
    id            int4        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         email       NOT NULL UNIQUE,
    password_hash text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);


-- Portfel użytkownika = jego salda w poszczególnych walutach.
-- CHECK (amount >= 0) to ostatnia linia obrony: saldo nigdy nie spadnie poniżej zera (NFR-05).
CREATE TABLE balances (
    user_id       int4        NOT NULL REFERENCES users (id),
    currency_code char(3)     NOT NULL REFERENCES currencies (code),
    amount        int8        NOT NULL DEFAULT 0 CHECK (amount >= 0),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, currency_code)
);


-- Historia transakcji. Kurs i dane tabeli NBP są kopiowane w chwili transakcji,
-- więc późniejsza zmiana kursów nie zmienia historii (pkt 4 specyfikacji).
CREATE TYPE transaction_type AS ENUM ('TOP_UP', 'BUY', 'SELL');
CREATE TYPE rate_type AS ENUM ('BID', 'ASK');

CREATE TABLE transactions (
    id                 int8             GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            int4             NOT NULL REFERENCES users (id),
    type               transaction_type NOT NULL,
    from_currency      char(3)          REFERENCES currencies (code),
    to_currency        char(3)          NOT NULL REFERENCES currencies (code),
    from_amount        int8             CHECK (from_amount > 0),
    to_amount          int8             NOT NULL CHECK (to_amount > 0),
    rate               numeric          CHECK (rate > 0),
    rate_type          rate_type,
    nbp_table_no       varchar(20),
    nbp_effective_date date,
    created_at         timestamptz      NOT NULL DEFAULT now(), -- czas serwera

    -- Zasilenie konta nie ma waluty źródłowej ani kursu; kupno i sprzedaż muszą mieć komplet danych.
    CHECK (
        (type = 'TOP_UP'
            AND from_currency IS NULL AND from_amount IS NULL
            AND rate IS NULL AND rate_type IS NULL AND nbp_table_no IS NULL AND nbp_effective_date IS NULL)
        OR
        (type IN ('BUY', 'SELL')
            AND from_currency IS NOT NULL AND from_amount IS NOT NULL
            AND rate IS NOT NULL AND rate_type IS NOT NULL AND nbp_table_no IS NOT NULL AND nbp_effective_date IS NOT NULL)
    )
);

CREATE INDEX transactions_user_id_created_at_idx ON transactions (user_id, created_at DESC);

-- Zapisanych transakcji nie można modyfikować ani usuwać (FR-S-11).
CREATE FUNCTION forbid_transaction_changes() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Transakcje są niezmienne – nie można ich modyfikować ani usuwać';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER transactions_are_immutable
    BEFORE UPDATE OR DELETE ON transactions
    FOR EACH ROW EXECUTE FUNCTION forbid_transaction_changes();


-- Pamięć podręczna tabel kursów C pobranych z API NBP (FR-S-05).
CREATE TABLE rate_tables (
    table_no       varchar(20) PRIMARY KEY,          -- np. 216/C/NBP/2026
    trading_date   date        NOT NULL,
    effective_date date        NOT NULL,             -- dzień publikacji tabeli
    fetched_at     timestamptz NOT NULL DEFAULT now() -- kiedy ostatnio potwierdziliśmy tę tabelę w NBP
);

CREATE TABLE rates (
    table_no      varchar(20) NOT NULL REFERENCES rate_tables (table_no),
    currency_code char(3)     NOT NULL REFERENCES currencies (code),
    bid           numeric     NOT NULL CHECK (bid > 0), -- kurs kupna (kantor kupuje od klienta)
    ask           numeric     NOT NULL CHECK (ask > 0), -- kurs sprzedaży (kantor sprzedaje klientowi)
    PRIMARY KEY (table_no, currency_code)
);
