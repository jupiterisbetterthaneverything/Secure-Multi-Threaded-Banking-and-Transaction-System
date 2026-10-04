# Multi-Threaded Banking System

A Java banking backend built to be correct under concurrent load. Balances are
protected by database row locks inside explicit transactions, with in-JVM
per-account locks layered on top to cut contention.

## Running it

Open in GitHub Codespaces. The devcontainer starts MySQL 8 as a service and
loads the schema automatically.

```bash
mvn test                                                    # run the concurrency suite
mvn compile exec:java -Dexec.mainClass=com.bank.App         # console demo walkthrough
mvn compile exec:java -Dexec.mainClass=com.bank.api.ApiServer  # web UI + REST API on :8080
```

For the web UI, once `ApiServer` is running, Codespaces will prompt to open
port 8080 in the browser — accept it, or open the **Ports** tab and click the
globe icon next to 8080.

If MySQL is not reachable, check the service and reload the schema by hand:

```bash
mysql -h db -u bankuser -pbankpass bankdb < sql/schema.sql
```

## Web UI

A REST API (`com.bank.api.ApiServer`, built on the JDK's own `HttpServer` —
no framework) sits in front of the same `AccountService` / `TransferService` /
`AuthService` classes the console demo and the tests use. The frontend
(`src/main/resources/public/`) is a static HTML/CSS/JS page served by the same
process, so there's no separate build step and no CORS configuration.

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/register` | POST | Create a user |
| `/api/login` | POST | Authenticate, returns user id |
| `/api/accounts` | GET / POST | List or open accounts |
| `/api/deposit`, `/api/withdraw` | POST | Balance mutation |
| `/api/transfer` | POST | Move money between two accounts |
| `/api/history` | GET | Recent ledger entries |

## Concurrency design

Deposits and withdrawals do a read-modify-write on a balance. Done naively,
two threads read the same starting value and one write silently overwrites the
other — money disappears with no error anywhere.

The fix has two layers:

| Layer | Mechanism | What it guarantees |
|---|---|---|
| Database | `SELECT ... FOR UPDATE` inside a transaction | Correctness, including across multiple app instances |
| JVM | `ReentrantLock` per account id | Reduced contention within one process |

The JVM layer alone would be a bug: it protects nothing once a second copy of
the application runs against the same database. That is why the row lock is
the primary mechanism and the Java lock is an optimisation.

**Deadlock prevention.** Transfers touch two accounts. Both the JVM locks and
the row locks are acquired in ascending account-id order, so a transfer of
`1 -> 2` running against `2 -> 1` cannot produce a hold-and-wait cycle.

## Test results

Run against MySQL 8.0 in the devcontainer.

| Test | Load | Result |
|---|---|---|
| Concurrent deposits | 20 threads x 50 ops on one account | Balance exact, 1000 ledger rows |
| Mixed deposit/withdraw | 20 threads, interleaved | Net drift zero |
| Opposing transfers | 20 threads, both directions | No deadlock, total conserved |
| Overdraft under contention | 400 attempts against 100 units | Exactly 100 succeed, balance zero |

To see the race the locking prevents, remove `FOR UPDATE` from
`AccountDAO#findByIdForUpdate` and re-run the first test. It fails with a
short balance.

## Other design notes

- **Money is `DECIMAL(19,2)` and `BigDecimal`**, never `double`. Binary
  floating point cannot represent 0.10 exactly, which is disqualifying for
  currency.
- **Every query is a prepared statement.** No string concatenation reaches SQL.
- **Passwords are bcrypt with cost 12.** Login returns the same message for a
  missing user and a wrong password, so responses cannot enumerate usernames.
- **Ledger rows are written in the same transaction as the balance update**, so
  a transaction record can never exist without its balance change.
- **DAOs receive a `Connection`** rather than opening their own, which is what
  lets the service layer span several DAO calls in one transaction.

## Known limitations

Worth saying plainly rather than being found out later:

- **No session tokens.** `/api/login` returns the user id; the frontend keeps
  it in `localStorage` and sends it back on later calls. There's no
  server-side session, expiry, or invalidation — treat this build as a demo
  of the banking logic, not a production auth system.
- No idempotency keys, so a client that retries a failed transfer could apply
  it twice. Real payment systems require this.
- Single-entry ledger. A true double-entry design would write balanced debit
  and credit rows and let you verify that all accounts sum to zero.
- Lock striping never evicts entries, so the lock map grows with the number of
  distinct accounts touched. Fine at this scale, not at millions of accounts.

## Next

1. Real session tokens with expiry, replacing the client-held user id.
2. Idempotency keys on transfers.
3. Double-entry ledger with a balance-reconciliation check.
