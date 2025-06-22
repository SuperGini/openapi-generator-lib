import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.time.LocalDateTime

/**
 * ./gradlew publish -PopenApiFileName="car-module-openapi" -> run this command for car-module-openapi.yaml file
 * ./gradlew javaSpring -PopenApiFileName="car-module-openapi" -> just to run the openApiGenerate with variables
 *
 * configuration properties for spring generator: https://openapi-generator.tech/docs/generators/spring/
 * configuration properties for typescript generator: https://openapi-generator.tech/docs/generators/typescript-angular/
 *
 * how to deploy to gitlab for java: https://docs.gitlab.com/user/packages/maven_repository/?tab=%60gradle%60
 * how to deploy to gitlab for typescript: https://docs.gitlab.com/user/packages/npm_registry/
 *
 * how to build java library 1: https://www.youtube.com/watch?v=6y7vuNHoQC0
 * how to build java library 2: https://www.youtube.com/watch?v=tr5_OWgXDiw&t=653s
 */


group = "com.gini"  // groupId of library
version =
    "1.0.0"  //version of the library -> is replaced with the version from the openapi.yaml file when we generate/publish the library using .gitlab-ci.yaml file

plugins {
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.13.0"            // used to generate the classes with openApiGenerate function
    id("java-library")                                      // instruct gradle generate a java library
    `maven-publish`                                         // instruct gradle that this is a library that needs to be published
}

val ANGULAR_GENERATOR: String = "typescript-angular"
val SPRING_GENERATOR: String = "spring"

val openApiFileName: String = project.findProperty("openApiFileName") as String?
    ?: "default" // get the filename from the openApiFileName variable. This value will be passed when we run the gitlab ci/cd
val openApiSpecPath: String =
    "$rootDir/specs/openapi/$openApiFileName.yaml"             //this will get the openapi filename from the root project ->

fun Project.configureSpringGenerator(task: GenerateTask) {
    task.outputDir.set("$rootDir/javagenerated")                     //output directory for the files generated
    task.apiPackage.set("com.gini.$openApiFileName.api")             //name if packages generated
    task.modelPackage.set("com.gini.$openApiFileName.model")         //name of packages generated
    task.globalProperties.set(mapOf("apis" to "", "models" to ""))   //generate only controllers/clients and models
    task.configOptions.set(
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

fun Project.configureAngularGenerator(task: GenerateTask) {
    task.outputDir.set("$rootDir/$openApiFileName")
    task.apiPackage.set("/api")                                      //name of packages generated for apis
    task.modelPackage.set("/model")                                      //name of packages generated for models
    task.configOptions.set(
        mapOf(
            "npmName" to "@openapi-generator/$openApiFileName",     //generates angular library with this name
            "ngVersion" to "19.0.0",                                // use angular 19.0.0
            "enumPropertyNaming" to "UPPERCASE",                    //generates all enums as uppercase
            "stringEnums" to "true",                                //generates enum and not constants like enums
            "serviceFileSuffix" to "Client",                        //replace service with Client
            "serviceSuffix" to "Client",                            //replace service with Client
        )
    )
}

fun Project.generateOpenApiCode(taskName: String, fileGenerator: String) {
    this.tasks.register<GenerateTask>(taskName) {
        this.generatorName.set(fileGenerator)                           // generator name to generate the files
        this.inputSpec.set(openApiSpecPath)                            //path to openapi.yaml file

        if (SPRING_GENERATOR == fileGenerator) {
            configureSpringGenerator(this)
        }

        if (ANGULAR_GENERATOR == fileGenerator) {
            configureAngularGenerator(this)
        }
    }
}

generateOpenApiCode("javaSpring", SPRING_GENERATOR)          //generates java files
generateOpenApiCode("typescriptAngular", ANGULAR_GENERATOR)  // generates typescripts files

//GITLAB--------------------------
publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            artifactId = openApiFileName                                         // artifactId for java library
        }
    }
    repositories {
        maven {
            url = uri("https://gitlab.com/api/v4/projects/70539492/packages/maven")
            credentials(HttpHeaderCredentials::class) {
                name = "Deploy-Token" //accepted values: Private-Token, Deploy-Token, Job-Token
                value = findProperty("gitlab_deploy_token") as String?
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


// this are the minimum dependencies to build the java library
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    //need this dependencies for openapi generator
    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.30")
    implementation("org.openapitools:jackson-databind-nullable:0.2.6")

}

//this task is optional
tasks.jar {
    archiveClassifier.set("javagenerated")                  //adds a classifier when generating the library. If this code is commented the default value will be -> "plain". This value can be anything, I chose -> generated
    manifest {                                               // to publish some information in the META-iNF file of the library. Is optional and you can deleted it if you want
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

