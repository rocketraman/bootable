// Kilua's SSR module imports Node.js's http module, to serve the renderer; the browser bundle has no use for it.
config.resolve.fallback = {
  "http": false,
};
