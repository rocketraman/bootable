// For the Kilua plugin's jsBrowserProductionWebpackSSR: bundles the app for Node.js, which the server's renderer runs.
config.target = "node";
config.output.chunkFormat = false;
config.devtool = undefined;
config.resolve.modules.push("kotlin");
