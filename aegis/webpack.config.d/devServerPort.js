// Dev-only: move the Compose/Wasm webpack dev server off :8080 (permanently
// occupied by the "Deen" project on this machine) to :9092, and allow the
// Aegis API origin so cross-origin calls to the Ktor server on :9090 work.
//
// Affects ONLY `wasmJsBrowserDevelopmentRun`. Safe to delete — added to fire up
// the local online (customer/buyonline) journey for testing.
config.devServer = config.devServer || {};
config.devServer.port = 9092;
config.devServer.host = 'localhost';
config.devServer.open = true;
