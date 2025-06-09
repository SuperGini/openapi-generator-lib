# Openapi Library Generator

### The scope of this project is to generate java-spring and typescript-angular libraries from openapi.yml files to Gitlab

1. Initiate project:
    - generate a simple spring boot project and add te web dependency.
    - delete the main class, test and resource folders.
    - add [package.json](package.json) for the typescript code
    - we will use the gradle wrapper to generate code and libraries.

2. Generate java library:
    - publish java to gitlab: https://docs.gitlab.com/user/packages/maven_repository/?tab=%60gradle%60
    - add deploy token: in the project go to Settings/CI/CD/repository
      in the Deploy tokens generate a token with an expiration date that will have the
      read_package_registry, write_package_registry  scopes. This token will be used in the 
      pipeline as ${CI_JOB_TOKEN} variable. The token represents the authentication method to
      authorize the publishing job of the library to gitlab registry. When the token expires 
      you will need to generate another token.
    - to activate the pipeline you will need to go in the project at build/pipelines. Click the
      New pipeline  button and in the fields at input variable key add: openApiFileName and in the
      input variable value: put the name of the openapi file for what you want to generate the libraries.
      Click the New pipeline button.
    - .gitlab-ci.yml file: 
      ```yaml
          artifacts:
            paths:
              - javagenerated/
      ```
      In the javagenerated folder the java classes will be generated. We all the files that are 
      generated in an artifact to make them available to the next stage, where we will publish this
      files into a library.

3. Generate typescript library:
    - publish typescript to gitlab: https://docs.gitlab.com/user/packages/npm_registry/
    - the classes are generated using gradle in: generate-java-typescript-files-stage in the folder typescriptgenerated.
      We save this folder as an artifact so we can have access to them in the publish-typescript-library-stage.
    - extract the version from the openapi.yaml file and set tit to version in package.json.
    - replace the name "name": "@openapi-generator/test-library", with   "name": "@openapi-generator/openApiFileName",
      where openApiFileName is the variable set in the gitlab UI.
    - create and populate the .npmrc file that is used tho authenticate the stage and publish the typescript files to gitlab
   ```yaml
        echo "@openapi-generator:registry=https://gitlab.com/api/v4/projects/${CI_PROJECT_ID}/packages/npm/" > .npmrc                 
        echo "//${CI_SERVER_HOST}/api/v4/projects/${CI_PROJECT_ID}/packages/npm/:_authToken=${CI_JOB_TOKEN}" >> .npmrc
    ```


