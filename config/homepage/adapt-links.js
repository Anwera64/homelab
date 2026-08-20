/**
 * Dynamic Ingress Link Adapter for Homepage
 * Rewrites homelab service URLs dynamically based on how the user accessed Homepage.
 * - Remote HTTPS (Tailscale *.ts.net / https): Maps to dedicated Let's Encrypt HTTPS ports.
 * - Local LAN (http / hostname / LAN IP): Keeps standard pure HTTP ports (0 SSL errors).
 */

const HTTP_TO_HTTPS_PORT = {
  '8096': '8443',   // Jellyfin
  '5055': '15055',  // Jellyseerr
  '8080': '18080',  // qBittorrent
  '8989': '18989',  // Sonarr
  '7878': '17878',  // Radarr
  '9696': '19696',  // Prowlarr
  '6767': '16767',  // Bazarr
  '8191': '18191'   // FlareSolverr
};

function adaptServiceUrl(targetHref, currentOrigin) {
  if (!targetHref || typeof targetHref !== 'string') {
    return targetHref;
  }

  try {
    const targetUrl = new URL(targetHref, currentOrigin);
    const currentUrl = new URL(currentOrigin);

    const isStandardPort = targetUrl.port === '' || targetUrl.port === '80' || targetUrl.port === '443';
    
    if (!isStandardPort && targetUrl.port) {
      const port = targetUrl.port;

      // Remote Tailscale Ingress (HTTPS / *.ts.net) ➡️ Dedicated HTTPS port + https protocol
      if (currentUrl.protocol === 'https:' || currentUrl.hostname.includes('.ts.net')) {
        const httpsPort = HTTP_TO_HTTPS_PORT[port] || port;
        targetUrl.hostname = currentUrl.hostname;
        targetUrl.port = httpsPort;
        targetUrl.protocol = 'https:';
        return targetUrl.toString();
      }

      // Local Ingress (LAN IP / Hostname / localhost) ➡️ Keep standard HTTP port + http protocol
      targetUrl.hostname = currentUrl.hostname;
      targetUrl.port = port;
      targetUrl.protocol = 'http:';
      return targetUrl.toString();
    }

    return targetHref;
  } catch (e) {
    return targetHref;
  }
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { adaptServiceUrl, HTTP_TO_HTTPS_PORT };
}
