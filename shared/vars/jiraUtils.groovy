import groovy.transform.Field
import org.porvenir.pipelineUtility

@Field def pipelineUtil = new pipelineUtility()

/******************************************
*********** Jira service management *******
*******************************************/

def createJiraServiceManagement( Map pipelineParams=[:]){

  def responsejson = [:]
 

  withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {

  //Unir el mensaje y generar el archivo datos.json
  def mensaje = """ {   "isAdfRequest": false,   "requestFieldValues": {     "summary": "Ticket de soporte para el  proyecto ${pipelineParams.nameProject}", "description": "Url: ${BUILD_URL} ;  Usuario: ${pipelineParams.user} ; Proyecto: ${pipelineParams.nameProject} ; stage ejecucion: ${pipelineParams.title} ; Version: ${pipelineParams.version} ","customfield_10424": "${pipelineParams.environment}","customfield_10425": "${pipelineParams.repo}"   },   "requestTypeId": "243",   "serviceDeskId": "21" } """ 
  sh "echo '${mensaje}' > datos.json"

    def responseIssue = sh(returnStdout: true, script: """
      curl --request POST \
      --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/servicedeskapi/request' \
      --header 'Authorization: Basic  ${JIRA_TOKEN}' \
      --header 'Accept: application/json' \
      --header 'Content-Type: application/json' \
      --data @datos.json

    """)

    responsejson=  readJSON text: responseIssue
    println "issueKey: "+ responsejson.issueKey
  
  }
  

  if (responsejson) {
    return responsejson.issueKey
  }else{
    return null
  }
 
  
  
}


def uploadFileJiraServiceManagement(Map pipelineParams = [:]){

  def responsejson = [:]
  def project = "MDAD"
  def files = pipelineParams.files

  withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {

    def responseIssue = sh(returnStdout: true, script: """
      curl -D- -X POST \
      --header 'Authorization: Basic  ${JIRA_TOKEN}' \
      -H 'X-ExperimentalApi: opt-in' \
      -H 'X-Atlassian-Token: no-check' \
      -F 'file=@${files}' https://jsp-sdlc-pr-prv-01.atlassian.net/rest/servicedeskapi/servicedesk/${project}/attachTemporaryFile \
    """)

    def lines = responseIssue.split('\n') 
    def index = (lines.size() - 1 )
        responsejson = readJSON text: lines[index]
       

      if (responsejson) {
        def array = []
          for (attachment in responsejson.temporaryAttachments) {
              array.add("\""+attachment.temporaryAttachmentId+"\"")
          }

          
        return array
      }else{
        return null
      }
  
  }


}


def AddFileServiceManagement(def filesIds, def ticked_id){

  def mensaje= """
        {
          "additionalComment": {
            "body": "Adjunto documentos para el ticker ${ticked_id}"
          },
          "public": true,
          "temporaryAttachmentIds": ${filesIds}
        }

  """
   sh "echo '${mensaje}' > attachment.json"

  withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {
    def responseIssue = sh(returnStdout: true, script: """
      curl --request POST \
      --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/servicedeskapi/request/${ticked_id}/attachment' \
      --header 'Authorization: Basic  ${JIRA_TOKEN}' \
      --header 'Accept: application/json' \
      --header 'Content-Type: application/json' \
      --data @attachment.json

    """)

  }
}


def moveStateServiceManagement(def state,def  ticked_id){

 withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {
    def msj = ""
    def transition= ""
    switch(state) {
      case "init":
        msj= "iniciacion despliegue automatico desde jenkins"
        transition = "81"
        break

      case "failed":
        msj= "Despliegue fallido revisar log ${BUILD_URL}"
        transition = "121"
        break
      case "end":
        msj= "Despliegue finalizado correctamente."
        transition = "111"
        break
      default:
        break

    }

    sh """
      curl --request POST \\
        --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/issue/${ticked_id}/transitions' \\
      --header 'Authorization: Basic ${JIRA_TOKEN} ' \\
        --header 'Accept: application/json' \\
        --header 'Content-Type: application/json' \\
        --data  '{
          "transition": {
            "id": "${transition}"
          }  
        }'

    """
    sh """
      curl --request POST \\
        --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/servicedeskapi/request/${ticked_id}/comment' \\
        --header 'Authorization: Basic ${JIRA_TOKEN}' \\
        --header 'Accept: application/json' \\
        --header 'Content-Type: application/json' \\
        --data '{
        "body": "${msj}",
        "public": true
        }'
    """
  }

}


/*******************************************************
*******************  Jira software **********************
**********************************************************/


def createJiraSoftwareFortify( Map pipelineParams=[:]){
  def responsejson = [:]
  def transicion =0
  def vulnerability
  def label = pipelineParams.proyecto != null ? pipelineParams.proyecto : pipelineParams.nameProject

 if(fileExists('vulnerability.txt')){

    def archivo = readFile 'vulnerability.txt'
   vulnerability = archivo.split('\n')   

  for (vuln in vulnerability) {
    temp = vuln.split(': ')
    switch(temp[0]) {
      case "contCritical": 
        if(temp[1].toInteger() > 0){
          println "prueba ${temp[0]} "+temp[1]
          transicion= 51
          break
        }
      case "contHigh":
       if(temp[1].toInteger() > 0){
          println "prueba ${temp[0]} "+temp[1]
          transicion= 61
          break
        }
      case "contMedium":
        if(temp[1].toInteger() > 0){
           println "prueba ${temp[0]} "+temp[1]
          transicion= 71
          break
        }
      case "contLow":
        if(temp[1].toInteger() > 0){
           println "prueba ${temp[0]} "+temp[1]
          transicion= 81
          break
        }
     
    }
    if(transicion > 0){
      break
    }
  }

  if(transicion == 0){
    transicion = 41
  }


  println "transition: "+transicion

   println "vulnerabilidades"+vulnerability

 }

    //Obtener el id del usuario en base al correo proporcionado
    def getIdUser = getIdUserJira(pipelineParams.email)
    // Crear el ticked en jira 
  withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {

    def responseIssue = sh(returnStdout: true, script: """
     curl --request POST \\
          --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/issue' \\
          --header 'Authorization: Basic ${JIRA_TOKEN}' \\
          --header 'Accept: application/json' \\
          --header 'Content-Type: application/json' \\
          --data '{
          "fields": {
            "assignee": {
              "id": "${getIdUser}"
            },
            "project":{
              "key": "LBFD"
            },
            "summary":"${pipelineParams.nameProject}",
            "description": "Ticket creado automaticamente por jenkins,  repositorio:  ${pipelineParams.repo},  rama:  ${pipelineParams.branch},  job: ${BUILD_URL}, Vulnerabilidades : ${vulnerability}",
            "issuetype":{
              "id": "10044"
            },
            "priority": {
              "id": "3"
            },
            "labels": [
              "topOwasp2021",
              "${label}"
            ],
            "customfield_10014": "DV2-3"
          },
          "transition":{
            "id": "${transicion}"
          }

        }'

    """)

    responsejson=  readJSON text: responseIssue
 
  }

 if (responsejson) {
    return responsejson.key
  }else{
    return null
  }

}

def getIdUserJira(String email){

    try {
    withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {

      def responseIssue = sh(returnStdout: true, script: """ 
        curl --request GET \
        --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/user/search?query=${email}' \
        --header 'Authorization: Basic ${JIRA_TOKEN}' \
        --header 'Accept: application/json' 
      
      """)
      responsejson=  readJSON text: responseIssue
      println "accountId :"+ responsejson[0].accountId

    }

    return responsejson[0].accountId
    }catch(NullPointerException e){
      println "No se encontro usuario correo en jira."
      def emailIdDevops="712020:7e772fe2-b70e-4f06-8dd6-6a4a734fb699"
      return emailIdDevops
    }
}


def addFileJiraSoftware(String ticked_id){
  withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {

      def responseIssue = sh(returnStdout: true, script: """ 
        curl -D- -X POST 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/issue/${ticked_id}/attachments' \
        -H 'Authorization: Basic ${JIRA_TOKEN}' \
        -H 'X-Atlassian-Token: no-check' \
        -F 'file=@code.pdf' 
      
      """)
     

    }
}



def moveStateJiraSoftwareSubTask(Map pipelineParams=[:]){

  if(fileExists('vulnerability.txt')){
    def archivo = readFile 'vulnerability.txt'
    vulnerability = archivo.split('\n')
    def contVulnerability = 0
    for (vuln in vulnerability) {
        temp = vuln.split(': ')
        if(temp[0] == "contCritical" || temp[0] == "contHigh"){
          contVulnerability += temp[1].toInteger()
        }
    }

    println "resutado final contador vulnera: "+contVulnerability
    /* Estados de la subtaks 
       31 -> listo
       41 -> fallido
    */
    def transition = contVulnerability > 0 ? '41':'31'
    withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {
      sh """
        curl --request POST \\
          --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/issue/${pipelineParams.issue}/transitions' \\
        --header 'Authorization: Basic ${JIRA_TOKEN} ' \\
          --header 'Accept: application/json' \\
          --header 'Content-Type: application/json' \\
          --data  '{
            "transition": {
              "id": "${transition}"
            }  
          }'

      """
    }

  }else{
    println "no existe el archivo vulnerability.txt"
  }
}

def issuelinkJiraSoftware(Map pipelineParams=[:]){

    def idIssue= pipelineParams.issue
    def idLineBase= pipelineParams.ticked_id
    def vulnerability

     if(fileExists('vulnerability.txt')){

      def archivo = readFile 'vulnerability.txt'
      vulnerability = archivo.split('\n')   

    }

 withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {



    try{
            def responseIssue = sh(returnStdout: true, script: """ 
            curl -s --request GET \
            --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/issue/${idIssue}' \
            --header 'Authorization: Basic ${JIRA_TOKEN}' \
            --header 'Accept: application/json' 
            
            """)
            responsejson=  readJSON text: responseIssue
            println "La incidencia ${idIssue} esta en estado : " +responsejson.fields.status.statusCategory.key

              if(responsejson.fields.status.statusCategory.key != "done" ){
                sh """
                  curl --request POST \\
                  --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/issueLink' \\
                  --header 'Authorization: Basic ${JIRA_TOKEN}' \\
                  --header 'Accept: application/json' \\
                  --header 'Content-Type: application/json' \\
                  --data '{
                  "comment": {
                  "body": "Se realiza escaneo fortfy en el repositorio: ${pipelineParams.repo}, en base a la rama: ${pipelineParams.branch}, con el ultimo commit: ${pipelineParams.commit}, generando las siguientes vulnerabilidades: ${vulnerability}, Estado del despliegue: ${pipelineParams.result}"
                  },
                  "inwardIssue": {
                  "key": "${idIssue}"
                  },
                  "outwardIssue": {
                  "key": "${idLineBase}"
                  },
                  "type": {
                  "name": "Relacionar"
                  }
                  }'
              """
              }else{
                println "no se linkea la issue ya que la tarea: ${idIssue} esta en estado: finalizada" 
              }
           
          }catch(NullPointerException e){
              println "${responsejson.errorMessages[0]}"
          }
 
  }
}

def deleteIssueExists(Map pipelineParams=[:]){
  idIssue=null
  withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {
     def responseIssue = sh(returnStdout: true, script: """ 
          curl --request GET \
          --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/search?jql=labels%20%3D%20\"${pipelineParams.project}\"' \
          --header 'Authorization: Basic ${JIRA_TOKEN}' \
          --header 'Accept: application/json' 
     
     """)
    responsejson=  readJSON text: responseIssue
    if (responsejson.issues[0]) {
      idIssue=responsejson.issues[0].key
    }else{
      idIssue=null
    }

  }

  if(idIssue != null){
    withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {
      
      sh """

      curl --request DELETE \
      --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/issue/${idIssue}' \
      --header 'Authorization: Basic ${JIRA_TOKEN}' \
      --header 'Accept: application/json'

      """


    }
  }
}

/**********************************************************************************************************
*Metodo que permite consultar la existencia de una unica incidencia en Jira por medio de una consulta JQL
 si esxiste una unica incidencia, retorna su issueId
*Paramatros:
    - jql: String, con la consulta JQL a ejecutar
***/
def existsIssue(Map pipelineParams=[:]){
  def issueId = [null,null,null]
  withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {
     def responseCurl = sh(returnStdout: true, script: """
      curl  --request POST \
            --url "https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/3/search" \
            --header 'Authorization: Basic ${JIRA_TOKEN}' \
            --header 'Accept: application/json' \
            --header 'Content-Type: application/json' \
            --data '{
            "fields": [
              "summary",
              "status",
              "assignee",
              "issuetype"
            ],
            "fieldsByKeys": false,
            "jql": "${pipelineParams.jql}",
            "maxResults": 5,
            "startAt": 0
            }'
     """)
    def responsejson =  readJSON text: responseCurl
    println "responsejson.: ${responsejson}"
    println "responsejson.total: ${responsejson.total}"
    issueId[1]=responsejson.total
    if (responsejson.total == 1) {
      issueId[0]=responsejson.issues[0].key
      println "issueId: ${responsejson.issues[0].key}"
    }
    if (responsejson.total > 1) {
      issueId[2]=responsejson.issues[0].key
      println "duplicated issueId: ${responsejson.issues[0].key}"
    }
  }
  return issueId
}

/**********************************************************************************************************
*Metodo que permite eliminar una incidencia de Jira indicando su issueId
*Paramatros:
    - issueId: String, con la consulta JQL a ejecutar
***/
def deleteIssue(Map pipelineParams=[:]){
  def issueId = null
  withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {
     def responseCurl = sh(returnStdout: true, script: """
      curl --request DELETE \
      --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/issue/${pipelineParams.issueId}' \
      --header 'Authorization: Basic ${JIRA_TOKEN}' \
      --header 'Accept: application/json'
     """)
  }
}

/**********************************************************************************************************
*Metodo que permite crear una incidencia en un tablero de Jira
*Paramatros:
    - projectKey: String, indicador clave del tablero
    - summary: String, con el titulo que llevara la incidencia
    - description: String, con descripcion de la incidencia
    - issueType: String, con el id identificador del tipo de incidencia a crear (se debe consultar previamente para cada uno de los tableros)
    - transition: String, con el id de la transition o estado donde se va a ubicar la incidencia en el tablero (se debe consultar previamente para cada uno de los tableros)
    - email: String, correo de la persona responsable de la incidencia
***/
def createIssue(Map pipelineParams=[:]){
  def issueId = null
  withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {
     def responseCurl = sh(returnStdout: true, script: """
      curl  --request POST \
            --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/issue' \
            --header 'Authorization: Basic ${JIRA_TOKEN}' \
            --header 'Accept: application/json' \
            --header 'Content-Type: application/json' \
            --data '{
            "fields": {
              "project":{
                "key": "${pipelineParams.projectKey}"
              },
              "summary":"${pipelineParams.summary}",
              "description": "${pipelineParams.description}",
              "issuetype":{
                "id": "${pipelineParams.issueType}"
              },
              "priority": {
                "id": "3"
              },
              "parent": {
                "key": "DV2-3"
              },
              "assignee": {
                "id": "${getIdUserJira(pipelineParams.email)}"
              }
            },
            "transition":{
              "id": "${pipelineParams.transition}"
            }
            }'
     """)
  }
}



/***************************************************
************** Epicas, tareas automaticas **********
****************************************************/
def createEpicTemplate(Map pipelineParams = [:]){

    def yamlRespons = pipelineUtil.infoEpicYaml(yamlName: 'project-epic-template.yaml')


    yamlRespons.epics.each { epic -> 
    
    responseEpicIssues = createEpica(
                  projectKey: pipelineParams.projectKey,
                  summary: epic.name
                 )
    println "idNewIssueEpic: "+ responseEpicIssues[0] + " idIssueEpic: "+responseEpicIssues[1]
    
    epic.tasks.each{ task ->
      responseTaskIssues=  createTask(
          projectKey: pipelineParams.projectKey,
          parentNewEpic: responseEpicIssues[0],
          parentExistsEpic: responseEpicIssues[1],
          summary: task.name
        )

      println "idNewIssueTask: "+ responseTaskIssues[0] + "idIssueTask"+responseTaskIssues[1]
    }
    
    
  }

}

def createEpica(Map pipelineParams = [:]){

 withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {
   //variables metodo
    idIssue=null
    idNewIssue= null

    //se busca la existencia de la issue en base projectKey,summary y retorna el id si no existe queda en null
    idIssue= searchIssue( projectKey: pipelineParams.projectKey, summary: pipelineParams.summary, type: "epic" )
    if(idIssue == null){

      responseNewIssue= sh(returnStdout: true, script: """ 
        curl --request POST \\
          --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/issue' \\
        --header 'Authorization: Basic ${JIRA_TOKEN} ' \\
          --header 'Accept: application/json' \\
          --header 'Content-Type: application/json' \\
          --data '{
                    "fields": {
                      "project":{
                        "key": "${pipelineParams.projectKey}"
                      },
                      "summary":"${pipelineParams.summary}",
                      "issuetype":{
                        "id": "10000"
                      }
                    }
                  }'
      """)
      responseEpicNew=  readJSON text: responseNewIssue
      idNewIssue= responseEpicNew.key
    }
    return [idNewIssue,idIssue]
  }

}


def createTask(Map pipelineParams = [:]){
  //variables
  def keyEpic=""
  if(pipelineParams.parentNewEpic != null){
    keyEpic=pipelineParams.parentNewEpic
  }else{
    keyEpic=pipelineParams.parentExistsEpic
  }

  withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {
    //variables metodo
    idIssue=null
    idNewIssue= null

    //se busca la existencia de la issue en base projectKey,summary y retorna el id si no existe queda en null
    idIssue= searchIssue( projectKey: pipelineParams.projectKey, summary: pipelineParams.summary, keyEpic: keyEpic , type: "task" )
    if(idIssue == null){
      //Creacion de la tarea asociada a la epica
      responseNewIssue= sh(returnStdout: true, script: """ 
          curl --request POST \
          --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/issue' \
          --header 'Authorization: Basic ${JIRA_TOKEN}' \
          --header 'Accept: application/json' \
          --header 'Content-Type: application/json' \
          --data '{
            "fields": {
              "project":{
                "key": "${pipelineParams.projectKey}"
              },
              "summary":"${pipelineParams.summary}",
              "issuetype":{
                "id": "10044"
              },
              "parent": {
                "key": "${keyEpic}"
              }
            }
          }'
        """)


        responseTaskNew=  readJSON text: responseNewIssue
        idNewIssue= responseTaskNew.key
    }

    return [idNewIssue,idIssue]
  }
 
}



def searchIssue(Map pipelineParams = [:]){
  //variables
  idIssue=null
    def jql =''
    //Procesar summary de la issue para buscar si existe en el proyecto.
    nameProccess= sh(returnStdout: true, script: """ echo ${pipelineParams.summary} | sed 's/ /%20/g'""").replace('\n', '')

    if(pipelineParams.type == "task"){
        jql ="project%20%3D%20${pipelineParams.projectKey}%20AND%20issuetype%20%3D%20Task%20AND%20parent%20%3D%20${pipelineParams.keyEpic}%20AND%20text%20~%20\"${nameProccess}\""
    }else if(pipelineParams.type == "epic"){
        jql= "project%20%3D%20\"${pipelineParams.projectKey}\"%20AND%20summary~\"${nameProccess}\""
    }

  
    
    withCredentials([string(credentialsId: 'jira-token', variable: 'JIRA_TOKEN')]) {
      //Consulta api para la existencia de la incidencia. Epic
       responseIssue = sh(returnStdout: true, script: """ 
      curl --request GET \
      --url 'https://jsp-sdlc-pr-prv-01.atlassian.net/rest/api/2/search?jql=${jql}' \
      --header 'Authorization: Basic ${JIRA_TOKEN}' \
      --header 'Accept: application/json' 

      """)
    
     
      //Lectura de la incidencia si existe se captura el ID
      responsejson=  readJSON text: responseIssue
      if (responsejson.issues[0]) {
        idIssue=responsejson.issues[0].key
      }

    }

    return idIssue
}

/**********************************************************************************************************
*Metodo que crear una incidencia en el tablero de Jira 'LBSD', validando incidencias existentes
*Paramatros:
    - projectName: String, nombre del proyecto escaneado
    - branch: String, nombre del la rama escaneada
    - responsable: String, correo de la persona responsable de la incidencia
***/
def createINCJiraLBSD(Map pipelineParams=[:]){
  def jiraProject = "LBSD"
  def currentIssueId = existsIssue(jql: "project = ${jiraProject} AND summary ~ \\\"${pipelineParams.branch}-${pipelineParams.projectName}\\\" ORDER BY created DESC")
  if (currentIssueId[1] > 1){
    jenkinsUtils.printm("Se encontró más de una incidencia creada con el mismo nombre.", "ERROR")
    deleteIssue(
      issueId: currentIssueId[2]
    )
  }
  if ((currentIssueId[0] != null) && (currentIssueId[1] == 1)){
    deleteIssue(
      issueId: currentIssueId[0]
    )
  }
  def sonarAnalysisResult = sonar.getQualityGateStatus(
          projectKey: pipelineParams.projectName
        )
  def transitionId = (sonarAnalysisResult[0] == "OK") ? "31" : "11"
  
  def ramas = ["develop", "release", "main"]
  if(!(pipelineParams.branch in ramas)){
    pipelineParams.branch = "develop"
  }

  if(currentIssueId[1] == 1){
    createIssue(
      projectKey: jiraProject,
      summary : pipelineParams.branch+"-"+pipelineParams.projectName,
      description : "Análisis SonarQube del proyecto: ${pipelineParams.projectName} Rama: ${pipelineParams.branch}. \\nURL: https://devops.porvenir.com/sonarqube/dashboard?id=${pipelineParams.projectName} \\nResultado del análisis:\\n${sonarAnalysisResult[1]}",
      issueType : "10015",
      transition : transitionId,
      email: pipelineParams.responsable
    )
  }
}



def getBuildUser() {
    def user = ''

        try {
             user = currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
        } catch(Exception ex) {
             println "\n\n-- No se obtuvo el id del usuario que se ejecuto se asigna null \n";
            
            user= null
        }
    return user
}
