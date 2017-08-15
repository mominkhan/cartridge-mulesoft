// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"
def projectScmNamespace = "${SCM_NAMESPACE}"

// Variables
def mulesoftAppGitRepo = "mulesoft-helloworld-app.git"
def mulesoftAppGitRepoUrl = "https://github.com/mominkhan/" + mulesoftAppGitRepo
def deploymentPlaybook = "https://github.com/mominkhan/playbook-mulesoft-deploy.git"
def environmentsRepo = "https://github.com/mominkhan/environments-mulesoft.git"

// ** The logrotator variables should be changed to meet your build archive requirements
def logRotatorDaysToKeep = 7
def logRotatorBuildNumToKeep = 7
def logRotatorArtifactsNumDaysToKeep = 7
def logRotatorArtifactsNumToKeep = 7

// Jobs
def buildJob = freeStyleJob(projectFolderName + "/Mulesoft_Build")
def unitTestJob = freeStyleJob(projectFolderName + "/Mulesoft_Unit_Test")
def codeAnalysisJob = freeStyleJob(projectFolderName + "/Mulesoft_Code_Analysis")
def publishJob = freeStyleJob(projectFolderName + "/Mulesoft_Publish")
def deployJob = freeStyleJob(projectFolderName + "/Mulesoft_Deploy")

// Views
def pipelineView = buildPipelineView(projectFolderName + "/Delivery_Pipeline")

pipelineView.with{
    title('MuleSoft Delivery Pipeline')
    displayedBuilds(1)
    selectedJob(projectFolderName + "/Mulesoft_Build")
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

// Build job definition
buildJob.with{
  description("Mulesoft application build job.")
  logRotator {
    daysToKeep(logRotatorDaysToKeep)
    numToKeep(logRotatorBuildNumToKeep)
    artifactDaysToKeep(logRotatorArtifactsNumDaysToKeep)
    artifactNumToKeep(logRotatorArtifactsNumToKeep)
  }
  scm {
        git {
            remote {
                url(mulesoftAppGitRepoUrl)
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
  }
  triggers{
      scm('@hourly')
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
      env('APP_REPO_URL',mulesoftAppGitRepoUrl)
  }
  label("docker")
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  steps {
    maven {
      mavenInstallation("ADOP Maven")
      goals('clean install')
      property('skipTests','true')
    }
  }
  publishers{
    archiveArtifacts("**/*.zip")
    downstreamParameterized{
      trigger(projectFolderName + "/Mulesoft_Unit_Test"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${BUILD_NUMBER}')
          predefinedProp("PARENT_BUILD", '${JOB_NAME}')
        }
      }
    }
  }
}

// Unit Test job definition
unitTestJob.with{
  description("This job executes the unit testcases related to Mulesoft application.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Mulesoft_Build","Parent build name")
  }
  logRotator {
    daysToKeep(logRotatorDaysToKeep)
    numToKeep(logRotatorBuildNumToKeep)
    artifactDaysToKeep(logRotatorArtifactsNumDaysToKeep)
    artifactNumToKeep(logRotatorArtifactsNumToKeep)
  }
  scm {
        git {
            remote {
                url(mulesoftAppGitRepoUrl)
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  label("docker")
  steps {
    maven {
      mavenInstallation("ADOP Maven")
      goals('clean test')
    }
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/Mulesoft_Code_Analysis"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
        }
      }
    }
  }
}

// Publish job definition
codeAnalysisJob.with{
  description("This job performs the code analysis on the Mulesoft application code.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Mulesoft_Build","Parent build name")
  }
  logRotator {
    daysToKeep(logRotatorDaysToKeep)
    numToKeep(logRotatorBuildNumToKeep)
    artifactDaysToKeep(logRotatorArtifactsNumDaysToKeep)
    artifactNumToKeep(logRotatorArtifactsNumToKeep)
  }
  scm {
    git {
      remote {
        url(mulesoftAppGitRepoUrl)
        credentials("adop-jenkins-master")
      }
      branch("*/master")
    }
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  label("docker")
  configure { myProject ->
        myProject / builders << 'hudson.plugins.sonar.SonarRunnerBuilder'(plugin: "sonar@2.2.1") {
            project('${WORKSPACE}/sonar-project.properties')
            properties()
            javaOpts("-Xms256m -Xmx512m")
            jdk('(Inherit From Job)')
            task()
        }
    }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/Mulesoft_Publish"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
        }
      }
    }
  }
}

// Publish job definition
publishJob.with{
  description("This job publishes the Mulesoft application build package to Nexus.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Mulesoft_Build","Parent build name")
  }
  logRotator {
    daysToKeep(logRotatorDaysToKeep)
    numToKeep(logRotatorBuildNumToKeep)
    artifactDaysToKeep(logRotatorArtifactsNumDaysToKeep)
    artifactNumToKeep(logRotatorArtifactsNumToKeep)
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
    credentialsBinding {
      usernamePassword("NEXUS_USER", "NEXUS_PASSWORD", "adop-admin-user")
    }
  }
  label("docker")
  steps {
    copyArtifacts("Mulesoft_Build") {
      buildSelector {
        buildNumber('${B}')
      }
      flatten(true)
      includePatterns('**/*.zip')
    }
    shell('''set +x
      |mv *.zip HELLO_WORLD.zip
      |curl -v -F r=releases -F hasPom=false -F e=zip \\
      | -F g=com.example.mulesoft -F a=HELLO_WORLD \\
      | -F v=1.0 -F p=maven-archetype -F file=@HELLO_WORLD.zip \\
      | -u "${NEXUS_USER}:${NEXUS_PASSWORD}" \\
      | http://nexus:8081/nexus/service/local/artifact/maven/content
      |set -x'''.stripMargin()
    )
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/Mulesoft_Deploy"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
        }
      }
    }
  }
}

// Deploy job definition
deployJob.with{
  description("This job deploys the Mulesoft application to the SANDBOX environment")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Mulesoft_Build","Parent build name")
    stringParam("ENVIRONMENT_NAME","Sandbox","Name of the environment.")
    stringParam("APPLICATION_NAME","helloworld-mk01","Mule Application Name.")
    stringParam("RELEASE_VERSION","1.0","Release version to be deployed.")
  }
  logRotator {
    daysToKeep(logRotatorDaysToKeep)
    numToKeep(logRotatorBuildNumToKeep)
    artifactDaysToKeep(logRotatorArtifactsNumDaysToKeep)
    artifactNumToKeep(logRotatorArtifactsNumToKeep)
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    timestamps()
    sshAgent("adop-jenkins-master")
    credentialsBinding {
      usernamePassword("NEXUS_USER", "NEXUS_PASSWORD", "adop-admin-user")
    }
  }
  multiscm {
    git {
      remote {
        url(mulesoftAppGitRepoUrl)
        credentials("adop-jenkins-master")
      }
      branch("*/master")
    }
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
      env('REPO','releases')
      env('GROUP_ID','com/example/mulesoft')
      env('ARTIFACT_ID','HELLO_WORLD')
  }
  label("docker")
  steps {
    shell('''set +x
      |wget --auth-no-challenge --user "${NEXUS_USER}" --password "${NEXUS_PASSWORD}" \\
      |http://nexus:8081/nexus/service/local/repositories/${REPO}/content/${GROUP_ID}/${ARTIFACT_ID}/${RELEASE_VERSION}/HELLO_WORLD-${RELEASE_VERSION}.zip
      |set -x'''.stripMargin()
    )
    maven {
      mavenInstallation("ADOP Maven")
      goals('org.mule.tools.maven:mule-maven-plugin:deploy')
      property('mule.environment','${ENVIRONMENT_NAME}')
      property('mule.application.name','${APPLICATION_NAME}')
      property('mule.application','${ARTIFACT_ID}-${RELEASE_VERSION}.zip')
      property('mule.username','${MULE_USERNAME}')
      property('mule.password','${MULE_PASSWORD}')
    }
    shell('''set +x
      |STATUS=""
      |while [[ "$STATUS" != "STARTED" ]] ; do
      |  echo "[INFO] Waiting for deployment to be completed ..."
      |  sleep 5
      |  STATUS=$(anypoint-cli runtime-mgr cloudhub-application describe -o json helloworld-mk01 | jq -r ."Status")
      |done
      |echo "[INFO] Deployment completed successfully"
      |set -x'''.stripMargin()
    )
  }
}
