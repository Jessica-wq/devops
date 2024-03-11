import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.transform.Field
import org.porvenir.pipelineUtility
import org.porvenir.kubeUtility
import java.text.SimpleDateFormat;

@Field def pipelineUtil = new pipelineUtility()
@Field def kubeUtil = new kubeUtility()

/**********************************************************************************************************
*Metodo que crea una etapa de APROBACION dependiendo si el ambiente es productivo
*Paramatros:
    - branch: string con la rama
***/
def deployProduction(Map pipelineParams) {
    if (pipelineParams.branch == "main"){
        timeout(time: 24, unit: 'HOURS'){
            stage('Aprobacion'){
                def despliegueProd = 
                input id: 'Deploy', 
                message: 'Despliegue en PROD', 
                submitter: 'admin',
                description: 'Necesita aprobación de un admin para desplegar'
            }
        }
    }
}

/**********************************************************************************************************
*Metodo que crea una etapa de APROBACION dependiendo si el ambiente es productivo
 (Aplicado a los ambientes destino de DataFactory)
*Paramatros:
    - ambienteDestino: string con el ambiente destino del despliegue
***/
def deployProductionDataFactory(Map pipelineParams) {
    def targetEnv = pipelineParams.ambienteDestino
    if (pipelineParams.ambienteDestino == "PRD" || pipelineParams.ambienteDestino == "DRP"){
        targetEnv = "main"
    }
    deployProduction(branch: targetEnv)
}

/***********************************************************************************************************
    metodo para generar metricas pipeline jenkins
    Parametros:
     -  state -> estado del pipeline (real,test) si es un despliegue real o de prueba
     -  target -> conexion influxdb al bucket correspondiente a guardar la informacion

*/
def dataInfluxdb(Map pipelineParams = [:]){
  def myFields1 = [:]
  def myCustomMeasurementFields = [:]
  def userId = pipelineUtil.getBuildUser()
  def numero= "${BUILD_NUMBER }"
  def result = "${currentBuild.result}"
  def fortifyState= env.fortifyState
  def sonarqubeState= env.sonarqubeState
  def nowDate = new Date()
      nowDate = nowDate.format("yyyy-MM-dd HH:mm:ss.SSS", TimeZone.getTimeZone('America/Bogota'))
  myFields1['execute'] = result
  def duracion = "${currentBuild.durationString.minus(' and counting')}"



  myCustomMeasurementFields['dataPipeline'] = myFields1
  myTags = ['dataPipeline':['Fecha': nowDate ,'Usuario': userId,'rama': env.environment , 'duracionPipeline': duracion, 'result': result , 'estado': pipelineParams.state, 'numero_job': numero , 'fortifyState': fortifyState,'sonarqubeState': sonarqubeState ]]
  influxDbPublisher(selectedTarget: pipelineParams.target, customDataMap: myCustomMeasurementFields, customDataMapTags: myTags)

}

/**********************************************************************************************************
*Metodo para obtener el nodo de un archivo yaml consultando por un atributo y valor en especifico
*Parametros:
    - maps: map, listado de atributos donde se va a realizar la busqueda  
    - elementNode: string, nombre del atributo que se desea buscar
    - valueElement: string, valor del atributo que se desea encontrar
***/
def getYamlNode(maps, elementNode, valueElement){
    for (map in maps){
        if(map[elementNode] == valueElement){
            return map
        }
    }
}


/*
 metodo para enviar notificacion 
 recibe los siguentes parametros:
  * email = "user@porvenir.com.co,user@porvenir.com.co"
  * logName = "nombre_archivo.txt"
*/
def notification (Map pipelineParams = [:]){
   // def jobname = currentBuild.fullDisplayName
    def state = currentBuild.result
    def subject_msg= "${JOB_NAME} - # ${BUILD_NUMBER} - ${state}!"
    env.user =  pipelineUtil.getBuildUser()   
    def nowDate = new Date()
      env.DATENOW = nowDate.format("yyyy-MM-dd HH:mm:ss.SSS", TimeZone.getTimeZone('America/Bogota'))      
    emailext body:'''${SCRIPT, template="email-html.template"}''', subject: subject_msg , to: pipelineParams.email, attachLog: true, attachmentsPattern: pipelineParams.logName

}

/*
  metodo que ejecuta robot de continustesting
*/
def robotExecute(runner) {
    stage("Ejecucion robot") {
       sh "chmod +x src/main/resources/drivers/chromedriver"
       sh "mvn clean -Dtest=${runner} test verify"
    }
}


/*Metodo que edita pom XMl de las function de maven con los campos
    * name,functionAppName,groupResource, appServicePlanName, region, runtime


*/
def editPomMVN(Map nameParams = [:]) {
    stage('Edit Pom.xml') {
        if (nameParams.configApp != null) {
            def pom = readMavenPom file: nameParams.configApp.rutePom
            pom.name = nameParams.nameProject
            pom.properties.functionAppName = nameParams.configApp.appName
            pom.properties.groupResource = nameParams.configApp.resourceGroup
            pom.properties.appServicePlanName = nameParams.configApp.plan
            pom.properties.region = nameParams.configApp.region
            pom.properties.runtime = nameParams.configApp.runtime
            //metodo para rescribir el pom.xml
            writeMavenPom model: pom
            sh "cat ${nameParams.configApp.rutePom}"
        } else {
             error('config app esta null valida con el admin del sistema')    
        }
    }
}



/*
    metodo que despliegua functiones en azure maven o python.
*/
def azDeployFunc(Map nameParams = [:]) {
  stage('Deploy function') {
    if(nameParams.type != null){
         def subscriptionName = "PORV-DEV"
        if(nameParams.environmet == "release")
        {
            subscriptionName = "PORV-DEV"
        }
        if(nameParams.environmet == "main")
        {
            subscriptionName = "PORV-PRD"
        }
        println "Nombre de la subscripcion elegida: "+subscriptionName
        switch(nameParams.type){
            case 'python':
                sh """
                    az login --identity
                    az account set --subscription ${subscriptionName}
                """ 
                withEnv(["DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1"]){
                    sh "func azure functionapp publish ${nameParams.funcApp} --python"
                }
                break;
            case 'maven':
                withCredentials([string(credentialsId: 'service-principal-devops', variable: 'CLIENT_SECRET')]) {
                    sh """
                        az login --service-principal -u f28b6432-5cb9-4277-962e-f9c14f64b290 -p $CLIENT_SECRET -t 10a76712-94f6-46a2-9155-31bd8b76f937
                        az account set --subscription ${subscriptionName}
                        cd target/azure-functions/${nameParams.name_function} && zip -r ../../../archive.zip ./* && cd -
                        az functionapp deployment source config-zip -g ${nameParams.resource_group} -n ${nameParams.name_function} --src archive.zip
                    """
                }
                
               

                break;
            default:
                error('no se encontro lenguaje para ejecutar revisa el pipeline')
                break;
        }

    }else{
        error('Por favor valida el tipo de funcion a desplegar ya que no se encuentra asociado.')
    }
  }
}

/**********************************************************************************************************
*Metodo que crea una etapa de APROBACION para los despliegues en los ambientes de PT, QA y PRD
*Paramatros:
    - branch: string con la rama
***/
def deployApproval(Map pipelineParams) {
    timeout(time: 24, unit: 'HOURS'){
        stage('Aprobacion'){
            def text = ''
            if (pipelineParams.branch == "develop"){text='Despliegue en PT'}
            if (pipelineParams.branch == "release"){text='Despliegue en QA'}
            if (pipelineParams.branch == "main"){text='Despliegue en PRD'}
            def despliegue = 
                input id: 'Deploy', 
                message: text, 
                submitter: 'admin',
                description: 'Necesita aprobación de un admin para desplegar'
        }
    }
}




def deployInBlobPythonBatch(Map pipelineParams) {
    pipelineUtil.mainStage(stageName: 'DeployArtifact', branch: env.BRANCH_NAME) {
        def blobGoyaAccountName = pipelineUtil.infoJson(
            jsonName: 'project-python-batch.json',
            project: pipelineParams.project,
            environment: pipelineParams.environment,
            object: 'blobGoyaAccountName'
        )
        def blobGoyaAccountKey = pipelineUtil.infoJson(
            jsonName: 'project-python-batch.json',
            project: pipelineParams.project,
            environment: pipelineParams.environment,
            object: 'blobGoyaAccountKey'
        )

        pipelineUtil.withSecretEnv(
            [
                [var: 'BLOB_GOYA_ACCOUNT_NAME', password: "${blobGoyaAccountName}"],
                [var: 'BLOB_GOYA_ACCOUNT_KEY', password: "${blobGoyaAccountKey}"]
            ]
        ){
            sh """
                az login --identity
                az storage blob delete-batch --account-name ${BLOB_GOYA_ACCOUNT_NAME} --account-key ${BLOB_GOYA_ACCOUNT_KEY} -s '${pipelineParams.container}'
                az storage blob upload-batch --account-name ${BLOB_GOYA_ACCOUNT_NAME} --account-key ${BLOB_GOYA_ACCOUNT_KEY} -s ${pipelineParams.path} -d '${pipelineParams.container}'
            """
        }
    }
}


def getKv(Map pipelineParams = [:]) {
    pipelineUtil.mainStage(stageName: 'getKv', branch: env.BRANCH_NAME) {
       def keyVaultDevops = "ZUE-PV-GOY-DVPS-PT-KV"
       def keyVaultGoyaPrd = "AZUE-PV-GOY-PRD-KV01"
        def kv =["AD-GRAPH-GOY",
    "AD-TENANT-ID-GOY", "AD-CLIENT-ID-GOY", "AD-CLIENT-ID-GOY-QA", "AD-CLIENT-ID-GOY-PRD", "LOGIN-MICROSOFT-GOY",
    "URL-REDIRECCION-GOY-QA", "URL-STORAGE-ICON-GOY-QA", "SERVICE-URL-GOY-QA",
    "URL-REDIRECCION-GOY-PT", "URL-STORAGE-ICON-GOY-PT", "SERVICE-URL-GOY-PT",
    "URL-REDIRECCION-GOY-PRD", "URL-STORAGE-ICON-GOY-PRD", "SERVICE-URL-GOY-PRD"]


        def subscriptionName = "PORV-DEV"
        def resourceGroup = "PORV-PT-RG-GOYA"

        sh"""
         az --version
         az login --identity
         """

        for(i=0; i<=kv.size();){
            def its=kv[i].replace("-","_")
            env.AD_GRAPH_GOY= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultDevops} --query value")
            print i
			      i++
            its=kv[i].replace("-","_")
            env.AD_TENANT_ID_GOY= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultDevops} --query value")
             i++
            its=kv[i].replace("-","_")
            env.AD_CLIENT_ID_GOY= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultDevops} --query value")
             i++
            its=kv[i].replace("-","_")
            env.AD_CLIENT_ID_GOY_QA= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultDevops} --query value")
             i++
            its=kv[i].replace("-","_")
            //env.AD_CLIENT_ID_GOY_PRD= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultGoyaPrd} --query value")
            env.AD_CLIENT_ID_GOY_PRD= ""
             i++
            its=kv[i].replace("-","_")
            env.LOGIN_MICROSOFT_GOY= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultDevops} --query value")
             i++
            its=kv[i].replace("-","_")
            env.URL_REDIRECCION_GOY_QA= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultDevops} --query value")
             i++
			      its=kv[i].replace("-","_")
            env.URL_STORAGE_ICON_GOY_QA= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultDevops} --query value")
             i++
            its=kv[i].replace("-","_")
            env.SERVICE_URL_GOY_QA= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultDevops} --query value")
             i++
            its=kv[i].replace("-","_")
            env.URL_REDIRECCION_GOY_PT= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultDevops} --query value")
             i++
            its=kv[i].replace("-","_")
            env.URL_STORAGE_ICON_GOY_PT= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultDevops} --query value")
             i++
			print i
			print kv.size()
            its=kv[i].replace("-","_")
            env.SERVICE_URL_GOY_PT= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultDevops} --query value")
            i++
            its=kv[i].replace("-","_")
            //env.URL_REDIRECCION_GOY_PRD= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultGoyaPrd} --query value")
            env.URL_REDIRECCION_GOY_PRD= ""
            i++
			      its=kv[i].replace("-","_")
            //env.URL_STORAGE_ICON_GOY_PRD= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultGoyaPrd} --query value")
            env.URL_STORAGE_ICON_GOY_PRD= ""
             i++
            its=kv[i].replace("-","_")
            //env.SERVICE_URL_GOY_PRD= sh(returnStdout: true, script: "az keyvault secret show --name ${kv[i]} --vault-name ${keyVaultGoyaPrd} --query value")
            env.SERVICE_URL_GOY_PRD= ""
			break
                  
        }

        echo env.AD_GRAPH_GOY
        echo env.SERVICE_URL_GOY_QA

         

    }
}

def getKvHis(Map pipelineParams = [:]) {
    
    kubeUtil.azlogin(
        project: pipelineParams.project,
        environment: pipelineParams.branch
    )
    
    def kvName = pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.branch,
        object: 'keyvaultName'
    )

    def APP_AHL = sh(returnStdout: true, script: "az keyvault secret show --name APP-AHL --vault-name ${kvName} --query value")
    def RECAPTCHA_KEY = sh(returnStdout: true, script: "az keyvault secret show --name RECAPTCHA-KEY --vault-name ${kvName} --query value")
    def CUATRO = sh(returnStdout: true, script: "az keyvault secret show --name CUATRO --vault-name ${kvName} --query value")
    def LAST = sh(returnStdout: true, script: "az keyvault secret show --name LAST --vault-name ${kvName} --query value")
    def OCP_SERVICES = sh(returnStdout: true, script: "az keyvault secret show --name OCP-SERVICES --vault-name ${kvName} --query value")
    def OCP_SDS_SERVICES = sh(returnStdout: true, script: "az keyvault secret show --name OCP-SDS-AHL --vault-name ${kvName} --query value")

    def envDefaultsContent = """
TKN_DATA=${APP_AHL}
RECAPTCHA_KEY=${RECAPTCHA_KEY}
CUATRO=${CUATRO}
LAST=${LAST}
OCP_SERVICES=${OCP_SERVICES}
OCP_SDS_SERVICES=${OCP_SDS_SERVICES}
    """
    
    writeFile file: '.env.defaults', text: envDefaultsContent.trim()
}

def getKvZta(Map pipelineParams = [:]) {
    
    kubeUtil.azlogin(
        project: pipelineParams.project,
        environment: pipelineParams.branch
    )
    
    def kvName = pipelineUtil.infoJson(
        jsonName: 'projects-resources-kubernetes.json',
        project: pipelineParams.project,
        environment: pipelineParams.branch,
        object: 'keyvaultName'
    )

    def APP_ZTA = sh(returnStdout: true, script: "az keyvault secret show --name APP-ZTA-AUD --vault-name ${kvName} --query value")
    def RECAPTCHA_KEY_SV = sh(returnStdout: true, script: "az keyvault secret show --name RECAPTCHA-SITE-KEY-SUC-VIRTUAL --vault-name ${kvName} --query value")
    def CUATRO = sh(returnStdout: true, script: "az keyvault secret show --name CUATRO --vault-name ${kvName} --query value")
    def LAST = sh(returnStdout: true, script: "az keyvault secret show --name LAST --vault-name ${kvName} --query value")
    def OCP_SERVICES_PUB = sh(returnStdout: true, script: "az keyvault secret show --name OCP-APIM-SUBS-KEY-SUC-VIRTUAL-PUB --vault-name ${kvName} --query value")
    def OCP_SERVICES_PRV = sh(returnStdout: true, script: "az keyvault secret show --name OCP-APIM-SUBS-KEY-SUC-VIRTUAL-PRV --vault-name ${kvName} --query value")

    def envDefaultsContent = """
TKN_DATA=${APP_ZTA}
RECAPTCHA_KEY_SV=${RECAPTCHA_KEY_SV}
CUATRO=${CUATRO}
LAST=${LAST}
OCP_SERVICES_PUB=${OCP_SERVICES_PUB}
OCP_SERVICES_PRV=${OCP_SERVICES_PRV}
    """
    
    writeFile file: '.env.defaults', text: envDefaultsContent.trim()
}

def changeParameters(Map pipelineParams) {
    pipelineUtil.mainStage(stageName: 'Change Parameters', branch: env.BRANCH_NAME) {  
            adGraphGoy = env.AD_GRAPH_GOY
            echo adGraphGoy
            adTenantIdGoy = env.AD_TENANT_ID_GOY
            adClientIdGoy = env.AD_CLIENT_ID_GOY
            loginMicrosoftGoy = env.LOGIN_MICROSOFT_GOY
        
            if (pipelineParams.branch == "release")
            {
                adClientIdGoy = env.AD_CLIENT_ID_GOY_QA
                urlRedireccionGoy = env.URL_REDIRECCION_GOY_QA
                urlStorageIconGoy = env.URL_STORAGE_ICON_GOY_QA
                serviceUrlGoy = env.SERVICE_URL_GOY_QA
            }
            if (pipelineParams.branch == "develop")
            {
                urlRedireccionGoy = env.URL_REDIRECCION_GOY_PT
                urlStorageIconGoy = env.URL_STORAGE_ICON_GOY_PT
                serviceUrlGoy = env.SERVICE_URL_GOY_PT
            }
            if (pipelineParams.branch == "main")
            {
                adClientIdGoy = env.AD_CLIENT_ID_GOY_PRD
                urlRedireccionGoy = env.URL_REDIRECCION_GOY_PRD
                urlStorageIconGoy = env.URL_STORAGE_ICON_GOY_PRD
                serviceUrlGoy = env.SERVICE_URL_GOY_PRD
            }
        
        pipelineUtil.withSecretEnv(
            [
                [var: 'AD_GRAPH_GOY', password: "${adGraphGoy}"],
                [var: 'AD_TENANT_ID_GOY', password: "${adTenantIdGoy}"],
                [var: 'AD_CLIENT_ID_GOY', password: "${adClientIdGoy}"],
                [var: 'LOGIN_MICROSOFT_GOY', password: "${loginMicrosoftGoy}"],
                [var: 'URL_REDIRECCION_GOY', password: "${urlRedireccionGoy}"],
                [var: 'URL_STORAGE_ICON_GOY', password: "${urlStorageIconGoy}"],
                [var: 'SERVICE_URL_GOY', password: "${serviceUrlGoy}"]

            ]
        )
        {
        String archivo = readFile "src/environments/environment.ts"

        def antes = ["AD_GRAPH", "AD_TENANT_ID", "AD_CLIENT_ID", "URL_REDIRECCION", "URL_STORAGE_ICON", "SERVICE_URI", "LOGIN_MICROSOFT"]
        def despues = ["${AD_GRAPH_GOY}", "${AD_TENANT_ID_GOY}", AD_CLIENT_ID_GOY, URL_REDIRECCION_GOY, URL_STORAGE_ICON_GOY, SERVICE_URL_GOY, LOGIN_MICROSOFT_GOY]

        for(i=0; i<antes.size(); i++){
        despues[i] = despues[i].replace('"', '')
        despues[i] = despues[i].replace('\n', '')
        archivo=archivo.replace(antes[i], despues[i])
        }
        writeFile file:'src/environments/environment.ts', text: archivo
        sh "cat src/environments/environment.ts"
        
            //Sed ya no se usa
            /*sh """
                sed -i "s%AD_GRAPH%${AD_GRAPH_GOY}%g" src/environments/environment.ts
                sed -i "s%AD_TENANT_ID%${AD_TENANT_ID_GOY}%g" src/environments/environment.ts
                sed -i "s%AD_CLIENT_ID%${AD_CLIENT_ID_GOY}%g" src/environments/environment.ts
                sed -i "s%URL_REDIRECCION%${URL_REDIRECCION_GOY}%g" src/environments/environment.ts
                sed -i "s%URL_STORAGE_ICON%${URL_STORAGE_ICON_GOY}%g" src/environments/environment.ts
                sed -i "s%SERVICE_URI%${SERVICE_URL_GOY}%g" src/environments/environment.ts
                sed -i "s%LOGIN_MICROSOFT%${LOGIN_MICROSOFT_GOY}%g" src/environments/environment.ts
                cat src/environments/environment.ts
            """*/
        }
    }
}


def zipFiles(Map pipelineParams) {
    zip zipFile: "${pipelineParams.nameArtifact}.zip", archive: false, dir: "${pipelineParams.pathFile}"
}


def deployInBlob(Map pipelineParams) {
    pipelineUtil.mainStage(stageName: 'DeployArtifact', branch: env.BRANCH_NAME) {
        def blobGoyaAccountName = ""
        def blobGoyaAccountKey = ""
        def keyVaultDevops = "ZUE-PV-GOY-DVPS-PT-KV"
        def keyVaultGoyaPrd = "AZUE-PV-GOY-PRD-KV01"

        sh """
        az login --identity
        
        """
        if (pipelineParams.branch == "release")
        {
            
            env.BLOB_GOYA_QA_ACCOUNT_NAME = sh(returnStdout: true, script: "az keyvault secret show --name blob-qa-goya-account-keys --vault-name ${keyVaultDevops} --query value").trim()
            env.BLOB_GOYA_QA_ACCOUNT_KEY = sh(returnStdout: true, script: "az keyvault secret show --name blob-qa-goya-account-key --vault-name ${keyVaultDevops} --query value").trim()
            blobGoyaAccountName = env.BLOB_GOYA_QA_ACCOUNT_NAME	//'azeupvgoyqastacangular' 
            blobGoyaAccountKey = env.BLOB_GOYA_QA_ACCOUNT_KEY //'1k9n6QNBpXcTdjYfDUTIYYbH7MiEpfIL7Lc3MfOBk10L6m4v8gGrZ9Ggkd3/BjtvhxTrX6edJuI/L692X15dHQ==' 
        }
        if (pipelineParams.branch == "develop")
        {
            blobGoyaAccountName =  sh(returnStdout: true, script: "az keyvault secret show --name blob-pt-goya-account-name --vault-name ${keyVaultDevops} --query value").trim() //'azeupvgoyptstacangular' 
            blobGoyaAccountKey = sh(returnStdout: true, script: "az keyvault secret show --name blob-pt-goya-account-key --vault-name ${keyVaultDevops} --query value").trim() //'2DLDrODol6iB7hKyMkOhUU/GPYgqdyV3cjFq42QRGGdMU+9VquhF3yDTIt6jSnl0fd7GFlCsL8S4wSDXCifBgw=='
        }
        if (pipelineParams.branch == "main")
        {
            blobGoyaAccountName = 'azeupvgoyprdstacangular'
            blobGoyaAccountKey = '0eAbpfp1Qq9B2i560c4QMbzkmnSAttgRy6pqOUnloYRCZRITnJuAUwYhpOhTdehWbALxQhDlOkkpeYU6d3m5NA=='
        }
        if (pipelineParams.branch == "DRP")
        {
            //Cambiar valores por valores del ambiente de DRP
            blobGoyaAccountName = 'azeupvgoyprdstacangular'
            blobGoyaAccountKey = '0eAbpfp1Qq9B2i560c4QMbzkmnSAttgRy6pqOUnloYRCZRITnJuAUwYhpOhTdehWbALxQhDlOkkpeYU6d3m5NA=='
        }

       pipelineUtil.withSecretEnv(
            [
                [var: 'BLOB_GOYA_ACCOUNT_NAME', password: "${blobGoyaAccountName}"],
                [var: 'BLOB_GOYA_ACCOUNT_KEY', password: "${blobGoyaAccountKey}"]
            ]
        ){
            sh """
                mv statics dist
                az login --identity

                az storage blob delete-batch --account-name ${BLOB_GOYA_ACCOUNT_NAME} --account-key ${BLOB_GOYA_ACCOUNT_KEY} -s '${pipelineParams.container}'
                az storage blob upload-batch --account-name ${BLOB_GOYA_ACCOUNT_NAME} --account-key ${BLOB_GOYA_ACCOUNT_KEY} -s ${pipelineParams.path} -d '${pipelineParams.container}'
            """
       }
    }
}

def deployResourceInBlob(Map pipelineParams){

  def subscription = pipelineUtil.infoJson(
    jsonName: 'project-blob-storage.json',
    project: "goya",
    environment: pipelineParams.branch,
    object: 'subscription'
  )
  def storageAccountName = pipelineUtil.infoJson(
    jsonName: 'project-blob-storage.json',
    project: "goya",
    environment: pipelineParams.branch,
    object: 'storageAccountName'
  )
  def storageAccountKey = pipelineUtil.infoJson(
    jsonName: 'project-blob-storage.json',
    project: "goya",
    environment: pipelineParams.branch,
    object: 'storageAccountKey'
  )
  
  withCredentials([string(credentialsId: "${storageAccountKey}", variable: 'STORAGE_KEY')]) {
    sh """
        az login --identity
        az account set --subscription ${subscription}
        az storage blob upload --account-name ${storageAccountName} --account-key ${STORAGE_KEY} --container-name ${pipelineParams.container} --file ${pipelineParams.recurso} --name ${pipelineParams.recurso}
        az storage blob list --account-name ${storageAccountName} --account-key ${STORAGE_KEY} -c ${pipelineParams.container} -o table
    """
  }
}

/**********************************************************************************************************
*Metodo para imprimir mensajes en el log con colores usando el plugin AnsiColor
*Paramatros:
    - message: String, mensaje a imprimir en el log
    - type: String, tipo de mensaje:
        Opciones de type:
            - INFO: imprime el mensaje en color azul
            - ERROR: imprime el mensaje en color rojo
            - SUCCESS: imprime el mensaje en color verde
            - WARNING: imprime mensaje en color amarillo 
*/
def printm( message, type){
    def date = new Date()
    def sdf = new SimpleDateFormat("HH:mm:ss")
    def color
    switch(type){
        case "ERROR":
            color = "41"
            break;
        case "INFO":
            color = "46"
            break;
        case "SUCCESS":
            color = "42"
            break;
        case "WARNING":
            color = "43"
            break;
    }
    ansiColor('xterm'){
        println("\033[${color}m********** ${message} ********** ${sdf.format(date)}\033[0m")
    }
}
