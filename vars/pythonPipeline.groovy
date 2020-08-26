def call(String gitUrl, String helmConfig, String appType, String projectName, String configDir, String moduleDir, String libDir, String testCommand, String dockerImage)
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
                    
                    
                    sh "cp ${WORKSPACE}/${configDir}/${REGION}/config_${ENV}_env.py ${WORKSPACE}/${libDir}/config.py"
                    sh "cp ${WORKSPACE}/deploy_config/Dockerfile ${WORKSPACE}/Dockerfile"
                    
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
