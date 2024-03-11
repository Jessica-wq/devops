package org.porvenir
import org.porvenir.pipelineUtility
import groovy.transform.Field
import groovy.json.JsonSlurper
import jenkins.model.*

@Field def pipelineUtil = new pipelineUtility()

/**********************************************************************************************************
*scanFortify: Realiza el analisis de seguridad de la aplicación
*Parámetros:
    - 
*Acción:
*El metodo utiliza el step sh para ejecutar la herramienta SCA por linea de comandos
*Primera linea (clean) realiza una limpieza inicial
*Segunda linea realiza la traducción del codigo
*Tercer linea (scan) realiza el analisis de la traduccion usando las opciones
***/
def scanFortify(String routeScan, String nameProject, String template){
    pipelineUtil.printm("Realizando escaneo de Seguridad", "INFO")
    sh "/opt/Fortify/Fortify_SCA_23.2.0/bin/sourceanalyzer -Dcom.fortify.sca.ProjectRoot=./ -clean -b ${nameProject}"
    sh "/opt/Fortify/Fortify_SCA_23.2.0/bin/sourceanalyzer -Dcom.fortify.sca.ProjectRoot=./ -b ${nameProject} -source 11 ${routeScan}"
    sh "/opt/Fortify/Fortify_SCA_23.2.0/bin/sourceanalyzer -Dcom.fortify.sca.ProjectRoot=./ -b ${nameProject} -scan -Xmx4G -f ${nameProject}.fpr"
    if (fileExists("result.fpr")){
      mergeFPR(nameProject)
    }
    sh "/opt/Fortify/Fortify_Tools/bin/BIRTReportGenerator -template 'OWASP Top 10' -source ${nameProject}.fpr -format PDF --Version 'OWASP Top 10 ${template}' --UseFortifyPriorityOrder --SecurityIssueDetails -output code.pdf"
   // sh "/opt/Fortify/Fortify_SCA_and_Apps_20.2.0/bin/BIRTReportGenerator -template 'PCI DSS Compliance: Application Security Requirements' -source code.fpr -format PDF --Version 'PCI 3.2.1' --UseFortifyPriorityOrder --SecurityIssueDetails -output code.pdf"
}

def scanFortifyPython(String routeScan, int version, String nameProject, String template){
    pipelineUtil.printm("Realizando escaneo de Seguridad", "INFO")
    sh "/opt/Fortify/Fortify_SCA_23.2.0/bin/sourceanalyzer -Dcom.fortify.sca.ProjectRoot=./ -clean -b ${nameProject}"
    sh "/opt/Fortify/Fortify_SCA_23.2.0/bin/sourceanalyzer -Dcom.fortify.sca.ProjectRoot=./ -b ${nameProject} -Dcom.fortify.sca.PythonVersion=${version}  ${routeScan}"
    sh "/opt/Fortify/Fortify_SCA_23.2.0/bin/sourceanalyzer -Dcom.fortify.sca.ProjectRoot=./ -b ${nameProject} -scan -f ${nameProject}.fpr"
    sh "/opt/Fortify/Fortify_Tools/bin/BIRTReportGenerator -template 'OWASP Top 10' -source ${nameProject}.fpr -format PDF --Version 'OWASP Top 10 ${template}' --UseFortifyPriorityOrder --SecurityIssueDetails -output code.pdf"
    //sh "/opt/Fortify/Fortify_SCA_and_Apps_20.2.0/bin/BIRTReportGenerator -template 'PCI DSS Compliance: Application Security Requirements' -source code.fpr -format PDF --Version 'PCI 3.2.1' --UseFortifyPriorityOrder --SecurityIssueDetails -output code.pdf"
}

def scanFortifyMobile(String routeScan, String nameProject, String template){
    pipelineUtil.printm("Realizando escaneo de Seguridad", "INFO")
    sh "/opt/Fortify/Fortify_SCA_23.2.0/bin/sourceanalyzer -Dcom.fortify.sca.ProjectRoot=./ -clean -b ${nameProject}"
    sh "/opt/Fortify/Fortify_SCA_23.2.0/bin/sourceanalyzer -Dcom.fortify.sca.ProjectRoot=./ -b ${nameProject} ${routeScan} -exclude '**/MockRepository/**/*' -exclude '**/DataSource/**/*' -exclude '**/TestConstants/**/*' "
    sh "/opt/Fortify/Fortify_SCA_23.2.0/bin/sourceanalyzer -Dcom.fortify.sca.ProjectRoot=./ -b ${nameProject} -scan -Xmx4G -f ${nameProject}.fpr"
    if (fileExists("result.fpr")){
      mergeFPR(nameProject)
    }
    sh "/opt/Fortify/Fortify_Tools/bin/BIRTReportGenerator -template 'OWASP Top 10' -source ${nameProject}.fpr -format PDF --Version 'OWASP Top 10 ${template}' --UseFortifyPriorityOrder --SecurityIssueDetails -output code.pdf"
   // sh "/opt/Fortify/Fortify_SCA_and_Apps_20.2.0/bin/BIRTReportGenerator -template 'PCI DSS Compliance: Application Security Requirements' -source code.fpr -format PDF --Version 'PCI 3.2.1' --UseFortifyPriorityOrder --SecurityIssueDetails -output code.pdf"
}

/***********************************************************************************************************
*Metodo que permite subir el fpr al SSC de Fortify
***/
def uploadFPR(String nameProject){
  sh """
      mv ${nameProject}.fpr ${nameProject}_${BUILD_NUMBER}.fpr
    """
  pipelineUtil.printm("Upload FPR -- App: ${nameProject} -- Version: ${nameProject}", "INFO")
  fortifyUpload appName: "${nameProject}", appVersion: "${nameProject}", resultsFile: "${nameProject}_${BUILD_NUMBER}.fpr"
}
/*def uploadFPR(boolean upload){
    withCredentials([string(credentialsId: 'tokenFortifyUp', variable: 'token')]) {
        sh"/opt/Fortify/Fortify_SCA_and_Apps_20.2.0/bin/fortifyclient -url http://10.160.144.198:8080/ssc -authtoken ${token} uploadFPR -file code.fpr -application ${env.JOB_NAME} -version ${BUILD_NUMBER}"
    }
}*/

def downloadFPR(String nameProject){
  def token = getAuthToken()
  def idVersion = getIdProjectVersion(token, nameProject,"${nameProject}")
  if (idVersion) {
    withCredentials([string(credentialsId: "fortify_token_download", variable: 'fortifyDownload')]) {
      sh """
        /opt/Fortify/Fortify_Tools/tools/fortifyclient downloadFPR -file result.fpr -url http://10.160.144.198:8080/ssc -authtoken ${fortifyDownload} -project "${nameProject}" -version "${nameProject}"
      """
    }
  }else {
    pipelineUtil.printm("No existe projecto ${nameProject}, se realizara escaneo desde cero", "INFO")
  }
}

def mergeFPR(nameProject) {
  sh """
    mv ${nameProject}.fpr ${nameProject}_copy.fpr
    /opt/Fortify/Fortify_Tools/bin/FPRUtility -merge -project result.fpr -source ${nameProject}_copy.fpr -f ${nameProject}.fpr
  """
}

/***********************************************************************************************************
*Metodo que ejecuta el script ScriptFortifyValidation.sh dentro del pod de Fortify
***/
/*def verifyVulnerabilites(){
    pipelineUtil.printm("Verificando Vulnerabilidades", "INFO")
    sh "/home/jenkins/ScriptFortifyValidation.sh './code.fpr'"
}*/


/***********************************************************************************************************
*Metodo que permite realizar el escaneo de seguridad de una imagen docker con la herramienta TRIVY
*Parametros:
    - image: String, nombre de la imagen a analizar
    - severity: String, severidades a mostrar en el escaneo (HIGH,CRITICAL,MEDIUM,LOW) por defecto '' las toma todas
    - stop: Booleano, determina si detiene el pipeline (true) cuando encuentre vulnerabilidades o no lo detiene (false)
***/
def scanTrivy(Map pipelineParams = [:]){
    def image = (pipelineParams.image == "" ? error('Por favor registra la imagen a analizar'): pipelineParams.image  )
    def severity = (pipelineParams.severity == "" ? "": "--severity ${pipelineParams.severity}"  )
    def stop = (pipelineParams.stop == true ? "--no-progress --exit-code 1":"")
    
    sh "trivy image --download-java-db-only"
    sh "trivy image ${stop} ${severity} ${image} >> report.txt"
}



/**
 Metodo para identificar las vulnerabilidades de fortify.
*/
def verifyVulnerabilites(String projectName , String template){
  println "Vulnerabilidades  ${projectName} -- Version: ${projectName} \n"
  def token = getAuthToken()
  def idVersion = getIdProjectVersion(token, projectName,"${projectName}")
  def contCritical =getContIssue(token,idVersion,"critical",template)
  def contHig =getContIssue(token,idVersion,"high",template)
  def contMedium =getContIssue(token,idVersion,"medium",template)
  def contLow =getContIssue(token,idVersion,"low",template)
  
  println "Verificación de vulnerabilidades \n"
  println "contCritical: "+contCritical
  println "contHigh: "+contHig
  println "contMedium: "+contMedium
  println "contLow: "+contLow

   sh """
    echo contCritical: ${contCritical} >> vulnerability.txt
    echo contHigh: ${contHig} >> vulnerability.txt
    echo contMedium: ${contMedium} >> vulnerability.txt
    echo contLow: ${contLow} >> vulnerability.txt
  """

  if( (contCritical + contHig) > 0 ){
      env.fortifyState="failed"
      error("Por favor revisa fortify hay vulnerabilidades que hay que revisar. revisa el pdf que fue enviado al correo.")
  }

}


/**
  Metodo para obtener el token fortify para consumir el api fortify ssc
*/
def getAuthToken() {
  def tokenApi
  withCredentials([string(credentialsId: "fortify-temp-token-api", variable: 'fortifyTokenApi')]) {
    tokenApi = "FortifyToken "+ fortifyTokenApi
  }
  return tokenApi
}




def getIdProjectVersion(def token, def nameProject, def version){
  def urlFortify = "http://10.160.144.198:8080/ssc/api/v1/projectVersions?start=0&limit=0&q=name%3A${version}&fulltextsearch=false&includeInactive=false&myAssignedIssues=false&onlyIfHasIssues=false"
  def idVer
  def http_client = new  URL(urlFortify).openConnection() as HttpURLConnection
  http_client.setRequestProperty("Authorization", token);
  http_client.setRequestProperty("Accept", "application/json");
  http_client.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
  http_client.setRequestMethod("GET");
  http_client.connect();

  def json_response = new JsonSlurper().parse(http_client.inputStream)

  for(int i=0; i<json_response["count"]; i++){

    if(json_response["data"][i]["project"]["name"] == nameProject ){

      idVer = json_response["data"][i]["id"];

      break;

    }

  }

  println "id del projecto: "+idVer

  return idVer

}


/*
  metodo que retorna la cantidad de vulnerabilidades del projecto 
  recibiendo como parametros:
  token -> token fortify
  idVersion -> de la aplicacion fortify para analizar las vulnerabilidades
  typeIssue -> tipo de vulnerabilidad a consultar si son high,medium, critical o low
*/
def getContIssue(def token, def idVersion, def typeIssue, String template){
  def contA1=0,contA2=0,contA3=0,contA4=0,contA5=0,contA6=0,contA7=0,contA8=0,contA9=0,contA10=0;
  def count=0

  def urlFortify = "http://10.160.144.198:8080/ssc/api/v1/projectVersions/${idVersion}/issues?start=0&limit=0&qm=issues&q=%5BOWASP%20Top%2010%20${template}%5D%3AA%20%5Bfortify%20priority%20order%5D%3A${typeIssue}&fields=id%2CfoundDate%2Creferences"
 
  def http_client = new  URL(urlFortify).openConnection() as HttpURLConnection
  http_client.setRequestProperty("Authorization", token);
  http_client.setRequestProperty("Accept", "application/json");
  http_client.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
  http_client.setRequestMethod("GET");
  http_client.connect();

  
  def json_response = new JsonSlurper().parse(http_client.inputStream)
  
  for(int i=0; i<json_response["count"]; i++){
    (stateBaseLine,issueReferences) = getIssuesWithoutBaseLine(json_response["data"][i]["id"], token)
    if(stateBaseLine == 1){
            count++;
            (contA1,contA2,contA3,contA4,contA5,contA6,contA7,contA8,contA9,contA10) = checkOwaspCategory(issueReferences,contA1,contA2,contA3,contA4,contA5,contA6,contA7,contA8,contA9,contA10,template)
            
    }
  
  }

    println "********************* Categorias de vulnerabilidad ${typeIssue} \n"
   
    println "Categoria A1 "+contA1
    println "Categoria A2 "+contA2
    println "Categoria A3 "+contA3
    println "Categoria A4 "+contA4
    println "Categoria A5 "+contA5
    println "Categoria A6 "+contA6
    println "Categoria A7 "+contA7
    println "Categoria A8 "+contA8
    println "Categoria A9 "+contA9
    println "Categoria A1 "+contA1
    println "\n Total de vulnerabilidad ${typeIssue}: "+count
  
    return count

}


/*
  metodo para validar detalladamente la issue y retornar las referencias
  estas referencias contienen las bases de que tipo de vulnerabilidad es para el OWASP top 10 2017
  entre las categorias A1...A10
*/
def getIssuesWithoutBaseLine(idIssue, token){


  def urlFortify = "http://10.160.144.198:8080/ssc/api/v1/issueDetails/${idIssue}"
 
  def http_client = new  URL(urlFortify).openConnection() as HttpURLConnection
  http_client.setRequestProperty("Authorization", token);
  http_client.setRequestProperty("Accept", "application/json");
  http_client.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
  http_client.setRequestMethod("GET");
  http_client.connect();

  
  def json_response = new JsonSlurper().parse(http_client.inputStream)
    //customTagBaseLineId = "10a7554f-a395-4595-bd76-3b003c150731"
  references = json_response["data"]["references"]
  return [1,references]
    /*if(json_response["data"]["customTagValues"].size() > 0){
        for(int i=0; i<json_response["data"]["customTagValues"].size(); i++){
            customTagId = json_response["data"]["customTagValues"][i]["customTagGuid"]
            if(customTagId == customTagBaseLineId){
                references = "No_Aplica"
                return [0,references]
            }
        }
        return [1,references]
    }else*/
   
}

/*
  Metodo que clasifica la vulnerabildiad en base a las referencias de la vulnerabilidad para aumentar el -
  contador dependiendo la vulnerabilidad en A1..A10
*/
def checkOwaspCategory(data,contaA1,contaA2,contaA3,contaA4,contaA5,contaA6,contaA7,contaA8,contaA9,contaA10,template){
    posOwaspA = data.indexOf("OWASP Top 10 ${template}")+"OWASP Top 10 ${template}".length()+2
    owaspA = (data.substring(posOwaspA,posOwaspA+3)).replace(" ","")
    if(template == '2017'){
      switch(owaspA){
            case "A1":
              contaA1++;
            break;
            case "A2":
              contaA2++;
            break;
            case "A3":
              contaA3++;
            break;
            case "A4":
              contaA4++;
            break;
            case "A5":
              contaA5++;
            break;
            case "A6":
              contaA6++;
            break;
            case "A7":
              contaA7++;
            break;
            case "A8":
              contaA8++;
            break;
            case "A9":
              contaA9++;
            break;
            case "A10":
              contaA10++;
            break;
      } 
    }else if (template == '2021'){
        switch(owaspA){
            case "A01":
              contaA1++;
            break;
            case "A02":
              contaA2++;
            break;
            case "A03":
              contaA3++;
            break;
            case "A04":
              contaA4++;
            break;
            case "A05":
              contaA5++;
            break;
            case "A06":
              contaA6++;
            break;
            case "A07":
              contaA7++;
            break;
            case "A08":
              contaA8++;
            break;
            case "A09":
              contaA9++;
            break;
            case "A10":
              contaA10++;
            break;
      } 
    }
   return [contaA1, contaA2, contaA3, contaA4, contaA5, contaA6, contaA7, contaA8, contaA9, contaA10]
}
