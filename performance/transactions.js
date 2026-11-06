import http from 'k6/http';
import { check, fail, sleep } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const seedCount = Number(__ENV.SEED_COUNT || 1000);
const p95TargetMs = Number(__ENV.P95_TARGET_MS || 500);
const batchSize = 50;

export const options = {
  stages: [
    { duration: '10s', target: 20 },
    { duration: '30s', target: 20 },
    { duration: '5s', target: 0 },
  ],
  thresholds: {
    'http_req_failed{stage:load}': ['rate<0.01'],
    'http_req_duration{stage:load}': [`p(95)<${p95TargetMs}`],
    'checks{stage:load}': ['rate>0.99'],
  },
};

function requestParams(stage) {
  return {
    headers: { 'Content-Type': 'application/json' },
    tags: { stage },
  };
}

function transactionBody(clientReference, amount, occurredAt) {
  return JSON.stringify({
    clientReference,
    senderAccountId: 'ACC-001',
    recipientAccountId: 'ACC-002',
    amount,
    currency: 'SGD',
    category: 'TRANSFER',
    description: 'k6 performance test',
    occurredAt,
  });
}

export function setup() {
  const accounts = http.get(`${baseUrl}/api/v1/accounts`, requestParams('setup'));
  if (accounts.status !== 200) {
    fail(`Account endpoint is unavailable: HTTP ${accounts.status}`);
  }

  const runId = Date.now();
  const transactionIds = [];

  for (let offset = 0; offset < seedCount; offset += batchSize) {
    const requests = [];
    const upperBound = Math.min(offset + batchSize, seedCount);

    for (let index = offset; index < upperBound; index += 1) {
      requests.push({
        method: 'POST',
        url: `${baseUrl}/api/v1/transactions`,
        body: transactionBody(`K6-SEED-${runId}-${index}`, 10 + (index % 100), runId - index - 1000),
        params: requestParams('setup'),
      });
    }

    const responses = http.batch(requests);
    responses.forEach((response) => {
      if (response.status !== 201) {
        fail(`Seeding failed: HTTP ${response.status}, body=${response.body}`);
      }
      transactionIds.push(response.json('transactionId'));
    });
  }

  return { runId, transactionIds };
}

export default function (data) {
  const operation = Math.random();
  const id = data.transactionIds[Math.floor(Math.random() * data.transactionIds.length)];
  const params = requestParams('load');

  if (operation < 0.20) {
    const response = http.get(`${baseUrl}/api/v1/transactions?page=0&size=20`, params);
    check(response, { 'list returns 200': (result) => result.status === 200 }, { stage: 'load' });
  } else if (operation < 0.40) {
    const response = http.get(
      `${baseUrl}/api/v1/transactions?accountId=ACC-001&currency=SGD&page=0&size=20`,
      params,
    );
    check(response, { 'filter returns 200': (result) => result.status === 200 }, { stage: 'load' });
  } else if (operation < 0.75) {
    const response = http.get(`${baseUrl}/api/v1/transactions/${id}`, params);
    check(response, { 'get returns 200': (result) => result.status === 200 }, { stage: 'load' });
  } else if (operation < 0.85) {
    const reference = `K6-CREATE-${data.runId}-${__VU}-${__ITER}`;
    const response = http.post(
      `${baseUrl}/api/v1/transactions`,
      transactionBody(reference, 25.50, Date.now() - 1000),
      params,
    );
    check(response, { 'create returns 201': (result) => result.status === 201 }, { stage: 'load' });
  } else if (operation < 0.95) {
    const response = http.put(
      `${baseUrl}/api/v1/transactions/${id}`,
      JSON.stringify({
        amount: 75.25,
        currency: 'USD',
        category: 'TRANSFER',
        description: 'Updated by k6',
        occurredAt: Date.now() - 1000,
      }),
      params,
    );
    check(response, { 'update returns 200': (result) => result.status === 200 }, { stage: 'load' });
  } else {
    const reference = `K6-DELETE-${data.runId}-${__VU}-${__ITER}`;
    const createResponse = http.post(
      `${baseUrl}/api/v1/transactions`,
      transactionBody(reference, 5.00, Date.now() - 1000),
      params,
    );
    const created = check(
      createResponse,
      { 'delete setup returns 201': (result) => result.status === 201 },
      { stage: 'load' },
    );

    if (created) {
      const deleteResponse = http.del(
        `${baseUrl}/api/v1/transactions/${createResponse.json('transactionId')}`,
        null,
        params,
      );
      check(
        deleteResponse,
        { 'delete returns 204': (result) => result.status === 204 },
        { stage: 'load' },
      );
    }
  }

  sleep(0.1);
}
