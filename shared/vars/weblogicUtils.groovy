import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.transform.Field
import org.porvenir.pipelineUtility
import org.porvenir.kubeUtility

@Field def pipelineUtil = new pipelineUtility()
@Field def kubeUtil = new kubeUtility()
@Field def contsh = 0

def getData(Map pipelineParams){
    def value = pipelineUtil.infoJson(
        jsonName: 'project-weblogic.json',
        project: pipelineParams.project,
        environment: pipelineParams.environment,
        object: pipelineParams.object
    )
    return value
}

def download (Map pipelineParams) {
  withCredentials([usernamePassword(credentialsId: 'artifactory-cloud', passwordVariable: 'pass', usernameVariable: 'user')]) {
    sh """
        curl -k -u ${user}:${pass} -O https://devops.porvenir.com/artifactory/${pipelineParams.project}/${pipelineParams.groupId}/${pipelineParams.artifactId}/${pipelineParams.version}/${pipelineParams.nameArtifact}.${pipelineParams.extension}
    """
  }
}

def validateStatus (Map pipelineParams) {

  def ip = getData(
      project: pipelineParams.project,
      environment: pipelineParams.environment,
      object: 'ip'
    )
  def port = getData(
      project: pipelineParams.project,
      environment: pipelineParams.environment,
      object: 'port'
    )
  def idUserWeblogic = getData(
      project: pipelineParams.project,
      environment: pipelineParams.environment,
      object: 'idUserWeblogic'
    )

  withCredentials([usernamePassword(credentialsId: idUserWeblogic, passwordVariable: 'pass', usernameVariable: 'user')]) {             
    sh """
      curl -v -o status.json -u ${user}:${pass} -H X-Requested-By:MyClient -H Accept:application/json -X GET http://${ip}:${port}/management/wls/latest/changeManager
    """
  }
  statusFile = readJSON file: 'status.json'
  state = statusFile.item['locked']
  if (state == 'false'){
    error('Por favor aplique cambios pendientes en la consola y vuelva a ejecutar')
  }
}

def shutdownStart (Map pipelineParams) {
  def clusters = []
  pipelineParams.infoComponent.each { component ->
    dominio = component.key
    if (params."${dominio}" != 'default_option'){
      clusters.add(component.value[0])
    }
  }

  if (clusters){
    def servers = []
    clusters.each{ cluster -> 
      servers.add(pipelineParams.infoServers.get(cluster))
    }
    servers = servers.flatten().unique()
    proyecto = pipelineParams.project
    ambiente = pipelineParams.environment
    operation = pipelineParams.operation
    operationShutdownStart(proyecto, ambiente, operation, servers)
  }
}

def operationShutdownStart (proyecto, ambiente, operation, servers) {

  def ip = getData(
      project: proyecto,
      environment: ambiente,
      object: 'ip'
    )
  def port = getData(
      project: proyecto,
      environment: ambiente,
      object: 'port'
    )
  def idUserWeblogic = getData(
      project: proyecto,
      environment: ambiente,
      object: 'idUserWeblogic'
    )

  withCredentials([usernamePassword(credentialsId: idUserWeblogic, passwordVariable: 'pass', usernameVariable: 'user')]) {
    servers.each { server ->
      if (operation == 'shutdown'){
        sh """
          curl -v -u ${user}:${pass} -H X-Requested-By:MyClient -H Accept:application/json -X POST http://${ip}:${port}/management/wls/latest/servers/id/${server}/shutdown?force=true
        """
      }
      if (operation == 'start'){
        sh """
          curl -v -u ${user}:${pass} -H X-Requested-By:MyClient -H Accept:application/json -X POST http://${ip}:${port}/management/wls/latest/servers/id/${server}/start?__detached=true
        """
      }
    }
  }
  servers.each { server ->
    validStopStart(server,operation, ip, port)
  }
}

def validStopStart (server, operation, ip, port) {

  def idUserWeblogic = getData(
      project: proyecto,
      environment: ambiente,
      object: 'idUserWeblogic'
    )

  println "Validando operación de ${operation}"

  withCredentials([usernamePassword(credentialsId: idUserWeblogic, passwordVariable: 'pass', usernameVariable: 'user')]) {
    sh """
      curl -v -o resultStartStop.json -u ${user}:${pass} -H X-Requested-By:MyClient -H Accept:application/json -X GET http://${ip}:${port}/management/wls/latest/servers/id/${server}
    """
  }
  result = readJSON file: 'resultStartStop.json'
  status = result.item['state']
  if (operation == 'shutdown') {
    if (status == 'running' || status == 'failed') {
      error("El servidor ${server} no se apago correctamente")
    }else if (status == 'force shutting down') {
      println "El servidor ${server} se esta apagando"
      contsh += 1
      if (contsh == 3){
        error("El servidor ${server} no se apago correctamente")
      }
      sleep (10)
      validStopStart(server,operation)
    }else if (status == 'shutdown') {
      println "El servidor ${server} se apago correctamente"
    }
  }
  contsh = 0
  if (operation == 'start') {
    if (status == 'shutdown' || status == 'failed') {
      error("El servidor ${server} no se apago correctamente")
    }else if (status == 'starting') {
      println "El servidor ${server} esta esta iniciando"
      sleep (30)
      validStopStart(server,operation)
    }else if (status == 'running') {
      println "El servidor ${server} se inicio correctamente"
    }
  }
}

def deploy (Map pipelineParams) {

  def ip = getData(
      project: pipelineParams.project,
      environment: pipelineParams.environment,
      object: 'ip'
    )
  def port = getData(
      project: pipelineParams.project,
      environment: pipelineParams.environment,
      object: 'port'
    )
  def idUserWeblogic = getData(
      project: pipelineParams.project,
      environment: pipelineParams.environment,
      object: 'idUserWeblogic'
    )

  withCredentials([usernamePassword(credentialsId: idUserWeblogic, passwordVariable: 'pass', usernameVariable: 'user')]) {
    sh """
      curl -v -o resultDeploy.json -u ${user}:${pass} -H X-Requested-By:MyClient -H Accept:application/json -H Content-Type:multipart/form-data -F "model={ name: "${pipelineParams.nameApp}", targets: [ "${pipelineParams.cluster}" ]}" -F "deployment=@${pipelineParams.nameApp}.${pipelineParams.ext}" -X POST http://${ip}:${port}/management/wls/latest/deployments/application
    """
  }

  validateUndeployDeploy(pipelineParams.nameApp, 'resultDeploy.json', 'deploy')
}

def undeploy (Map pipelineParams) {

  def ip = getData(
      project: pipelineParams.project,
      environment: pipelineParams.environment,
      object: 'ip'
    )
  def port = getData(
      project: pipelineParams.project,
      environment: pipelineParams.environment,
      object: 'port'
    )
  def idUserWeblogic = getData(
      project: pipelineParams.project,
      environment: pipelineParams.environment,
      object: 'idUserWeblogic'
    )

  withCredentials([usernamePassword(credentialsId: idUserWeblogic, passwordVariable: 'pass', usernameVariable: 'user')]) {
    sh """
      curl -v -o resultUndeploy.json -u ${user}:${pass} -H X-Requested-By:MyClient -H Accept:application/json -X DELETE http://${ip}:${port}/management/wls/latest/deployments/application/id/${pipelineParams.nameApp}
    """
  }
  validateUndeployDeploy(pipelineParams.nameApp, 'resultUndeploy.json', 'undeploy')
}

def validateUndeployDeploy (nameApp, message, operation) {
  result = readJSON file: "${message}"
  if (operation == 'undeploy') {
    if (result.messages['message'][0].contains('was not found')){
      println "La aplicación ${nameApp} no existe"
    } else if (result.messages['severity'][0] == "SUCCESS"){
      println "La aplicación ${nameApp} se elimino correctamente"
    } else {
      error("Error eliminando la aplicación ${nameApp}")
    }
  }
  if (operation == 'deploy') {
    if (result.messages['severity'][0] == "SUCCESS"){
      println "Aplicación ${nameApp} desplegada correctamente"
    } else {
      error ("Error desplegando la aplicación ${nameApp}")
    }
  }
}
