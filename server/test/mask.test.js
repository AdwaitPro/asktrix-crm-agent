'use strict';
const assert = require('node:assert');
const { test } = require('node:test');
const { maskPhone, maskEmail, findLeaks } = require('../src/mask');

/**
 * §4 is enforced here, in the only place that ever reads a full phone number or email.
 *
 * These assertions are deliberately about what must NOT survive masking. A test that only checks the
 * happy-path format would still pass if someone widened the mask to keep six digits.
 */

test('maskPhone matches the format the requirements specify', () => {
  assert.strictEqual(maskPhone('9876543212'), '98XXXXXX12');
});

test('maskPhone hides the subscriber digits', () => {
  const masked = maskPhone('9876543212');
  assert.ok(!masked.includes('76543'), 'middle digits leaked');
  assert.strictEqual((masked.match(/\d/g) || []).length, 4, 'more than four digits survived');
});

test('maskPhone strips formatting before masking, so spacing cannot leak digits', () => {
  assert.strictEqual(maskPhone('+91 98765 43212'), maskPhone('919876543212'));
});

test('maskPhone never echoes a number too short to mask safely', () => {
  assert.strictEqual(maskPhone('12345'), 'XXXXXXXXXX');
  assert.strictEqual(maskPhone(''), '');
});

test('maskEmail matches the format the requirements specify', () => {
  assert.strictEqual(maskEmail('sivakumar@gmail.com'), 'siv****@gmail.com');
});

test('maskEmail never leaks the full local part', () => {
  const masked = maskEmail('sivakumar@gmail.com');
  assert.ok(!masked.includes('sivakumar'), 'local part leaked');
  assert.ok(!masked.includes('kumar'), 'partial local part leaked');
});

test('maskEmail keeps at most three characters even for a very short local part', () => {
  assert.strictEqual(maskEmail('ab@x.com'), 'a****@x.com');
  assert.strictEqual(maskEmail('a@x.com'), 'a****@x.com');
});

test('maskEmail handles a malformed address without echoing it', () => {
  assert.strictEqual(maskEmail('not-an-email'), '****');
});

test('findLeaks catches an unmasked phone number anywhere in a response', () => {
  const leaks = findLeaks({ client: { name: 'X', notes: 'call me on 9876543212' } });
  assert.strictEqual(leaks.length, 1);
  assert.ok(leaks[0].includes('phone'));
});

test('findLeaks catches an unmasked email nested in an array', () => {
  const leaks = findLeaks({ items: [{ remark: 'email sivakumar@gmail.com' }] });
  assert.strictEqual(leaks.length, 1);
  assert.ok(leaks[0].includes('email'));
});

test('findLeaks passes a correctly masked payload', () => {
  const leaks = findLeaks({
    contact: { phoneMasked: '98XXXXXX12', emailMasked: 'siv****@gmail.com' },
  });
  assert.deepStrictEqual(leaks, []);
});

test('findLeaks does not flag ordinary ids that merely contain digits', () => {
  assert.deepStrictEqual(findLeaks({ clientId: 'CLI-10240', serviceId: 'SVC-GST-2291' }), []);
});
