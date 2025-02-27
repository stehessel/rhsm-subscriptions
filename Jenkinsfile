pipeline {
    options { buildDiscarder(logRotator(numToKeepStr: '50')) }
    agent {
        kubernetes {
            label 'swatch' // this value + unique identifier becomes the pod name
            idleMinutes 5  // how long the pod will live after no jobs have run on it
            containerTemplate {
                name 'openjdk11'
                image 'registry.access.redhat.com/ubi8/openjdk-11'
                command 'sleep'
                args '99d'
                resourceRequestCpu '2'
                resourceLimitCpu '6'
                resourceRequestMemory '2Gi'
                resourceLimitMemory '6Gi'
            }

            defaultContainer 'openjdk11'
        }
    }
    stages {
        stage('Build/Test/Lint') {
            steps {
                // The build task includes check, test, and assemble.  Linting happens during the check
                // task and uses the spotless gradle plugin.
                sh "./gradlew --no-daemon build"
            }
        }

        stage('Upload PR to SonarQube') {
            when {
                changeRequest()
            }
            steps {
                withSonarQubeEnv('sonarcloud.io') {
                    sh "./gradlew --no-daemon sonarqube -Duser.home=/tmp -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN} -Dsonar.pullrequest.key=${CHANGE_ID} -Dsonar.pullrequest.base=${CHANGE_TARGET} -Dsonar.pullrequest.branch=${BRANCH_NAME} -Dsonar.organization=rhsm -Dsonar.projectKey=rhsm-subscriptions"
                }
            }
        }
        stage('Upload Branch to SonarQube') {
            when {
                not {
                    changeRequest()
                }
            }
            steps {
                withSonarQubeEnv('sonarcloud.io') {
                    sh "./gradlew --no-daemon sonarqube -Duser.home=/tmp -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN} -Dsonar.branch.name=${BRANCH_NAME} -Dsonar.organization=rhsm -Dsonar.projectKey=rhsm-subscriptions"
                }
            }
        }
        stage('SonarQube Quality Gate') {
            steps {
                withSonarQubeEnv('sonarcloud.io') {
                    echo "SonarQube scan results will be visible at: ${SONAR_HOST_URL}/dashboard?id=rhsm-subscriptions"
                }
                retry(4) {
                    script {
                        try {
                            timeout(time: 5, unit: 'MINUTES') {
                                waitForQualityGate abortPipeline: true
                            }
                        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                            // "rethrow" as something retry will actually retry, see https://issues.jenkins-ci.org/browse/JENKINS-51454
                            if (e.causes.find { it instanceof org.jenkinsci.plugins.workflow.steps.TimeoutStepExecution$ExceededTimeout } != null) {
                                error("Timeout waiting for SonarQube results")
                            }
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            junit '**/build/test-results/test/*.xml'
        }
    }
}
