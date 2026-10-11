// ==============================================================================
// Homepage Custom JavaScript - Dynamic Client-Side Browser GPS Weather Component
// ==============================================================================

(function() {
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
      badge.style.cssText = 'display: inline-flex; align-items: center; gap: 6px; font-size: 0.875rem; font-weight: 500; padding: 4px 10px; border-radius: 9999px; background: rgba(255,255,255,0.08); backdrop-filter: blur(8px); border: 1px solid rgba(255,255,255,0.12); color: inherit; cursor: pointer; transition: transform 0.2s; white-space: nowrap; flex-shrink: 0;';
    }

    badge.innerHTML = '<span>' + weatherInfo.icon + '</span><span>' + Math.round(temp) + '°C</span>';
    badge.title = weatherInfo.text + ' (' + Math.round(temp) + '°C) • GPS: ' + lat.toFixed(2) + ', ' + lon.toFixed(2);

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
      var wrapper = document.getElementById('datetime-weather-wrapper');
      if (!wrapper) {
        wrapper = document.createElement('div');
        wrapper.id = 'datetime-weather-wrapper';
        wrapper.style.cssText = 'display: inline-flex; flex-direction: row; align-items: center; justify-content: flex-end; gap: 12px; margin-left: auto; flex-wrap: nowrap;';
        timeContainer.parentElement.insertBefore(wrapper, timeContainer);
        wrapper.appendChild(badge);
        wrapper.appendChild(timeContainer);
      } else {
        if (!wrapper.contains(badge)) {
          wrapper.insertBefore(badge, wrapper.firstChild);
        }
      }
    } else {
      var headerTarget = document.querySelector('.information-widgets') || document.querySelector('header') || document.body.firstElementChild;
      if (headerTarget && !headerTarget.contains(badge)) {
        headerTarget.appendChild(badge);
      }
    }
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
