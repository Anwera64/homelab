// ==============================================================================
// Homepage Custom JavaScript - Dynamic Ingress Link Adapter (Split Ports)
// ==============================================================================

(function() {
  var HTTP_TO_HTTPS_PORT = {
    '8096': '8443',   // Jellyfin
    '5055': '15055',  // Jellyseerr
    '8080': '18080',  // qBittorrent
    '8989': '18989',  // Sonarr
    '7878': '17878',  // Radarr
    '9696': '19696',  // Prowlarr
    '6767': '16767'   // Bazarr
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

        // Remote Tailscale Ingress (HTTPS / *.ts.net) ➡️ Dedicated HTTPS port + https protocol
        if (currentUrl.protocol === 'https:' || currentUrl.hostname.indexOf('.ts.net') !== -1) {
          var httpsPort = HTTP_TO_HTTPS_PORT[port] || port;
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
