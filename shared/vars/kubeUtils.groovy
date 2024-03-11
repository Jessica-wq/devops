import groovy.transform.Field
import org.porvenir.kubeUtility
import org.porvenir.pipelineUtility
import java.net.HttpURLConnection
import java.net.URL

@Field def kubeUtil = new kubeUtility()
@Field def pipelineUtil = new pipelineUtility()


/**********************************************************************************************************
*Metodo que permite crear imagen docker en base a un dockerfile y realiza el push al ACR
*Paramatros:
    - registryName: String, nombre del ACR
    - imageName: String, Nombre de la imagen (comunmente se configura con el JenkinsFile.yaml)
    - imageVersion: String, Version de la imagen (comunmente se configura con el metodo riseVersion)
    - dockerfilePath: String, path donde se encuentra el archivo dockerfile dentro del proyecto
***/
def buildPushDocker(Map pipelineParams){
    def tokenACR
    container('azcli'){
        echo"********** Azure LogIn for ACR **********"
        kubeUtil.azLoginAcr()
        echo"**********    Get Access Token    **********"
        tokenACR = kubeUtil.accessTokenACR(
            registryName: pipelineParams.registryName
        )
    }
    container('gradle'){
        echo"********** LogIn ACR with buildah **********"
        kubeUtil.registryLogin(
            registryName: pipelineParams.registryName,
            tokenACR: tokenACR
        )
        echo"********** Construyendo imagen **********"
        kubeUtil.buildImageFromDockerfile(
            registryName: pipelineParams.registryName,
            imageName: pipelineParams.imageName,
            imageVersion: pipelineParams.imageVersion,
            dockerfilePath: pipelineParams.dockerfilePath,
            nodeEnv: pipelineParams.nodeEnv
        )
       
        echo"**********  Push image to ACR  **********"
        kubeUtil.pushToRegistry(
            registryName: pipelineParams.registryName,
            imageName: pipelineParams.imageName,
            imageVersion: pipelineParams.imageVersion
        )
    }
}

/**********************************************************************************************************
*Metodo que permite crear imagen docker en base a un dockerfile y realiza el push al ACR
*Paramatros:
    - businessDomain: String, nombre del dominio de negocio del ACR
    - registryName: String, nombre del ACR
    - imageName: String, Nombre de la imagen (comunmente se configura con el JenkinsFile.yaml)
    - imageVersion: String, Version de la imagen (comunmente se configura con el metodo riseVersion)
    - dockerfilePath: String, path donde se encuentra el archivo dockerfile dentro del proyecto
***/
def buildDockerImgPush(Map pipelineParams){
    //Login Registry
    if(pipelineParams.businessDomain == "embargos"){
      container('gradle'){
        withCredentials([string(credentialsId: 'service-principal-embargos', variable: 'CLIENT_SECRET')]) {
          sh """
              img login ${pipelineParams.registryName} -u ded7bad8-1e71-423b-a37e-b47f5901f952 --password $CLIENT_SECRET 
          """
        }
      }
    }

    //Build and push image
    container('gradle'){
      echo"********** Construyendo imagen **********"
      kubeUtil.buildImageFromDockerfile(
        registryName: pipelineParams.registryName,
        imageName: pipelineParams.imageName,
        imageVersion: pipelineParams.imageVersion,
        dockerfilePath: pipelineParams.dockerfilePath,
        dockerARGS: pipelineParams.dockerARGS
      )
      
      echo"**********  Push image to ACR  **********"
      kubeUtil.pushToRegistry(
        registryName: pipelineParams.registryName,
        imageName: pipelineParams.imageName,
        imageVersion: pipelineParams.imageVersion
      )
    }
}

/**********************************************************************************************************
*Metodo que permite aplicar un recurso deployment en base a un yaml 
*Paramatros:
    - project: String, Nombre del proyecto (ej: goya, mobile)
    - environment: String, Rama en github (main, release, develop, drp)
    - path: String, Path donde se encuentra el yaml del despliegue
    - imageName: String, Nombre de la imagen para reemplazar en el yaml
    - imageVersion: String, Version de la imagen para reemplazar en el yaml
*Este metodo primero realiza un azlogin que se conecta a los distintos AKS de los proyectos
*luego configura la variable namespace con el metodo infoJson (Ver en src/org/porvenir/pipelineUtility)
*reemplaza los valores en el yaml, realiza el kubectl apply -f del yaml del recurso y por ultimo 
*realiza una pausa de 30 segundos para luego imprimir los logs del pod recien desplegado
***/
def deployPod(Map pipelineParams) {
    pipelineUtil.mainStage(stageName: 'DeployArtifact', branch: env.environment) {
      def replicas
      if(pipelineParams.replicasDev != null || pipelineParams.replicasPrd != null ){
          replicas = pipelineParams.replicasDev
          if(pipelineParams.environment == 'main' || pipelineParams.environment == 'drp'){
              replicas = pipelineParams.replicasPrd
          }
        }
        
        kubeUtil.azlogin(
            businessDomain: pipelineParams.businessDomain,
            project: pipelineParams.project,
            environment: pipelineParams.environment
        )

        def namespace = pipelineUtil.infoJson(
            jsonName: 'projects-resources-kubernetes.json',
            project: pipelineParams.project,
            environment: pipelineParams.environment,
            object: 'nsMicroservicios'
        )
        def domainIngress = pipelineUtil.infoJson(
           jsonName: 'projects-resources-kubernetes.json',
           project: pipelineParams.project,
           environment: pipelineParams.environment,
           object: 'domainIngress'
            )
        ansiColor('xterm') {
          validateURL("https://" + domainIngress)
        }
        if(replicas != null){
          sh "sed -i 's/REPLICASET/${replicas}/g' ${pipelineParams.path}"
        }
        sh "sed -i 's/:VERSION/:${pipelineParams.imageVersion}/g' ${pipelineParams.path}"
        sh "sed -i 's/NAME_ARTIFACT/${pipelineParams.imageName}/g' ${pipelineParams.path}"
        sh "sed -i 's/NAMESPACE_AKS/${namespace}/g' ${pipelineParams.path}"
        sh "kubectl apply -f ${pipelineParams.path} -n ${namespace}"        

        if(pipelineParams.type != null && pipelineParams.type == "cj"){
            sh"kubectl get cronjobs -n ${namespace}"
        }else{            
            def deploymentName = kubeUtil.getDeploymentName(pipelineParams.path)
            sleep(30)
            sh "kubectl logs deployment/${deploymentName} -n ${namespace}"
        }
    }
}

def validateURL(String url) {
 try{
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection()
        connection.setRequestMethod("GET")
        connection.connect()

        int responseCode = connection.getResponseCode()
        if (responseCode == 200) {
            println "\u001B[43m\u001B[1m////////// La URL del Dashboard es: ${url} //////////\u001B[0m"
        } else {
            println "\u001B[43m\u001B[1m////////// La URL del Dashboard está caída: ${url} (Código de respuesta: ${responseCode}) //////////\u001B[0m"
         }
     }catch (Exception e){
            println "\u001B[43m\u001B[1m////////// Error al consultar el Dashboard: ${url} (Código de respuesta: ${e}) //////////\u001B[0m"
    }
}
/**********************************************************************************************************
*Metodo que permite aplicar un recurso Ingress en base a un yaml 
*Paramatros:
    - project: String, Nombre del proyecto (ej: goya, mobile)
    - branch: String, Rama en github (main, release, develop, drp)
    - fileIngressRoutes: String, Ruta y nombre donde se encuentra el ingress-routes en formato yaml
*Este metodo primero realiza un azlogin que se conecta a los distintos AKS de los proyectos
*luego configura la variable namespace con el metodo infoJson (Ver en src/org/porvenir/pipelineUtility)
*y por ultimo reemplaza los valores en el yaml y realiza el kubectl apply -f del yaml del recurso
***/
def deployIngress(Map pipelineParams = [:]) {
    kubeUtil.azlogin(
        project: pipelineParams.project,
        environment: pipelineParams.branch
    )
    def namespace = pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.branch,
        object: 'nsIngress'
    )
    def domain = pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.branch,
        object: 'domainIngress'
    )
    def ipLoadBalancer = pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.branch,
        object: 'ipLoadBalancerIngress'
    )
    def secretTLS = pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.branch,
        object: 'secretTLS'
    )
    sh "sed -i 's/NAMESPACE/${namespace}/g' ${pipelineParams.fileIngressRoutes}"
    sh "sed -i 's/DOMAIN_NAME/${domain}/g' ${pipelineParams.fileIngressRoutes}"
    sh "sed -i 's/IP_LOAD_BALANCER/${ipLoadBalancer}/g' ${pipelineParams.fileIngressRoutes}"
    sh "sed -i 's/SECRETTLS/${secretTLS}/g' ${pipelineParams.fileIngressRoutes}"
    sh "kubectl apply -f ${pipelineParams.fileIngressRoutes} -n ${namespace}"
}


/**********************************************************************************************************
*Metodo que permite aplicar un recurso SecretProviderClass en base a un yaml 
*Paramatros:
    - project: String, Nombre del proyecto (ej: goya, mobile)
    - branch: String, Rama en github (main, release, develop, drp)
    - fileSecretProviderClass: String, Ruta y nombre donde se encuentra el secret-provider en formato yaml
*Este metodo primero realiza un azlogin que se conecta a los distintos AKS de los proyectos
*luego configura la variable namespace con el metodo infoJson (Ver en src/org/porvenir/pipelineUtility)
*y por ultimo reemplaza los valores en el yaml y realiza el kubectl apply -f del yaml del recurso
***/
def deploySecretProvider(Map pipelineParams) {
    kubeUtil.azlogin(
        project: pipelineParams.project,
        environment: pipelineParams.branch
    )
    def namespace = pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.branch,
        object: 'nsSecret'
    )
    def userAssignedIdentityID = pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.branch,
        object: 'userAssignedIdentityID'
    )
    def tenantId = pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.branch,
        object: 'tenantId'
    )
    def keyvaultName = pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.branch,
        object: 'keyvaultName'
    )
    sh "sed -i 's/USER_ASSIGNED_IDENTITY/${userAssignedIdentityID}/g' ${pipelineParams.fileSecretProviderClass}"
    sh "sed -i 's/TENANT_ID/${tenantId}/g' ${pipelineParams.fileSecretProviderClass}"
    sh "sed -i 's/KEYVAULT_NAME/${keyvaultName}/g' ${pipelineParams.fileSecretProviderClass}"
    sh "sed -i 's/NAMESPACE/${namespace}/g' ${pipelineParams.fileSecretProviderClass}"
    sh "kubectl apply -f ${pipelineParams.fileSecretProviderClass} -n ${namespace}"

}

/*Metodo que permite aplicar un recurso ConfigMap en base a un yaml */

def deployConfigMap(Map pipelineParams = [:]) {
    kubeUtil.azlogin(
        project: pipelineParams.project,
        environment: pipelineParams.branch
    )
    def namespace = pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.branch,
        object: 'nsSecret'
    )
    sh "kubectl apply -f ${pipelineParams.configMapPath} -n ${namespace}"
}

/**********************************************************************************************************
*Metodo que permite aplicar un recurso Logstash en base a un yaml 
*Paramatros:
    - project: String, Nombre del proyecto (ej: goya, mobile)
    - environment: String, Rama en github (main, release, develop, drp)
*Este metodo primero realiza un azlogin que se conecta a los distintos AKS de los proyectos
*luego configura la variable namespace con el metodo infoJson (Ver en src/org/porvenir/pipelineUtility)
*y por ultimo reemplaza los valores en el yaml y realiza el kubectl apply -f del yaml del recurso
***/

def deployLogstash(Map pipelineParams = [:]) {
    kubeUtil.azlogin(
        project: pipelineParams.project,
        environment: pipelineParams.branch
    )
    def namespace = pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.branch,
        object: 'nsSecret'
    )
    def deploymentName = kubeUtil.getDeploymentName(pipelineParams.logstashPath)

    if(deploymentName != null){
        //Verificar si el despliegue existe
        def deploymentExists = sh(
            returnStdout: true,
            script: "kubectl get deployment ${deploymentName} -n ${namespace} --ignore-not-found"
        ).trim()
    
        if(!deploymentExists.empty){
            //Si despliegue existe eliminarlo y aplicar el nuevo archivo de configuración
            sh "kubectl delete deployment ${deploymentName} -n ${namespace} & kubectl apply -f ${pipelineParams.logstashPath} -n ${namespace}"
        }else{  
            //Si el despliegue no existe, aplicar el nuevo archivo de configuración          
            sh "kubectl apply -f ${pipelineParams.logstashPath} -n ${namespace}"
        }

    }else{
        //Si no se obtuvo un nombre de despliegue válido. aplicar el nuevo archivo de configuración
        sh "kubectl apply -f ${pipelineParams.logstashPath} -n ${namespace}"
    }
}

/**********************************************************************************************************
*Metodo que permite instalar el ingress controller en un AKS especifico  
*Paramatros:
    - project: String, Nombre del proyecto (ej: goya, mobile)
    - branch: String, Rama en github (main, release, develop, drp)
    - urlRegistry: 'azuepvgoydvpsptacr.azurecr.io',
    - controller_image: 'ingress-controller/ingress-nginx/controller',
    - controller_tag: 'v1.2.1',
    - patch_image: 'ingress-controller/ingress-nginx/kube-webhook-certgen',
    - patch_tag: 'v1.1.1',
    - defaultbackend_image: 'ingress-controller/defaultbackend-amd64',
    - defaultbackend_tag: '1.5'
*Este metodo primero realiza un azlogin que se conecta a los distintos AKS de los proyectos
*luego configura la variable namespace con el metodo infoJson (Ver en src/org/porvenir/pipelineUtility)
*y por ultimo ejecuta el comando helm para la instalacion del ingress version 4.4.0
***/
def installIngress( Map pipelineParams ){
    def nsIngress = pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.branch,
        object: 'nsIngress'
    )
    try{
        sh"helm uninstall -n ${nsIngress} ingress-nginx"
    }catch(e){
        println "WARNING: ingress-nginx not found in namespace ${nsIngress}"
    }
    kubeUtil.azlogin(
        project: pipelineParams.project,
        environment: pipelineParams.branch
    )
    println "INSTALLING INGRESS CONTROLLER IN NAMESPACE: ${nsIngress}..."
    sh"""
    helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
    helm repo update
    helm install ingress-nginx ingress-nginx/ingress-nginx \\
    --version 4.4.0 \\
    --namespace ${nsIngress} \\
    --set controller.replicaCount=2 \\
    --set controller.nodeSelector."kubernetes\\.io/os"=linux \\
    --set controller.image.registry=${pipelineParams.urlRegistry} \\
    --set controller.image.image=${pipelineParams.controller_image} \\
    --set controller.image.tag=${pipelineParams.controller_tag} \\
    --set controller.image.digest="" \\
    --set controller.admissionWebhooks.patch.nodeSelector."kubernetes\\.io/os"=linux \\
    --set controller.service.annotations."service\\.beta\\.kubernetes\\.io/azure-load-balancer-health-probe-request-path"=/healthz \\
    --set controller.admissionWebhooks.patch.image.registry=${pipelineParams.urlRegistry} \\
    --set controller.admissionWebhooks.patch.image.image=${pipelineParams.patch_image} \\
    --set controller.admissionWebhooks.patch.image.tag=${pipelineParams.patch_tag} \\
    --set controller.admissionWebhooks.patch.image.digest="" \\
    --set defaultBackend.nodeSelector."kubernetes\\.io/os"=linux \\
    --set defaultBackend.image.registry=${pipelineParams.urlRegistry} \\
    --set defaultBackend.image.image=${pipelineParams.defaultbackend_image} \\
    --set defaultBackend.image.tag=${pipelineParams.defaultbackend_tag} \\
    --set defaultBackend.image.digest="" \\
    -f internal-ingress.yaml \\
    -n ${nsIngress}"""
}



/**********************************************************************************************************
*Metodo que permite crear imagen docker en base a un dockerfile y realiza el push al ACR
*Paramatros:
    - registryName: String, nombre del ACR
    - imageName: String, Nombre de la imagen (comunmente se configura con el JenkinsFile.yaml)
    - imageVersion: String, Version de la imagen (comunmente se configura con el metodo riseVersion)
    - dockerfilePath: String, path donde se encuentra el archivo dockerfile dentro del proyecto
***/
def buildPushDockerAws(Map pipelineParams){
    def tokenECR

    def tokenACR
    container('azcli'){
        echo"********** Azure LogIn for ACR **********"
        kubeUtil.azLoginAcr()
        echo"**********    Get Access Token    **********"
        tokenACR = kubeUtil.accessTokenACR(
            registryName: 'azuepvgoydvpsptacr.azurecr.io'
        )
    }

    container('awscli'){

        def registry = pipelineUtil.infoJson(
            jsonName: 'projects-eks.json',
            project: pipelineParams.project,
            environment: pipelineParams.environment,
            object: 'registry'
        )

        echo"********** aws LogIn for ECR **********"
        kubeUtil.awsLogin(
            awscredentialid: "credentialaws",
            project: pipelineParams.project,
            environment: pipelineParams.environment

        )

        echo"********** LogIn ACR with buildah **********"
        kubeUtil.registryLogin(
            registryName: 'azuepvgoydvpsptacr.azurecr.io',
            tokenACR: tokenACR
        )

        echo"********** Construyendo imagen **********"
        kubeUtil.buildImageFromDockerfile(
            registryName: registry,
            imageName: pipelineParams.imageName,
            imageVersion: pipelineParams.imageVersion,
            dockerfilePath: pipelineParams.dockerfilePath
        )

        sh "img logout azuepvgoydvpsptacr.azurecr.io"

        echo"********** LogIn ECR with img **********"
        kubeUtil.awsLoginEcr(
           project: pipelineParams.project,
           environment: pipelineParams.environment
        )

        echo"**********  Push image to ECR  **********"
        kubeUtil.pushToRegistry(
            registryName: registry,
            imageName: pipelineParams.imageName,
            imageVersion: pipelineParams.imageVersion
        )
    }
}

def loginAws (Map pipelineParams){
        echo"********** aws LogIn for AWSCLI **********"
        kubeUtil.awsLogin(
            awscredentialid: "credentialaws",
            project: pipelineParams.project,
            environment: pipelineParams.environment

        )
}


def awsDeployPod(Map pipelineParams = [:]){
    
        def nameContainer = pipelineParams.imageName.replaceAll("ccm-","").trim()
        def registryName =pipelineUtil.infoJson(
            jsonName: 'projects-eks.json',
            project: pipelineParams.project,
            environment: pipelineParams.environment,
            object: 'registry'
        )

         def namespace =pipelineUtil.infoJson(
            jsonName: 'projects-eks.json',
            project: pipelineParams.project,
            environment: pipelineParams.environment,
            object: 'namespace'
        )
        def region =pipelineUtil.infoJson(
            jsonName: 'projects-eks.json',
            project: pipelineParams.project,
            environment: pipelineParams.environment,
            object: 'region'
        )

        sh "sed -i 's/REGISTRY/${registryName}/g' k8s/deploy-${pipelineParams.imageName}-${pipelineParams.environment}.yaml"
        sh "sed -i 's/PROJECT/${pipelineParams.imageName}/g' k8s/deploy-${pipelineParams.imageName}-${pipelineParams.environment}.yaml"
        sh "sed -i 's/VERSION/${pipelineParams.imageVersion}/g' k8s/deploy-${pipelineParams.imageName}-${pipelineParams.environment}.yaml"
       
        sh "cat k8s/deploy-${pipelineParams.imageName}-${pipelineParams.environment}.yaml"
        sh "aws eks --region ${region} update-kubeconfig --name ccm-cluster"
        sh "kubectl get ns"
        sh "kubectl apply -f k8s/deploy-${pipelineParams.imageName}-${pipelineParams.environment}.yaml -n ${namespace}"
        
    

}


/**********************************************************************************************************
*Metodo que permite aplicar un recurso NameSpace en base a un yaml 
*Paramatros:
    - project: Nombre del proyecto (ej: goya, mobile)
    - branch: Rama en github (main, release, develop, drp)
    - fileName: Ruta y nombre donde se encuentra el recurso AKS en formato yaml
*Este metodo primero realiza un azlogin que se conecta a los distintos AKS de los proyectos
*y por ultimo reemplaza los valores en el yaml y realiza el kubectl apply -f del yaml del recurso
***/
def deployNamespace(Map pipelineParams = [:]) {
    kubeUtil.azlogin(
        project: pipelineParams.project,
        environment: pipelineParams.environment
    )
    sh "kubectl apply -f ${pipelineParams.fileName}"
}


/**********************************************************************************************************
*Metodo que permite aplicar un recurso Dashboard en base a un yaml 
*Paramatros:
    - project: Nombre del proyecto (ej: goya, mobile)
    - branch: Rama en github (main, release, develop, drp)
    - fileName: Ruta y nombre donde se encuentra el recurso AKS en formato yaml
*Este metodo primero realiza un azlogin que se conecta a los distintos AKS de los proyectos
*y por ultimo reemplaza los valores en el yaml y realiza el kubectl apply -f del yaml del recurso
***/
def deployDashboard(Map pipelineParams = [:]) {
    kubeUtil.azlogin(
        project: pipelineParams.project,
        environment: pipelineParams.environment
    )    
    def domainDashboard =pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.environment,
        object: 'domainDashboard'
    )
    sh "sed -i 's/DOMAIN_NAME/${domainDashboard}/g' ${pipelineParams.fileName}"
    sh "kubectl apply -f ${pipelineParams.fileName}"
}
