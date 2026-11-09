import tls from 'node:tls';

// Include the operating system's trusted roots (for managed networks and
// local HTTPS inspection) while retaining Node's bundled/extra certificates.
// Keep normal certificate and hostname verification enabled.
export function configureSystemTrust(api = tls) {
  if (typeof api.getCACertificates !== 'function' || typeof api.setDefaultCACertificates !== 'function') return 0;
  const system = api.getCACertificates('system');
  api.setDefaultCACertificates([...new Set([...api.getCACertificates('default'), ...system])]);
  return system.length;
}
