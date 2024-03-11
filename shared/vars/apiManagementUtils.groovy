import groovy.transform.Field
import org.porvenir.pipelineUtility

@Field def pipelineUtil = new pipelineUtility()

/***********************************************************************************************************
*Script que realiza el despliegue de Api Management
*Variables:
    - proyecto: string, nombre del proyecto (ej: mobile, goya)
    - environment: String, ambiente a desplegar (ej: develop, release, main)
***/

def deployApiManagement(Map pipelineParams = [:]) {
    pipelineUtil.mainStage(stageName: 'deployApiManagement', branch: pipelineParams.branch){

        def subscriptionName = pipelineUtil.infoJson(
            jsonName: 'projects-api-management.json',
            project: pipelineParams.project,
            environment: pipelineParams.branch,
            object: 'suscription'
        )
        def resourceGroup = pipelineUtil.infoJson(
            jsonName: 'projects-api-management.json',
            project: pipelineParams.project,
            environment: pipelineParams.branch,
            object: 'resourceGroup'
        )
        def apiManage = pipelineUtil.infoJson(
            jsonName: 'projects-api-management.json',
            project: pipelineParams.project,
            environment: pipelineParams.branch,
            object: 'apiManage'
        )
        def configFile = pipelineUtil.infoJson(
            jsonName: 'projects-api-management.json',
            project: pipelineParams.project,
            environment: pipelineParams.branch,
            object: 'configFileYaml'
        )
        
        sh """
            dotnet run create --configFile ${configFile}
            ls ARMT/* 
            find . -name '*api.template.json'  >> result.txt
            cat result.txt  
        """

        def String file = readFile 'result.txt'
        file=file.replace('./', '')
        lines = file.readLines()
        sh """
            az login --identity
            az account set --subscription ${subscriptionName}
        """
        for(i in lines){
            sh """
                cat ${configFile}
                cat ${i}
                az deployment group create --resource-group ${resourceGroup} --template-file ${i} --parameters ApimServiceName='${apiManage}'
            """
        }      
    }
}
