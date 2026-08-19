/**
 * Dynamic Ingress Link Adapter for Homepage
 * Rewrites homelab service URLs dynamically based on how the user accessed Homepage.
 * - Remote HTTPS (Tailscale *.ts.net / https): Maps to clean HTTPS subpaths on port 443.
 * - Local LAN (http / hostname / LAN IP): Maps to pure HTTP direct ports with zero SSL conflicts.
 */

const PORT_TO_PATH = {
  '8096': '/jellyfin/',
  '5055': '/seerr/',
  '8080': '/qbit/',
  '8989': '/sonarr/',
  '7878': '/radarr/',
  '9696': '/prowlarr/',
  '6767': '/bazarr/'
};

function adaptServiceUrl(targetHref, currentOrigin) {
  if (!targetHref || typeof targetHref !== 'string') {
    return targetHref;
  }

  try {
    const targetUrl = new URL(targetHref, currentOrigin);
    const currentUrl = new URL(currentOrigin);

    // Only adapt internal homelab service links targeting specific ports
    const isStandardPort = targetUrl.port === '' || targetUrl.port === '80' || targetUrl.port === '443';
    
    if (!isStandardPort && targetUrl.port) {
      const port = targetUrl.port;

      // Remote Tailscale Ingress (HTTPS / .ts.net): Map to clean HTTPS subpath on port 443
      if (currentUrl.protocol === 'https:' || currentUrl.hostname.includes('.ts.net')) {
        const subPath = PORT_TO_PATH[port];
        if (subPath) {
          return `${currentUrl.origin}${subPath}`;
        }
      }

      // Local Ingress (LAN IP / Hostname / localhost): Preserve active hostname + Pure HTTP port
      targetUrl.hostname = currentUrl.hostname;
      targetUrl.protocol = 'http:';
      return targetUrl.toString();
    }

    return targetHref;
  } catch (e) {
    return targetHref;
  }
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { adaptServiceUrl, PORT_TO_PATH };
}
