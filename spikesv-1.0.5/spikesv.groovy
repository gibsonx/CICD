pipeline {
    agent any
    environment {
        ENV = "sit"
        project_name = "spikesv"
    }
    stages{
        stage('Checkout') {
            steps {
                deleteDir()
                //拉取代码
                checkout([$class           : 'GitSCM',
                          branches         : [[name: '*/test']],
                          // doGenerateSubmoduleConfigurations: false,
                          extensions: [[$class: 'CleanBeforeCheckout']],
                          // submoduleCfg: [],
                          userRemoteConfigs: [[credentialsId: '223d2042-c31c-4b52-a1d3-c38168217058', url: 'git@10.32.36.7:train/cango-train.git']]])
                sh 'git rev-parse HEAD > COMMIT_HASH'
            }
        }
        stage('Build') {
            steps {
                dir('cango-train-service/cango-train-service-provider') {
                    withMaven(
                            maven: 'maven3') {
                        sh 'mvn -Dmaven.test.skip=true clean package appassembler:assemble '
                    }
                }
            }
        }
        stage('Package') {
            steps {
                dir('cango-train-service/cango-train-service-provider'){
                    sh 'tar czvf ${project_name}.tar.gz target/spikeSv'
                }
            }
        }
        stage('Docker Build') {
            environment{
                COMMIT_HASH = readFile('COMMIT_HASH').trim()
            }
            steps {
                script {
                    echo COMMIT_HASH
                    dir('cango-train-service/cango-train-service-provider') {
                        sh "docker login -u cn-east-2@9PAH7GNMM6NT34VJS29G -p 716bcb006e930ef21615ee8c2c725e726d765bf3b721c0e9dfa3e88db459bda7 swr.cn-east-2.myhuaweicloud.com"
                        sh "docker build -t swr.cn-east-2.myhuaweicloud.com/${ENV}/${project_name}:${COMMIT_HASH} . && docker push swr.cn-east-2.myhuaweicloud.com/${ENV}/${project_name}:${COMMIT_HASH}"
                    }
                }
            }
        }
        stage('Release') {
            environment{
                COMMIT_HASH = readFile('COMMIT_HASH').trim()
            }
            steps {
                dir('/home/TEST-tomcat/.helm/cache/archive/spikesv') {
                    sh 'count=`helm list | grep ${project_name} | wc -l` && if [ $count -eq 0 ]; then \
                     helm install --name ${project_name} --set image.${project_name}.tag=${COMMIT_HASH} --set image.${project_name}.env=${ENV} .; \
                    else \
                        helm upgrade $project_name --set image.${project_name}.tag=${COMMIT_HASH} --set image.${project_name}.env=${ENV} .; fi'
                }
            }
        }
    }
}
