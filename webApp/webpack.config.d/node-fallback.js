// Prevent bundling Node built-ins when sql.js is processed for browser/Wasm.
config.resolve = config.resolve || {};
config.resolve.fallback = config.resolve.fallback || {};
config.resolve.fallback.fs = false;
config.resolve.fallback.path = false;
config.resolve.fallback.crypto = false;
