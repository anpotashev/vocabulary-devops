if (params.JOB_RESET ||  params.JOB_RESET == null) {
    properties([
            parameters(
                    [
                            string(name: 'BRANCH', defaultValue: 'master', description: 'Git branch to build', trim: true),
                            booleanParam(name: 'JOB_RESET', defaultValue: false, description: 'Job reset'),
                            booleanParam(name: 'CLEAR_WORKSPACE', defaultValue: true, description: 'Clear workspace'),
                    ]
            ),
            // хранить информацию о 20 последних сборках
            buildDiscarder(logRotator(numToKeepStr: '3')),
            // запрет выполнения, если уже идет выполнение задачи
            disableConcurrentBuilds()
    ])
}
pipeline {
    agent {
        node {
            label 'java && maven && docker'
        }
    }
    environment {
        USER_CREDS = credentials('nexus.arh.net.ru')
    }
    stages {
        stage('Break if JOB_RESET') {
            steps {
                script {
                    if (params.JOB_RESET || params.JOB_RESET == null) {
                        currentBuild.result = 'ABORTED'
                        error('Job params reloaded. Aborted.')
                    }
                }
            }
        }
        stage('Clone repository') {
            steps {
                git branch: params.BRANCH,
                        url: 'https://github.com/anpotashev/vocabulary-study.git'
            }
        }
        stage('Build applications, deploy them to nexus repository. Build and push docker image') {
            steps {
                configFileProvider([configFile(fileId: 'maven-settings.xml', variable: 'MAVEN_SETTINGS_XML')]) {
                    sh 'mvn deploy -DskipTests=true -s $MAVEN_SETTINGS_XML'
                    sh 'mvn -pl bh spring-boot:build-image -DskipTests=true -Dspring-boot.build-image.publish=true -DBUILD_NUMBER=$BUILD_NUMBER -s $MAVEN_SETTINGS_XML'
                }
            }
        }
    }
    post {
        // Clean after build
        always {
            script {
                if (params.CLEAR_WORKSPACE) {
                    cleanWs()
                }
            }
        }
    }
}