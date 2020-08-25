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

def createNamespace (namespace) {
    echo "Creating namespace ${namespace} if needed"

    sh "[ ! -z \"\$(kubectl get ns ${namespace} -o name 2>/dev/null)\" ] || kubectl create ns ${namespace}"
}

/*
    Helm install
 */
def helmInstall (namespace, release, HELM_REPO, IMG_PULL_SECRET, DOCKER_REG, IMAGE_NAME, DOCKER_TAG) {
    echo "Installing ${release} in ${namespace}"

    script {
        release = "${release}-${namespace}"
        sh "helm repo add helm ${HELM_REPO}; helm repo update"
        sh """
            helm upgrade --install --namespace ${namespace} ${release} \
                --set imagePullSecrets=${IMG_PULL_SECRET} \
                --set image.repository=${DOCKER_REG}/${IMAGE_NAME},image.tag=${DOCKER_TAG} helm/acme
        """
        sh "sleep 5"
    }
}

/*
    Helm delete (if exists)
 */
def helmDelete (namespace, release) {
    echo "Deleting ${release} in ${namespace} if deployed"

    script {
        release = "${release}-${namespace}"
        sh "[ -z \"\$(helm ls --short ${release} 2>/dev/null)\" ] || helm delete --purge ${release}"
    }
}

/*
    Run a curl against a given url
 */
def curlRun (url, out) {
    echo "Running curl on ${url}"

    script {
        if (out.equals('')) {
            out = 'http_code'
        }
        echo "Getting ${out}"
            def result = sh (
                returnStdout: true,
                script: "curl --output /dev/null --silent --connect-timeout 5 --max-time 5 --retry 5 --retry-delay 5 --retry-max-time 30 --write-out \"%{${out}}\" ${url}"
        )
        echo "Result (${out}): ${result}"
    }
}

/*
    Test with a simple curl and check we get 200 back
 */
def curlTest (namespace, out, ID) {
    echo "Running tests in ${namespace}"

    script {
        if (out.equals('')) {
            out = 'http_code'
        }

        // Get deployment's service IP
        def svc_ip = sh (
                returnStdout: true,
                script: "kubectl get svc -n ${namespace} | grep ${ID} | awk '{print \$3}'"
        )

        if (svc_ip.equals('')) {
            echo "ERROR: Getting service IP failed"
            sh 'exit 1'
        }

        echo "svc_ip is ${svc_ip}"
        url = 'http://' + svc_ip

        curlRun (url, out)
    }
}

