// The port each service publishes, and the name it has under *.spicy-llama.duckdns.org.
// The suites check the compose files, the Pi's Caddyfile and the README against it.
const PORT_TO_SERVICE = {
  '8096': 'jellyfin',
  '5055': 'seerr',
  '3005': 'stat',
  '6246': 'maintainerr',
  '11011': 'cleanuparr',
  '8080': 'qbit',
  '8989': 'sonarr',
  '7878': 'radarr',
  '9696': 'prowlarr',
  '6767': 'bazarr',
  '8191': 'flaresolverr',
  '3002': 'grafana'
};

module.exports = { PORT_TO_SERVICE };
