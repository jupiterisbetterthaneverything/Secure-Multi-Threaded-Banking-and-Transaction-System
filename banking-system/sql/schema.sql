-- Banking system schema. Safe to re-run.

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(60)  NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS accounts (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT         NOT NULL,
    account_type ENUM('CHECKING','SAVINGS') NOT NULL,
    balance      DECIMAL(19,2)  NOT NULL DEFAULT 0.00,
    created_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0),
    INDEX idx_accounts_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS transactions (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id         BIGINT        NOT NULL,
    related_account_id BIGINT        NULL,
    operation_type     ENUM('DEPOSIT','WITHDRAW','TRANSFER_IN','TRANSFER_OUT') NOT NULL,
    amount             DECIMAL(19,2) NOT NULL,
    balance_after      DECIMAL(19,2) NOT NULL,
    created_at         TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_tx_account FOREIGN KEY (account_id) REFERENCES accounts(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    INDEX idx_tx_account_time (account_id, created_at)
) ENGINE=InnoDB;
