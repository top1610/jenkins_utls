def pythonPineline(String gitUrl, String helmConfig, String appType, String projectName, String configDir, String moduleDir, String libDir, String testCommand, String dockerImage)
{
    pipeline {
    agent any
    environment {
        REGION = "vn"
        ENV = 'test'
    }
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
                                    url: '${gitUrl}'
                                ]
                                ]
                            ]
                            )
                    
                    sh "cp ${WORKSPACE}/${configDir}/${REGION}/config_${ENV}_env.py ${WORKSPACE}/${libDir}/config.py"
                    sh "cp ${WORKSPACE}/deploy_config/Dockerfile ${WORKSPACE}/Dockerfile"
                    
                    script {
                    def appimage = docker.build (dockerImage + ":$BUILD_NUMBER", "--network=host .")
                    docker.withRegistry( 'https://registry.cooky.vn', registryCredential ) {
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
                        sh "cd deploy_config/helm_deploy/"
                        sh "helm install ${projectName} ./${projectName} --set image.repository=${image_id} --set image.tag=$BUILD_NUMBER"
                    }
                }
            }
        }
    }
}
