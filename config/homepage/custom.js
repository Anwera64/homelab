// ==============================================================================
// Homepage Custom JavaScript - Dynamic Ingress Link Adapter
// ==============================================================================

(function() {
  var PORT_TO_PATH = {
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
      var targetUrl = new URL(targetHref, currentOrigin);
      var currentUrl = new URL(currentOrigin);

      var isStandardPort = targetUrl.port === '' || targetUrl.port === '80' || targetUrl.port === '443';
      
      if (!isStandardPort && targetUrl.port) {
        var port = targetUrl.port;

        // Remote Tailscale Ingress (HTTPS / *.ts.net) ➡️ Map to Port 443 HTTPS Subpath
        if (currentUrl.protocol === 'https:' || currentUrl.hostname.indexOf('.ts.net') !== -1) {
          var subPath = PORT_TO_PATH[port];
          if (subPath) {
            return currentUrl.origin + subPath;
          }
        }

        // Local Ingress (LAN IP / Hostname / localhost) ➡️ Pure HTTP direct port
        targetUrl.hostname = currentUrl.hostname;
        targetUrl.protocol = 'http:';
        return targetUrl.toString();
      }

      return targetHref;
    } catch (e) {
      return targetHref;
    }
  }

  function rewriteLinks() {
    var origin = window.location.href;
    document.querySelectorAll('a[href]').forEach(function(anchor) {
      var currentHref = anchor.getAttribute('href');
      if (currentHref && (currentHref.indexOf(':') !== -1 || currentHref.indexOf('http') === 0)) {
        var adapted = adaptServiceUrl(currentHref, origin);
        if (adapted !== currentHref) {
          anchor.setAttribute('href', adapted);
        }
      }
    });
  }

  // Run on initial load
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', rewriteLinks);
  } else {
    rewriteLinks();
  }

  // Observe dynamically rendered React/Next.js cards
  var observer = new MutationObserver(function() {
    rewriteLinks();
  });

  if (document.documentElement) {
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }

  // Capture click events as safety net
  document.addEventListener('click', function(e) {
    var anchor = e.target.closest('a');
    if (anchor) {
      var href = anchor.getAttribute('href');
      if (href) {
        var adapted = adaptServiceUrl(href, window.location.href);
        if (adapted !== href) {
          anchor.setAttribute('href', adapted);
        }
      }
    }
  }, true);
})();
