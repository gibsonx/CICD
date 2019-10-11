pipeline {
    agent { label 'master' }
    parameters {
        choice(name:'BRANCH', choices:'develop\nqa', description: '')
    }
    tools {
        maven 'maven3'
        jdk 'jdk12'
    }
    environment {
        ENV = "dev"
        project_name = "tokentubersearch"
        BRANCH = "${params.BRANCH}"
        TAGPREFIX = "${params.BRANCH}".replace("/", "-")
    }
    stages{
        stage('Checkout') {
            steps {
                // deleteDir()
                //拉取代码
                checkout([$class           : 'GitSCM',
                          branches         : [[name: '*/${BRANCH}']],
                          // doGenerateSubmoduleConfigurations: false,
                          //   extensions: [[$class: 'CleanBeforeCheckout']],
                          // submoduleCfg: [],
                          userRemoteConfigs: [[credentialsId: '0de357af-094d-4bd9-9cf5-ff320810d3c2', url: 'berniegao@vs-ssh.visualstudio.com:v3/berniegao/tokentuber-search/tokentuber-search' ]]])
                sh 'git rev-parse HEAD > COMMIT_HASH'
            }
        }
        stage('MavenBuild') {
            steps {
                sh 'mvn -Dmaven.test.skip=true clean install'
            }
        }
        stage('Docker Build') {
            environment{
                COMMIT_HASH = readFile('COMMIT_HASH').trim()
            }
            steps{
                sh "docker login -u tokentuber -p ZycQj0qpa4UfV4IF3bmDDZ6Ue/BvjyXV tokentuber.azurecr.io"
                sh "docker build -t tokentuber.azurecr.io/${project_name}:${TAGPREFIX}-${COMMIT_HASH} . && docker push tokentuber.azurecr.io/${project_name}:${TAGPREFIX}-${COMMIT_HASH}"
            }
        }
        stage('Release') {
            environment{
                COMMIT_HASH = readFile('COMMIT_HASH').trim()

            }
            steps {
                dir('/home/ttengineerqa/cicd-dev/tokentubersearch/helm') {
                    sh 'count=`helm list | grep -w ${project_name}-${ENV} | wc -l` && if [ $count -eq 0 ]; then \
                     helm install --name ${project_name}-${ENV} --set image.tokentubersearch.tag=${TAGPREFIX}-${COMMIT_HASH} .; \
                    else \
                        helm upgrade ${project_name}-${ENV} --set image.tokentubersearch.tag=${TAGPREFIX}-${COMMIT_HASH} . ; fi'
                }
            }
        }
    }
}

