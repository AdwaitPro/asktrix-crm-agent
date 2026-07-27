'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { P, ROLES, permissionsFor, statusesFor } = require('../src/roles');

/**
 * The six roles of §2.
 *
 * These tests exist because the separation is a business rule, not a UI preference: an accounts
 * clerk must not be able to move a government filing, and hiding the button is not a control. If a
 * future edit widens a role, one of these fails.
 */

test('all six roles from the requirements exist', () => {
  assert.deepEqual(
    [...ROLES].sort(),
    ['ACCOUNTS', 'CUSTOMER_SUPPORT', 'DOCUMENTATION', 'RELATIONSHIP_MANAGER', 'SALES', 'TEAM_LEADER'],
  );
});

test('every role can read its clients, because none of them can work otherwise', () => {
  for (const role of ROLES) {
    assert.ok(permissionsFor(role).includes(P.CLIENTS_READ), `${role} cannot read clients`);
  }
});

test('accounts may record a payment and nothing else', () => {
  assert.deepEqual(statusesFor('ACCOUNTS'), ['PAYMENT_RECEIVED']);
});

test('accounts cannot move a government filing', () => {
  assert.ok(!statusesFor('ACCOUNTS').includes('WAITING_GOVERNMENT_APPROVAL'));
});

test('documentation cannot record a payment', () => {
  assert.ok(!statusesFor('DOCUMENTATION').includes('PAYMENT_RECEIVED'));
});

test('only the roles that speak to customers may place calls', () => {
  const callers = ROLES.filter((r) => permissionsFor(r).includes(P.CALLS_PLACE));
  assert.deepEqual(
    callers.sort(),
    ['CUSTOMER_SUPPORT', 'RELATIONSHIP_MANAGER', 'SALES', 'TEAM_LEADER'],
  );
});

test('a team leader is the only role with oversight of others', () => {
  const overseers = ROLES.filter((r) => permissionsFor(r).includes(P.TEAM_VIEW));
  assert.deepEqual(overseers, ['TEAM_LEADER']);
});

test('an unknown role gets nothing rather than everything', () => {
  assert.deepEqual(permissionsFor('SUPERUSER'), []);
  assert.deepEqual(statusesFor('SUPERUSER'), []);
});

test('permission and status sets are copies, so a caller cannot widen a role at runtime', () => {
  permissionsFor('ACCOUNTS').push(P.CALLS_PLACE);
  statusesFor('ACCOUNTS').push('WAITING_GOVERNMENT_APPROVAL');
  assert.ok(!permissionsFor('ACCOUNTS').includes(P.CALLS_PLACE));
  assert.deepEqual(statusesFor('ACCOUNTS'), ['PAYMENT_RECEIVED']);
});
