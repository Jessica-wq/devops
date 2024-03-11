import groovy.transform.Field
import org.porvenir.pipelineUtility

@Field def pipelineUtil = new pipelineUtility()

/**********************************************************************************************************
*Metodo que permite realizar el scaneo de calidad de las fuentes de los proyectos MVN con Sonarqube  
*Paramatros:
    - nameProject: String, nombre del proyecto
    - idSonarToken: String, id de la credencial en el vault de jenkins con el token de sonar
***/
def sonarqubeMvn(Map pipelineParams = [:]) {
    withSonarQubeEnv('sonarqube'){
        withCredentials([string(credentialsId: pipelineParams.idSonarToken , variable: 'token')]) {
            sh "mvn clean package sonar:sonar -Dsonar.host.url=http://sonarqube/sonarqube  -Dsonar.projectKey=${pipelineParams.nameProject} -Dsonar.projectName=${pipelineParams.nameProject} -Dsonar.login=${token}"
        }
    }
    timeout(time: 1, unit: 'HOURS') { 
        def qg = waitForQualityGate() 
        if (qg.status != 'OK') {
            env.sonarqubeState="failed"
            error "Pipeline aborted due to quality gate failure: ${qg.status}"
        }
    }
}

/**********************************************************************************************************
*Metodo que permite realizar el scaneo de calidad de las fuentes de los proyectos MVN con Sonarqube  
*Paramatros:
    - nameProject: String, nombre del proyecto
    - idSonarToken: String, id de la credencial en el vault de jenkins con el token de sonar
***/
def sonarqubeMvnSonarScanner(Map pipelineParams = [:]) {
    stage('sonar') {
        withSonarQubeEnv('sonarqube'){
            withCredentials([string(credentialsId: pipelineParams.idSonarToken , variable: 'token')]) {
                sh "sonar-scanner -Dsonar.host.url=http://sonarqube/sonarqube -Dsonar.projectKey=${pipelineParams.nameProject} -Dsonar.projectName=${pipelineParams.nameProject} -Dsonar.sources=src/main/java -Dsonar.java.binaries=target/classes -Dsonar.coverage.jacoco.xmlReportPaths=jacoco.xml -Dsonar.exclusions=${pipelineParams.exclusion} -Dsonar.login=${token}"
            }
        }
        timeout(time: 1, unit: 'HOURS') { 
            def qg = waitForQualityGate() 
            if (qg.status != 'OK') {
                env.sonarqubeState="failed"
                error "Pipeline aborted due to quality gate failure: ${qg.status}"
            }
        }
    }
}

/**********************************************************************************************************
*Metodo que permite realizar el scaneo de calidad de las fuentes de los proyectos NPM con Sonarqube  
*Paramatros:
    - nameProject: String, nombre del proyecto
    - idSonarToken: String, id de la credencial en el vault de jenkins con el token de sonar
***/
def sonarqubeNpm(Map pipelineParams = [:]) {
    withSonarQubeEnv('sonarqube'){
        sh "sonar-scanner ${pipelineParams.sonarArgs}"
    }
    timeout(time: 1, unit: 'HOURS') { 
        def qg = waitForQualityGate() 
        if (qg.status != 'OK') {
            env.sonarqubeState="failed"
            error "Pipeline aborted due to quality gate failure: ${qg.status}"
        }
    }
}

def sonarqubeGradle(Map pipelineParams = [:]) {
    withSonarQubeEnv('sonarqube'){
        build.runGradleCmd("sonarqube ${pipelineParams.sonarArgs}")
    }
    timeout(time: 1, unit: 'HOURS') { 
            def qg = waitForQualityGate() 
            if (qg.status != 'OK') {
                env.sonarqubeState="failed"
                error "Pipeline aborted due to quality gate failure: ${qg.status}"
            }
        }   
}

def sonarqubePython(Map pipelineParams = [:]) {
    stage('sonar') {
        withSonarQubeEnv('sonarqube'){
            withCredentials([string(credentialsId: pipelineParams.idSonarToken , variable: 'token')]) {
                sh "sonar-scanner -Dsonar.host.url=http://sonarqube/sonarqube  -Dsonar.projectKey=${pipelineParams.nameProject} -Dsonar.projectName=${pipelineParams.nameProject} -Dsonar.python.coverage.reportPath=coverage.xml -Dsonar.exclusions=${pipelineParams.exclusion} -Dsonar.login=${token}"
            }
        }
        timeout(time: 1, unit: 'HOURS') { 
            def qg = waitForQualityGate() 
            if (qg.status != 'OK') {
                env.sonarqubeState="failed"
                error "Pipeline aborted due to quality gate failure: ${qg.status}"
            }
        }
    }
}

/**************************************************************************************************************************
*Metodo que permite realizar el escaneo de calidad de las fuentes de los proyectos basado en el comando sonar-scanner
*Paramatros:
    - project_name: String, nombre del proyecto
***/
def sonarScannerCommand(Map pipelineParams = [:]) {
    def project_name = pipelineParams.project_name
    def sources = ""
    try {
        sources = pipelineUtil.infoJsonProperty(
            jsonName: "sonarscanner-settings.json",
            project: project_name,
            property: "sources"
        )
    }catch(NullPointerException e){
        println("Exception NullObject .-.")
        def directory = sh (
            script: "[ -d \"target\" ] && echo \"true\" || echo \"false\"",
            returnStdout: true
            ).trim()
        println("EXISTS TARGET FOLDER? ${directory}")

        if (directory == "true"){
            project_name = "default-mvn"
        } else{
            project_name = "default"
        }
        
        sources = pipelineUtil.infoJsonProperty(
            jsonName: "sonarscanner-settings.json",
            project: project_name,
            property: "sources"
        )
    }
    
    def binaries = pipelineUtil.infoJsonProperty(
        jsonName: "sonarscanner-settings.json",
        project: project_name,
        property: "binaries"
    )
    def exclusions = pipelineUtil.infoJsonProperty(
        jsonName: "sonarscanner-settings.json",
        project: project_name,
        property: "exclusions"
    )

    withSonarQubeEnv('sonarqube'){
        sh "sonar-scanner -Dsonar.host.url=http://sonarqube/sonarqube -Dsonar.login=1c99b4cb9edfe4d325af351f5264a99f7536edef -Dsonar.projectKey=${pipelineParams.project_name} -Dsonar.projectName=${pipelineParams.project_name} -Dsonar.sources=${sources} -Dsonar.java.binaries=${binaries} -Dsonar.exclusions=${exclusions}"
    }
    timeout(time: 1, unit: 'HOURS') { 
        def qg = waitForQualityGate() 
        if (qg.status != 'OK') {
            env.sonarqubeState="failed"
            error "Pipeline aborted due to quality gate failure: ${qg.status}"
        }
    }
}

/**************************************************************************************************************************
*Metodo que permite realizar el escaneo de calidad de las fuentes de los proyectos basado en el comando sonar-scanner pero no valida los quality gates
*Paramatros:
    - project_name: String, nombre del proyecto
***/
def sonarScannerWithoutValidation(Map pipelineParams = [:]) {
    def project_name = pipelineParams.project_name
    def sources = ""
    try {
        sources = pipelineUtil.infoJsonProperty(
            jsonName: "sonarscanner-settings.json",
            project: project_name,
            property: "sources"
        )
    }catch(NullPointerException e){
        println("Exception NullObject .-.")
        def directory = sh (
            script: "[ -d \"target\" ] && echo \"true\" || echo \"false\"",
            returnStdout: true
            ).trim()
        println("EXISTS TARGET FOLDER? ${directory}")

        if (directory == "true"){
            project_name = "default-mvn"
        } else{
            project_name = "default"
        }
        
        sources = pipelineUtil.infoJsonProperty(
            jsonName: "sonarscanner-settings.json",
            project: project_name,
            property: "sources"
        )
    }
    
    def binaries = pipelineUtil.infoJsonProperty(
        jsonName: "sonarscanner-settings.json",
        project: project_name,
        property: "binaries"
    )
    def exclusions = pipelineUtil.infoJsonProperty(
        jsonName: "sonarscanner-settings.json",
        project: project_name,
        property: "exclusions"
    )

    withSonarQubeEnv('sonarqube'){
        sh "sonar-scanner -Dsonar.host.url=http://sonarqube/sonarqube -Dsonar.login=1c99b4cb9edfe4d325af351f5264a99f7536edef -Dsonar.projectKey=${pipelineParams.project_name} -Dsonar.projectName=${pipelineParams.project_name} -Dsonar.sources=${sources} -Dsonar.java.binaries=${binaries} -Dsonar.exclusions=${exclusions}"
    }
    timeout(time: 1, unit: 'HOURS') { 
        def qg = waitForQualityGate()
    }
}

/**************************************************************************************************************************
*Metodo que permite realizar el escaneo de calidad de las fuentes de los proyectos basado en el comando sonar-scanner
*Paramatros:
    - project_name: String, nombre del proyecto
***/
def sonarScannerBasicCommand(Map pipelineParams = [:]) {
    def project_name = pipelineParams.project_name
    def sources = "src"    
    def binaries = "."

    def directory = sh (
            script: "[ -d \"target\" ] && echo \"true\" || echo \"false\"",
            returnStdout: true
            ).trim()
        println("EXISTS TARGET FOLDER? ${directory}")

        if (directory == "true"){
            binaries = "target"
        } else{
            binaries = "build"
        }


    withSonarQubeEnv('sonarqube'){
        sh "sonar-scanner -Dsonar.host.url=http://sonarqube/sonarqube -Dsonar.login=1c99b4cb9edfe4d325af351f5264a99f7536edef -Dsonar.projectKey=${pipelineParams.project_name} -Dsonar.projectName=${pipelineParams.project_name} -Dsonar.sources=${sources} -Dsonar.java.binaries=${binaries}"
    }
    timeout(time: 1, unit: 'HOURS') { 
        def qg = waitForQualityGate() 
    }
}

/**************************************************************************************************************************
*Metodo que permite realizar el escaneo de calidad de las fuentes de los proyectos basado en el comando sonar-scanner
*Paramatros:
    - project_name: String, nombre del proyecto
***/
def sonarScannerBasicCommandNpm(Map pipelineParams = [:]) {
    def project_name = pipelineParams.project_name
    def sources = "."
  
    withSonarQubeEnv('sonarqube'){
        sh "sonar-scanner -Dsonar.host.url=http://sonarqube/sonarqube -Dsonar.login=1c99b4cb9edfe4d325af351f5264a99f7536edef -Dsonar.projectKey=${pipelineParams.project_name} -Dsonar.projectName=${pipelineParams.project_name} -Dsonar.sources=${sources}"
    }
    timeout(time: 1, unit: 'HOURS') { 
        def qg = waitForQualityGate() 
    }
}

/**********************************************************************************************************
*Metodo que permite realizar la consulta del resultado del escaneo de un proyecto usando la api de  sonarqube
*Paramatros:
    - projectKey: String, nombre del proyecto
***/
def getQualityGateStatus(Map pipelineParams=[:]){
  def response = ""
  def responseMetric = ""
  withCredentials([string(credentialsId: 'sonarToken', variable: 'SONAR_TOKEN')]) {
    def responseCurl = sh(returnStdout: true, script: """
    curl  --header 'Authorization: Bearer ${SONAR_TOKEN}' \
        http://sonarqube/sonarqube/api/qualitygates/project_status?projectKey=${pipelineParams.projectKey}
    """)
    
    def responsejson =  readJSON text: responseCurl
    response = "[projectStatus: ${responsejson.projectStatus.status}, #CONDITIONS]"

    for(metric in responsejson.projectStatus.conditions){
        responseMetric += "[metrica: ${metric.metricKey}, status: ${metric.status}, hallazgos: ${metric.actualValue}] "
    }

    return [responsejson.projectStatus.status,response.replaceAll("#CONDITIONS", responseMetric)]
  }
}
