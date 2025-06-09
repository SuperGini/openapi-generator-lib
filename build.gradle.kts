import java.time.LocalDateTime

// ./gradlew publish -PopenApiFileName="car-module-openapi" run this command for car-module-openapi.yaml file
// ./gradlew openApiGenerate -PopenApiFileName="car-module-openapi" just to run the openApiGenerate with variables
// how to deploy to gitlab: https://docs.gitlab.com/user/packages/maven_repository/?tab=%60gradle%60
// https://www.youtube.com/watch?v=6y7vuNHoQC0

group = "com.gini"  // groupId of library
version = "1.0.0"  //version of the library -> is replaced with the version from the openapi.yaml file when we generate/publish the library

plugins {
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.13.0"            // used to generate the classes with openApiGenerate function
    id("java-library")                                      // we generate a java library
    `maven-publish`                                         // instruct gradle that this is a library that needs to be pushed
}

val angularGenerator: String = "typescript-angular"
val springGenerator: String = "spring"
val openApiFileName: String = project.findProperty("openApiFileName") as String?
    ?: "default" // get the filename from the openApiFileName variable. This value will be passed when we run the gitlab ci/cd
val openApiSpecPath: String = "$rootDir/openapi/$openApiFileName.yaml"             //this will get the openapi filename from the root project ->

// configuration properties: https://openapi-generator.tech/docs/generators/spring/
fun Project.generateOpenApiCode(taskName: String, fileGenerator: String) {
    tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>(taskName) {

        this.generatorName.set(fileGenerator)                           // generator name to generate the files
        this.inputSpec.set(openApiSpecPath)                            //path to openapi.yaml file
        this.apiPackage.set("com.gini.$openApiFileName.api")          //name if packages generated
        this.modelPackage.set("com.gini.$openApiFileName.model")      //name of packages generated
        this.globalProperties.set(mapOf("apis" to "", "models" to "")) //generate only controllers/clients and models

        if(springGenerator.equals(fileGenerator)) {
            this.outputDir.set("$rootDir/javagenerated")            //output directory for the files generated
            this.configOptions.set(
                mapOf(
                    "interfaceOnly" to "true",                      // Generate interfaces instead of classes for controllers.
                    "useSpringBoot3" to "true",                     // Ensure compatibility with Spring Boot 3. It will automatically set "useJakartaEe" to "true"
                    "useJakartaEe" to "true",                       // Use Jakarta EE
                    "dateLibrary" to "java8",
                    "useTags" to "true",                            //generate an interface for all controllers under that tag
                    "skipDefaultInterface" to "true",               //do not create default interface
                    "useResponseEntity" to "false",                 // don't use ResponseEntity
                )
            )
        }

        if(angularGenerator.equals(fileGenerator)) {
            this.outputDir.set("$rootDir/typescriptgenerated")
        }
    }
}

generateOpenApiCode("javaSpring", springGenerator)          //generates java files
generateOpenApiCode("typescriptAngular", angularGenerator)  // generates typescripts files

//GITLAB--------------------------
publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("https://gitlab.com/api/v4/projects/70539492/packages/maven")
            credentials(HttpHeaderCredentials::class) {
                name = "Deploy-Token" //accepted values: Private-Token, Deploy-Token, Job-Token
                value = System.getenv("CI_JOB_TOKEN")

                println("TOKEN_VALUE: $value +++++++++++++++++++++++++++++++++++++++++++++++++++++++")
            }
            authentication {
                create("header", HttpHeaderAuthentication::class)
            }
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21) // can only be used in projects with java 21 or higher
    }
}

repositories {
    mavenCentral() //we need this for the dependencies block
}


// this are the minimum dependencies to build the library
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    //need this dependencies for openapi generator
    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.30")
    implementation("org.openapitools:jackson-databind-nullable:0.2.6")

}

tasks.jar {
    archiveClassifier.set("javagenerated")                  //adds a classifier when generating the library. If this code is commented the default value will be -> plain This value can be anything i chose -> generated
    manifest { // to publish some information in the META-iNF file of the library. Is optional and you can delete it if you want
        attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Build-By" to System.getProperty("user.name"),
                "Build-Time" to LocalDateTime.now().toString(),
                "Created-By" to "Gradle ${gradle.gradleVersion}",
                "Build-Jdk" to "${System.getProperty("java.runtime.version")} (${System.getProperty("java.vendor")}) (${
                    System.getProperty(
                        "java.jvm.version"
                    )
                })",
                "Build-OS" to "${System.getProperty("os.name")} (${System.getProperty("os.arch")}) (${
                    System.getProperty(
                        "os.version"
                    )
                })",
                )
        )
    }
}

sourceSets {
    main {
        java {
            srcDirs("${rootDir}/javagenerated/src/main/java") // Adjust path if needed -> need this to include the classes in the library
        }
    }
}

tasks.bootJar {
    enabled = false // Disable the bootJar task as this is a library, not an executable application but a library
                    // if we don't disable the bootJar it will fail because we don't have the main class
}

