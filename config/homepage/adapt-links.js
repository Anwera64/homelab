/**
 * Dynamic Ingress Link Adapter for Homepage
 * Rewrites homelab service URLs dynamically based on how the user accessed Homepage.
 * Adopts both the active hostname AND the active protocol (http/https).
 *
 * @param {string} targetHref - The target URL configured on the service tile.
 * @param {string} currentOrigin - The user's active location origin (e.g. window.location.href).
 * @returns {string} The adapted target URL.
 */
function adaptServiceUrl(targetHref, currentOrigin) {
  if (!targetHref || typeof targetHref !== 'string') {
    return targetHref;
  }

  try {
    const targetUrl = new URL(targetHref, currentOrigin);
    const currentUrl = new URL(currentOrigin);

    // Only adapt URLs with explicit non-standard ports (e.g. 8096, 5055, 8989, 7878, 8080, etc.)
    // Do not modify standard web links (port 80/443/blank) or external websites without custom ports
    const isStandardPort = targetUrl.port === '' || targetUrl.port === '80' || targetUrl.port === '443';
    
    if (!isStandardPort && targetUrl.port) {
      // Adopt both hostname and protocol from user's active ingress
      targetUrl.hostname = currentUrl.hostname;
      targetUrl.protocol = currentUrl.protocol;
      return targetUrl.toString();
    }

    return targetHref;
  } catch (e) {
    return targetHref;
  }
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { adaptServiceUrl };
}
