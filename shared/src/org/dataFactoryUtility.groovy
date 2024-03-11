package org.porvenir
import org.porvenir.pipelineUtility
import groovy.transform.Field

@Field def pipelineUtil = new pipelineUtility()


/**********************************************************************************************************
*Metodo para realizar el log in a los distintos DataFactory de los proyectos
*Paramatros:
    - project: String, Nombre proyecto (ej: goya, mobile)
    - environment: String, ambiente donde se requiere hacer el log in (main, release, develop, drp)
*El metodo utiliza el metodo infoJson para extraer información del json de los proyectos y asi configurar
*las variables de conexión a los AKS (Ver pipelineUtility.infoJson)
***/
def azlogin(Map pipelineParams = [:]) {

    def subscriptionName = pipelineUtil.infoJson(
        jsonName: 'projects-datafactory.json',
        project: pipelineParams.project,
        environment: pipelineParams.environment,
        object: 'suscription'
    )

    sh """
        az login --identity
        az account set --subscription ${subscriptionName}
    """
}

/**********************************************************************************************************
*Metodo para instalar la extension adecuada para despliegles de pipelines de DataFactory,
 debido a incompatibilidad con otras versiones.
***/
def installEnvironmentForPipelineComponent() {
    //Los comandos funcionan con la version (azure-cli 2.40.0)
    //az upgrade --yes
    sh """
        az --version
        az config set extension.use_dynamic_install=yes_without_prompt
        az extension remove --name datafactory
        az extension add --name datafactory --version 0.6.0
    """
}
