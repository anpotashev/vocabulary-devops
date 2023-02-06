if (params.JOB_RESET || params.JOB_RESET == null) {
    properties([
            parameters(
                    [
                            credentials(name: 'NEW_ACCOUNT_CREDENTIALS_ID', credentialType: 'com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey', defaultValue: 'ansible', description: 'Credentials, that will be able to use after user creation', required: true),
                            credentials(name: 'EXISTS_ACCOUNT_CREDENTIALS_CREDENTIALS_ID', credentialType: 'com.cloudbees.plugins.credentials.common.StandardCredentials', defaultValue: '', description: 'Credentials of exists account', required: true),
                            credentials(name: 'BECOME_PASSWORD_CREDENTIALS_ID', credentialType: 'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl', defaultValue: '', description: 'Become password', required: true),
                            string(name: 'HOSTNAME', defaultValue: '', description: 'Hostname or IP address', trim: true),
                            booleanParam(name: 'JOB_RESET', defaultValue: false, description: 'Job reset'),
                    ]
            ),
            buildDiscarder(logRotator(numToKeepStr: '3')),
            disableConcurrentBuilds()
    ])
}

pipeline {
    agent { label 'ansible' }

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
                git branch: 'build', url: "https://github.com/anpotashev/vocabulary-devops.git"
            }
        }
        stage('Prepare agent.') {
            steps {
                dir('ansible') {
                    script {
                        ansiblePlaybook playbook: 'prepareAgentEnv1.yaml',
                                vaultCredentialsId: "vault_pass",
                                inventory: "127.0.0.1,",
                                colorized: true
                    }
                }
            }
        }
        stage('Add user') {
            steps {
                    withCredentials([
                            sshUserPrivateKey(credentialsId: params.NEW_ACCOUNT_CREDENTIALS_ID, keyFileVariable: 'KEY_FILE_PATH', usernameVariable: 'NEW_USER_LOGIN'),
                            string(credentialsId: params.BECOME_PASSWORD_CREDENTIALS_ID, variable: 'BECOME_PASSWORD')
                    ]) {
                        dir('ansible') {
                            ansiblePlaybook playbook: 'addUser.yaml',
                                    inventory: "${params.HOSTNAME},",
                                    credentialsId: params.EXISTS_ACCOUNT_CREDENTIALS_CREDENTIALS_ID,
                                    extraVars: [
                                            USERNAME         : "$NEW_USER_LOGIN",
                                            KEY_FILE_PATH    : "$KEY_FILE_PATH",
                                            HOSTNAME         : params.HOSTNAME,
                                            ansible_sudo_pass: "$BECOME_PASSWORD",
                                    ],
                                    colorized: true
                        }
                }
            }
        }
    }
    post {
        // Clean after build
        always {
            cleanWs()
        }
    }
}