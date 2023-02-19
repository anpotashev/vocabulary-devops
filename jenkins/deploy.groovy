properties([
        parameters(
                [
                        choice(choices: ['TEST', 'PROD'], description: 'Stand', name: 'STAND'),
                        booleanParam(name: 'DEPLOY_HAZELCAST', defaultValue: false, description: 'Deploy hazelcast in k8s'),
                        booleanParam(name: 'PREPARE_NAMESPACE', defaultValue: false, description: 'Prepare namespace in k8s'),
                        booleanParam(name: 'UPDATE_DB', defaultValue: true, description: 'Update database'),
                        booleanParam(name: 'DEPLOY_APP', defaultValue: true, description: 'Deploy application'),
                        booleanParam(name: 'ENABLE_DEBUG', defaultValue: false, description: 'Enable remote debug'),
                        booleanParam(name: 'RELEASE', defaultValue: true, description: 'Deploy release (checked) or snapshot (unchecked)'),
                        string(name: 'BUILD_VERSION', defaultValue: '1.0.0', description: 'Build version', trim: true),
                        string(name: 'BUILD_NUMBER', defaultValue: '0', description: 'Build number', trim: true),
                        booleanParam(name: 'JOB_RESET', defaultValue: false, description: 'Job reset'),
                        booleanParam(name: 'CLEAR_WORKSPACE', defaultValue: true, description: 'Clear workspace'),
                ]
        ),
        buildDiscarder(logRotator(numToKeepStr: '3')),
        disableConcurrentBuilds()
])

def host = ""
switch (params.STAND) {
    case "TEST":
        host = "test"
        break
    case "PROD":
        host = "arh"
}
pipeline {
    agent {
        node {
            label 'ansible'
        }
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
        stage('Checkout') {
            steps {
                git branch: 'feature/7', url: "https://github.com/anpotashev/vocabulary-devops.git"
            }
        }
        stage('Prepare agent. Stage 1') {
            steps {
                dir('ansible') {
                    script {
                            ansiblePlaybook playbook: 'prepareAgentEnv1.yaml',
                                    vaultCredentialsId: "vault_pass",
                                    inventory: "hosts-${host}.txt",
                                    colorized: true
                    }
                }
            }
        }
        stage('Prepare agent. Stage 2') {
            steps {
                dir('ansible') {
                    script {
                            ansiblePlaybook playbook: 'prepareAgentEnv2.yaml',
                                    vaultCredentialsId: "vault_pass",
                                    inventory: "hosts-${host}.txt",
                                    colorized: true
                    }
                }
            }
        }
        stage('Run ansible playbook to deploy app') {
            steps {
                dir('ansible') {
//                    withCredentials([string(credentialsId: 'API_KEY_ID', variable: 'API_KEY')]) {
                    script {
                        ansiblePlaybook playbook: 'deploy2K8s.yaml',
                                vaultCredentialsId: "vault_pass",
                                inventory: "hosts-${host}.txt",
                                extraVars: [
                                        DEPLOY_HAZELCAST : params.DEPLOY_HAZELCAST,
                                        PREPARE_NAMESPACE: params.PREPARE_NAMESPACE,
                                        UPDATE_DB        : params.UPDATE_DB,
                                        DEPLOY_APP       : params.DEPLOY_APP,
                                        HOST             : host,
                                        BUILD_VERSION    : params.BUILD_VERSION,
                                        BUILD_NUMBER     : params.BUILD_NUMBER,
                                        RELEASE          : params.RELEASE,
                                        ENABLE_DEBUG     : params.RELEASE,
                                ],
                                colorized: true
                    }
                }
//                }
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