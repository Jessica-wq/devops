def tokenEditFile(def token){
    stage('Edit token file'){
        wrap([$class: 'MaskPasswordsBuildWrapper',
        varPasswordPairs: [[password: token]]]) {
            sh "cat providers.tf"
            configFileProvider([configFile(fileId: 'file.yaml', targetLocation: '.')]) {
                sh "ansible-playbook --extra-vars \"ext_token=${token}\" file.yaml"
            }
            sh "cat providers.tf"
        }
    }
    
}
// Metodo para obtener el token del storage account y editar en el providers.tf para la externalización de estados,para  terraform state
def storageToken(){
    def subscriptionName = "PORV-PRD"
    def storageAccount= "azuepvdevopsprdtfstates"
    def resourceGroup= "RG-PRD-PORV-DEVOPS"
    def token=""
    stage("storage token"){
            sh """
                az login --identity
                az account set --subscription ${subscriptionName}
            """
            token = sh(script: "az storage account keys list -g ${resourceGroup} -n ${storageAccount} |jq -r 'map( select(.keyName | startswith(\"key1\") ) )|.[].value'", returnStdout: true).trim()
     }
    
    return token
}

def terraformDeploy (Map pipelineParams = [:]){
    
    
    stage('Terraform PLAN'){
        withCredentials([usernamePassword(credentialsId: pipelineParams.credentialsId, passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_OWNER')]) {
            withEnv(["GITHUB_TOKEN=${GITHUB_TOKEN}", "GITHUB_OWNER=${GITHUB_OWNER}"]) {
                sh """
                    terraform init
                    terraform plan
                """
            }
        }
    }

    stage('Aprobacion Terraform'){
        def despliegueProd = 
        input id: 'Deploy', 
        message: 'Aprovisionamiento terraform', 
        submitter: 'admin, admin-goya',
        description: 'Revisa el terraform plan si estas de acuerdo para proseguir.'
    }

    stage("Deploy Terraform "){
        withCredentials([usernamePassword(credentialsId: pipelineParams.credentialsId, passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_OWNER')]) {
            withEnv(["GITHUB_TOKEN=${GITHUB_TOKEN}", "GITHUB_OWNER=${GITHUB_OWNER}"]) {
              
                    sh "terraform apply -auto-approve"
                
            }
        }
       
    }
}
