// Ledgr frontend — talks to the REST API defined in com.bank.api.ApiServer.
// No framework, no build step: this file is served as-is by StaticFileHandler.

const state = {
  user: null,        // { id, username, fullName }
  accounts: [],       // [{ id, userId, type, balance }]
  selectedAccountId: null,
};

const el = (id) => document.getElementById(id);

const money = (n) =>
  '₹' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.error || 'Something went wrong');
  }
  return body;
}

// ===================================================================
// Auth view
// ===================================================================

function initAuthTabs() {
  const tabs = document.querySelectorAll('.auth-tab');
  tabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      tabs.forEach((t) => t.classList.remove('is-active'));
      tab.classList.add('is-active');
      const isLogin = tab.dataset.tab === 'login';
      el('login-form').hidden = !isLogin;
      el('register-form').hidden = isLogin;
    });
  });
}

el('login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = new FormData(e.target);
  el('login-error').textContent = '';
  try {
    const user = await api('/api/login', {
      method: 'POST',
      body: JSON.stringify({
        username: form.get('username'),
        password: form.get('password'),
      }),
    });
    enterDashboard(user);
  } catch (err) {
    el('login-error').textContent = err.message;
  }
});

el('register-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = new FormData(e.target);
  el('register-error').textContent = '';
  try {
    await api('/api/register', {
      method: 'POST',
      body: JSON.stringify({
        username: form.get('username'),
        password: form.get('password'),
        fullName: form.get('fullName'),
      }),
    });
    // Registration succeeded — log the user straight in rather than making
    // them retype credentials on a second screen.
    const user = await api('/api/login', {
      method: 'POST',
      body: JSON.stringify({
        username: form.get('username'),
        password: form.get('password'),
      }),
    });
    enterDashboard(user);
  } catch (err) {
    el('register-error').textContent = err.message;
  }
});

// ===================================================================
// Dashboard
// ===================================================================

function enterDashboard(user) {
  state.user = user;
  localStorage.setItem('ledgr_user', JSON.stringify(user));

  el('auth-view').hidden = true;
  el('dashboard-view').hidden = false;
  el('user-name').textContent = user.fullName;

  refreshAccounts();
}

function exitDashboard() {
  state.user = null;
  state.accounts = [];
  state.selectedAccountId = null;
  localStorage.removeItem('ledgr_user');

  el('dashboard-view').hidden = true;
  el('auth-view').hidden = false;
  el('login-form').reset();
}

el('logout-btn').addEventListener('click', exitDashboard);

async function refreshAccounts() {
  const accounts = await api(`/api/accounts?userId=${state.user.id}`);
  state.accounts = accounts;

  renderTotalBalance();
  renderAccountRows();
  renderAccountSelects();

  if (accounts.length && !state.selectedAccountId) {
    state.selectedAccountId = accounts[0].id;
  }
  if (state.selectedAccountId) {
    refreshLedger(state.selectedAccountId);
  }
}

function renderTotalBalance() {
  const total = state.accounts.reduce((sum, a) => sum + Number(a.balance), 0);
  el('total-balance').textContent = money(total);
  el('account-count').textContent =
    state.accounts.length + (state.accounts.length === 1 ? ' account' : ' accounts');
}

function renderAccountRows() {
  const container = el('account-rows');
  if (!state.accounts.length) {
    container.innerHTML = '<p class="card-sub">No accounts yet — open one to get started.</p>';
    return;
  }
  container.innerHTML = state.accounts
    .map(
      (a) => `
      <div class="account-row" data-account-id="${a.id}">
        <div>
          <div class="account-row-type">${capitalize(a.type)}</div>
          <div class="account-row-id">Account #${a.id}</div>
        </div>
        <div class="account-row-balance">${money(a.balance)}</div>
      </div>`
    )
    .join('');

  container.querySelectorAll('.account-row').forEach((row) => {
    row.addEventListener('click', () => {
      state.selectedAccountId = Number(row.dataset.accountId);
      refreshLedger(state.selectedAccountId);
    });
  });
}

function renderAccountSelects() {
  const options = state.accounts
    .map((a) => `<option value="${a.id}">${capitalize(a.type)} #${a.id} — ${money(a.balance)}</option>`)
    .join('');

  el('mutate-account').innerHTML = options;
  el('transfer-from').innerHTML = options;
  el('transfer-to').innerHTML = options;
}

async function refreshLedger(accountId) {
  const body = el('ledger-body');
  const history = await api(`/api/history?accountId=${accountId}&limit=20`);

  if (!history.length) {
    body.innerHTML = '<tr><td colspan="5" class="ledger-empty">No activity on this account yet.</td></tr>';
    return;
  }

  body.innerHTML = history
    .map((tx) => {
      const isCredit = tx.operationType === 'DEPOSIT' || tx.operationType === 'TRANSFER_IN';
      const sign = isCredit ? '+' : '−';
      const amountClass = isCredit ? 'amount-positive' : 'amount-negative';
      return `
        <tr>
          <td>${formatDate(tx.createdAt)}</td>
          <td>#${tx.accountId}</td>
          <td>${formatOperation(tx.operationType)}</td>
          <td class="${amountClass}">${sign}${money(tx.amount)}</td>
          <td>${money(tx.balanceAfter)}</td>
        </tr>`;
    })
    .join('');
}

// ===================================================================
// Forms: open account, deposit/withdraw, transfer
// ===================================================================

el('open-account-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = new FormData(e.target);
  const errorEl = el('open-account-error');
  errorEl.textContent = '';
  try {
    await api('/api/accounts', {
      method: 'POST',
      body: JSON.stringify({
        userId: state.user.id,
        type: form.get('type'),
        openingBalance: form.get('openingBalance'),
      }),
    });
    e.target.reset();
    await refreshAccounts();
  } catch (err) {
    errorEl.textContent = err.message;
  }
});

el('mutate-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const submitter = e.submitter; // which button (deposit/withdraw) was pressed
  const action = submitter ? submitter.dataset.action : 'deposit';
  const form = new FormData(e.target);
  const errorEl = el('mutate-error');
  errorEl.textContent = '';
  try {
    await api(`/api/${action}`, {
      method: 'POST',
      body: JSON.stringify({
        accountId: Number(form.get('accountId')),
        amount: form.get('amount'),
      }),
    });
    e.target.reset();
    await refreshAccounts();
  } catch (err) {
    errorEl.textContent = err.message;
  }
});

el('transfer-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = new FormData(e.target);
  const errorEl = el('transfer-error');
  errorEl.textContent = '';

  const from = Number(form.get('fromAccountId'));
  const to = Number(form.get('toAccountId'));
  if (from === to) {
    errorEl.textContent = 'Choose two different accounts.';
    return;
  }

  try {
    await api('/api/transfer', {
      method: 'POST',
      body: JSON.stringify({ fromAccountId: from, toAccountId: to, amount: form.get('amount') }),
    });
    e.target.reset();
    await refreshAccounts();
  } catch (err) {
    errorEl.textContent = err.message;
  }
});

// ===================================================================
// Helpers
// ===================================================================

function capitalize(s) {
  return s.charAt(0) + s.slice(1).toLowerCase();
}

function formatOperation(op) {
  return { DEPOSIT: 'Deposit', WITHDRAW: 'Withdraw', TRANSFER_IN: 'Transfer in', TRANSFER_OUT: 'Transfer out' }[op] || op;
}

function formatDate(isoString) {
  const d = new Date(isoString);
  return isNaN(d) ? isoString : d.toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
}

// ===================================================================
// Boot
// ===================================================================

initAuthTabs();

// Resume a session if the browser still has one — this is a per-viewer
// convenience only, not real auth; there is no server-side session token.
const savedUser = localStorage.getItem('ledgr_user');
if (savedUser) {
  try {
    enterDashboard(JSON.parse(savedUser));
  } catch {
    localStorage.removeItem('ledgr_user');
  }
}
