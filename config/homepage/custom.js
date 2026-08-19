// ==============================================================================
// Homepage Custom JavaScript - Dynamic Ingress Link Adapter
// ==============================================================================

(function() {
  function adaptServiceUrl(targetHref, currentOrigin) {
    if (!targetHref || typeof targetHref !== 'string') {
      return targetHref;
    }

    try {
      const targetUrl = new URL(targetHref, currentOrigin);
      const currentUrl = new URL(currentOrigin);

      const isStandardPort = targetUrl.port === '' || targetUrl.port === '80' || targetUrl.port === '443';
      
      if (!isStandardPort && targetUrl.port) {
        targetUrl.hostname = currentUrl.hostname;
        targetUrl.protocol = currentUrl.protocol;
        return targetUrl.toString();
      }

      return targetHref;
    } catch (e) {
      return targetHref;
    }
  }

  function rewriteLinks() {
    const origin = window.location.href;
    document.querySelectorAll('a[href]').forEach(function(anchor) {
      var currentHref = anchor.getAttribute('href');
      if (currentHref && (currentHref.includes(':') || currentHref.startsWith('http'))) {
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
