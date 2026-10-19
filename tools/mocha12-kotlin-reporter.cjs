'use strict';
const { createRequire } = require('node:module');
const { realpathSync } = require('node:fs');
const path = require('node:path');

// Kotlin web helpers 3.5.4 initialize their ES5 reporter with Base.call(this).
// Mocha 12 made Base an ES class. Keep its initialization and shared runner state
// while providing the callable constructor expected by that test-only adapter.
function install(Mocha, version) {
  if (!/^12\./.test(version)) throw new Error('Recheck the Kotlin reporter bridge when upgrading Mocha.');
  const Base = Mocha.reporters.Base;
  if (Base.tanvritLegacyCallableBase) return;
  function CallableBase(runner, options) {
    const initialized = Reflect.construct(Base, [runner, options]);
    Object.defineProperties(this, Object.getOwnPropertyDescriptors(initialized));
  }
  Object.setPrototypeOf(CallableBase, Base);
  CallableBase.prototype = Base.prototype;
  Object.defineProperty(CallableBase, 'tanvritLegacyCallableBase', { value: true });
  Mocha.reporters.Base = CallableBase;
}
module.exports = install;

const entry = process.argv[1] && realpathSync(process.argv[1]);
if (entry && path.basename(path.dirname(entry)) === 'bin' && path.basename(path.dirname(path.dirname(entry))) === 'mocha') {
  const localRequire = createRequire(entry);
  install(localRequire('mocha'), localRequire('mocha/package.json').version);
}
