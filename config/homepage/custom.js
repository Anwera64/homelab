// ==============================================================================
// Homepage Custom JavaScript - Dynamic Ingress Link Adapter (Subdomain & Local)
// ==============================================================================

(function() {
  var SERVICE_PORTS = {
    'jellyfin': '8096',
    'jellyseerr': '5055',
    'seerr': '5055',
    'jellystat': '3005',
    'stat': '3005',
    'maintainerr': '6246',
    'qbittorrent': '8080',
    'qbit': '8080',
    'sonarr': '8989',
    'radarr': '7878',
    'prowlarr': '9696',
    'bazarr': '6767',
    'flaresolverr': '8191'
  };

  var PORT_TO_SERVICE = {
    '8096': 'jellyfin',
    '5055': 'seerr',
    '3005': 'stat',
    '6246': 'maintainerr',
    '8080': 'qbit',
    '8989': 'sonarr',
    '7878': 'radarr',
    '9696': 'prowlarr',
    '6767': 'bazarr',
    '8191': 'flaresolverr'
  };

  function isHomelabHost(hostname, currentHostname) {
    if (!hostname) return false;
    if (hostname === currentHostname) return true;
    if (hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1') return true;
    if (hostname.endsWith('.local') || hostname.endsWith('.ts.net') || hostname.endsWith('.duckdns.org')) return true;
    if (/^192\.168\.\d{1,3}\.\d{1,3}$/.test(hostname)) return true;
    if (/^10\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(hostname)) return true;
    if (/^172\.(1[6-9]|2\d|3[0-1])\.\d{1,3}\.\d{1,3}$/.test(hostname)) return true;
    if (hostname === 'desktop-kujo8mp' || hostname === 'homelab') return true;
    return false;
  }

  function getDuckDnsRootDomain(hostname) {
    if (!hostname || hostname.indexOf('.duckdns.org') === -1) {
      return 'spicy-llama.duckdns.org';
    }
    var parts = hostname.split('.');
    if (parts.length >= 3) {
      return parts.slice(-3).join('.');
    }
    return 'spicy-llama.duckdns.org';
  }

  function adaptServiceUrl(targetHref, currentOrigin) {
    if (!targetHref || typeof targetHref !== 'string') {
      return targetHref;
    }

    try {
      var targetUrl = new URL(targetHref, currentOrigin);
      var currentUrl = new URL(currentOrigin);

      if (!isHomelabHost(targetUrl.hostname, currentUrl.hostname)) {
        return targetHref;
      }

      var isLocalOrigin = currentUrl.protocol === 'http:' && 
        (currentUrl.hostname === 'desktop-kujo8mp' || 
         currentUrl.hostname === 'localhost' || 
         currentUrl.hostname.endsWith('.local') || 
         /^192\.168\.\d{1,3}\.\d{1,3}$/.test(currentUrl.hostname));

      // When accessing locally over HTTP (e.g. desktop-kujo8mp), adapt subdomain HTTPS links to direct local HTTP ports
      if (isLocalOrigin && targetUrl.hostname.endsWith('.duckdns.org')) {
        var subdomain = targetUrl.hostname.split('.')[0];
        var localPort = SERVICE_PORTS[subdomain];
        if (localPort) {
          targetUrl.hostname = currentUrl.hostname;
          targetUrl.port = localPort;
          targetUrl.protocol = 'http:';
          return targetUrl.toString();
        }
      }

      // When accessing over DuckDNS or HTTPS, ensure clean standard HTTPS subdomain
      if (currentUrl.protocol === 'https:' || currentUrl.hostname.indexOf('.duckdns.org') !== -1 || currentUrl.hostname.indexOf('.ts.net') !== -1) {
        if (targetUrl.port && PORT_TO_SERVICE[targetUrl.port]) {
          var serviceName = PORT_TO_SERVICE[targetUrl.port];
          var rootDomain = getDuckDnsRootDomain(currentUrl.hostname);
          targetUrl.hostname = serviceName + '.' + rootDomain;
          targetUrl.port = '';
          targetUrl.protocol = 'https:';
          return targetUrl.toString();
        }
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

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', rewriteLinks);
  } else {
    rewriteLinks();
  }

  var observer = new MutationObserver(function() {
    rewriteLinks();
  });

  if (document.documentElement) {
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }

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
