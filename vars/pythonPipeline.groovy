def call(String gitUrl, String helmConfig, String appType, String projectName, String configDir, String moduleDir, String libDir, String testCommand, String dockerImage)
{
    pipeline {
        agent any  
        stages {
            stage('Prepare') {   
                steps {                    
                    checkout([
                        $class: 'GitSCM', 
                        branches: [[name: '*/test-cicd']], 
                        doGenerateSubmoduleConfigurations: false, 
                        extensions: [[
                            $class: 'SubmoduleOption', 
                            disableSubmodules: false, 
                            parentCredentials: true, 
                            recursiveSubmodules: true,
                            reference: '', 
                            trackingSubmodules: false
                          ]], 
                        submoduleCfg: [], 
                        userRemoteConfigs: [[
                            credentialsId: 'hieupham-cooky-git', 
                            url: "${gitUrl}"
                          ]
                        ]
                      ]
                    )
                    sh """
                        if [ -f ${WORKSPACE}/deploy_config/${ENV}_env/${moduleDir}/.env ] 
                            then export \$(cat ${WORKSPACE}/deploy_config/${ENV}_env/${moduleDir}/.env | sed 's/#.*//g' | xargs) 
                        fi 
                    """
                    //sh "export \$(cat ${WORKSPACE}/deploy_config/${ENV}_env/.env | sed 's/#.*//g' | xargs)"
                    sh 'env'
                    sh "cp ${WORKSPACE}/${configDir}/${REGION}/config_${ENV}_env.py ${WORKSPACE}/${libDir}/config.py"
                    sh "cp ${WORKSPACE}/deploy_config/Dockerfile ${WORKSPACE}/Dockerfile"
                    sh 'sed -i "s+\\[HOST_PORT\\]+$HOST_PORT+g" common/deploy/*'
                    sh 'sed -i "s+\\[NUM_WORKERS\\]+$NUM_WORKERS+g" common/deploy/*'
                    sh 'sed -i "s+\\[DOCKER_SERVICE\\]+$DOCKER_SERVICE+g" common/deploy/*'
                    script {
                        def appimage = docker.build (dockerImage + ":$BUILD_NUMBER", "--network=host .")
                        docker.withRegistry( 'https://registry.cooky.vn', 'hieupham-cooky-git' ) {
                            appimage.push()
                            appimage.push('latest')
                        }
                    }
            
                }
            }
            stage ('Deploy') {
                steps {
                    script{
                        def image_id = dockerImage
                        dir('deploy_config/helm_deploy'){
                            sh "helm install ${projectName} ./${projectName} --set image.repository=${image_id} --set image.tag=$BUILD_NUMBER"
                        }
                    }
                }
            }
        }
    }
}
