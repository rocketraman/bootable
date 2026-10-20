// For `./gradlew :examples:compose-html:client:jsBrowserDevelopmentRun`: the development server serves the client, with
// live reload, and forwards API calls to the server, which runs separately on its configured port.
if (config.devServer) {
  config.devServer.proxy = [
    {
      context: ["/api"],
      target: "http://localhost:8002",
    },
  ];
}
