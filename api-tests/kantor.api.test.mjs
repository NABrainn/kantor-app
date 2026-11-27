// Testy API backendu kantoru – przypadki użycia z diagramu przypadki_uzycia.puml
// oraz wymagania bezpieczeństwa (FR-S-02, FR-S-03, FR-S-04, NFR-01, NFR-05).
//
// Wymagania: Node.js 18+ (bez dodatkowych pakietów) i uruchomiony backend.
// Uruchomienie (z katalogu banking-backend):
//   node --test api-tests/
// Inny adres backendu:
//   API=http://localhost:2137 node --test api-tests/

import { test, before } from "node:test";
import assert from "node:assert/strict";

const API = process.env.API ?? "http://localhost:2137";
const PASSWORD = "tajnehaslo1";
const run = Date.now();
const emailA = `test_a_${run}@example.com`;
const emailB = `test_b_${run}@example.com`;
let tokenA = "";
let tokenB = "";
let quotedPln = 0;

async function call(method, path, { body, token, rawBody } = {}) {
  const headers = {};
  if (body !== undefined || rawBody !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const res = await fetch(API + path, {
    method,
    headers,
    body: rawBody ?? (body !== undefined ? JSON.stringify(body) : undefined),
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* treść nie jest JSON-em */ }
  return { status: res.status, json, text };
}

function expectError(res, status) {
  assert.equal(res.status, status, res.text);
  assert.equal(typeof res.json?.error, "string", "odpowiedź błędu ma pole error");
}

const day = (d) => d.toISOString().slice(0, 10);
const daysAgo = (n) => day(new Date(Date.now() - n * 24 * 3600 * 1000));

before(async () => {
  try {
    await fetch(API + "/api/rates");
  } catch {
    throw new Error(`Backend nie odpowiada pod ${API} – uruchom go (docker compose up -d)`);
  }
});

// ---------- UC-01 Zarejestruj się ----------

test("UC-01 rejestracja – 201, odpowiedź bez hasła", async () => {
  const res = await call("POST", "/api/auth/register", { body: { email: emailA, password: PASSWORD } });
  assert.equal(res.status, 201, res.text);
  assert.equal(typeof res.json.id, "number");
  assert.equal(res.json.email, emailA);
  assert.ok(!("password" in res.json) && !("passwordHash" in res.json), "nie zwraca hasła ani hasha");
  assert.ok(!res.text.includes(PASSWORD));
});

test("UC-01 rejestracja – ten sam e-mail (inna wielkość liter) → 409", async () => {
  expectError(await call("POST", "/api/auth/register", { body: { email: emailA.toUpperCase(), password: PASSWORD } }), 409);
});

test("UC-01 rejestracja – walidacja danych → 400", async () => {
  expectError(await call("POST", "/api/auth/register", { body: { email: "to-nie-jest-email", password: PASSWORD } }), 400);
  expectError(await call("POST", "/api/auth/register", { body: { email: `krotkie_${run}@example.com`, password: "abc" } }), 400);
  expectError(await call("POST", "/api/auth/register", { body: { email: `dlugie_${run}@example.com`, password: "a".repeat(73) } }), 400);
  expectError(await call("POST", "/api/auth/register", { body: {} }), 400);
  expectError(await call("POST", "/api/auth/register", { rawBody: "{to nie json" }), 400);
});

// ---------- UC-02 Zaloguj się ----------

test("UC-02 logowanie – złe hasło i nieistniejący e-mail dają ten sam komunikat 401", async () => {
  const wrongPassword = await call("POST", "/api/auth/login", { body: { email: emailA, password: "zlehaslo123" } });
  const noUser = await call("POST", "/api/auth/login", { body: { email: `nikt_${run}@example.com`, password: PASSWORD } });
  expectError(wrongPassword, 401);
  expectError(noUser, 401);
  assert.equal(wrongPassword.json.error, noUser.json.error, "jednolity komunikat (FR-S-02)");
});

test("UC-02 czas odpowiedzi nie zdradza, czy konto istnieje", async () => {
  const timeLogin = async (email) => {
    const start = performance.now();
    await call("POST", "/api/auth/login", { body: { email, password: "zlehaslo123" } });
    return performance.now() - start;
  };
  let existing = 0;
  let missing = 0;
  for (let i = 0; i < 3; i++) {
    existing += await timeLogin(emailA);
    missing += await timeLogin(`nikt_${run}_${i}@example.com`);
  }
  // bez wyrównania czasu nieistniejący e-mail odpowiadał ok. 10× szybciej
  assert.ok(missing > existing * 0.5, `istniejące konto: ${existing.toFixed(0)} ms, nieistniejące: ${missing.toFixed(0)} ms`);
});

test("UC-02 logowanie – 200 i token JWT z czasem ważności", async () => {
  const res = await call("POST", "/api/auth/login", { body: { email: emailA, password: PASSWORD } });
  assert.equal(res.status, 200, res.text);
  assert.equal(res.json.tokenType, "Bearer");
  assert.ok(res.json.expiresIn > 0);
  tokenA = res.json.accessToken;
  assert.equal(tokenA.split(".").length, 3, "token ma format JWT");

  const payload = JSON.parse(Buffer.from(tokenA.split(".")[1], "base64url").toString());
  assert.ok(payload.exp > payload.iat, "token wygasa (NFR-04)");
  assert.ok(!JSON.stringify(payload).includes(PASSWORD), "token nie zawiera hasła");
});

// ---------- UC-03 Wyloguj się / autoryzacja (FR-S-03) ----------

test("UC-03 chronione zasoby bez tokenu → 401", async () => {
  for (const [method, path] of [
    ["GET", "/api/wallet"], ["POST", "/api/wallet/top-up"], ["GET", "/api/rates"],
    ["POST", "/api/exchange/quote"], ["POST", "/api/exchange/buy"], ["POST", "/api/exchange/sell"],
    ["GET", "/api/transactions"],
  ]) {
    expectError(await call(method, path), 401);
  }
});

test("UC-03 sfałszowany token → 401 (zły podpis, alg none, podmieniony użytkownik)", async () => {
  const [h, , s] = tokenA.split(".");
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const forgedSub = `${h}.${b64({ iss: "kantor-backend", sub: "1", exp: 9999999999 })}.${s}`;
  const algNone = `${b64({ alg: "none", typ: "JWT" })}.${b64({ iss: "kantor-backend", sub: "1" })}.`;
  const badSignature = tokenA.slice(0, -3) + (tokenA.endsWith("aaa") ? "bbb" : "aaa");

  for (const token of [forgedSub, algNone, badSignature, "to.nie.jest.token"]) {
    expectError(await call("GET", "/api/wallet", { token }), 401);
  }
});

// ---------- UC-05 / UC-09 Aktualne kursy ----------

test("UC-05/UC-09 aktualna tabela C NBP", async () => {
  const res = await call("GET", "/api/rates", { token: tokenA });
  assert.equal(res.status, 200, res.text);
  assert.match(res.json.tableNo, /\/C\/NBP\//);
  assert.ok(res.json.effectiveDate);
  const usd = res.json.rates.find((r) => r.code === "USD");
  assert.ok(usd, "jest USD");
  assert.ok(Number(usd.ask) > Number(usd.bid), "kurs sprzedaży > kurs kupna");
});

// ---------- UC-06 / UC-12 Kursy archiwalne ----------

test("UC-06/UC-12 kursy archiwalne EUR z 14 dni", async () => {
  const res = await call("GET", `/api/rates/EUR/history?from=${daysAgo(14)}&to=${day(new Date())}`, { token: tokenA });
  assert.equal(res.status, 200, res.text);
  assert.ok(Array.isArray(res.json) && res.json.length > 0);
  assert.deepEqual(Object.keys(res.json[0]).sort(), ["ask", "bid", "effectiveDate", "tableNo"]);
});

test("UC-06 walidacja dat i waluty → 400", async () => {
  const today = day(new Date());
  for (const query of [
    `EUR/history?from=${daysAgo(120)}&to=${today}`,        // zakres > 93 dni
    `EUR/history?from=${today}&to=${daysAgo(5)}`,          // from > to
    `EUR/history?from=01-01-2026&to=${today}`,             // zły format
    `EUR/history?from=${daysAgo(5)}&to=2999-01-01`,        // przyszłość
    `EUR/history`,                                         // brak dat
    `XYZ/history?from=${daysAgo(5)}&to=${today}`,          // waluta spoza tabeli C
  ]) {
    expectError(await call("GET", `/api/rates/${query}`, { token: tokenA }), 400);
  }
});

// ---------- UC-10 Portfel nowego konta ----------

test("UC-10 nowe konto ma portfel 0 PLN (Z-08)", async () => {
  const res = await call("GET", "/api/wallet", { token: tokenA });
  assert.equal(res.status, 200, res.text);
  assert.equal(res.json.balances.length, 1);
  assert.equal(res.json.balances[0].currency, "PLN");
  assert.equal(res.json.balances[0].amount, 0);
});

test("UC-07 kupno przy saldzie 0 → 422, bez zmian w bazie (FR-S-09)", async () => {
  expectError(await call("POST", "/api/exchange/buy", { token: tokenA, body: { currency: "USD", amount: 10000 } }), 422);
  const history = await call("GET", "/api/transactions", { token: tokenA });
  assert.deepEqual(history.json, []);
});

// ---------- UC-04 Zasil konto ----------

test("UC-04 walidacja kwoty → 400", async () => {
  for (const amount of [0, -100, 10.5, "abc", "100", true, 100_000_001, null]) {
    expectError(await call("POST", "/api/wallet/top-up", { token: tokenA, body: { amount } }), 400);
  }
});

test("UC-04 zasilenie 1000,00 PLN → 201", async () => {
  const res = await call("POST", "/api/wallet/top-up", { token: tokenA, body: { amount: 100000 } });
  assert.equal(res.status, 201, res.text);
  assert.equal(res.json.transaction.type, "TOP_UP");
  assert.equal(res.json.transaction.toCurrency, "PLN");
  assert.equal(res.json.transaction.toAmount, 100000);
  assert.equal(res.json.transaction.rate, null);
  assert.equal(res.json.wallet.balances.find((b) => b.currency === "PLN").amount, 100000);
});

// ---------- Wycena (FR-M-09) ----------

test("Wycena kupna (ASK, w górę) i sprzedaży (BID, w dół) – bez zmiany salda", async () => {
  const buy = await call("POST", "/api/exchange/quote", { token: tokenA, body: { type: "BUY", currency: "USD", amount: 10000 } });
  assert.equal(buy.status, 200, buy.text);
  assert.equal(buy.json.rateType, "ASK");
  assert.equal(buy.json.plnAmount, Math.ceil(Math.round(10000 * Number(buy.json.rate) * 1e6) / 1e6));
  quotedPln = buy.json.plnAmount;

  const sell = await call("POST", "/api/exchange/quote", { token: tokenA, body: { type: "SELL", currency: "USD", amount: 10000 } });
  assert.equal(sell.status, 200, sell.text);
  assert.equal(sell.json.rateType, "BID");
  assert.equal(sell.json.plnAmount, Math.floor(Math.round(10000 * Number(sell.json.rate) * 1e6) / 1e6));

  expectError(await call("POST", "/api/exchange/quote", { token: tokenA, body: { type: "SWAP", currency: "USD", amount: 100 } }), 400);

  const wallet = await call("GET", "/api/wallet", { token: tokenA });
  assert.equal(wallet.json.balances.find((b) => b.currency === "PLN").amount, 100000, "wycena nic nie zapisuje");
});

// ---------- UC-07 Kup walutę ----------

test("UC-07 kupno 100,00 USD – kurs liczy serwer, pola od klienta są ignorowane", async () => {
  const res = await call("POST", "/api/exchange/buy", {
    token: tokenA,
    // rate, plnAmount i userId od klienta muszą zostać zignorowane (pkt 2B specyfikacji)
    body: { currency: "USD", amount: 10000, rate: "0.0001", plnAmount: 1, userId: 1 },
  });
  assert.equal(res.status, 201, res.text);
  const { transaction, wallet } = res.json;
  assert.equal(transaction.type, "BUY");
  assert.equal(transaction.fromCurrency, "PLN");
  assert.equal(transaction.toCurrency, "USD");
  assert.equal(transaction.toAmount, 10000);
  assert.equal(transaction.rateType, "ASK");
  assert.equal(transaction.fromAmount, quotedPln, "koszt zgodny z wyceną serwera");
  assert.notEqual(transaction.rate, "0.0001");
  assert.match(transaction.nbpTableNo, /\/C\/NBP\//);
  assert.ok(transaction.nbpEffectiveDate);
  assert.ok(!Number.isNaN(Date.parse(transaction.createdAt)));
  assert.equal(wallet.balances.find((b) => b.currency === "PLN").amount, 100000 - quotedPln);
  assert.equal(wallet.balances.find((b) => b.currency === "USD").amount, 10000);
});

test("UC-07 kupno – błędy: brak środków 422, waluta spoza tabeli / PLN / zła kwota 400", async () => {
  expectError(await call("POST", "/api/exchange/buy", { token: tokenA, body: { currency: "USD", amount: 90_000_000 } }), 422);
  expectError(await call("POST", "/api/exchange/buy", { token: tokenA, body: { currency: "XYZ", amount: 100 } }), 400);
  expectError(await call("POST", "/api/exchange/buy", { token: tokenA, body: { currency: "PLN", amount: 100 } }), 400);
  expectError(await call("POST", "/api/exchange/buy", { token: tokenA, body: { currency: "USD", amount: 0 } }), 400);
  expectError(await call("POST", "/api/exchange/buy", { token: tokenA, body: { amount: 100 } }), 400);
});

// ---------- UC-08 Sprzedaj walutę ----------

test("UC-08 sprzedaż 50,00 USD po kursie BID", async () => {
  const res = await call("POST", "/api/exchange/sell", { token: tokenA, body: { currency: "USD", amount: 5000 } });
  assert.equal(res.status, 201, res.text);
  const { transaction, wallet } = res.json;
  assert.equal(transaction.type, "SELL");
  assert.equal(transaction.fromCurrency, "USD");
  assert.equal(transaction.fromAmount, 5000);
  assert.equal(transaction.toCurrency, "PLN");
  assert.equal(transaction.rateType, "BID");
  assert.equal(transaction.toAmount, Math.floor(Math.round(5000 * Number(transaction.rate) * 1e6) / 1e6));
  assert.equal(wallet.balances.find((b) => b.currency === "USD").amount, 5000);
});

test("UC-08 sprzedaż więcej niż w portfelu / waluty, której nie ma → 422", async () => {
  expectError(await call("POST", "/api/exchange/sell", { token: tokenA, body: { currency: "USD", amount: 999999 } }), 422);
  expectError(await call("POST", "/api/exchange/sell", { token: tokenA, body: { currency: "EUR", amount: 100 } }), 422);
});

// ---------- NFR-05 Spójność przy równoczesnych żądaniach ----------

test("NFR-05 10 równoczesnych sprzedaży po 10,00 USD przy saldzie 50,00 USD – przejdzie dokładnie 5", async () => {
  const results = await Promise.all(
    Array.from({ length: 10 }, () => call("POST", "/api/exchange/sell", { token: tokenA, body: { currency: "USD", amount: 1000 } }))
  );
  const statuses = results.map((r) => r.status).sort();
  assert.equal(statuses.filter((s) => s === 201).length, 5, statuses.join(","));
  assert.equal(statuses.filter((s) => s === 422).length, 5, statuses.join(","));
  const wallet = await call("GET", "/api/wallet", { token: tokenA });
  assert.equal(wallet.json.balances.find((b) => b.currency === "USD").amount, 0, "saldo nie spada poniżej zera");
});

// ---------- UC-11 Historia transakcji ----------

test("UC-11 historia od najnowszej, każda transakcja z kursem i czasem (pkt 4 specyfikacji)", async () => {
  const res = await call("GET", "/api/transactions?limit=100", { token: tokenA });
  assert.equal(res.status, 200, res.text);
  const types = res.json.map((t) => t.type);
  assert.deepEqual(types.slice(-3), ["SELL", "BUY", "TOP_UP"]);
  assert.equal(types.length, 3 + 5);
  const times = res.json.map((t) => Date.parse(t.createdAt));
  assert.deepEqual(times, [...times].sort((a, b) => b - a), "od najnowszej");
  for (const t of res.json.filter((t) => t.type !== "TOP_UP")) {
    for (const field of ["type", "fromCurrency", "toCurrency", "fromAmount", "toAmount", "rate", "rateType", "nbpTableNo", "createdAt"]) {
      assert.notEqual(t[field], null, `transakcja ${t.id} ma pole ${field}`);
    }
  }
  const limited = await call("GET", "/api/transactions?limit=2", { token: tokenA });
  assert.equal(limited.json.length, 2);
  expectError(await call("GET", "/api/transactions?limit=0", { token: tokenA }), 400);
  expectError(await call("GET", "/api/transactions?limit=abc", { token: tokenA }), 400);
});

// ---------- FR-S-03 Izolacja danych użytkowników ----------

test("FR-S-03 drugi użytkownik widzi tylko własne dane", async () => {
  assert.equal((await call("POST", "/api/auth/register", { body: { email: emailB, password: PASSWORD } })).status, 201);
  tokenB = (await call("POST", "/api/auth/login", { body: { email: emailB, password: PASSWORD } })).json.accessToken;

  const wallet = await call("GET", "/api/wallet", { token: tokenB });
  assert.deepEqual(wallet.json.balances.map((b) => [b.currency, b.amount]), [["PLN", 0]]);
  const history = await call("GET", "/api/transactions", { token: tokenB });
  assert.deepEqual(history.json, []);

  // użytkownik B nie może sprzedać „cudzych” dolarów, podając userId użytkownika A
  const idA = (await call("GET", "/api/wallet", { token: tokenA })).json.userId;
  expectError(await call("POST", "/api/exchange/sell", { token: tokenB, body: { currency: "USD", amount: 100, userId: idA } }), 422);
});
