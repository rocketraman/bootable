// For `./gradlew :examples:kilua:client:jsBrowserDevelopmentRun`: the development server serves the client, with live
// reload, and forwards API calls to the server, which runs separately on its configured port.
// Pages aren't server-rendered here; the client renders them in the browser, as it does after the first page.
if (config.devServer) {
  config.devServer.proxy = [
    {
      context: ["/api"],
      target: "http://localhost:8004",
    },
  ];
}
