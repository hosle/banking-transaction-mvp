(() => {
  "use strict";

  const API = "/api/v1";
  const PAGE_SIZE = 10;
  const state = {
    accounts: [],
    transactions: new Map(),
    editingId: null,
    page: 0,
    totalPages: 0,
    requestSequence: 0
  };

  const elements = {
    transactionForm: document.querySelector("#transaction-form"),
    formTitle: document.querySelector("#form-title"),
    formMessage: document.querySelector("#form-message"),
    cancelEdit: document.querySelector("#cancel-edit"),
    saveTransaction: document.querySelector("#save-transaction"),
    clientReference: document.querySelector("#client-reference"),
    senderAccount: document.querySelector("#sender-account"),
    recipientAccount: document.querySelector("#recipient-account"),
    amount: document.querySelector("#amount"),
    currency: document.querySelector("#currency"),
    category: document.querySelector("#category"),
    description: document.querySelector("#description"),
    descriptionCount: document.querySelector("#description-count"),
    occurredAt: document.querySelector("#occurred-at"),
    filterForm: document.querySelector("#filter-form"),
    filterAccount: document.querySelector("#filter-account"),
    filterFrom: document.querySelector("#filter-from"),
    filterTo: document.querySelector("#filter-to"),
    filterCurrency: document.querySelector("#filter-currency"),
    filterCategory: document.querySelector("#filter-category"),
    clearFilters: document.querySelector("#clear-filters"),
    refreshTransactions: document.querySelector("#refresh-transactions"),
    listMessage: document.querySelector("#list-message"),
    loadingState: document.querySelector("#loading-state"),
    rows: document.querySelector("#transaction-rows"),
    emptyState: document.querySelector("#empty-state"),
    previousPage: document.querySelector("#previous-page"),
    nextPage: document.querySelector("#next-page"),
    pageSummary: document.querySelector("#page-summary")
  };

  class ApiError extends Error {
    constructor(problem, status) {
      super(problem.detail || problem.title || `Request failed with status ${status}.`);
      this.name = "ApiError";
      this.status = status;
      this.problem = problem;
    }
  }

  async function request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.body) {
      headers.set("Content-Type", "application/json");
    }
    headers.set("Accept", "application/json, application/problem+json");

    const response = await fetch(path, { ...options, headers });
    if (response.status === 204) {
      return null;
    }

    const contentType = response.headers.get("content-type") || "";
    let body = null;
    if (contentType.includes("json")) {
      body = await response.json();
    } else {
      const text = await response.text();
      body = text ? { detail: text } : {};
    }

    if (!response.ok) {
      throw new ApiError(body || {}, response.status);
    }
    return body;
  }

  function showMessage(element, message, kind = "error") {
    element.textContent = message;
    element.className = `message message-${kind}`;
    element.hidden = false;
  }

  function hideMessage(element) {
    element.textContent = "";
    element.hidden = true;
  }

  function errorMessage(error) {
    if (error instanceof ApiError) {
      const fieldMessages = Array.isArray(error.problem.fieldErrors)
        ? error.problem.fieldErrors.map((item) => `${item.field}: ${item.message}`)
        : [];
      return [error.message, ...fieldMessages].join(" ");
    }
    if (error instanceof TypeError) {
      return "The service could not be reached. Check that it is running, then try again.";
    }
    return error.message || "An unexpected error occurred. Please try again.";
  }

  function clearFieldErrors() {
    elements.transactionForm.querySelectorAll("input, select, textarea").forEach((field) => {
      field.setCustomValidity("");
      field.removeAttribute("aria-invalid");
    });
    document.querySelectorAll("[data-error-for]").forEach((element) => {
      element.textContent = "";
    });
  }

  function applyFieldErrors(error) {
    if (!(error instanceof ApiError) || !Array.isArray(error.problem.fieldErrors)) {
      return;
    }
    let firstInvalid = null;
    error.problem.fieldErrors.forEach(({ field, message }) => {
      const input = elements.transactionForm.elements.namedItem(field);
      const output = document.querySelector(`[data-error-for="${CSS.escape(field)}"]`);
      if (input instanceof HTMLElement) {
        input.setCustomValidity(message);
        input.setAttribute("aria-invalid", "true");
        firstInvalid = firstInvalid || input;
      }
      if (output) {
        output.textContent = message;
      }
    });
    if (firstInvalid) {
      firstInvalid.focus();
    }
  }

  function addOption(select, value, label) {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = label;
    select.append(option);
  }

  function replaceAccountOptions(select, initialLabel) {
    select.replaceChildren();
    addOption(select, "", initialLabel);
    state.accounts.forEach((account) => {
      addOption(select, account.accountId, `${account.accountId} · ${account.accountType}`);
    });
  }

  async function loadAccounts() {
    try {
      const accounts = await request(`${API}/accounts`);
      state.accounts = Array.isArray(accounts) ? accounts : [];
      replaceAccountOptions(elements.senderAccount, "Select sender");
      replaceAccountOptions(elements.recipientAccount, "Select recipient");
      replaceAccountOptions(elements.filterAccount, "All accounts");
    } catch (error) {
      elements.senderAccount.replaceChildren();
      elements.recipientAccount.replaceChildren();
      addOption(elements.senderAccount, "", "Accounts unavailable");
      addOption(elements.recipientAccount, "", "Accounts unavailable");
      showMessage(elements.formMessage, errorMessage(error));
    }
  }

  function timestampFromInput(input) {
    if (!input.value) {
      return null;
    }
    const timestamp = new Date(input.value).getTime();
    return Number.isFinite(timestamp) ? timestamp : null;
  }

  function localDateTimeValue(timestamp) {
    const date = new Date(Number(timestamp));
    if (!Number.isFinite(date.getTime())) {
      return "";
    }
    const pad = (value) => String(value).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
      + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  }

  function displayDate(timestamp) {
    const date = new Date(Number(timestamp));
    if (!Number.isFinite(date.getTime())) {
      return "—";
    }
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: "medium",
      timeStyle: "short"
    }).format(date);
  }

  function displayAmount(transaction) {
    const amount = Number(transaction.amount);
    if (!Number.isFinite(amount)) {
      return `${transaction.amount} ${transaction.currency}`;
    }
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency: transaction.currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 4
    }).format(amount);
  }

  function buildFilters() {
    const params = new URLSearchParams({
      page: String(state.page),
      size: String(PAGE_SIZE)
    });
    const values = {
      accountId: elements.filterAccount.value,
      from: timestampFromInput(elements.filterFrom),
      to: timestampFromInput(elements.filterTo),
      currency: elements.filterCurrency.value,
      category: elements.filterCategory.value
    };
    Object.entries(values).forEach(([key, value]) => {
      if (value !== "" && value !== null) {
        params.set(key, String(value));
      }
    });
    return params;
  }

  function makeCell(text, className) {
    const cell = document.createElement("td");
    cell.textContent = text;
    if (className) {
      cell.className = className;
    }
    return cell;
  }

  function makeActionButton(label, className, action, transactionId) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `button button-small ${className}`;
    button.textContent = label;
    button.dataset.action = action;
    button.dataset.transactionId = String(transactionId);
    return button;
  }

  function renderTransactions(content) {
    state.transactions.clear();
    elements.rows.replaceChildren();
    content.forEach((transaction) => {
      state.transactions.set(String(transaction.transactionId), transaction);
      const row = document.createElement("tr");
      row.append(makeCell(transaction.clientReference, "reference"));
      row.append(makeCell(`${transaction.senderAccountId} → ${transaction.recipientAccountId}`));
      row.append(makeCell(displayAmount(transaction), "amount"));
      row.append(makeCell(transaction.category || "—"));
      row.append(makeCell(displayDate(transaction.occurredAt)));
      row.append(makeCell(transaction.description || "—", "description-cell"));

      const actions = document.createElement("td");
      actions.className = "row-actions";
      actions.append(
        makeActionButton("Edit", "button-secondary", "edit", transaction.transactionId),
        makeActionButton("Delete", "button-danger", "delete", transaction.transactionId)
      );
      row.append(actions);
      elements.rows.append(row);
    });
    elements.emptyState.hidden = content.length !== 0;
  }

  function renderPagination(page) {
    state.page = Number.isInteger(page.page) ? page.page : state.page;
    state.totalPages = Number.isInteger(page.totalPages) ? page.totalPages : 0;
    const visiblePage = state.totalPages === 0 ? 0 : state.page + 1;
    elements.pageSummary.textContent = `Page ${visiblePage} of ${state.totalPages} · ${page.totalElements || 0} records`;
    elements.previousPage.disabled = state.page <= 0;
    elements.nextPage.disabled = state.totalPages === 0 || state.page >= state.totalPages - 1;
  }

  async function loadTransactions() {
    const sequence = ++state.requestSequence;
    hideMessage(elements.listMessage);
    elements.loadingState.hidden = false;
    try {
      const page = await request(`${API}/transactions?${buildFilters().toString()}`);
      if (sequence !== state.requestSequence) {
        return;
      }
      const content = Array.isArray(page.content) ? page.content : [];
      if (content.length === 0 && state.page > 0 && state.page >= page.totalPages) {
        state.page -= 1;
        await loadTransactions();
        return;
      }
      renderTransactions(content);
      renderPagination(page);
    } catch (error) {
      if (sequence === state.requestSequence) {
        showMessage(elements.listMessage, errorMessage(error));
        renderTransactions([]);
        renderPagination({ page: state.page, totalPages: 0, totalElements: 0 });
      }
    } finally {
      if (sequence === state.requestSequence) {
        elements.loadingState.hidden = true;
      }
    }
  }

  function transactionPayload() {
    return {
      amount: Number(elements.amount.value),
      currency: elements.currency.value,
      category: elements.category.value || null,
      description: elements.description.value.trim() || null,
      occurredAt: timestampFromInput(elements.occurredAt)
    };
  }

  function resetForm({ keepMessage = false } = {}) {
    state.editingId = null;
    elements.transactionForm.reset();
    elements.formTitle.textContent = "Add transaction";
    elements.saveTransaction.textContent = "Create transaction";
    elements.cancelEdit.hidden = true;
    elements.clientReference.disabled = false;
    elements.senderAccount.disabled = false;
    elements.recipientAccount.disabled = false;
    elements.currency.value = "SGD";
    elements.occurredAt.value = localDateTimeValue(Date.now());
    elements.descriptionCount.textContent = "0 / 140";
    clearFieldErrors();
    if (!keepMessage) {
      hideMessage(elements.formMessage);
    }
  }

  function editTransaction(transaction) {
    state.editingId = transaction.transactionId;
    clearFieldErrors();
    hideMessage(elements.formMessage);
    elements.formTitle.textContent = `Edit transaction #${transaction.transactionId}`;
    elements.saveTransaction.textContent = "Save changes";
    elements.cancelEdit.hidden = false;
    elements.clientReference.value = transaction.clientReference;
    elements.senderAccount.value = transaction.senderAccountId;
    elements.recipientAccount.value = transaction.recipientAccountId;
    elements.clientReference.disabled = true;
    elements.senderAccount.disabled = true;
    elements.recipientAccount.disabled = true;
    elements.amount.value = transaction.amount;
    elements.currency.value = transaction.currency;
    elements.category.value = transaction.category || "";
    elements.description.value = transaction.description || "";
    elements.descriptionCount.textContent = `${elements.description.value.length} / 140`;
    elements.occurredAt.value = localDateTimeValue(transaction.occurredAt);
    elements.formTitle.scrollIntoView({ behavior: "smooth", block: "start" });
    elements.amount.focus({ preventScroll: true });
  }

  async function submitTransaction(event) {
    event.preventDefault();
    clearFieldErrors();
    hideMessage(elements.formMessage);
    if (!elements.transactionForm.checkValidity()) {
      elements.transactionForm.reportValidity();
      return;
    }

    const editing = state.editingId !== null;
    const payload = transactionPayload();
    if (!editing) {
      payload.clientReference = elements.clientReference.value.trim();
      payload.senderAccountId = elements.senderAccount.value;
      payload.recipientAccountId = elements.recipientAccount.value;
    }

    elements.saveTransaction.disabled = true;
    elements.saveTransaction.textContent = editing ? "Saving…" : "Creating…";
    try {
      await request(
        editing ? `${API}/transactions/${state.editingId}` : `${API}/transactions`,
        { method: editing ? "PUT" : "POST", body: JSON.stringify(payload) }
      );
      resetForm({ keepMessage: true });
      showMessage(elements.formMessage, editing ? "Transaction updated." : "Transaction created.", "success");
      state.page = 0;
      await loadTransactions();
    } catch (error) {
      showMessage(elements.formMessage, errorMessage(error));
      applyFieldErrors(error);
    } finally {
      elements.saveTransaction.disabled = false;
      elements.saveTransaction.textContent = state.editingId === null ? "Create transaction" : "Save changes";
    }
  }

  async function deleteTransaction(transaction) {
    const confirmed = window.confirm(
      `Delete transaction ${transaction.clientReference}? The record will be hidden but retained for audit.`
    );
    if (!confirmed) {
      return;
    }
    hideMessage(elements.listMessage);
    try {
      await request(`${API}/transactions/${transaction.transactionId}`, { method: "DELETE" });
      if (String(state.editingId) === String(transaction.transactionId)) {
        resetForm();
      }
      await loadTransactions();
      showMessage(elements.listMessage, "Transaction deleted and retained for audit.", "success");
    } catch (error) {
      showMessage(elements.listMessage, errorMessage(error));
    }
  }

  function validateFilterRange() {
    const from = timestampFromInput(elements.filterFrom);
    const to = timestampFromInput(elements.filterTo);
    if (from !== null && to !== null && from >= to) {
      showMessage(elements.listMessage, "The From time must be earlier than the To time.");
      elements.filterTo.focus();
      return false;
    }
    return true;
  }

  elements.transactionForm.addEventListener("submit", submitTransaction);
  elements.cancelEdit.addEventListener("click", () => resetForm());
  elements.description.addEventListener("input", () => {
    elements.descriptionCount.textContent = `${elements.description.value.length} / 140`;
  });
  elements.transactionForm.addEventListener("input", (event) => {
    if (event.target instanceof HTMLInputElement
        || event.target instanceof HTMLSelectElement
        || event.target instanceof HTMLTextAreaElement) {
      event.target.setCustomValidity("");
      event.target.removeAttribute("aria-invalid");
      const error = document.querySelector(`[data-error-for="${CSS.escape(event.target.name)}"]`);
      if (error) {
        error.textContent = "";
      }
    }
  });
  elements.filterForm.addEventListener("submit", (event) => {
    event.preventDefault();
    hideMessage(elements.listMessage);
    if (validateFilterRange()) {
      state.page = 0;
      loadTransactions();
    }
  });
  elements.clearFilters.addEventListener("click", () => {
    elements.filterForm.reset();
    hideMessage(elements.listMessage);
    state.page = 0;
    loadTransactions();
  });
  elements.refreshTransactions.addEventListener("click", loadTransactions);
  elements.previousPage.addEventListener("click", () => {
    if (state.page > 0) {
      state.page -= 1;
      loadTransactions();
    }
  });
  elements.nextPage.addEventListener("click", () => {
    if (state.page + 1 < state.totalPages) {
      state.page += 1;
      loadTransactions();
    }
  });
  elements.rows.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) {
      return;
    }
    const transaction = state.transactions.get(button.dataset.transactionId);
    if (!transaction) {
      return;
    }
    if (button.dataset.action === "edit") {
      editTransaction(transaction);
    } else if (button.dataset.action === "delete") {
      deleteTransaction(transaction);
    }
  });

  resetForm();
  Promise.all([loadAccounts(), loadTransactions()]);
})();
