import test from 'node:test';
import assert from 'node:assert/strict';
import { configureSystemTrust } from '../lib/tls-trust.js';
test('system trust keeps bundled/extra roots and removes duplicates', () => {
  let trusted;
  const count = configureSystemTrust({
    getCACertificates: source => source === 'system' ? ['system-root', 'shared'] : ['bundled-root', 'extra-root', 'shared'],
    setDefaultCACertificates: certs => { trusted = certs; }
  });
  assert.equal(count, 2);
  assert.deepEqual(trusted, ['bundled-root', 'extra-root', 'shared', 'system-root']);
});
test('older Node versions keep normal TLS verification', () => { assert.equal(configureSystemTrust({}), 0); });
