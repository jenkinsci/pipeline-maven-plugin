/*
 * `buildPlugin` step provided by: https://github.com/jenkins-infra/pipeline-library
 */
buildPlugin(
  // cannot use this with Docker tests
  useContainerAgent: false,
  configurations: [
    [ platform: "linux", jdk: "25" ],
    [ platform: "windows", jdk: "21" ]
  ],
  timeout: 120
)
