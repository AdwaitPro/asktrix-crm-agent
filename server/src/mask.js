'use strict';

/**
 * Server-side PII masking (§4, ADR-0003).
 *
 * This module is the ONLY place a full phone number or email may be read, and it is a one-way door:
 * nothing here returns the original value. The unmasked value never enters an API response, so an
 * employee with the APK and an HTTP proxy sees exactly what the UI shows.
 *
 * If you find yourself wanting an "unmasked" variant for convenience, that is the requirement
 * failing. Do it server-side instead.
 */

/** `9876543212` -> `98XXXXXX12`: first two and last two digits survive. */
function maskPhone(full) {
  if (!full) return '';
  const digits = String(full).replace(/\D/g, '');
  if (digits.length < 6) return 'XXXXXXXXXX';
  const head = digits.slice(0, 2);
  const tail = digits.slice(-2);
  return head + 'X'.repeat(digits.length - 4) + tail;
}

/** `sivakumar@gmail.com` -> `siv****@gmail.com`: at most three local characters survive. */
function maskEmail(full) {
  if (!full) return '';
  const at = String(full).indexOf('@');
  if (at <= 0) return '****';
  const local = full.slice(0, at);
  const domain = full.slice(at);
  const keep = Math.min(3, Math.max(1, local.length - 1));
  return local.slice(0, keep) + '****' + domain;
}

/**
 * Guard used by the response serialiser and by tests: asserts a payload about to be sent to a device
 * carries nothing that looks like a full phone number or email address.
 *
 * Defence in depth. The schema already prevents it; this catches the case where someone adds a field
 * and forgets. Returns a list of offending JSON paths.
 */
const FULL_PHONE = /\b(?:\+?91[-\s]?)?[6-9]\d{9}\b/;
const FULL_EMAIL = /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b/;

function findLeaks(value, path = '$', found = []) {
  if (value == null) return found;
  if (typeof value === 'string') {
    // A masked value contains X's or asterisks; those are expected and not a leak.
    if (!value.includes('X') && !value.includes('*')) {
      if (FULL_PHONE.test(value)) found.push(`${path} (phone)`);
      else if (FULL_EMAIL.test(value)) found.push(`${path} (email)`);
    }
    return found;
  }
  if (Array.isArray(value)) {
    value.forEach((v, i) => findLeaks(v, `${path}[${i}]`, found));
    return found;
  }
  if (typeof value === 'object') {
    for (const [k, v] of Object.entries(value)) findLeaks(v, `${path}.${k}`, found);
  }
  return found;
}

module.exports = { maskPhone, maskEmail, findLeaks };
