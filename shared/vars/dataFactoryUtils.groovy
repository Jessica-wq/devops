import groovy.transform.Field
import org.porvenir.pipelineUtility
import org.porvenir.dataFactoryUtility

@Field def pipelineUtil = new pipelineUtility()
@Field def dataFactoryUtil = new dataFactoryUtility()

/**********************************************************************************************************
*Metodo encargado de realizar el Login a Azure segun el ambiente indicado.
*Paramatros:
    - project: String, nombre del proyecto que se desea consultar
    - environment: String, nombre del ambiente que se desea consultar
    - component: String, nombre del componente que se desea consultar
***/
def dataFatoryLogin(Map pipelineParams = [:]){

    dataFactoryUtil.azlogin(
        project: pipelineParams.project,
        environment: pipelineParams.environment
    )

    pipelineParams.component == 'pipeline' ? dataFactoryUtil.installEnvironmentForPipelineComponent() : ""
}

/**********************************************************************************************************
*Metodo que devuelve el listado de componetes desplegado segun el ambiente de DataFactory consultado.
*Paramatros:
    - project: String, nombre del proyecto que se desea consultar
    - environment: String, nombre del ambiente que se desea consultar
    - component: String, nombre del componente que se desea consultar (ej: pipeline, dataset, trigger, data-flow)
***/
def getDataFatoryComponents(Map pipelineParams = [:]){

    def factoryName = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.environment,
        object: 'factory'
    )

    def resourceGroup = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.environment,
        object: 'resourceGroup'
    )

    def components=sh(returnStdout: true, script: "az datafactory ${pipelineParams.component} list --factory-name ${factoryName} --resource-group ${resourceGroup} --query [*].name")
            
    //Transforma String a ArrayList
    componentsArrayList = components.replaceAll("\\[","").replaceAll("]","").replaceAll('"','\'').split(",")
    return componentsArrayList
}

/**********************************************************************************************************
*Metodo que enruta a las actividades de consulta y despliegue de cada uno de los componentes de DataFactory.
*Paramatros:
    - project: String, nombre del proyecto que se desea consultar
    - component: String, nombre del componente que se desea desplegar (ej: pipeline, dataset, trigger, data-flow)
    - nombreComponente: String, nombre del componete que se desea desplegar
    - ambienteOrigen: String, nombre del ambiente de origen
    - ambienteDestino: String, nombre del ambiente de destino
***/
def migrateDataFactoryComponent(Map pipelineParams = [:]){

    switch(pipelineParams.componente) {
        case "pipeline":
            println "Desplegando pipeline."
            migrateDataFactoryPipeline(
                project: pipelineParams.project,
                componente: pipelineParams.componente,
                nombreComponente: pipelineParams.nombreComponente,
                ambienteOrigen: pipelineParams.ambienteOrigen,
                ambienteDestino: pipelineParams.ambienteDestino
            )
            break
        case "dataset":
            println "Desplegando dataset"
            migrateDataFactoryDataset(
                project: pipelineParams.project,
                componente: pipelineParams.componente,
                nombreComponente: pipelineParams.nombreComponente,
                ambienteOrigen: pipelineParams.ambienteOrigen,
                ambienteDestino: pipelineParams.ambienteDestino
            )
            break
        case "trigger":
            println "Desplegando trigger"
            migrateDataFactoryTrigger(
                project: pipelineParams.project,
                componente: pipelineParams.componente,
                nombreComponente: pipelineParams.nombreComponente,
                stopTrigger: pipelineParams.stopTrigger,
                ambienteOrigen: pipelineParams.ambienteOrigen,
                ambienteDestino: pipelineParams.ambienteDestino
            )
            break
        case "data-flow":
            println "Desplegando data-flow"
            migrateDataFactoryDataFlow(
                project: pipelineParams.project,
                componente: pipelineParams.componente,
                nombreComponente: pipelineParams.nombreComponente,
                ambienteOrigen: pipelineParams.ambienteOrigen,
                ambienteDestino: pipelineParams.ambienteDestino
            )
            break
        default:
            println "El componente indicado no tiene configurada opcion de despliegue."
            break
    }
}

/**********************************************************************************************************
*Metodo que realiza la consulta y despliegue de los componentes tipo pipeline de DataFactory.
*Paramatros:
    - project: String, nombre del proyecto que se desea consultar
    - component: String, nombre del componente que se desea desplegar (ej: pipeline, dataset, trigger, data-flow)
    - nombreComponente: String, nombre del componete que se desea desplegar
    - ambienteOrigen: String, nombre del ambiente de origen
    - ambienteDestino: String, nombre del ambiente de destino
***/
def migrateDataFactoryPipeline(Map pipelineParams = [:]){
    //Busca componente a migrar y crea una copia del mismo en un archivo json.
    def resourceGroup = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteOrigen,
        object: 'resourceGroup'
    )

    def factoryName = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteOrigen,
        object: 'factory'
    )

    def suscription = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteOrigen,
        object: 'suscription'
    )

    sh """
        az account set --subscription ${suscription}
        az datafactory ${pipelineParams.componente} show --name "${pipelineParams.nombreComponente}" --factory-name "${factoryName}" --resource-group "${resourceGroup}" -o json > ${pipelineParams.nombreComponente}.json
        ls -la
    """

    //Basado en el archivo json previamente guardado, se realiza la migracion del componente a un nuevo ambiente.
    resourceGroup = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteDestino,
        object: 'resourceGroup'
    )

    factoryName = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteDestino,
        object: 'factory'
    )

    suscription = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteDestino,
        object: 'suscription'
    )

    sh """
        ls -la
        az account set --subscription ${suscription}
        az datafactory ${pipelineParams.componente} create --factory-name "${factoryName}" --pipeline ${pipelineParams.nombreComponente}.json --name "${pipelineParams.nombreComponente}" --resource-group "${resourceGroup}"
    """
}

/**********************************************************************************************************
*Metodo que realiza la consulta y despliegue de los componentes tipo dataset de DataFactory.
*Paramatros:
    - project: String, nombre del proyecto que se desea consultar
    - component: String, nombre del componente que se desea desplegar (ej: pipeline, dataset, trigger, data-flow)
    - nombreComponente: String, nombre del componete que se desea desplegar
    - ambienteOrigen: String, nombre del ambiente de origen
    - ambienteDestino: String, nombre del ambiente de destino
***/
def migrateDataFactoryDataset(Map pipelineParams = [:]){
    //Busca componente a migrar y crea una copia del mismo en un archivo json.
    def resourceGroup = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteOrigen,
        object: 'resourceGroup'
    )

    def factoryName = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteOrigen,
        object: 'factory'
    )

    def suscription = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteOrigen,
        object: 'suscription'
    )

    sh """
        az account set --subscription ${suscription}
        az datafactory ${pipelineParams.componente} show --name "${pipelineParams.nombreComponente}" --factory-name "${factoryName}" --resource-group "${resourceGroup}" -o json > ${pipelineParams.nombreComponente}_org.json
        jq .properties ${pipelineParams.nombreComponente}_org.json > ${pipelineParams.nombreComponente}.json
        ls -la
    """

    //Basado en el archivo json previamente guardado, se realiza la migracion del componente a un nuevo ambiente.
    resourceGroup = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteDestino,
        object: 'resourceGroup'
    )

    factoryName = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteDestino,
        object: 'factory'
    )

    suscription = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteDestino,
        object: 'suscription'
    )

    sh """
        ls -la
        az account set --subscription ${suscription}
        az datafactory ${pipelineParams.componente} create --properties ${pipelineParams.nombreComponente}.json --factory-name "${factoryName}" --name "${pipelineParams.nombreComponente}" --resource-group "${resourceGroup}"
    """
}

/**********************************************************************************************************
*Metodo que realiza la consulta y despliegue de los componentes tipo trigger de DataFactory.
*Paramatros:
    - project: String, nombre del proyecto que se desea consultar
    - component: String, nombre del componente que se desea desplegar (ej: pipeline, dataset, trigger, data-flow)
    - nombreComponente: String, nombre del componete que se desea desplegar
    - stopTrigger: String, valor para identificar si se debe detener o no el trigger antes del despliegue (ej: 'true', 'false')
    - ambienteOrigen: String, nombre del ambiente de origen
    - ambienteDestino: String, nombre del ambiente de destino
***/
def migrateDataFactoryTrigger(Map pipelineParams = [:]){
    //Busca componente a migrar y crea una copia del mismo en un archivo json.
    def resourceGroup = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteOrigen,
        object: 'resourceGroup'
    )

    def factoryName = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteOrigen,
        object: 'factory'
    )

    def suscription = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteOrigen,
        object: 'suscription'
    )

    sh """
        az account set --subscription ${suscription}
        az datafactory ${pipelineParams.componente} show --name "${pipelineParams.nombreComponente}" --factory-name "${factoryName}" --resource-group "${resourceGroup}" -o json > ${pipelineParams.nombreComponente}.json
        ls -la
    """
    String targetFile = "triggerFile.json"
    createTriggerFile(
        triggerFile: targetFile,
        triggerExportFile: pipelineParams.nombreComponente+".json"
    )

    //Basado en el archivo json previamente guardado, se realiza la migracion del componente a un nuevo ambiente.
    resourceGroup = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteDestino,
        object: 'resourceGroup'
    )

    factoryName = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteDestino,
        object: 'factory'
    )

    suscription = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteDestino,
        object: 'suscription'
    )

    sh """
        az account set --subscription ${suscription}
    """
    if(pipelineParams.stopTrigger.equals('true')){
        sh """
          az datafactory ${pipelineParams.componente} stop --factory-name "${factoryName}" --resource-group "${resourceGroup}" --name "${pipelineParams.nombreComponente}"
        """
    }
    
    sh"""
        ls -la
        az datafactory ${pipelineParams.componente} create --properties ${targetFile} --factory-name "${factoryName}" --name "${pipelineParams.nombreComponente}" --resource-group "${resourceGroup}"
        az datafactory ${pipelineParams.componente} start --factory-name "${factoryName}" --resource-group "${resourceGroup}" --name "${pipelineParams.nombreComponente}"
    """
}

/**********************************************************************************************************
*Metodo que construye archivo .json con la logica de despliegue de los componentes Trigger de DataFactory.
*Paramatros:
    - triggerFile: String, nombre del archivo que va a construir el metodo
    - triggerExportFile: String, nombre del archivo exportado que tiene toda la logical del Trigger
***/
def createTriggerFile(Map pipelineParams){
    String targetFile = "${pipelineParams.triggerFile}"
    String triggerFileAZ = "${pipelineParams.triggerExportFile}"
    
    def triggerName=sh(returnStdout: true, script: "jq .name ${triggerFileAZ}")
    def triggerType=sh(returnStdout: true, script: "jq .properties.type ${triggerFileAZ}")
    def triggerRecurrence=sh(returnStdout: true, script: "jq .properties.recurrence ${triggerFileAZ}")
    def triggerPipelines=sh(returnStdout: true, script: "jq .properties.pipelines ${triggerFileAZ}")
    
    sh """
       echo "{\\"name\\": #NAME,\\"type\\": #TYPE,\\"typeProperties\\": {\\"recurrence\\": #RECURRENCE},\\"pipelines\\": #PIPELINES}" > ${targetFile}
       chmod a+rw ${targetFile}
    """

    String archivoTrigger = readFile "${targetFile}"
    archivoTrigger=archivoTrigger.replaceAll("#NAME","${triggerName}")
    archivoTrigger=archivoTrigger.replaceAll("#TYPE","${triggerType}")
    archivoTrigger=archivoTrigger.replaceAll("#RECURRENCE","${triggerRecurrence}")
    archivoTrigger=archivoTrigger.replaceAll("#PIPELINES","${triggerPipelines}")
    
    writeFile file:"${targetFile}", text: archivoTrigger
 }

/**********************************************************************************************************
*Metodo que realiza la consulta y despliegue de los componentes tipo DataFlow de DataFactory.
*Paramatros:
    - project: String, nombre del proyecto que se desea consultar
    - component: String, nombre del componente que se desea desplegar (ej: pipeline, dataset, trigger, data-flow)
    - nombreComponente: String, nombre del componete que se desea desplegar
    - ambienteOrigen: String, nombre del ambiente de origen
    - ambienteDestino: String, nombre del ambiente de destino
***/
def migrateDataFactoryDataFlow(Map pipelineParams = [:]){
    //Busca componente a migrar y crea una copia del mismo en un archivo json.
    def resourceGroup = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteOrigen,
        object: 'resourceGroup'
    )

    def factoryName = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteOrigen,
        object: 'factory'
    )

    def suscription = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteOrigen,
        object: 'suscription'
    )

    sh """
        az account set --subscription ${suscription}
        az datafactory ${pipelineParams.componente} show --data-flow-name "${pipelineParams.nombreComponente}" --factory-name "${factoryName}" --resource-group "${resourceGroup}" -o json > ${pipelineParams.nombreComponente}.json
        ls -la
    """
    
    String targetFile = "dataflowFile.json"
    createDataFlowFile(
        dataFlowFile: targetFile,
        dataFlowExportFile: pipelineParams.nombreComponente+".json"
    )

    //Basado en el archivo json previamente guardado, se realiza la migracion del componente a un nuevo ambiente.
    resourceGroup = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteDestino,
        object: 'resourceGroup'
    )

    factoryName = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteDestino,
        object: 'factory'
    )

    suscription = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.ambienteDestino,
        object: 'suscription'
    )

    sh """
        ls -la
        az account set --subscription ${suscription}
        az datafactory ${pipelineParams.componente} create --factory-name "${factoryName}" --resource-group "${resourceGroup}" --flow-type "MappingDataFlow" --data-flow-name "${pipelineParams.nombreComponente}" --properties ${targetFile}
    """
}

/**********************************************************************************************************
*Metodo que construye archivo .json con la logica de despliegue de los componentes Data-Flow de DataFactory.
*Paramatros:
    - triggerFile: String, nombre del archivo que va a construir el metodo
    - triggerExportFile: String, nombre del archivo exportado que tiene toda la logical del Data-Flow
***/
def createDataFlowFile(Map pipelineParams){
    String targetFile = "${pipelineParams.dataFlowFile}"
    String dataFlowFileAZ = "${pipelineParams.dataFlowExportFile}"
    
    def name=sh(returnStdout: true, script: "jq .name ${dataFlowFileAZ}")
    def targetFolder=sh(returnStdout: true, script: "jq .properties.folder.name ${dataFlowFileAZ}")
    def sinks=sh(returnStdout: true, script: "jq .properties.sinks ${dataFlowFileAZ}")
    def sources=sh(returnStdout: true, script: "jq .properties.sources ${dataFlowFileAZ}")
    def transformations=sh(returnStdout: true, script: "jq .properties.transformations ${dataFlowFileAZ}")
    
    sh """
       echo "{\\"name\\": #NAME,\\"folder\\": {\\"name\\": #FOLDERTARGET},\\"typeProperties\\": { \\"scriptLines\\": [ " > ${targetFile}
       chmod a+rw ${targetFile}
    """
  	int intScriptLines=sh(returnStdout: true, script: "jq '.properties.scriptLines | length' ${dataFlowFileAZ}") as Integer
	  def scriptLinesContent=""
	  if(intScriptLines == 0){
		  scriptLinesContent=sh(returnStdout: true, script: "jq .properties.script ${dataFlowFileAZ} >> ${targetFile}")
	  } else{
		  scriptLinesContent=sh(returnStdout: true, script: "jq .properties.scriptLines[0] ${dataFlowFileAZ} >> ${targetFile}")
	  }
  
	
    sh """
       echo "], \\"sinks\\": #SINKS ,\\"sources\\": #SOURCES ,\\"transformations\\": #TRANSFORMATIONS }}" >> ${targetFile}
    """

    String archivoTarget = readFile "${targetFile}"
    archivoTarget=archivoTarget.replaceAll("#NAME","${name}")
    archivoTarget=archivoTarget.replaceAll("#FOLDERTARGET","${targetFolder}")
    archivoTarget=archivoTarget.replaceAll("#SINKS","${sinks}")
    archivoTarget=archivoTarget.replaceAll("#SOURCES","${sources}")
    archivoTarget=archivoTarget.replaceAll("#TRANSFORMATIONS","${transformations}")
    
    writeFile file:"${targetFile}", text: archivoTarget
 }
