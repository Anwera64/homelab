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

  // ==============================================================================
  // Dynamic Client-Side Browser GPS Weather Component
  // ==============================================================================
  var WEATHER_CODES = {
    0: { text: 'Clear', icon: '☀️' },
    1: { text: 'Mainly Clear', icon: '🌤️' },
    2: { text: 'Partly Cloudy', icon: '⛅' },
    3: { text: 'Overcast', icon: '☁️' },
    45: { text: 'Fog', icon: '🌫️' },
    48: { text: 'Depositing Rime Fog', icon: '🌫️' },
    51: { text: 'Light Drizzle', icon: '🌦️' },
    53: { text: 'Moderate Drizzle', icon: '🌧️' },
    55: { text: 'Dense Drizzle', icon: '🌧️' },
    61: { text: 'Slight Rain', icon: '🌦️' },
    63: { text: 'Moderate Rain', icon: '🌧️' },
    65: { text: 'Heavy Rain', icon: '🌧️' },
    71: { text: 'Slight Snow', icon: '🌨️' },
    73: { text: 'Moderate Snow', icon: '❄️' },
    75: { text: 'Heavy Snow', icon: '❄️' },
    80: { text: 'Rain Showers', icon: '🌦️' },
    81: { text: 'Moderate Showers', icon: '🌧️' },
    82: { text: 'Violent Showers', icon: '⛈️' },
    95: { text: 'Thunderstorm', icon: '⛈️' },
    96: { text: 'Thunderstorm with Hail', icon: '⛈️' },
    99: { text: 'Severe Thunderstorm', icon: '⛈️' }
  };

  function updateWeatherBadge(temp, code, lat, lon) {
    var weatherInfo = WEATHER_CODES[code] || { text: 'Weather', icon: '🌤️' };
    var badge = document.getElementById('dynamic-gps-weather-badge');
    if (!badge) {
      badge = document.createElement('div');
      badge.id = 'dynamic-gps-weather-badge';
      badge.style.cssText = 'display: inline-flex; align-items: center; gap: 6px; font-size: 0.875rem; font-weight: 500; padding: 4px 10px; border-radius: 9999px; background: rgba(255,255,255,0.08); backdrop-filter: blur(8px); border: 1px solid rgba(255,255,255,0.12); color: inherit; cursor: pointer; transition: transform 0.2s; margin-right: 12px; margin-left: 6px; vertical-align: middle;';
      
      // Locate the datetime widget in the header
      var header = document.querySelector('header') || document.querySelector('.information-widgets') || document.querySelector('.widgets-container') || document.body;
      var candidateNodes = header.querySelectorAll('div, span, p');
      var timeContainer = null;
      var datePattern = /(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|\d{1,2}:\d{2})/i;
      
      for (var i = candidateNodes.length - 1; i >= 0; i--) {
        var node = candidateNodes[i];
        if (datePattern.test(node.textContent) && node.children.length === 0) {
          timeContainer = node.parentElement;
          break;
        }
      }

      if (timeContainer && timeContainer.parentElement) {
        timeContainer.parentElement.insertBefore(badge, timeContainer);
      } else {
        var headerTarget = document.querySelector('.information-widgets') || document.querySelector('header') || document.body.firstElementChild;
        if (headerTarget) headerTarget.appendChild(badge);
      }
    }
    badge.innerHTML = '<span>' + weatherInfo.icon + '</span><span>' + Math.round(temp) + '°C</span>';
    badge.title = weatherInfo.text + ' (' + Math.round(temp) + '°C) • GPS: ' + lat.toFixed(2) + ', ' + lon.toFixed(2);
  }

  function fetchGpsWeather(lat, lon) {
    var url = 'https://api.open-meteo.com/v1/forecast?latitude=' + lat + '&longitude=' + lon + '&current=temperature_2m,weather_code&timezone=auto';
    fetch(url)
      .then(function(res) { return res.json(); })
      .then(function(data) {
        if (data && data.current) {
          updateWeatherBadge(data.current.temperature_2m, data.current.weather_code, lat, lon);
        }
      })
      .catch(function(err) {
        console.warn('GPS weather fetch error:', err);
      });
  }

  function initGpsWeather() {
    if (navigator && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        function(pos) {
          if (pos && pos.coords) {
            fetchGpsWeather(pos.coords.latitude, pos.coords.longitude);
          }
        },
        function(err) {
          fetchGpsWeather(19.4326, -99.1332);
        },
        { timeout: 8000, maximumAge: 600000 }
      );
    } else {
      fetchGpsWeather(19.4326, -99.1332);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initGpsWeather);
  } else {
    initGpsWeather();
  }
})();
