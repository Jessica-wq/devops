import groovy.transform.Field
import org.porvenir.kubeUtility
import org.porvenir.awsUtility
import org.porvenir.scanUtility

@Field def kubeUtil = new kubeUtility()
@Field def awsUtil = new awsUtility()
@Field def scanUtil = new scanUtility()


/***********************************************************************************************************
*Metodo que permite realizar el escaneo de seguridad de una imagen docker almacenada en un ACR (Azure container Registry)
*Parametros:
    - registryName: String, nombre del ACR donde se encuentra almacenada la imagen
    - image: String, nombre de la imagen a analizar
    - severity: String, severidades a mostrar en el escaneo (HIGH,CRITICAL,MEDIUM,LOW) por defecto '' las toma todas
    - stop: Booleano, determina si detiene el pipeline (true) cuando encuentre vulnerabilidades o no lo detiene (false)
***/
def scanImageAzure(Map pipelineParams =  [:]){
    def tokenACR
    container('azcli'){
        println "********** Azure LogIn for ACR modificado **********"
        kubeUtil.azLoginAcr()
        println "**********    Get Access Token    **********"
        tokenACR = kubeUtil.accessTokenACR(
            registryName: pipelineParams.registryName
        )
    }
    container('trivy'){
        kubeUtil.registryLoginImg(
            registryName: pipelineParams.registryName,
            tokenACR: tokenACR
        )
        scanUtil.scanTrivy(
          image: pipelineParams.image,
          severity: pipelineParams.severity,
          stop: pipelineParams.stop
        )
    }
}


/***********************************************************************************************************
*Metodo que permite realizar el escaneo de seguridad de una imagen docker almacenada en un ECR (Elastic container Registry)
*Parametros:
    - registryName: String, nombre del ACR donde se encuentra almacenada la imagen
    - image: String, nombre de la imagen a analizar
    - severity: String, severidades a mostrar en el escaneo (HIGH,CRITICAL,MEDIUM,LOW) por defecto '' las toma todas
    - stop: Booleano, determina si detiene el pipeline (true) cuando encuentre vulnerabilidades o no lo detiene (false)
***/
def scanImageAws(Map pipelineParams =  [:]){
    def tokenECR
    container('awscli'){
        println "********** aws LogIn for ECR  **********"
        awsUtil.awsLogin(
          awscredentialid: "credentialaws",
          aws_region: "us-east-1",
          aws_output: "json"
         )
        println "**********    Get Access Token    **********"
        tokenECR = awsUtil.accessTokenECR(
            region: "us-east-1"
        )
    }
    container('trivy'){
        awsUtil.registryLoginImg(
            registryName: pipelineParams.registryName,
            tokenECR: tokenECR
        )
        scanUtil.scanTrivy(
          image: pipelineParams.image,
          severity: pipelineParams.severity,
          stop: pipelineParams.stop
        )
    }
}
