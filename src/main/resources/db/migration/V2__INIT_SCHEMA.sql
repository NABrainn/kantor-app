CREATE EXTENSION citext;
CREATE DOMAIN email AS citext CHECK ( value ~ '^[a-zA-Z0-9.!#$%&''*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$' );
CREATE TABLE currency_dictionary(
    id int4 GENERATED ALWAYS AS IDENTITY,
    value varchar(20),
    PRIMARY KEY (id)
);
CREATE TABLE users(
    id int4 GENERATED ALWAYS AS IDENTITY,
    email email NOT NULL UNIQUE,
    password_hash text NOT NULL,
    created_at date NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at date NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE TYPE balance_kind AS ENUM ('internal', 'external');
CREATE TABLE balances(
    id int4 GENERATED ALWAYS AS IDENTITY,
    kind balance_kind NOT NULL,
    currency int4 REFERENCES currency_dictionary(id),
    created_by int4 references users(id),
    created_at date NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at date NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE TYPE transaction_kind AS ENUM ('withdrawal', 'payment', 'exchange');
CREATE TABLE transactions(
    id int8 GENERATED ALWAYS AS IDENTITY,
    kind transaction_kind NOT NULL,
    source_currency_id int4 REFERENCES currency_dictionary(id),
    target_currency_id int4 REFERENCES currency_dictionary(id),
    amount float8 NOT NULL CHECK (amount > 0.0),
    exchange_rate float8 NOT NULL CHECK (exchange_rate > 0.0),
    execution_time float4 NOT NULL,
    created_by int4 REFERENCES users(id),
    created_at date NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at date NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE TABLE exchange_rates_history(
    id int4 GENERATED ALWAYS AS IDENTITY,
    data json NOT NULL,
    created_at date NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at date NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
