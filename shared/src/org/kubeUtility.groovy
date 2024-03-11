package org.porvenir
import org.porvenir.pipelineUtility
import groovy.transform.Field

@Field def pipelineUtil = new pipelineUtility()


/**********************************************************************************************************
*Metodo para construir imagen docker en base a un dockerfile, utiliza el paquete buildah
*Paramatros:
    - registryName: String, Nombre del ACR
    - imageName: String, Nombre de la imagen a construir
    - imageVersion: String, Version de la imagen
    - dockerfilePath: String, Path donde se encuentra el dockerfile
***/
def buildImageFromDockerfile (Map pipelineParams) {
    stage('Build Image') {
        sh"img build -t ${pipelineParams.registryName}/${pipelineParams.imageName}:${pipelineParams.imageVersion} --build-arg VERSION=${pipelineParams.imageVersion} ${pipelineParams.dockerfilePath} --build-arg ENV_NODE=${pipelineParams.nodeEnv} ${pipelineParams.dockerARGS}"
    }
}


/**********************************************************************************************************
*Metodo que permite enmascarar contraseñas para que NO sean visibles el el log de jenkins
*/
def withSecretEnv(List<Map> varAndPasswordList, Closure closure) {
  wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: varAndPasswordList]) {
    withEnv(varAndPasswordList.collect { "${it.var}=${it.password}" }) {
      closure()
    }
  }
}


/**********************************************************************************************************
*Metodo que permite loguearnos en Azure para tener acceso al ACR
***/
def azLoginAcr() {
        withSecretEnv(
            [
                [var: 'user', password: "devgoya@porvenir.com.co"],
                [var: 'password', password: "Porv3nir#163278"]
            ]
        ){
            sh """
                az login --username ${user} --password ${password}
            """
        }
}


/**********************************************************************************************************
*Metodo que devuelve un string con el access token del ACR
*Paramatros:
    - registryName: String, Nombre del ACR 
***/
def accessTokenACR(Map pipelineParams) {
    azPassword = sh (returnStdout: true, script: "az acr login --name ${pipelineParams.registryName} --expose-token | jq -r '.accessToken'").trim()
    return azPassword
}


/**********************************************************************************************************
*Metodo para realizar el log in al Registry (ACR), utiliza la herramienta buildah
*Paramatros:
    - registryName: String, Nombre del ACR 
    - tokenACR: String, Token de acceso al ACR (Ver metodo accessTokenACR)
***/
def registryLogin(Map pipelineParams){
    withCredentials([usernamePassword(credentialsId: 'az-login-goya', usernameVariable: 'azUsername', passwordVariable: 'azPassword')]) {
        sh"img login -u ${azUsername} -p ${pipelineParams.tokenACR} ${pipelineParams.registryName}"
    }
}


/**********************************************************************************************************
*Metodo para realizar el log in al Registry (ACR), utiliza la herramienta img
*Paramatros:
    - registryName String,: Nombre del ACR 
    - tokenACR: String, Token de acceso al ACR (Ver metodo accessTokenACR)
***/
def registryLoginImg(Map pipelineParams){
    withCredentials([usernamePassword(credentialsId: 'az-login-goya', usernameVariable: 'azUsername', passwordVariable: 'azPassword')]) {
         wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [[password: "${pipelineParams.tokenACR}", var: 'PSWD']]]) {
           sh "echo '${pipelineParams.tokenACR}'|img login -u $azUsername --password-stdin ${pipelineParams.registryName}"
         }
    }
}


/**********************************************************************************************************
*Metodo para realizar el push de la imagen al ACR, utiliza la herramienta buildah
*Paramatros:
    - registryName: String, Nombre del ACR 
***/
def pushToRegistry(Map pipelineParams) {
    pipelineUtil.mainStage(stageName: 'PushToRegistry', branch: env.environment) {
        sh"img push ${pipelineParams.registryName}/${pipelineParams.imageName}:${pipelineParams.imageVersion}"
    }
}


/**********************************************************************************************************
*Metodo para realizar el log in a los distintos AKS de los proyectos
*Paramatros:
    - project: String, Nombre proyecto (ej: goya, mobile)
    - environment: String, ambiente donde se requiere hacer el log in (main, release, develop, drp)
*El metodo utiliza el metodo infoJson para extraer información del json de los proyectos y asi configurar
*las variables de conexión a los AKS (Ver pipelineUtility.infoJson)
***/
def azlogin(Map pipelineParams = [:]) {

    def subscriptionName = pipelineUtil.infoJson(
        jsonName: 'projects-aks.json',
        project: pipelineParams.project,
        environment: pipelineParams.environment,
        object: 'suscription'
    )
    def resourceGroup = pipelineUtil.infoJson(
        jsonName: 'projects-aks.json',
        project: pipelineParams.project,
        environment: pipelineParams.environment,
        object: 'resourceGroup'
    )
    def aksName = pipelineUtil.infoJson(
        jsonName: 'projects-aks.json',
        project: pipelineParams.project,
        environment: pipelineParams.environment,
        object: 'nameAks'
    )

    if(pipelineParams.businessDomain == "embargos"){
        withCredentials([usernamePassword(credentialsId: 'sp-embargos', usernameVariable: 'userSPembargos', passwordVariable: 'passSPembrgos')]) {
        sh """
          az login --service-principal -u ${userSPembargos} -p ${passSPembrgos} -t cd403a65-991b-4445-ab10-3960197144c1
        """
        }
    }else{
        sh """
          az login --identity
        """
    }

    sh """
      az account set --subscription ${subscriptionName}
      az aks get-credentials --resource-group ${resourceGroup} --name ${aksName}
    """
}


/**********************************************************************************************************
*Metodo que devuelve el nombre del deployment leyendo el yaml, usado para imprimir los logs una vez desplegado
*Paramatros:
    - file: String, ruta donde se encuentra el archivo .yaml del deployment
*El metodo utiliza el metodo readYaml y luego devuelve el nombre del deployment filtrando por kind=Deployment
***/
def getDeploymentName(String file){
    def yaml = readYaml file: file
    def request
    yaml.each{ resource ->
        if(resource.get('kind') == 'Deployment'){
            request = resource.get('metadata').name
        }
    }
    return request
}

def getLastTagVersionImage(Map config) {
    def tag_version = "0.0.0"
    tag_version = sh(returnStdout: true, script: "az acr repository show-manifests -n ${config.acrName} --repository ${config.artifactName} --top 1 --orderby time_desc | jq .[0].tags[] -r")
    echo "TAG VERSION: ${tag_version}"
    return tag_version.trim()
}


/*
    Metodo para el logueo a aws con el user IAM asignado  
*/

def awsLogin(Map pipelineParams= [:]){

    def region = pipelineUtil.infoJson(
        jsonName: 'projects-eks.json',
        project: pipelineParams.project,
        environment: pipelineParams.environment,
        object: 'region'
    )

    withCredentials([[
    $class: 'AmazonWebServicesCredentialsBinding',
    credentialsId: pipelineParams.awscredentialid,
    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
    ]]) {
        script{
            env.AWS_ACCESS_KEY_ID = AWS_ACCESS_KEY_ID
            env.AWS_SECRET_ACCESS_KEY = AWS_SECRET_ACCESS_KEY
            env.AWS_REGION = region
            env.AWS_DEFAULT_OUTPUT = 'json'
        
        }
    }
    
}



def awsLoginEcr(Map pipelineParams= [:]){
   def region = pipelineUtil.infoJson(
        jsonName: 'projects-eks.json',
        project: pipelineParams.project,
        environment: pipelineParams.environment,
        object: 'region'
    )

    def registry = pipelineUtil.infoJson(
        jsonName: 'projects-eks.json',
        project: pipelineParams.project,
        environment: pipelineParams.environment,
        object: 'registry'
    )

    sh "aws ecr get-login-password --region ${region}|img login -u AWS --password-stdin ${registry}"
         
}
