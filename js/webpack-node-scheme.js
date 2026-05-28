// Shared webpack.config.d fragment, injected into every browser (Karma) bundle by the
// Sphereon ConventionsPlugin (see configureBrowserWebpackConfigDir).
//
// Ktor 3.5.0 replaced ktor-network's eval('require')('node:net') hack with a static `node:net`
// import (an ES-module compatibility fix). The CIO engine is transitively on the JS classpath of
// HTTP-transport modules but is a Node-only engine; browser tests run against MockEngine / the Js
// engine and never open a socket. webpack cannot resolve the `node:`-scheme import for the browser
// target ("You may need an additional plugin to handle node: URIs"), and resolve.fallback does not
// match the `node:` prefix (webpack#14166). So strip the prefix, then stub the bare Node socket
// modules to `false` for the browser bundle.
const webpack = require('webpack');
config.plugins.push(
    new webpack.NormalModuleReplacementPlugin(/^node:/, (resource) => {
        resource.request = resource.request.replace(/^node:/, '');
    })
);
config.resolve = config.resolve || {};
config.resolve.fallback = Object.assign({}, config.resolve.fallback, {
    net: false,
    tls: false,
    dns: false,
    fs: false,
});
